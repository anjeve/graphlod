package graphlod.output.renderer;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import graphlod.algorithms.GraphFeatures;
import graphlod.dataset.Dataset;
import org.apache.commons.lang3.StringUtils;
import org.jgraph.graph.DefaultEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class GraphRenderer {
    private static final Logger logger = LoggerFactory.getLogger(GraphRenderer.class);

    private static final int MAX_VERTICES_PER_GROUP = 500000000;
    public static final int MIN_VERTICES = 2;
    private String fileName;

	private String filePath;

	private List<DotFile> files;
    private ExecutorService pool;
    
    private HashMap<String, String> classColors = new HashMap<>();

	private Dataset dataset;

	private boolean colored;

    private static class DotFile {
        public int vertices;
        private int graphs;
        public String fileName;

        public DotFile(String fileName, int vertices, int graphs) {
            this.fileName = fileName;
            this.vertices = vertices;
            this.graphs = graphs;
        }
    }

    private static class DotFileSorter implements Comparator<DotFile> {
        @Override
        public int compare(DotFile o1, DotFile o2) {
            return Integer.compare(o1.vertices, o2.vertices);
        }
    }

    public GraphRenderer(String fileName, String output, int threadcount) {
        this.filePath = output;
        //this.filePath = fileName.replaceFirst("(?s)"+this.fileName+"(?!.*?"+this.fileName+")", "");
        logger.debug(this.filePath);
        this.fileName = new File(fileName).getName();
        logger.debug(this.fileName);
        files = new ArrayList<>();
        pool = Executors.newFixedThreadPool(threadcount);
    }


    public void setDataset(Dataset dataset) {
        this.dataset = dataset;
    }
    
    public String getFilePath() {
		return filePath;
	}
    
    public String getFileName() {
		return fileName;
	}

    public void writeDotFile(String type, GraphFeatures features, boolean colored) {
    	ArrayList<GraphFeatures> featureList = new ArrayList<GraphFeatures>();
    	featureList.add(features);
    	writeDotFiles(type, featureList, colored);
    }
    
    public void writeDotFiles(String type, List<GraphFeatures> features, boolean colored) {
        this.colored = colored;
        try {
            int c = 0;
            int lastVertexCount = 0;
            int vertexCount;
            HashMap<Integer, Integer> written = new HashMap<>();
            Writer writer = null;
            for (GraphFeatures f : features) {
                vertexCount = f.getVertexCount();
                int alreadyWrittenForSizeGroup = 0;
                if (written.containsKey(vertexCount)) {
                    alreadyWrittenForSizeGroup = written.get(vertexCount);
                }
                String dotFileName = this.filePath + "dot/" + this.fileName + "_" + type + "_dotgraph" + (vertexCount) + ".txt";
                if (lastVertexCount != vertexCount) {
                    if (lastVertexCount == 0) {
                        lastVertexCount = vertexCount;
                    } else {
                        closeDot(writer);
                    }
                    writer = createDot(dotFileName);
                }
                if (f.getVertexCount() >= MIN_VERTICES) {
                    if ((alreadyWrittenForSizeGroup + f.getVertexCount() < MAX_VERTICES_PER_GROUP) /* || (!written.containsKey(vertexCount) && (f.getVertexCount() < MAX_VERTICES_PER_GROUP))*/ ) {
                        alreadyWrittenForSizeGroup += f.getVertexCount();
                        written.remove(vertexCount);
                        written.put(vertexCount, alreadyWrittenForSizeGroup);
                        writeDot(f, writer);
                    }
                }
                c++;
                if (((lastVertexCount != vertexCount) && (written.containsKey(vertexCount))) || (c == features.size() && (written.containsKey(vertexCount)))) {
                    logger.debug(c + " " + alreadyWrittenForSizeGroup + " " + dotFileName);
                    files.add(new DotFile(dotFileName, alreadyWrittenForSizeGroup, written.size()));
                }
                lastVertexCount = vertexCount;
            }
            if (writer != null) {
                closeDot(writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<String> render() {
        ArrayList<DotFile> sorted = new ArrayList<>(files);
        Collections.sort(sorted, new DotFileSorter()); // process small files first
        final List<String> htmlFiles = Collections.synchronizedList(new ArrayList<String>());
        final AtomicInteger jobs = new AtomicInteger(0);

        for (final DotFile file : sorted) {
        	jobs.incrementAndGet();
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    if ((file.vertices > MAX_VERTICES_PER_GROUP) && (file.graphs == 1)) {
                        logger.warn("Won't process " + file.fileName + " (too large)");
                    } else {
                        logger.debug("Processing visualization for " + file.vertices + " vertices in " + file.graphs + " graphs, output: " + file.fileName);
                        callGraphViz(file.fileName);
                        String htmlFile = createHtml(file.fileName);
                        if (htmlFile != null) {
                            htmlFiles.add(htmlFile);
                        }
                    }
                    if (!new File(file.fileName).delete()) {
                        logger.warn("could not delete: " + file.fileName);
                    }
                    jobs.decrementAndGet();
                }
            });
        }
        pool.shutdown();
        try {
            while(!pool.awaitTermination(10, TimeUnit.SECONDS)){
            	logger.debug("Waiting on " + jobs.get() + " graphviz jobs");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        files.clear();
        return htmlFiles;
    }

    private String createHtml(String fileName) {
        String htmlFile = null;
        try {
            logger.debug("Creating HTML statistics for " + fileName);
            File file = new File(fileName + ".html");
            String localFileName = file.getName();
            Files.createParentDirs(file);
            BufferedWriter out = Files.newWriter(file, Charsets.UTF_8);
            htmlFile = fileName + ".html";
            BufferedReader map = Files.newReader(new File(fileName + ".cmapx"), Charsets.UTF_8);
            out.write("<img src=\"" + localFileName.replaceAll("\\.html$", "") + ".png\" USEMAP=\"#G\" />\n");
            String line;
            while ((line = map.readLine()) != null) {
                out.write(line + "\n");
            }
            map.close();
            out.close();
            if (!new File(fileName + ".cmapx").delete()) {
                logger.warn("could not delete: " + fileName + ".cmapx");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return htmlFile;
    }

    private Writer createDot(String fileName) throws IOException {
        File file = new File(fileName);
        Files.createParentDirs(file);
        Writer writer = new BufferedWriter(new FileWriter(file));
        writer.write("digraph G {\n");
        writer.write("node [fontname=Verdana,fontsize=12]\n");
        writer.write("node [style=filled]\n");
        writer.write("node [fillcolor=\"#888888\"]\n");
        writer.write("node [color=\"#888888\"]\n");
        writer.write("node [shape=point]\n");
        writer.write("node [width=\"0.1\"]\n");
        writer.write("edge [color=\"#31CEF0\"]\n");
        writer.write("edge [arrowsize=\"0.5\"]\n");
        return writer;
    }

    private void writeDot(GraphFeatures features, Writer writer) throws IOException {
        for (String vertex : features.getVertices()) {
            writer.write("\t\"" + vertex + "\" ");
            writer.write("[");
            if (this.colored && (dataset.getClass(vertex) != null)) {
                //logger.debug(vertex + " will be of color " + getColor(dataset.getClass(vertex)));
            	writer.write("color=\"#" + getColor(dataset.getClass(vertex)) + "\",fillcolor=\"#" + getColor(dataset.getClass(vertex)) + "\",");
            }
            writer.write("tooltip=\"" + vertex.substring(StringUtils.lastOrdinalIndexOf(vertex, "/", 2)) + "\" ");
            writer.write("URL=\"" + vertex + "\"]\n");
        }
        for (DefaultEdge edge : features.getEdges()) {
            writer.write("\t\"" + edge.getSource() + "\" -> \"" + edge.getTarget() + "\"\n");
        }
    }

    private String getColor(String className) {
    	if (classColors.containsKey(className)) {
    		return classColors.get(className);
    	}
    	int R = (int)(Math.random()*256);
    	int G = (int)(Math.random()*256);
    	int B= (int)(Math.random()*256);
    	Color color = new Color(R, G, B); //random color, but can be bright or dull

    	//to get rainbow, pastel colors
    	Random random = new Random();
    	final float hue = random.nextFloat();
    	final float saturation = 0.9f;//1.0 for brilliant, 0.0 for dull
    	final float luminance = 1.0f; //1.0 for brighter, 0.0 for black
    	color = Color.getHSBColor(hue, saturation, luminance);
    	
    	String hexString = Integer.toHexString( color.getRGB() & 0x00ffffff );
    	while(hexString.length() < 6) {
    	    hexString = "0" + hexString;
    	}
    	classColors.put(className, hexString);
    	return hexString;
    }
    
    private void closeDot(Writer writer) throws IOException {
        writer.write("}");
        writer.close();
    }

    private void callGraphViz(final String fileName) {
        try {
            Process p = new ProcessBuilder("sfdp", "-Goverlap=prism", "-Tpng", "-Tcmapx", "-Gsize=300,300", "-O", fileName).start();
            p.waitFor();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

}
