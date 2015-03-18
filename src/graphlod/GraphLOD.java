package graphlod;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.*;

import graphlod.algorithms.GraphFeatures;
import graphlod.dataset.Dataset;
import graphlod.output.GraphCsvOutput;
import graphlod.output.VertexCsvOutput;
import graphlod.output.renderer.GraphRenderer;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Multiset;
import com.google.common.collect.TreeMultiset;
import com.google.common.io.Files;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.jgrapht.experimental.isomorphism.GraphIsomorphismInspector;

import static org.jgrapht.experimental.isomorphism.AdaptiveIsomorphismInspectorFactory.*;

public class GraphLOD {
    private static final Logger logger = Logger.getLogger(GraphLOD.class);
    public static final int MAX_SIZE_FOR_DIAMETER = 500;
    public static final int MAX_SIZE_FOR_CS_PRINT = 50;

    private final GraphCsvOutput graphCsvOutput;
    private final VertexCsvOutput vertexCsvOutput;
    private final GraphRenderer graphRenderer;
    private String name;
    private Dataset dataset;
    private List<GraphFeatures> connectedGraphs = new ArrayList<>();
    private List<GraphFeatures> stronglyConnectedGraphs = new ArrayList<>();
    private List<GraphFeatures> pathGraphs = new ArrayList<>();
    private List<GraphFeatures> directedPathGraphs = new ArrayList<>();
    private List<GraphFeatures> outboundStarGraphs = new ArrayList<>();
    private List<GraphFeatures> inboundStarGraphs = new ArrayList<>();
    private List<GraphFeatures> mixedDirectedStarGraphs = new ArrayList<>();
    private List<GraphFeatures> treeGraphs = new ArrayList<>();
    private List<GraphFeatures> caterpillarGraphs = new ArrayList<>();
    private List<GraphFeatures> lobsterGraphs = new ArrayList<>();
    private List<GraphFeatures> completeGraphs = new ArrayList<>();
    private List<GraphFeatures> bipartiteGraphs = new ArrayList<>();
    private List<GraphFeatures> unrecognizedStructure = new ArrayList<>();
    private List<String> htmlFiles = new ArrayList<>();
    private String output;
    private HashMap<Integer, List<GraphFeatures>> groups = new HashMap<>();
    private List<List<GraphFeatures>> isomorphicGraphs = new ArrayList<>();

    public GraphLOD(String name, Collection<String> datasetFiles, boolean skipChromaticNumber, boolean skipGraphviz,
                    String namespace, String ontns, Collection<String> excludedNamespaces, int minImportantSubgraphSize,
                    int importantDegreeCount, String output, boolean debugMode, int threadcount) {
        if (debugMode) {
            logger.setLevel(Level.DEBUG);
        } else {
            logger.setLevel(Level.INFO);
        }
        this.output = output;
        if (!this.output.isEmpty()) {
            try {
                File file = new File(this.output);
                Files.createParentDirs(file);
            } catch (IOException e) {
                this.output = "";
                e.printStackTrace();
            }
        }
        this.name = name;
        graphCsvOutput = new GraphCsvOutput(name, MAX_SIZE_FOR_DIAMETER);
        vertexCsvOutput = new VertexCsvOutput(name);
        
        if (!skipGraphviz) {
            graphRenderer = new GraphRenderer(name, debugMode, this.output, threadcount);
        } else {
            graphRenderer = null;
        }
        GraphFeatures graphFeatures = readDataset(datasetFiles, namespace, ontns, excludedNamespaces);
        analyze(graphFeatures, minImportantSubgraphSize, skipChromaticNumber, importantDegreeCount);
        graphFeatures = null;

        graphCsvOutput.close();
        vertexCsvOutput.close();

        if (graphRenderer != null) {
            Stopwatch sw = Stopwatch.createStarted();
            htmlFiles = graphRenderer.render();
            logger.debug("visualization took " + sw);
        }
        createHtmlStructures();
        createHtmlConnectedSets();
    }

    private GraphFeatures readDataset(Collection<String> datasetFiles, String namespace, String ontns, Collection<String> excludedNamespaces) {
        Stopwatch sw = Stopwatch.createStarted();
        dataset = Dataset.fromFiles(datasetFiles, namespace, ontns, excludedNamespaces);
        if (graphRenderer != null) {
        	graphRenderer.setDataset(dataset);
        }
        GraphFeatures graphFeatures = new GraphFeatures("main_graph", dataset.getGraph(), dataset.getSimpleGraph());
        System.out.println("Loading the dataset took " + sw + " to execute.");
        return graphFeatures;
    }

