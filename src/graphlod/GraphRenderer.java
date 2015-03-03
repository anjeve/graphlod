package graphlod;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jgraph.graph.DefaultEdge;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class GraphRenderer {
    private static final Logger logger = Logger.getLogger(GraphRenderer.class);

    private static final int MAX_VERTICES_PER_GROUP = 5000;
    public static final int MIN_VERTICES = 1;
    private String fileName;
    private String filePath;
    private List<DotFile> files;
    private ExecutorService pool;


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

    public GraphRenderer(String fileName, boolean debugMode, String output, int threadcount) {
        if (debugMode) {
            logger.setLevel(Level.DEBUG);
        } else {
            logger.setLevel(Level.INFO);
        }
        this.filePath = output;
        //this.filePath = fileName.replaceFirst("(?s)"+this.fileName+"(?!.*?"+this.fileName+")", "");
        logger.debug(this.filePath);
        this.fileName = new File(fileName).getName();
        logger.debug(this.fileName);
        files = new ArrayList<>();
        pool = Executors.newFixedThreadPool(threadcount);
    }


    public void writeDotFiles(String type, List<GraphFeatures> features) {
        try {
            int i = 0;
            int lastI = 0;
            int fileCounter = 0;
            while (i < features.size()) {
                String dotFileName = this.filePath + "dot/" + this.fileName + "_" + type + "_dotgraph" + (fileCounter++) + ".txt";
                Writer writer = createDot(dotFileName);
                int written = 0;
                lastI = i;
                while (i < features.size() && (written == 0 || written + features.get(i).getVertexCount() < MAX_VERTICES_PER_GROUP)) {
                    GraphFeatures f = features.get(i);
                    if (f.getVertexCount() >= MIN_VERTICES) {
                        written += f.getVertexCount();
                        writeDot(f, writer);
                    } else {
                        lastI++;
                    }
                    i++;
                }
                closeDot(writer);
                files.add(new DotFile(dotFileName, written, i - lastI));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<String> render() {
        ArrayList<DotFile> sorted = new ArrayList<>(files);
        Collections.sort(sorted, new DotFileSorter()); // process small files first
        final List<String> htmlFiles = Collections.synchronizedList(new ArrayList<String>());

        for (final DotFile file : sorted) {
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    logger.debug("Processing visualization for " + file.vertices + " vertices in " + file.graphs + " graphs, output: " + file.fileName);

                    if (file.vertices > MAX_VERTICES_PER_GROUP) {
                        logger.warn("Won't process " + file.fileName + " (too large)");
                    } else {
                        callGraphViz(file.fileName);
                        String htmlFile = createHtml(file.fileName);
                        if (htmlFile != null) {
                            htmlFiles.add(htmlFile);
                        }
                    }
                    if (!new File(file.fileName).delete()) {
                        logger.warn("could not delete: " + file.fileName);
                    }
                }
            });
        }
        try {
            while(!pool.awaitTermination(10, TimeUnit.SECONDS)){
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
            writer.write("[tooltip=\"" + vertex.substring(StringUtils.lastOrdinalIndexOf(vertex, "/", 2)) + "\" ");
            writer.write("URL=\"" + vertex + "\"]\n");
        }
        for (DefaultEdge edge : features.getEdges()) {
            writer.write("\t\"" + edge.getSource() + "\" -> \"" + edge.getTarget() + "\"\n");
        }
    }

    private void closeDot(Writer writer) throws IOException {
        writer.write("}");
        writer.close();
    }

    private void callGraphViz(final String fileName) {
        try {
            Process p = new ProcessBuilder("sfdp", "-Goverlap=prism", "-Tpng", "-Tcmapx", "-Gsize=120,90", "-O", fileName).start();
            p.waitFor();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

}
