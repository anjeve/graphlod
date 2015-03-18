package graphlod.output;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import graphlod.algorithms.GraphFeatures;
import graphlod.graph.Degree;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import com.google.common.base.Charsets;
import com.google.common.base.Verify;

public class VertexCsvOutput {

	CSVPrinter writer;

	public VertexCsvOutput(String name) {
		Writer out;
		try {
			Path path = Paths.get(name + "_vertices.csv");
			out = Files.newBufferedWriter(path, Charsets.UTF_8);
			writer = CSVFormat.DEFAULT.withHeader("graph", "vertex", "indegree", "outdegree").print(out);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void writeGraph(GraphFeatures graph) {
		List<Degree> inDegrees = graph.getIndegrees2();
		List<Degree> outDegrees = graph.getOutdegrees2();

		for (int i = 0; i < inDegrees.size(); i++) {
			Degree in = inDegrees.get(i);
			Degree out = outDegrees.get(i);
			Verify.verify(in.vertex.equals(out.vertex));
			try {
				writer.printRecord(graph.getId(), in.vertex, in.degree, out.degree);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}
	public void close() {
		try {
			writer.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}
