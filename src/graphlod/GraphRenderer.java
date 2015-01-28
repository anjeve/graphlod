package graphlod;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jgraph.graph.DefaultEdge;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class GraphRenderer {

	private static final int MAXVERTICES = 5000;

	public GraphRenderer() {

	}


	public void render(String name, List<GraphFeatures> features) {
		try {
			int i = 0;
			int lastI = 0;
			int fileCounter = 0;
			while (i < features.size()) {
				String fileName = name + "_dotgraph" + (fileCounter++) + ".txt";
				Writer writer = createDot(fileName);
				int written = 0;
				lastI = i;
				while (written == 0 || i < features.size() && written + features.get(i).getVertexCount() < MAXVERTICES  ) {
					GraphFeatures f = features.get(i);
					written += f.getVertexCount();
					writeDot(f, writer);
					i++;
				}
				closeDot(writer);
				System.out.printf("processing visualization for %s vertices in %s graphs, output: %s\n", written, i - lastI, fileName);

				callGraphViz(fileName);
				createHtml(fileName);
				if (!new File(fileName).delete()) {
					System.out.println("could not delete: " + fileName);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void createHtml(String fileName) {
		try {
			BufferedWriter out = Files.newWriter(new File(fileName + ".html"), Charsets.UTF_8);
			BufferedReader map = Files.newReader(new File(fileName + ".cmapx"), Charsets.UTF_8);
			out.write("<img src=\"" + fileName + ".png\" USEMAP=\"#G\" />\n");
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

	public Writer createDot(String fileName) throws IOException {
		Writer writer = new BufferedWriter(new FileWriter(fileName));
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

	public void writeDot(GraphFeatures features, Writer writer) throws IOException {
		for (String vertex : features.getVertices()) {
			writer.write("\t\"" + vertex + "\" ");
			writer.write("[tooltip=\"" + vertex.substring(StringUtils.lastOrdinalIndexOf(vertex, "/", 2)) + "\" ");
			writer.write("URL=\"" + vertex + "\"]\n");
		}
		for (DefaultEdge edge : features.getEdges()) {
			writer.write("\t\"" + edge.getSource() + "\" -> \"" + edge.getTarget() + "\"\n");
		}
	}

	public void closeDot(Writer writer) throws IOException {
		writer.write("}");
		writer.close();
	}

	public void callGraphViz(String fileName) {
		Process p = null;
		try {
			p = new ProcessBuilder("sfdp", "-Goverlap=prism", "-Tpng", "-Tcmapx", "-Gsize=120,90", "-O", fileName).start();
			p.waitFor();
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}


}