    private void analyze(GraphFeatures graphFeatures, int minImportantSubgraphSize, boolean skipChromaticNumber, int importantDegreeCount) {
        Stopwatch sw;
        System.out.println("Vertices: " + formatInt(graphFeatures.getVertexCount()));
        System.out.println("Edges: " + formatInt(graphFeatures.getEdgeCount()));

        sw = Stopwatch.createStarted();
        if (graphFeatures.isConnected()) {
            System.out.println("Connectivity: yes");
        } else {
            System.out.println("Connectivity: no");
        }

        List<Set<String>> sets = graphFeatures.getConnectedSets();
        System.out.println("Connected sets: " + formatInt(sets.size()));
        printComponentSizeAndCount(sets);

        List<Set<String>> sci_sets = graphFeatures.getStronglyConnectedSets();
        System.out.println("Strongly connected components: " + formatInt(sci_sets.size()));
        printComponentSizeAndCount(sci_sets);
        stronglyConnectedGraphs = graphFeatures.createSubGraphFeatures(sci_sets);
        logger.debug("Getting the connectivity took " + sw + " to execute.");

        int i = 0;

        sw = Stopwatch.createStarted();

        if (graphFeatures.isConnected()) {
            connectedGraphs.add(graphFeatures);
        } else {
            connectedGraphs = graphFeatures.createSubGraphFeatures(graphFeatures.getConnectedSets());
        }
        groupIsomorphicGraphs();

        for (GraphFeatures subGraph : connectedGraphs) {
            if (subGraph.getVertexCount() < minImportantSubgraphSize) {
                continue;
            }

            if (connectedGraphs.size() == 1) {
                logger.info("Graph: " + subGraph.getVertexCount());
            } else {
                logger.info("Subgraph: " + subGraph.getVertexCount());
            }
            logger.info("  " + subGraph.getVertexCount() + " vertices");

            analyzeConnectedGraph(subGraph, importantDegreeCount, i++);

            boolean cycles = subGraph.containsCycles();
            System.out.printf("\tContains cycles: %s\n", cycles);

            boolean isTree = false;
            boolean isCaterpillar = false;
			boolean isLobster = false;
            if (!cycles) {
                isTree = subGraph.isTree();
                if (isTree) {
                    System.out.printf("\tTree: %s\n", isTree);
                    treeGraphs.add(subGraph);
                    isCaterpillar = subGraph.isCaterpillar();
                    if (isCaterpillar) {
                        System.out.printf("\tCaterpillar: %s\n", isCaterpillar);
                        caterpillarGraphs.add(subGraph);
                    } else {
						isLobster = subGraph.isLobster();
						System.out.printf("\tLobster: %s\n", isLobster);
						lobsterGraphs.add(subGraph);
						
					}
                }
            }

            boolean isCompleteGraph = subGraph.isCompleteGraph();
            if (isCompleteGraph) {
                System.out.printf("\tComplete graph: %s\n", isCompleteGraph);
                completeGraphs.add(subGraph);
            }

            boolean isBipartiteGraph = false;
            /* TODO Takes too long for some graphs and results in OutOfMemoryError. Deal with that first.
            boolean isBipartiteGraph = subGraph.isBipartite();
            if (isBipartiteGraph) {
                System.out.printf("\tBipartite graph: %s\n", isBipartiteGraph);
                bipartiteGraphs.add(subGraph);
            }
            */

            boolean isPathGraph = false;
            boolean isDirectedPathGraph = false;
            boolean isMixedDirectedStarGraph = false;
            boolean isOutboundStarGraph = false;
            boolean isInboundStarGraph = false;

            if (subGraph.getVertexCount() < MAX_SIZE_FOR_DIAMETER) {
                isPathGraph = subGraph.isPathGraph();
                System.out.printf("\tPath graph: %s\n", isPathGraph);
                if (isPathGraph) {
                    pathGraphs.add(subGraph);
                    isDirectedPathGraph = subGraph.isDirectedPathGraph();
                    System.out.printf("\tDirected path graph: %s\n",
                            isDirectedPathGraph);
                    if (isDirectedPathGraph) {
                        directedPathGraphs.add(subGraph);
                    }
                } else {
                    isMixedDirectedStarGraph = subGraph
                            .isMixedDirectedStarGraph();
                    if (isMixedDirectedStarGraph) {
                        System.out.printf(
                                "\tMixed directed star graph: %s\n",
                                isMixedDirectedStarGraph);
                        mixedDirectedStarGraphs.add(subGraph);
                        isOutboundStarGraph = subGraph.isOutboundStarGraph();
                        if (isOutboundStarGraph) {
                            System.out.printf("\tOutbound star graph: %s\n",
                                    isOutboundStarGraph);
                            outboundStarGraphs.add(subGraph);
                        } else {
                            isInboundStarGraph = subGraph.isInboundStarGraph();
                            if (isInboundStarGraph) {
                                System.out.printf("\tInbound star graph: %s\n",
                                        isInboundStarGraph);
                                inboundStarGraphs.add(subGraph);
                            }
                        }

                    }
                }
            }

            if (!isTree && !isBipartiteGraph && !isCaterpillar && !isLobster && !isCompleteGraph && !isPathGraph && !isMixedDirectedStarGraph && !isOutboundStarGraph && !isInboundStarGraph) {
                unrecognizedStructure.add(subGraph);
            }

        }

        if (graphRenderer != null) {
            graphRenderer.writeDotFiles("connected", connectedGraphs, true);
            graphRenderer.writeDotFiles("strongly-connected", graphFeatures.createSubGraphFeatures(sci_sets), true);
            graphRenderer.writeDotFiles("pathgraphs", pathGraphs, true);
            graphRenderer.writeDotFiles("directedpathgraphs", directedPathGraphs, true);
            graphRenderer.writeDotFiles("outboundstargraphs", outboundStarGraphs, true);
            graphRenderer.writeDotFiles("inboundstargraphs", inboundStarGraphs, true);
            graphRenderer.writeDotFiles("stargraphs", mixedDirectedStarGraphs, true);
            graphRenderer.writeDotFiles("trees", treeGraphs, true);
            graphRenderer.writeDotFiles("caterpillargraphs", caterpillarGraphs, true);
            graphRenderer.writeDotFiles("lobstergraphs", lobsterGraphs, true);
            graphRenderer.writeDotFiles("completegraphs", completeGraphs, true);
            graphRenderer.writeDotFiles("bipartitegraphs", completeGraphs, true);
            graphRenderer.writeDotFiles("unrecognized", unrecognizedStructure, true);
        }

        logger.debug("Analysing the components took " + sw + " to execute.");

        logger.info("Vertex Degrees:");
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
            logger.info("Chromatic Number: " + cN);
            logger.debug("Getting the Chromatic Number took " + sw + " to execute.");
        }
    }

    private void exportEntities(List<GraphFeatures> graphs, String string) {
        File file = new File("entities/" + string + ".csv");
        try {
            Files.createParentDirs(file);
            Writer writer = new BufferedWriter(new FileWriter(file));
            for (GraphFeatures graph : graphs) {
                for (String v : graph.getVertices()) {
                    writer.write(v + "\n");
                }
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void printStats(BufferedWriter out, List<GraphFeatures> graphs, String string, int times) throws IOException {
        out.write("<tr>\n");
        out.write("<td>");
        for (int i = 1; i <= times; i++) {
            System.out.print("\t");
            out.write("&nbsp;&nbsp;");
        }
        out.write(string + "</td>\n");
        System.out.println(string + ": " + graphs.size());
        out.write("<td>" + graphs.size() + "</td>\n");
        if (graphs.size() == 0) {
            out.write("<td></td>\n");
            out.write("<td></td>\n");
            out.write("<td></td>\n");
            out.write("<td></td>\n");
            out.write("</tr>\n");
            return;
        }
        int min = 0;
        int max = 0;
        List<Integer> sizes = new ArrayList<>();
        for (GraphFeatures graph : graphs) {
            sizes.add(graph.getVertexCount());
            if (min == 0) {
                min = graph.getVertexCount();
            } else if (graph.getVertexCount() < min) {
                min = graph.getVertexCount();
            }
            if (graph.getVertexCount() > max) {
                max = graph.getVertexCount();
            }
        }
        for (int i = 0; i <= times; i++) {
            System.out.print("\t");
        }
        double avg = calculateAverage(sizes);
        out.write("<td>" + min + "</td>\n");
        out.write("<td>" + max + "</td>\n");
        out.write("<td>" + avg + "</td>\n");
        System.out.println("min #v: " + min + ", max #v: " + max + ", avg #v: " + avg);

        String stringNormalized = string.toLowerCase().replace(" ", "");
        if (stringNormalized.startsWith("connected")) {
            stringNormalized = "_connected";
        }
        if (stringNormalized.startsWith("strongly")) {
            stringNormalized = "strongly-connected";
        }
        exportEntities(graphs, stringNormalized);

        out.write("<td>\n");
        for (String file : htmlFiles) {
            String htmlFile = new File(file).getName();
            if (htmlFile.contains(stringNormalized)) {
                out.write("<a href=dot/" + htmlFile + ">*</a> \n");
            }
        }
        out.write("</td>\n");

        out.write("</tr>\n");
    }

    private void createHtmlConnectedSets() {
        try {
            File file = new File(this.output + this.name + "_cs_statistics.html");
            Files.createParentDirs(file);
            BufferedWriter out = Files.newWriter(file, Charsets.UTF_8);
            printTableHeader(out);
            printStats(out, connectedGraphs, "Connected sets", 0);
            printStats(out, stronglyConnectedGraphs, "Strongly connected sets", 0);
            printTableFooter(out);
            printGroups(out);
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void printGroups(BufferedWriter out) {
        for (List<GraphFeatures> graphs : this.isomorphicGraphs) {
            try {
                out.write("<p>" + graphs.size() + " x ");
                String imgName = "dot/" + this.graphRenderer.getFileName() + "_" + this.isomorphicGraphs.indexOf(graphs) + "_dotgraph"  + "0.txt.png";
                String imgDetailedName = "dot/" + this.graphRenderer.getFileName() + "_" + this.isomorphicGraphs.indexOf(graphs) + "_detailed_dotgraph"  + "0.txt.html";
                out.write("<a href=\"" + imgDetailedName + "\"><img src=\"" + imgName + "\"></a></p>");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
	}

	private void createHtmlStructures() {
        logger.info("\nStructure Statistics");

        try {
            File file = new File(this.output + this.name + "_statistics.html");
            Files.createParentDirs(file);
            BufferedWriter out = Files.newWriter(file, Charsets.UTF_8);
            printTableHeader(out);
            // TODO printStats(out, bipartiteGraphs, "Bipartite graphs", 0);
            printStats(out, completeGraphs, "Complete graphs", 0);
            printStats(out, treeGraphs, "Trees", 0);
            printStats(out, caterpillarGraphs, "Caterpillar graphs", 1);
            printStats(out, lobsterGraphs, "Lobster graphs", 1);
            printStats(out, pathGraphs, "Path graphs", 1);
            printStats(out, directedPathGraphs, "Directed path graphs", 2);
            printStats(out, mixedDirectedStarGraphs, "Star graphs", 1);
            printStats(out, inboundStarGraphs, "Inbound star graphs", 2);
            printStats(out, outboundStarGraphs, "Outbound star graphs", 2);
            printStats(out, unrecognizedStructure, "Unrecognized", 0);
            printTableFooter(out);
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void printTableFooter(BufferedWriter out) throws IOException {
        out.write("</table></p>\n");
    }

    private void printTableHeader(BufferedWriter out) throws IOException {
        out.write("<p><table>\n");
        out.write("<tr>\n");
        out.write("<td>Structure</td>\n");
        out.write("<td>Number</td>\n");
        out.write("<td># Vertices (min)</td>\n");
        out.write("<td># Vertices (max)</td>\n");
        out.write("<td># Vertices (avg)</td>\n");
        out.write("<td>Graphs</td>\n");
        out.write("</tr>\n");
    }

    private void groupIsomorphicGraphs() {
        for (GraphFeatures connectedSet : this.connectedGraphs) {
            if (connectedSet.getVertexCount() > MAX_SIZE_FOR_CS_PRINT) continue;
            int putIntoBag = -1;
            for (List<GraphFeatures> isomorphicGraphList : this.isomorphicGraphs) {
                GraphFeatures firstGraph = isomorphicGraphList.get(0);
                if ((firstGraph.getVertexCount() != connectedSet.getVertexCount()) || (firstGraph.getEdgeCount() != connectedSet.getEdgeCount())) continue;
                GraphIsomorphismInspector inspector = createIsomorphismInspector(connectedSet.getSimpleGraph(), firstGraph.getSimpleGraph());
                if (inspector.isIsomorphic()) {
                    putIntoBag = this.isomorphicGraphs.indexOf(isomorphicGraphList);
                }
            }
            List<GraphFeatures> graphs = new ArrayList<>();
            graphs.add(connectedSet);
            if (putIntoBag >= 0) {
                graphs.addAll(this.isomorphicGraphs.get(putIntoBag));
                this.isomorphicGraphs.remove(putIntoBag);
                this.isomorphicGraphs.add(putIntoBag, graphs);
            } else {
                this.isomorphicGraphs.add(graphs);
            }
        }
        Collections.sort(this.isomorphicGraphs, new Comparator<List<?>>(){
            public int compare(List<?> a1, List<?> a2) {
                return a2.size() - a1.size();
            }
        });
        for (List<GraphFeatures> isomorphicGraphList : this.isomorphicGraphs) {
            Integer index = isomorphicGraphs.indexOf(isomorphicGraphList);
            this.graphRenderer.writeDotFile(index.toString(), isomorphicGraphList.get(0), false);
            this.graphRenderer.writeDotFiles(index.toString() + "_detailed", isomorphicGraphList, true);
        }
    }

    private double calculateAverage(List<Integer> sizes) {
        Integer sum = 0;
        if (!sizes.isEmpty()) {
            for (Integer size : sizes) {
                sum += size;
            }
            return round(sum.doubleValue() / sizes.size(), 2);
        }
        return sum;
    }

    private double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    private void printComponentSizeAndCount(Collection<Set<String>> sets) {
        Multiset<Integer> sizes = TreeMultiset.create();
        for (Set<String> component : sets) {
            sizes.add(component.size());
        }
        logger.info("\t\tComponents (and sizes): ");
        for (Multiset.Entry<Integer> group : sizes.entrySet()) {
            logger.info("\t\t\t" + group.getCount() + " x " + group.getElement());
        }
    }

    private void analyzeConnectedGraph(GraphFeatures graph, int importantDegreeCount, int groupnr) {
        Preconditions.checkArgument(graph.isConnected());
        if (graph.getVertexCount() < MAX_SIZE_FOR_DIAMETER) {
            logger.info("\tedges: " + graph.getEdgeCount() + ", diameter: " + graph.getDiameter());
        } else {
            logger.warn("\tGraph too big to show diameter");
        }
        graphCsvOutput.writeGraph(graph);
        vertexCsvOutput.writeGraph(graph);

        logger.info("\thighest indegrees:");
        logger.info("\t\t" + StringUtils.join(graph.maxInDegrees(importantDegreeCount), "\n\t\t"));
        logger.info("\thighest outdegrees:");
        logger.info("\t\t" + StringUtils.join(graph.maxOutDegrees(importantDegreeCount), "\n\t\t"));

        // TODO: BiconnectedSets are too slow, even for Diseasome!
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
        parser.addArgument("--ontns").type(String.class).setDefault("");
        parser.addArgument("--excludedNamespaces").nargs("*").setDefault(Collections.emptyList());
        parser.addArgument("--skipChromatic").action(Arguments.storeTrue());
        parser.addArgument("--skipGraphviz").action(Arguments.storeTrue());
        parser.addArgument("--minImportantSubgraphSize").type(Integer.class).action(Arguments.store()).setDefault(1);
        parser.addArgument("--importantDegreeCount").type(Integer.class).action(Arguments.store()).setDefault(5);
        parser.addArgument("--threadcount").type(Integer.class).action(Arguments.store()).setDefault(4);
        parser.addArgument("--debug").action(Arguments.storeTrue());
        parser.addArgument("--output").type(String.class).setDefault("");
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
            File file = new File(name);
            name = file.getName().replaceAll("\\..*", "");
        }


        String namespace = result.getString("namespace");
        String ontns = result.getString("ontns");
        if (ontns.isEmpty() && !namespace.isEmpty()) {
        	ontns = namespace;
        }
        List<String> excludedNamespaces = result.getList("excludedNamespaces");
        boolean skipChromatic = result.getBoolean("skipChromatic");
        boolean skipGraphviz = result.getBoolean("skipGraphviz");
        int minImportantSubgraphSize = result.getInt("minImportantSubgraphSize");
        int importantDegreeCount = result.getInt("importantDegreeCount");
        int threadcount = result.getInt("threadcount");
        boolean debugMode = result.getBoolean("debug");
        String output = result.getString("output");

        BasicConfigurator.configure();
        logger.info("reading: " + dataset);
        logger.info("name: " + name);
        logger.info("namespace: " + namespace);
        logger.info("ontology namespace: " + ontns);
        logger.info("skip chromatic: " + skipChromatic);
        logger.info("skip graphviz: " + skipGraphviz);
        logger.info("excluded namespaces: " + excludedNamespaces);
        logger.info("min important subgraph size: " + minImportantSubgraphSize);
        logger.info("number of important degrees: " + importantDegreeCount);
        logger.info("threadcount: " + threadcount);
        logger.info("output: " + output);

        Locale.setDefault(Locale.US);

        new GraphLOD(name, dataset, skipChromatic, skipGraphviz, namespace, ontns, excludedNamespaces, minImportantSubgraphSize, importantDegreeCount, output, debugMode, threadcount);
    }

}
