package graphlod;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Multiset;
import com.google.common.collect.TreeMultiset;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class GraphLOD {
	private static final String DEFAULT_DATASET_LOCATION = "/Users/anjeve/Desktop/keket backup/mappingbased_properties_en.nt";
	private static final Logger logger = Logger.getLogger(GraphLOD.class);
	public static final int MAX_SIZE_FOR_DIAMETER = 500;

	public GraphLOD(Collection<String> datasetFiles, boolean skipChromaticNumber, String namespace, Collection<String> excludedNamespaces, float minImportantSubgraphSize, int importantDegreeCount) {
		Stopwatch sw = Stopwatch.createStarted();
		Dataset dataset = Dataset.fromFiles(datasetFiles, namespace, excludedNamespaces);

		GraphFeatures graphFeatures = new GraphFeatures(dataset.getGraph());

		System.out.println("Loading the dataset took " + sw + " to execute.");

		System.out.println("Vertices: " + formatInt(graphFeatures.getVertexCount()));
		System.out.println("Edges: " + formatInt(graphFeatures.getEdgeCount()));

		sw = Stopwatch.createStarted();
		if (graphFeatures.isConnected()) {
			System.out.println("Connectivity: yes");
		} else {
			System.out.println("Connectivity: no");
		}

		if (!graphFeatures.isConnected()) {
			List<Set<String>> sets = graphFeatures.getConnectedSets();
			System.out.println("Connected sets: " + formatInt(sets.size()));

			Multiset<Integer> componentSizes = TreeMultiset.create();
			for (Set<String> component : sets) {
				componentSizes.add(component.size());
			}

			System.out.println("  Components (and sizes): ");
			for (Multiset.Entry<Integer> group : componentSizes.entrySet()) {
				System.out.println("    " + group.getCount() + " x " + group.getElement());
			}
		}

		List<Set<String>> sci_sets = graphFeatures.getStronglyConnectedSets();
		System.out.println("Strongly connected components: " + formatInt(sci_sets.size()));

		Multiset<Integer> sciSizes = TreeMultiset.create();
		for (Set<String> component : sci_sets) {
			sciSizes.add(component.size());
		}
		System.out.println("  Components (and sizes): ");
		for (Multiset.Entry<Integer> group : sciSizes.entrySet()) {
			System.out.println("    " + group.getCount() + " x " + group.getElement());
		}

		System.out.println("Getting the connectivity took " + sw + " to execute.");

		if (graphFeatures.isConnected()) {
			System.out.printf("Graph: %s vertices\n", graphFeatures.getVertexCount());
			analyzeConnectedGraph(graphFeatures, importantDegreeCount);
		} else {
			sw = Stopwatch.createStarted();
			List<GraphFeatures> connectedSubgraphs = graphFeatures.getConnectedSubGraphFeatures(minImportantSubgraphSize);
			for (GraphFeatures subGraph : connectedSubgraphs) {
				System.out.printf("Subgraph: %s vertices\n", subGraph.getVertexCount());
				analyzeConnectedGraph(subGraph, importantDegreeCount);
			}
			System.out.println("Analysing the subgraphs took " + sw + " to execute.");
		}

		System.out.println("Vertex Degrees:");
		List<Integer> indegrees = graphFeatures.getIndegrees();
		System.out.printf("Average indegree: %.3f\n", CollectionUtils.average(indegrees));
		System.out.println("Max indegree: " + CollectionUtils.max(indegrees));
		System.out.println("Min indegree: " + CollectionUtils.min(indegrees));

		List<Integer> outdegrees = graphFeatures.getOutdegrees();
		System.out.printf("Average outdegree: %.3f\n", CollectionUtils.average(outdegrees));
		System.out.println("Max outdegree: " + CollectionUtils.max(outdegrees));
		System.out.println("Min outdegree: " + CollectionUtils.min(outdegrees));


		ArrayList<Integer> edgeCounts = graphFeatures.getEdgeCounts();
		System.out.printf("Average links: %.3f\n", CollectionUtils.average(edgeCounts));

		if (!skipChromaticNumber) {
			sw = Stopwatch.createStarted();
			int cN = graphFeatures.getChromaticNumber();
			System.out.println("Chromatic Number: " + cN);
			System.out.println("Getting the Chromatic Number took " + sw + " to execute.");
		}
	}

	private void analyzeConnectedGraph(GraphFeatures graph, int importantDegreeCount) {
		Preconditions.checkArgument(graph.isConnected());
		if (graph.getVertexCount() < MAX_SIZE_FOR_DIAMETER) {
			System.out.printf("\tedges: %s, diameter: %s\n", graph.getEdgeCount(), graph.getDiameter());
		} else {
			System.out.println("\tGraph too big to show diameter");
		}

		System.out.println("\thighest indegrees:");
		System.out.println("\t\t" + StringUtils.join(graph.maxInDegrees(importantDegreeCount), "\n\t\t"));
		System.out.println("\thighest outdegrees:");
		System.out.println("\t\t" + StringUtils.join(graph.maxOutDegrees(importantDegreeCount), "\n\t\t"));
	}

	private String formatInt(int integer) {
		return NumberFormat.getNumberInstance(Locale.US).format(integer);
	}

	public static void main(final String[] args) {
		ArgumentParser parser = ArgumentParsers.newArgumentParser("GraphLOD")
				.defaultHelp(true).description("calculates graph features.");
		parser.addArgument("dataset").nargs("+").setDefault(Arrays.asList(DEFAULT_DATASET_LOCATION));
		parser.addArgument("--namespace").type(String.class).setDefault("");
		parser.addArgument("--excludedNamespaces").nargs("*").setDefault(Collections.emptyList());
		parser.addArgument("--skipChromatic").action(Arguments.storeTrue());
		parser.addArgument("--minImportantSubgraphSize").type(Integer.class).action(Arguments.store()).setDefault(20);
		parser.addArgument("--importantDegreeCount").type(Integer.class).action(Arguments.store()).setDefault(5);
		Namespace result = null;
		try {
			result = parser.parseArgs(args);
		} catch (ArgumentParserException e) {
			parser.handleError(e);
			System.exit(1);
		}
		List<String> dataset = result.getList("dataset");
		String namespace = result.getString("namespace");
		List<String> excludedNamespaces = result.getList("excludedNamespaces");
		boolean skipChromatic = result.getBoolean("skipChromatic");
		int minImportantSubgraphSize = result.getInt("minImportantSubgraphSize");
		int importantDegreeCount = result.getInt("importantDegreeCount");

		System.out.println("reading: " + dataset);
		System.out.println("namespace: " + namespace);
		System.out.println("skip chromatic: " + skipChromatic);
		System.out.println("excluded namespaces: " + excludedNamespaces);
		System.out.println("min important subgraph size: " + minImportantSubgraphSize);
		System.out.println("number of important degrees: " + importantDegreeCount);
		System.out.println();

		Locale.setDefault(Locale.US);

		new GraphLOD(dataset, skipChromatic, namespace, excludedNamespaces, minImportantSubgraphSize, importantDegreeCount);
	}

}
