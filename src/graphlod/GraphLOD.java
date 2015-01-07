package graphlod;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.log4j.Logger;

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

	public GraphLOD(Collection<String> datasetLocations, boolean skipChromaticNumber, Collection<String> excludedNamespaces, float minImportantSubgraphSize) {
		Stopwatch sw = Stopwatch.createStarted();
		Dataset dataset = Dataset.fromFiles(datasetLocations, excludedNamespaces);

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
			sw = Stopwatch.createStarted();
			double diameter = graphFeatures.getDiameter();
			System.out.println("Diameter: " + diameter);
			System.out.println("Getting the diameter took " + sw + " to execute.");
		} else {
			List<GraphFeatures> connectedSubgraphs = graphFeatures.getConnectedSubGraphFeatures(minImportantSubgraphSize);
			for (GraphFeatures subGraph : connectedSubgraphs) {
				System.out.printf("Subgraph: %s vertices\n", subGraph.getVertexCount());
				if (subGraph.getVertexCount() < 1000) {
					System.out.printf("\t%s edges, %s diameter\n", subGraph.getEdgeCount(), subGraph.getDiameter());
				}

				System.out.printf("\thighest indegrees %s\n", subGraph.maxInDegrees(5));
				System.out.printf("\thighest outdegrees %s\n", subGraph.maxOutDegrees(5));
			}
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

	private String formatInt(int integer) {
		return NumberFormat.getNumberInstance(Locale.US).format(integer);
	}

	public static void main(final String[] args) {
		ArgumentParser parser = ArgumentParsers.newArgumentParser("GraphLOD")
				.defaultHelp(true).description("calculates graph features.");
		parser.addArgument("dataset").nargs("+").setDefault(Arrays.asList(DEFAULT_DATASET_LOCATION));
		parser.addArgument("--excludedNamespace").nargs("*").setDefault(Collections.emptyList());
		parser.addArgument("--skipChromatic").action(Arguments.storeTrue());
		parser.addArgument("--minImportantSubgraphSize").type(Integer.class).action(Arguments.store()).setDefault(20);
		Namespace result = null;
		try {
			result = parser.parseArgs(args);
		} catch (ArgumentParserException e) {
			parser.handleError(e);
			System.exit(1);
		}
		List<String> excludedNamespaces = result.getList("excludedNamespace");
		List<String> dataset = result.getList("dataset");
		boolean skipChromatic = result.getBoolean("skipChromatic");
		int minImportantSubgraphSize = result.getInt("minImportantSubgraphSize");

		System.out.println("reading: " + dataset);
		System.out.println("skip chromatic: " + skipChromatic);
		System.out.println("excluded namespaces: " + excludedNamespaces);
		System.out.println("min important subgraph size: " + minImportantSubgraphSize);
		System.out.println();

		Locale.setDefault(Locale.US);

		new GraphLOD(dataset, skipChromatic, excludedNamespaces, minImportantSubgraphSize);
	}

}
