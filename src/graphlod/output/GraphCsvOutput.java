package graphlod.output;


import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import graphlod.utils.CollectionUtils;
import graphlod.algorithms.GraphFeatures;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import com.google.common.base.Charsets;

public class GraphCsvOutput {

	private final CSVPrinter writer;
	private final int maxSizeForDiameter;

	public GraphCsvOutput(String name, int maxSizeForDiameter) {
		this.maxSizeForDiameter = maxSizeForDiameter;
		Writer out;
		try {
			Path path = Paths.get(name + "_graphs.csv");
			out = Files.newBufferedWriter(path, Charsets.UTF_8);
			writer = CSVFormat.DEFAULT.withHeader("graph", "vertices", "edges", "diameter", "avgindegree", "maxindegree", "avgoutdegree", "maxoutdegree").print(out);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void writeGraph(GraphFeatures graph) {
		double diameter = graph.getVertexCount() < maxSizeForDiameter ? graph.getDiameter() : -1;
		try {
			writer.printRecord(graph.getId(), graph.getVertexCount(), graph.getEdgeCount(), diameter,
					CollectionUtils.average(graph.getIndegrees()), CollectionUtils.max(graph.getIndegrees()),
					CollectionUtils.average(graph.getOutdegrees()), CollectionUtils.max(graph.getOutdegrees()));
		} catch (IOException e) {
			e.printStackTrace();
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
