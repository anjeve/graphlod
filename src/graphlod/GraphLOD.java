package graphlod;

import ch.qos.logback.classic.Level;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Multiset;
import com.google.common.collect.TreeMultiset;
import com.google.common.io.Files;
import graphlod.algorithms.GraphFeatures;
import graphlod.dataset.Dataset;
import graphlod.graph.Degree;
import graphlod.output.GramiOutput;
import graphlod.output.GraphCsvOutput;
import graphlod.output.JsonOutput;
import graphlod.output.VertexCsvOutput;
import graphlod.output.renderer.GraphRenderer;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.lang3.StringUtils;
import org.jgrapht.experimental.isomorphism.GraphIsomorphismInspector;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.*;
import static org.jgrapht.experimental.isomorphism.AdaptiveIsomorphismInspectorFactory.createIsomorphismInspector;

public class GraphLOD {
    private static Logger logger = LoggerFactory.getLogger(GraphLOD.class);

    public static final int MAX_SIZE_FOR_DIAMETER = 50;
    public static final int MAX_SIZE_FOR_CS_PRINT = 500000000;
    public static final int MAX_SIZE_FOR_PROLOD = 3000;

    private GraphCsvOutput graphCsvOutput = null;
    private VertexCsvOutput vertexCsvOutput = null;
    private final GraphRenderer graphRenderer;
    private String name;
    public Dataset dataset;
    private boolean exportJson;
    private boolean exportGrami;
    private boolean apiOnly;
    public List<GraphFeatures> connectedGraphs = new ArrayList<>();
    public List<GraphFeatures> stronglyConnectedGraphs = new ArrayList<>();
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
    private List<List<Integer>> isomorphicGraphs = new ArrayList<>();
    private HashMap<Integer, List<List<GraphFeatures>>> colorPreservingIsomorphicGraphs = new HashMap<>();
    public GraphFeatures graphFeatures;
    public HashMap<Integer, HashMap<String, Integer>> patterns = new HashMap<>();
    public HashMap<Integer, Double> patternDiameter = new HashMap<>();
    public HashMap<Integer, List<String>> coloredPatterns = new HashMap<>();
    public JSONObject nodeDegreeDistribution;
    public JSONObject highestIndegrees;
    public JSONObject highestOutdegrees;
    public double averageLinks;
    public List<Integer> connectedGraphSizes = new ArrayList<>();
    public List<Integer> stronglyconnectedGraphSizes = new ArrayList<>();

    public GraphLOD(String name, Collection<String> datasetFiles, boolean skipChromaticNumber, boolean skipGraphviz,
                    boolean exportJson, boolean exportGrami, String namespace, String ontologyNS, Collection<String> excludedNamespaces, int minImportantSubgraphSize,
                    int importantDegreeCount, String output, int threadCount, boolean apiOnly) {
        this.output = output;
        this.name = name;
        this.exportJson = exportJson;
        this.exportGrami = exportGrami;
        this.apiOnly = apiOnly;
        if (!skipGraphviz && !exportJson && !exportGrami) {
            this.graphRenderer = new GraphRenderer(name, this.output, threadCount);
        } else {
            this.graphRenderer = null;
        }


        if (apiOnly) {
            graphFeatures = readDataset(datasetFiles, namespace, ontologyNS, excludedNamespaces);
            analyze(graphFeatures, minImportantSubgraphSize, skipChromaticNumber, importantDegreeCount);
        } else {
            this.graphCsvOutput = new GraphCsvOutput(name, MAX_SIZE_FOR_DIAMETER);
            this.vertexCsvOutput = new VertexCsvOutput(name);

            if (!this.output.isEmpty()) {
                try {
                    File file = new File(this.output);
                    Files.createParentDirs(file);
                } catch (IOException e) {
                    this.output = "";
                    e.printStackTrace();
                }
            }

            GraphFeatures graphFeatures = readDataset(datasetFiles, namespace, ontologyNS, excludedNamespaces);
            if (!exportJson && !exportGrami) {
                analyze(graphFeatures, minImportantSubgraphSize, skipChromaticNumber, importantDegreeCount);

                graphCsvOutput.close();
                vertexCsvOutput.close();

                if (graphRenderer != null) {
                    Stopwatch sw = Stopwatch.createStarted();
                    this.htmlFiles = graphRenderer.render();
                    logger.debug("visualization took " + sw);
                }
                createHtmlStructures();
                createHtmlConnectedSets();
            }
            graphFeatures = null;
        }
    }

