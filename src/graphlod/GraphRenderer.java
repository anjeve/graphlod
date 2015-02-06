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

import org.apache.commons.lang3.StringUtils;
import org.jgraph.graph.DefaultEdge;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class GraphRenderer {

	private static final int MAX_VERTICES_PER_GROUP = 5000;
	public static final int MIN_VERTICES = 4;
	private String name;

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

	List<DotFile> files;

	public GraphRenderer(String name) {
		this.name = name;
		files = new ArrayList<>();
	}


	public void writeDotFiles(String type, List<GraphFeatures> features) {
		try {
			int i = 0;
			int lastI = 0;
			int fileCounter = 0;
			while (i < features.size()) {
				String fileName = "dot/" + name + "_" + type + "_dotgraph" + (fileCounter++) + ".txt";
				Writer writer = createDot(fileName);
				int written = 0;
				lastI = i;
				while (i < features.size() && (written == 0 || written + features.get(i).getVertexCount() < MAX_VERTICES_PER_GROUP)) {
					GraphFeatures f = features.get(i);
					if (f.getVertexCount() > MIN_VERTICES) {
						written += f.getVertexCount();
						writeDot(f, writer);
					} else {
						lastI++;
					}
					i++;
				}
				closeDot(writer);
				files.add(new DotFile(fileName, written, i - lastI));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void render() {
		ArrayList<DotFile> sorted = new ArrayList<>(files);
		Collections.sort(sorted, new DotFileSorter()); // process small files first

		for (DotFile file : sorted) {
			System.out.printf("processing visualization for %s vertices in %s graphs, output: %s\n", file.vertices, file.graphs, file.fileName);

			callGraphViz(file.fileName);
			createHtml(file.fileName);
			if (!new File(file.fileName).delete()) {
				System.out.println("could not delete: " + file.fileName);
			}
		}
		files.clear();
	}

	private void createHtml(String fileName) {
		try {
			BufferedWriter out = Files.newWriter(new File(fileName + ".html"), Charsets.UTF_8);
			BufferedReader map = Files.newReader(new File(fileName + ".cmapx"), Charsets.UTF_8);
			out.write("<img src=\"" + StringUtils.stripStart(fileName, "dot/") + ".png\" USEMAP=\"#G\" />\n");
			String line;
			while ((line = map.readLine()) != null) {
				out.write(line + "\n");
			}
			map.close();
			out.close();
			if (!new File(fileName + ".cmapx").delete()) {
				System.out.println("could not delete: " + fileName + ".cmapx");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
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

	private void callGraphViz(String fileName) {
		Process p = null;
		try {
			p = new ProcessBuilder("sfdp", "-Goverlap=prism", "-Tpng", "-Tcmapx", "-Gsize=120,90", "-O", fileName).start();
			p.waitFor();
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}


}
