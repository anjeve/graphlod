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
    private static final Logger logger = Logger.getLogger(GraphLOD.class);
    public static final int MAX_SIZE_FOR_DIAMETER = 500;
    		
    private final GraphCsvOutput graphCsvOutput;
    private final VertexCsvOutput vertexCsvOutput;
    private final GraphRenderer graphRenderer;

    public GraphLOD(String name, Collection<String> datasetFiles, boolean skipChromaticNumber, boolean skipGraphviz, String namespace, Collection<String> excludedNamespaces, int minImportantSubgraphSize, int importantDegreeCount) {
        graphCsvOutput = new GraphCsvOutput(name, MAX_SIZE_FOR_DIAMETER);
        vertexCsvOutput = new VertexCsvOutput(name);
        if (!skipGraphviz) {
            graphRenderer = new GraphRenderer(name);
        } else {
            graphRenderer = null;
        }

        GraphFeatures graphFeatures = readDataset(datasetFiles, namespace, excludedNamespaces);
        analyze(graphFeatures, minImportantSubgraphSize, skipChromaticNumber, importantDegreeCount);
        graphFeatures = null;

        graphCsvOutput.close();
        vertexCsvOutput.close();

        if (graphRenderer != null) {
            Stopwatch sw = Stopwatch.createStarted();
            graphRenderer.render();
            System.out.println("visualization took " + sw);
        }
    }

    private GraphFeatures readDataset(Collection<String> datasetFiles, String namespace, Collection<String> excludedNamespaces) {
        Stopwatch sw = Stopwatch.createStarted();
        Dataset dataset = Dataset.fromFiles(datasetFiles, namespace, excludedNamespaces);
        GraphFeatures graphFeatures = new GraphFeatures("main_graph", dataset.getGraph());
        System.out.println("Loading the dataset took " + sw + " to execute.");
        return graphFeatures;
    }

    private void analyze(GraphFeatures graphFeatures, float minImportantSubgraphSize, boolean skipChromaticNumber, int importantDegreeCount) {
        Stopwatch sw;
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

            printComponentSizeAndCount(sets);
        }

        List<Set<String>> sci_sets = graphFeatures.getStronglyConnectedSets();
        System.out.println("Strongly connected components: " + formatInt(sci_sets.size()));
        printComponentSizeAndCount(sci_sets);
        if (graphRenderer != null) {
            graphRenderer.writeDotFiles( "strongly-connected", graphFeatures.createSubGraphFeatures(sci_sets));
        }
        System.out.println("Getting the connectivity took " + sw + " to execute.");

        List<GraphFeatures> connectedGraphs = new ArrayList<>();
        List<GraphFeatures> pathGraphs = new ArrayList<>();
        List<GraphFeatures> directedPathGraphs = new ArrayList<>();
        List<GraphFeatures> outboundStarGraphs = new ArrayList<>();
        List<GraphFeatures> inboundStarGraphs = new ArrayList<>();

        int i = 0;
        
        sw = Stopwatch.createStarted();

        if (graphFeatures.isConnected()) {
            connectedGraphs.add(graphFeatures);
        } else {
            connectedGraphs = graphFeatures.createSubGraphFeatures(graphFeatures.getConnectedSets());
        }        
        
        for (GraphFeatures subGraph : connectedGraphs) {
            if (subGraph.getVertexCount() < minImportantSubgraphSize) {
                continue;
            }
            
            if (connectedGraphs.size() == 1) {
                System.out.printf("Graph: ", subGraph.getVertexCount());
            } else {
                System.out.printf("Subgraph: ", subGraph.getVertexCount());
            }
            System.out.printf("%s vertices\n", subGraph.getVertexCount());

            analyzeConnectedGraph(subGraph, importantDegreeCount, i++);

            boolean cycles = subGraph.containsCycles();
            System.out.printf("\tContains cycles: %s\n", cycles);
            
            boolean isPathGraph = subGraph.isPathGraph();
            System.out.printf("\tPath graph: %s\n", isPathGraph);
            if (isPathGraph) {
            	pathGraphs.add(subGraph);
            	boolean isDirectedPathGraph = subGraph.isDirectedPathGraph();
                System.out.printf("\tDirected path graph: %s\n", isDirectedPathGraph);
                if (isDirectedPathGraph) {
            		directedPathGraphs.add(subGraph);
            	}
            } else {
            	boolean isOutboundStarGraph = subGraph.isOutboundStarGraph();
                if (isOutboundStarGraph) {
                	System.out.printf("\tOutbound star graph: %s\n", isOutboundStarGraph);
                	outboundStarGraphs.add(subGraph);
            	} else {
            		boolean isInboundStarGraph = subGraph.isInboundStarGraph();
                    if (isInboundStarGraph) {
                    	System.out.printf("\tInbound star graph: %s\n", isInboundStarGraph);
                    	inboundStarGraphs.add(subGraph);
                    }
            	}
            }
        }
        if (graphRenderer != null) {
            graphRenderer.writeDotFiles( "connected", connectedGraphs);
            if (!pathGraphs.isEmpty()) {
                graphRenderer.writeDotFiles( "pathgraphs", pathGraphs);
            }
            if (!directedPathGraphs.isEmpty()) {
                graphRenderer.writeDotFiles( "directedpathgraphs", directedPathGraphs);
            }
            if (!outboundStarGraphs.isEmpty()) {
                graphRenderer.writeDotFiles( "outboundstargraphs", outboundStarGraphs);
            }
            if (!inboundStarGraphs.isEmpty()) {
                graphRenderer.writeDotFiles( "inboundstargraphs", inboundStarGraphs);
            }
            if (!inboundStarGraphs.isEmpty() || !outboundStarGraphs.isEmpty()) {
            	List<GraphFeatures> starGraphs = inboundStarGraphs;
            	starGraphs.addAll(outboundStarGraphs);
            	graphRenderer.writeDotFiles( "stargraphs", starGraphs);
            }
        }
        
        System.out.println("Analysing the components took " + sw + " to execute.");

        System.out.println("Vertex Degrees:");
        List<Integer> indegrees = graphFeatures.getIndegrees();
        System.out.printf("\tAverage indegree: %.3f\n", CollectionUtils.average(indegrees));
        System.out.println("\tMax indegree: " + CollectionUtils.max(indegrees));
        System.out.println("\tMin indegree: " + CollectionUtils.min(indegrees));

        List<Integer> outdegrees = graphFeatures.getOutdegrees();
        System.out.printf("\tAverage outdegree: %.3f\n", CollectionUtils.average(outdegrees));
        System.out.println("\tMax outdegree: " + CollectionUtils.max(outdegrees));
        System.out.println("\tMin outdegree: " + CollectionUtils.min(outdegrees));

        ArrayList<Integer> edgeCounts = graphFeatures.getEdgeCounts();
        System.out.printf("\tAverage links: %.3f\n", CollectionUtils.average(edgeCounts));

        if (!skipChromaticNumber) {
            sw = Stopwatch.createStarted();
            int cN = graphFeatures.getChromaticNumber();
            System.out.println("Chromatic Number: " + cN);
            System.out.println("Getting the Chromatic Number took " + sw + " to execute.");
        }
    }


    private void printComponentSizeAndCount(Collection<Set<String>> sets) {
        Multiset<Integer> sizes = TreeMultiset.create();
        for (Set<String> component : sets) {
            sizes.add(component.size());
        }
        System.out.println("\t\tComponents (and sizes): ");
        for (Multiset.Entry<Integer> group : sizes.entrySet()) {
            System.out.println("\t\t\t" + group.getCount() + " x " + group.getElement());
        }
    }

    private void analyzeConnectedGraph(GraphFeatures graph, int importantDegreeCount, int groupnr) {
        Preconditions.checkArgument(graph.isConnected());
        if (graph.getVertexCount() < MAX_SIZE_FOR_DIAMETER) {
            System.out.printf("\tedges: %s, diameter: %s\n", graph.getEdgeCount(), graph.getDiameter());
        } else {
            System.out.println("\tGraph too big to show diameter");
        }
        graphCsvOutput.writeGraph(graph);
        vertexCsvOutput.writeGraph(graph);

        System.out.println("\thighest indegrees:");
        System.out.println("\t\t" + StringUtils.join(graph.maxInDegrees(importantDegreeCount), "\n\t\t"));
        System.out.println("\thighest outdegrees:");
        System.out.println("\t\t" + StringUtils.join(graph.maxOutDegrees(importantDegreeCount), "\n\t\t"));

        // TODO: BiconnectedSets are too slow, even for diseasome!
        //Set<Set<String>> bcc_sets = graph.getBiConnectedSets();
        //System.out.println("\tBiconnected components: " + formatInt(bcc_sets.size()));
        //printComponentSizeAndCount(bcc_sets);
        //graphRenderer.writeDotFiles(name, "biconnected_"+groupnr, graph.createSubGraphFeatures(bcc_sets));
    }

    private String formatInt(int integer) {
        return NumberFormat.getNumberInstance(Locale.US).format(integer);
    }

    public static void main(final String[] args) {
        ArgumentParser parser = ArgumentParsers.newArgumentParser("GraphLOD")
                .defaultHelp(true).description("calculates graph features.");
        parser.addArgument("dataset").nargs("+").setDefault(Collections.emptyList());
        parser.addArgument("--name").type(String.class).setDefault("");
        parser.addArgument("--namespace").type(String.class).setDefault("");
        parser.addArgument("--excludedNamespaces").nargs("*").setDefault(Collections.emptyList());
        parser.addArgument("--skipChromatic").action(Arguments.storeTrue());
        parser.addArgument("--skipGraphviz").action(Arguments.storeTrue());
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
        String name = result.getString("name");
        if (name.isEmpty()) {
            name = dataset.get(0);
        }
        String namespace = result.getString("namespace");
        List<String> excludedNamespaces = result.getList("excludedNamespaces");
        boolean skipChromatic = result.getBoolean("skipChromatic");
        boolean skipGraphviz = result.getBoolean("skipGraphviz");
        int minImportantSubgraphSize = result.getInt("minImportantSubgraphSize");
        int importantDegreeCount = result.getInt("importantDegreeCount");

        System.out.println("reading: " + dataset);
        System.out.println("name: " + name);
        System.out.println("namespace: " + namespace);
        System.out.println("skip chromatic: " + skipChromatic);
        System.out.println("skip graphviz: " + skipGraphviz);
        System.out.println("excluded namespaces: " + excludedNamespaces);
        System.out.println("min important subgraph size: " + minImportantSubgraphSize);
        System.out.println("number of important degrees: " + importantDegreeCount);
        System.out.println();

        Locale.setDefault(Locale.US);

        new GraphLOD(name, dataset, skipChromatic, skipGraphviz, namespace, excludedNamespaces, minImportantSubgraphSize, importantDegreeCount);
    }

}