    private GraphFeatures readDataset(Collection<String> datasetFiles, String namespace, String ontns, Collection<String> excludedNamespaces) {
        Stopwatch sw = Stopwatch.createStarted();
        dataset = Dataset.fromFiles(datasetFiles, this.name, namespace, ontns, excludedNamespaces);
        if (graphRenderer != null) {
        	graphRenderer.setDataset(dataset);
        }
        if (this.exportGrami) {
            GramiOutput grami = new GramiOutput(this.dataset);
            grami.write(this.output);
        }
        if (this.exportJson) {
            JsonOutput jsonOutput = new JsonOutput(this.dataset);
            jsonOutput.write(this.output);
        }
        GraphFeatures graphFeatures = new GraphFeatures("main_graph", dataset.getGraph(), dataset.getSimpleGraph());
        logger.info("Loading the dataset took " + sw + " to execute.");
        return graphFeatures;
    }

    private void analyze(GraphFeatures graphFeatures, int minImportantSubgraphSize, boolean skipChromaticNumber, int importantDegreeCount) {
        Stopwatch sw;
        logger.info("Vertices: " + formatInt(graphFeatures.getVertexCount()));
        logger.info("Edges: " + formatInt(graphFeatures.getEdgeCount()));

        sw = Stopwatch.createStarted();
        if (graphFeatures.isConnected()) {
            logger.info("Connectivity: yes");
        } else {
            logger.info("Connectivity: no");
        }

        List<Set<String>> sets = graphFeatures.getConnectedSets();
        logger.info("Connected sets: " + formatInt(sets.size()));
        printComponentSizeAndCount(sets);

        List<Set<String>> sci_sets = graphFeatures.getStronglyConnectedSets();
        logger.info("Strongly connected components: " + formatInt(sci_sets.size()));
        printComponentSizeAndCount(sci_sets);
        stronglyConnectedGraphs = graphFeatures.createSubGraphFeatures(sci_sets);
        for (GraphFeatures subGraph : stronglyConnectedGraphs) {
            this.stronglyconnectedGraphSizes.add(subGraph.getVertexCount());
        }
        logger.debug("Getting the connectivity took " + sw + " to execute.");

        int i = 0;

        sw = Stopwatch.createStarted();

        if (graphFeatures.isConnected()) {
            connectedGraphs.add(graphFeatures);
        } else {
            connectedGraphs = graphFeatures.createSubGraphFeatures(graphFeatures.getConnectedSets());
        }

        if (apiOnly) {
            List<Degree> maxInDegrees = graphFeatures.maxInDegrees(importantDegreeCount);
            List<Degree> maxOutDegrees = graphFeatures.maxOutDegrees(importantDegreeCount);
            HashMap<String, Integer> highestIndegreeMap = new HashMap<>();
            HashMap<String, Integer> highestOutdegreeMap = new HashMap<>();
            for (Degree degree : maxInDegrees) {
                highestIndegreeMap.put(degree.vertex, degree.degree);
            }
            highestIndegrees = new JSONObject(highestIndegreeMap);
            for (Degree degree : maxOutDegrees) {
                highestOutdegreeMap.put(degree.vertex, degree.degree);
            }
            highestOutdegrees = new JSONObject(highestOutdegreeMap);
        }

        for (GraphFeatures subGraph : connectedGraphs) {
            this.connectedGraphSizes.add(subGraph.getVertexCount());

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
            logger.info("\tContains cycles: {}", cycles);

            boolean isTree = false;
            boolean isCaterpillar = false;
			boolean isLobster = false;
            if (!cycles) {
                logger.debug("Checking for tree graph.");
                isTree = subGraph.isTree();
                if (isTree) {
                    logger.info("\tTree: {}", isTree);
                    treeGraphs.add(subGraph);
                    logger.debug("Checking for caterpillar graph.");
                    isCaterpillar = subGraph.isCaterpillar();
                    if (isCaterpillar) {
                        logger.info("\tCaterpillar: {}", isCaterpillar);
                        caterpillarGraphs.add(subGraph);
                    } else {
                        logger.debug("Checking for lobster graph.");
						isLobster = subGraph.isLobster();
                        logger.info("\tLobster: {}", isLobster);
						lobsterGraphs.add(subGraph);
						
					}
                }
            }

            logger.debug("Checking for completeness.");
            boolean isCompleteGraph = subGraph.isCompleteGraph();
            if (isCompleteGraph) {
                logger.info("\tComplete graph: {}", isCompleteGraph);
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
                logger.debug("Checking for path graph.");
                isPathGraph = subGraph.isPathGraph();
                logger.info("\tPath graph: {}", isPathGraph);
                if (isPathGraph) {
                    pathGraphs.add(subGraph);
                    logger.debug("Checking for directed path graph.");
                    isDirectedPathGraph = subGraph.isDirectedPathGraph();
                    logger.info("\tDirected path graph: {}",
                            isDirectedPathGraph);
                    if (isDirectedPathGraph) {
                        directedPathGraphs.add(subGraph);
                    }
                } else {
                    logger.debug("Checking for mixed directed star graph.");
                    isMixedDirectedStarGraph = subGraph.isMixedDirectedStarGraph();
                    if (isMixedDirectedStarGraph) {
                        logger.info(
                                "\tMixed directed star graph: {}",
                                isMixedDirectedStarGraph);
                        mixedDirectedStarGraphs.add(subGraph);
                        logger.debug("Checking for outbound star graph.");
                        isOutboundStarGraph = subGraph.isOutboundStarGraph();
                        if (isOutboundStarGraph) {
                            logger.info("\tOutbound star graph: {}",
                                    isOutboundStarGraph);
                            outboundStarGraphs.add(subGraph);
                        } else {
                            logger.debug("Checking for inbound star graph.");
                            isInboundStarGraph = subGraph.isInboundStarGraph();
                            if (isInboundStarGraph) {
                                logger.info("\tInbound star graph: {}",
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

        logger.debug("Checking for isomorphisms.");
        sw = Stopwatch.createStarted();
        groupIsomorphicGraphs();
        logger.debug("Isomorphism check took " + sw);

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
        logger.info("\tAverage indegree: {}", graphFeatures.getAverageIndegree());
        logger.info("\tMax indegree: " + graphFeatures.getMaxIndegree());
        logger.info("\tMin indegree: " + graphFeatures.getMinIndegree());

        this.nodeDegreeDistribution = new JSONObject(graphFeatures.getDegreeDistribution());
        logger.info("\tNode degree distribution: {}", this.nodeDegreeDistribution);

        List<Integer> outdegrees = graphFeatures.getOutdegrees();
        logger.info("\tAverage outdegree: {}", CollectionUtils.average(outdegrees));
        logger.info("\tMax outdegree: " + CollectionUtils.max(outdegrees));
        logger.info("\tMin outdegree: " + CollectionUtils.min(outdegrees));

        ArrayList<Integer> edgeCounts = graphFeatures.getEdgeCounts();
        this.averageLinks = CollectionUtils.average(edgeCounts);
        logger.info("\tAverage links: {}", averageLinks);

        if (!skipChromaticNumber) {
            sw = Stopwatch.createStarted();
            int cN = graphFeatures.getChromaticNumber();
            logger.info("Chromatic Number: {}", cN);
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
            out.write("&nbsp;&nbsp;");
        }
        out.write(string + "</td>\n");
        logger.info(string + ": " + graphs.size());
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
        double avg = calculateAverage(sizes);
        out.write("<td>" + min + "</td>\n");
        out.write("<td>" + max + "</td>\n");
        out.write("<td>" + avg + "</td>\n");
        logger.info("min #v: " + min + ", max #v: " + max + ", avg #v: " + avg);

        String stringNormalized = string.toLowerCase().replace(" ", "");
        if (stringNormalized.startsWith("connected")) {
            stringNormalized = "_connected";
        }
        if (stringNormalized.startsWith("strongly")) {
            stringNormalized = "strongly-connected";
        }
        exportEntities(graphs, stringNormalized);

        out.write("<td>\n");
        for (String file : this.htmlFiles) {
            String htmlFile = new File(file).getName();
            if (htmlFile.contains(stringNormalized)) {
                out.write("<a href=dot/" + htmlFile + ">");
                String number = htmlFile.replaceFirst(".*dotgraph", "").replaceAll("\\.txt\\.html", "");
                out.write(number);
                out.write("</a> \n");
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
        for (List<Integer> graphs : this.isomorphicGraphs) {
            try {
                out.write("<p>" + graphs.size() + " x ");
                int index = this.isomorphicGraphs.indexOf(graphs);
                int vertices = this.connectedGraphs.get(index).getVertexCount();
                String imgName = "dot/" + this.graphRenderer.getFileName() + "_" + index + "_dotgraph"  + vertices + ".txt.png";
                String imgDetailedName = "dot/" + this.graphRenderer.getFileName() + "_" + index + "_detailed_dotgraph"  + vertices + ".txt.html";
                out.write("<a href=\"" + imgDetailedName + "\"><img src=\"" + imgName + "\"></a></p>");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
	}

	private void createHtmlStructures() {
        logger.info("Structure Statistics");

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
        out.write("<td>Graphs (# vertices)</td>\n");
        out.write("</tr>\n");
    }

    private void groupIsomorphicGraphs() {
        int i = 0;
        for (GraphFeatures connectedSet : this.connectedGraphs) {
            if (connectedSet.getVertexCount() > MAX_SIZE_FOR_PROLOD) continue;
            logger.debug("\tChecking graph {}/{} {}.", ++i, this.connectedGraphs.size(), connectedSet.getVertexCount());
            int putIntoBag = -1;
            int putIntoColorBag = -1;
            for (List<Integer> isomorphicGraphList : this.isomorphicGraphs) {
                GraphFeatures firstGraph = this.connectedGraphs.get(isomorphicGraphList.get(0));
                try {
                    if ((firstGraph.getVertexCount() != connectedSet.getVertexCount()) || (firstGraph.getEdgeCount() != connectedSet.getEdgeCount())) continue;
                    if (firstGraph.getAverageIndegree() != connectedSet.getAverageIndegree()) continue;
                    GraphIsomorphismInspector inspector = createIsomorphismInspector(connectedSet.getSimpleGraph(), firstGraph.getSimpleGraph());
                    if (inspector.isIsomorphic()) {
                        putIntoBag = this.isomorphicGraphs.indexOf(isomorphicGraphList);
                        break;
                    }
                } catch (ArrayIndexOutOfBoundsException e) {
                    putIntoBag = -1;
                    logger.warn(e.getMessage());
                }
            }
            List<Integer> graphs = new ArrayList<>();
            graphs.add(this.connectedGraphs.indexOf(connectedSet));
            /* TODO color isomorphism
            List<GraphFeatures> coloredGraphs = new ArrayList<>();
            coloredGraphs.add(connectedSet);
            */
            if (putIntoBag >= 0) {
                /*
                List<List<GraphFeatures>> isomorphismGroup = this.colorPreservingIsomorphicGraphs.get(putIntoBag);
                // TODO check only for one item in list! (for iterates over all)
                for (List<GraphFeatures> coloredIsomorphicGraph : isomorphismGroup) {
                    GraphIsomorphismInspector inspector = AdaptiveIsomorphismInspectorFactory.createClassIsomorphismInspector(connectedSet.getSimpleGraph(), coloredIsomorphicGraph.get(0).getSimpleGraph());
                    if (inspector.isIsomorphic()) {
                        //putIntoBag = this.isomorphicGraphs.indexOf(isomorphicGraphList);
                        break;
                    }
                }
                   */

                /*
                List<List<GraphFeatures>> isomorphismGroup = this.colorPreservingIsomorphicGraphs.get(putIntoBag);
                for (List<GraphFeatures> coloredIsomorphicGraphList : isomorphismGroup) {
                    if (connectedSet.checkColorIsomorphism(coloredIsomorphicGraphList.get(0))) {
                        putIntoColorBag = isomorphismGroup.indexOf(coloredIsomorphicGraphList);
                        break;
                    }
                }
                if (putIntoColorBag >= 0) {
                    List<GraphFeatures> colorIsomorphismGroup = isomorphismGroup.get(putIntoBag);
                    coloredGraphs.addAll(

                    )
                    this.colorPreservingIsomorphicGraphs.remove(putIntoBag);

                } else {
                    this.colorPreservingIsomorphicGraphs.put(coloredGraphs);
                    logger.debug("\tCreating new isomorphism bag for graph of coloring.");
                }
                */
                graphs.addAll(this.isomorphicGraphs.get(putIntoBag));
                this.isomorphicGraphs.remove(putIntoBag);
                this.isomorphicGraphs.add(putIntoBag, graphs);
                logger.debug("\tAdding graph of size {} to isomorphism group bag of size {}.", connectedSet.getVertexCount(), graphs.size()-1);
            } else {
                this.isomorphicGraphs.add(graphs);
                /* TODO color isomorphism
                List colorIsomorphicList = new ArrayList();
                colorIsomorphicList.add(coloredGraphs);
                this.colorPreservingIsomorphicGraphs.put(this.colorPreservingIsomorphicGraphs.size(), colorIsomorphicList);
                logger.debug("\tCreating new isomorphism bags for graph of size {} and coloring.", connectedSet.getVertexCount());
                */
            }
        }
        /*
        Collections.sort(this.isomorphicGraphs, new Comparator<List<?>>() {
            public int compare(List<?> a1, List<?> a2) {
                return a2.size() - a1.size();
            }
        });
        */
        Collections.sort(this.isomorphicGraphs, new GraphLODComparator());
        for (List<Integer> isomorphicGraphList : this.isomorphicGraphs) {
            Integer index = isomorphicGraphs.indexOf(isomorphicGraphList);
            if (graphRenderer != null) {
                this.graphRenderer.writeDotFile(index.toString(), this.connectedGraphs.get(isomorphicGraphList.get(0)), false);
            }
            List<GraphFeatures> isomorphicGraphs = new ArrayList<>();
            for (Integer graphNr : isomorphicGraphList) {
                GraphFeatures gf = this.connectedGraphs.get(graphNr);
                isomorphicGraphs.add(gf);
                List<String> colored = new ArrayList<String>();
                if (coloredPatterns.containsKey(this.isomorphicGraphs.indexOf(isomorphicGraphList))) {
                    colored = coloredPatterns.get(this.isomorphicGraphs.indexOf(isomorphicGraphList));
                }
                colored.add(JsonOutput.getJsonColored(gf, this.dataset).toString());
                coloredPatterns.put(this.isomorphicGraphs.indexOf(isomorphicGraphList), colored);
            }
            logger.info("Patterns");
            if (isomorphicGraphs.get(0).getEdgeCount() <= MAX_SIZE_FOR_PROLOD) {
                logger.info(isomorphicGraphs.size() + " x ");
                logger.info(JsonOutput.getJson(this.connectedGraphs.get(isomorphicGraphList.get(0))).toString());
                HashMap patternTemp = new HashMap<String, Integer>();
                patternTemp.put(JsonOutput.getJson(this.connectedGraphs.get(isomorphicGraphList.get(0))).toString(), isomorphicGraphs.size());
                patterns.put(index, patternTemp);
                patternDiameter.put(index, this.connectedGraphs.get(isomorphicGraphList.get(0)).getDiameter());

                if (graphRenderer != null) {
                    this.graphRenderer.writeDotFiles(index.toString() + "_detailed", isomorphicGraphs, true);
                }
            }
        }
    }

    public double calculateAverage(List<Integer> sizes) {
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
        if (!apiOnly) {
            graphCsvOutput.writeGraph(graph);
            vertexCsvOutput.writeGraph(graph);
        }
        logger.info("\thighest indegrees:");
        logger.info("\t\t" + StringUtils.join(graph.maxInDegrees(importantDegreeCount), "\n\t\t"));
        logger.info("\thighest outdegrees:");
        logger.info("\t\t" + StringUtils.join(graph.maxOutDegrees(importantDegreeCount), "\n\t\t"));

        // TODO: BiconnectedSets are too slow, even for Diseasome!
        //Set<Set<String>> bcc_sets = graph.getBiConnectedSets();
        //logger.info("\tBiconnected components: " + formatInt(bcc_sets.size()));
        //printComponentSizeAndCount(bcc_sets);
        //graphRenderer.writeDotFiles(name, "biconnected_"+groupnr, graph.createSubGraphFeatures(bcc_sets));
    }

    private String formatInt(int integer) {
        return NumberFormat.getNumberInstance(Locale.US).format(integer);
    }

    public static GraphLOD loadDataset(String name, Collection<String> datasetFiles, String namespace, String ontologyNS, Collection<String> excludedNamespaces) {
        return new GraphLOD(name, datasetFiles, true, true, false, false, namespace, ontologyNS, excludedNamespaces, 1, 1, "", 4, true);
        /* GraphStatistics graphStats = new GraphStatistics();
        return graphStats;
        */
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
        parser.addArgument("--exportJson").action(Arguments.storeTrue());
        parser.addArgument("--exportGrami").action(Arguments.storeTrue());
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
        boolean exportJson = result.getBoolean("exportJson");
        boolean exportGrami = result.getBoolean("exportGrami");
        int minImportantSubgraphSize = result.getInt("minImportantSubgraphSize");
        int importantDegreeCount = result.getInt("importantDegreeCount");
        int threadcount = result.getInt("threadcount");
        boolean debugMode = result.getBoolean("debug");
        String output = result.getString("output");

        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        if (debugMode) {
            root.setLevel(Level.DEBUG);
        } else {
            root.setLevel(Level.INFO);
        }

        //BasicConfigurator.configure();
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

        new GraphLOD(name, dataset, skipChromatic, skipGraphviz, exportJson, exportGrami, namespace, ontns, excludedNamespaces, minImportantSubgraphSize, importantDegreeCount, output, threadcount, false);
    }

    public class GraphLODComparator implements Comparator<List<?>>{
        public GraphLODComparator() {

        }

        public int compare(List<?>a1,List<?>a2) {
            return a2.size()-a1.size();
        }
    }

}

