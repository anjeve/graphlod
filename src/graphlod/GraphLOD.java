package graphlod;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.log4j.Logger;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class GraphLOD {
	private static String defaultDatasetLocation = "/Users/anjeve/Desktop/keket backup/mappingbased_properties_en.nt";
	private static final Logger logger = Logger.getLogger(GraphLOD.class);

	public GraphLOD(String datasetLocation, boolean skipChromaticNumber) {
		long t1 = System.currentTimeMillis();
		Dataset dataset = new Dataset(datasetLocation);
		//DirectedGraph<String, DefaultEdge> graph = dataset.getGraph();
		GraphFeatures graphFeatures = new GraphFeatures(dataset);

		long t2 = System.currentTimeMillis();
		System.out.println("Loading the dataset took " + (t2 - t1) + " milliseconds to execute.");

		System.out.println("Vertices: " + formatInt(dataset.getVertices()));
		System.out.println("Edges: " + formatInt(dataset.getEdges()));

		long t5 = System.currentTimeMillis();
		if (graphFeatures.isConnected()) {
			System.out.println("Connectivity: yes");
		} else {
			System.out.println("Connectivity: no");
		}

		List<Set<String>> sets = graphFeatures.getConnectedSets();
		System.out.println("Connected sets: " + formatInt(sets.size()));

		ArrayList<Integer> componentSizes = new ArrayList<Integer>();
		for (Set<String> component : sets) {
			componentSizes.add(component.size());
		}
		Collections.sort(componentSizes);

		Set<Integer> uniqComponentSizes = new HashSet<Integer>(componentSizes);
		System.out.println("  Components (and sizes): ");
		for (Integer integer : uniqComponentSizes) {
			int freq = Collections.frequency(componentSizes, integer);
			System.out.println("    " + freq + " x " + integer);
		}

//		System.out.println(" Set sizes: " + StringUtils.join(componentSizes,","));

		List<Set<String>> sci_sets = graphFeatures.getStronglyConnectedSets();
		System.out.println("Strongly connected components: " + formatInt(sci_sets.size()));

		ArrayList<Integer> sciSizes = new ArrayList<Integer>();
		for (Set<String> component : sci_sets) {
			sciSizes.add(component.size());
		}
		Collections.sort(componentSizes);

		Set<Integer> uniqSCSizes = new HashSet<Integer>(sciSizes);
		System.out.println("  Components (and sizes): ");
		for (Integer integer : uniqSCSizes) {
			int freq = Collections.frequency(sciSizes, integer);
			System.out.println("    " + freq + " x " + integer);
		}


		long t6 = System.currentTimeMillis();
		System.out.println("Getting the connectivity took " + (t6 - t5) + " milliseconds to execute.");

		if (graphFeatures.isConnected()) {
			long t3 = System.currentTimeMillis();
			double diameter = graphFeatures.getDiameter();
			System.out.println("Diameter: " + diameter);
			long t4 = System.currentTimeMillis();
			System.out.println("Getting the diameter took " + (t4 - t3) + " milliseconds to execute.");
		}

		System.out.println("Average indegree: " + calculateAverage(graphFeatures.getIndegrees()));
		System.out.println("Average outdegree: " + calculateAverage(graphFeatures.getOutdegrees()));

		ArrayList<Integer> edgeCounts = graphFeatures.getEdgeCounts();
		System.out.println("Average links: " + calculateAverage(edgeCounts));

		if (!skipChromaticNumber) {
			long t7 = System.currentTimeMillis();
			int cN = graphFeatures.getChromaticNumber();
			System.out.println("Chromatic Number: " + cN);
			long t8 = System.currentTimeMillis();
			System.out.println("Getting the Chromatic Number took " + (t8 - t7) + " milliseconds to execute.");
		}
	}

	private double calculateAverage(List<Integer> marks) {
		Integer sum = 0;
		if (!marks.isEmpty()) {
			for (Integer mark : marks) {
				sum += mark;
			}
			return sum.doubleValue() / marks.size();
		}
		return sum;
	}

	private String formatInt(int integer) {
		return NumberFormat.getNumberInstance(Locale.US).format(integer);
	}

	public static void main(final String[] args) {
		ArgumentParser parser = ArgumentParsers.newArgumentParser("GraphLOD")
				.defaultHelp(true).description("calculates graph features.");
		parser.addArgument("database").nargs("?").setDefault(defaultDatasetLocation);
		parser.addArgument("--skipChromatic").action(Arguments.storeTrue());
		Namespace result = null;
		try {
			result = parser.parseArgs(args);
		} catch (ArgumentParserException e) {
			parser.handleError(e);
			System.exit(1);
		}
		new GraphLOD(result.getString("database"), result.getBoolean("skipChromatic"));
	}

}
