package graphlod;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.log4j.Logger;

import com.google.common.base.Stopwatch;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class GraphLOD {
	private static final String DEFAULT_DATASET_LOCATION = "/Users/anjeve/Desktop/keket backup/mappingbased_properties_en.nt";
	private static final Logger logger = Logger.getLogger(GraphLOD.class);

	public GraphLOD(String datasetLocation, boolean skipChromaticNumber) {
		Stopwatch sw = Stopwatch.createStarted();
		Dataset dataset = new Dataset(datasetLocation);
		//DirectedGraph<String, DefaultEdge> graph = dataset.getGraph();
		GraphFeatures graphFeatures = new GraphFeatures(dataset.getGraph());

		System.out.println("Loading the dataset took " + sw + " to execute.");

		System.out.println("Vertices: " + formatInt(dataset.getVertices()));
		System.out.println("Edges: " + formatInt(dataset.getEdges()));

		sw = Stopwatch.createStarted();
		if (graphFeatures.isConnected()) {
			System.out.println("Connectivity: yes");
		} else {
			System.out.println("Connectivity: no");
		}

		List<Set<String>> sets = graphFeatures.getConnectedSets();
		System.out.println("Connected sets: " + formatInt(sets.size()));

		ArrayList<Integer> componentSizes = new ArrayList<>();
		for (Set<String> component : sets) {
			componentSizes.add(component.size());
		}
		Collections.sort(componentSizes);

		Set<Integer> uniqComponentSizes = new HashSet<>(componentSizes);
		System.out.println("  Components (and sizes): ");
		for (Integer integer : uniqComponentSizes) {
			int freq = Collections.frequency(componentSizes, integer);
			System.out.println("    " + freq + " x " + integer);
		}

//		System.out.println(" Set sizes: " + StringUtils.join(componentSizes,","));

		List<Set<String>> sci_sets = graphFeatures.getStronglyConnectedSets();
		System.out.println("Strongly connected components: " + formatInt(sci_sets.size()));

		ArrayList<Integer> sciSizes = new ArrayList<>();
		for (Set<String> component : sci_sets) {
			sciSizes.add(component.size());
		}
		Collections.sort(componentSizes);

		Set<Integer> uniqSCSizes = new HashSet<>(sciSizes);
		System.out.println("  Components (and sizes): ");
		for (Integer integer : uniqSCSizes) {
			int freq = Collections.frequency(sciSizes, integer);
			System.out.println("    " + freq + " x " + integer);
		}


		long t6 = System.currentTimeMillis();
		System.out.println("Getting the connectivity took " + sw + " to execute.");

		if (graphFeatures.isConnected()) {
			sw = Stopwatch.createStarted();
			double diameter = graphFeatures.getDiameter();
			System.out.println("Diameter: " + diameter);
			System.out.println("Getting the diameter took " + sw + " to execute.");
		}

		List<Integer> indegrees = graphFeatures.getIndegrees();
		System.out.printf("Average indegree: %.3f\n", CollectionAggregates.average(indegrees));
		System.out.println("Max indegree: " + CollectionAggregates.max(indegrees));
		System.out.println("Min indegree: " + CollectionAggregates.min(indegrees));
		List<Integer> outdegrees = graphFeatures.getOutdegrees();
		System.out.printf("Average outdegree: %.3f\n", CollectionAggregates.average(outdegrees));
		System.out.println("Max outdegree: " + CollectionAggregates.max(outdegrees));
		System.out.println("Min outdegree: " + CollectionAggregates.min(outdegrees));

		ArrayList<Integer> edgeCounts = graphFeatures.getEdgeCounts();
		System.out.printf("Average links: %.3f\n", CollectionAggregates.average(edgeCounts));

		if (!skipChromaticNumber) {
			long t7 = System.currentTimeMillis();
			int cN = graphFeatures.getChromaticNumber();
			System.out.println("Chromatic Number: " + cN);
			long t8 = System.currentTimeMillis();
			System.out.println("Getting the Chromatic Number took " + (t8 - t7) + " to execute.");
		}
	}

	private String formatInt(int integer) {
		return NumberFormat.getNumberInstance(Locale.US).format(integer);
	}

	public static void main(final String[] args) {
		ArgumentParser parser = ArgumentParsers.newArgumentParser("GraphLOD")
				.defaultHelp(true).description("calculates graph features.");
		parser.addArgument("dataset").nargs("?").setDefault(DEFAULT_DATASET_LOCATION);
		parser.addArgument("--skipChromatic").action(Arguments.storeTrue());
		Namespace result = null;
		try {
			result = parser.parseArgs(args);
		} catch (ArgumentParserException e) {
			parser.handleError(e);
			System.exit(1);
		}
		new GraphLOD(result.getString("dataset"), result.getBoolean("skipChromatic"));
	}

}
