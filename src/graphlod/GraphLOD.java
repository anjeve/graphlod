package graphlod;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Multiset;
import com.google.common.collect.TreeMultiset;
import com.google.common.io.Files;
import graphlod.algorithms.GraphFeatures;
import graphlod.algorithms.PermutationClassIsomorphismInspector;
import graphlod.dataset.Dataset;
import graphlod.graph.BFSMinimizingOrderedIterator;
import graphlod.graph.BFSOrderedIterator;
import graphlod.graph.Degree;
import graphlod.output.*;
import graphlod.output.renderer.GraphRenderer;
import graphlod.utils.GraphUtils;
import graphlod.utils.MapUtil;
import org.apache.commons.lang3.StringUtils;
import org.jgraph.graph.DefaultEdge;
import org.jgrapht.DirectedGraph;
import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.alg.StrongConnectivityInspector;
import org.jgrapht.experimental.isomorphism.GraphIsomorphismInspector;
import org.jgrapht.experimental.isomorphism.IsomorphismRelation;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DirectedSubgraph;
import org.jgrapht.graph.SimpleGraph;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.text.NumberFormat;
import java.util.*;

import static org.jgrapht.experimental.isomorphism.AdaptiveIsomorphismInspectorFactory.createIsomorphismInspector;

//import org.jgrapht.experimental.equivalence.

public class GraphLOD {
    public static final String STAR = "Star";
    public static final String MIXED_STAR = "Mixed Star";
    public static final String INBOUND_STAR = "Inbound Star";
    public static final String OUTBOUND_STAR = "Outbound Star";
    public static final String SIAMESE_STAR = "Siamese Star";
    public static final String DOUBLY_LINKED_PATH = "Doubly Linked Path";
    public static final String PATH = "Path";
    public static final String CATERPILLAR = "Caterpillar";
    public static final String LOBSTER = "Lobster";
    public static final String ANTENNA = "Antenna";
    public static final String CIRCLE = "Circle";
    public static final String WINDMILL = "Windmill ";
    public static final String WHEEL = "Wheel";
    public static final String COMPLETE = "Complete";
    public static final String UNRECOGNIZED = "";
    public static final String STRONGLY_CONNECTED = "Strongly Connected Component";

    private static Logger logger = LoggerFactory.getLogger(GraphLOD.class);

    public static final int MAX_SIZE_FOR_DIAMETER = 50;
    public static final int MAX_SIZE_FOR_CS_PRINT = 500000000;
    public static final int MAX_SIZE_FOR_PROLOD = 5000;
    public static final int MAX_SIZE_FOR_ISO = 1000;

    private GraphCsvOutput graphCsvOutput = null;
    private VertexCsvOutput vertexCsvOutput = null;
    public final GraphRenderer graphRenderer;
    public final String name;
    public final Dataset dataset;
    private final boolean exportJson;
    private final boolean exportGrami;
    private final boolean apiOnly;
    private final boolean numbersOnly;

    public GraphFeatures graphFeatures;

    public List<GraphFeatures> connectedGraphFeatures = new ArrayList<>();
    public List<GraphFeatures> stronglyConnectedGraphs = new ArrayList<>();

    public boolean giantComponent = false;

    public int nodes = 0;
    public int edges = 0;
    public int gcEdges = 0;
    public int gcNodes = 0;

    private List<GraphFeatures> bipartiteGraphs = new ArrayList<>();

    public List<String> htmlFiles = new ArrayList<>();
    public String output;

    public List<SimpleGraph<String, DefaultEdge>> connectedGraphs = new ArrayList<>();
    public List<String> connectedGraphsTypes = new ArrayList<>();

    public List<List<Integer>> isomorphicGraphs = new ArrayList<>();
    public List<SimpleGraph<String, DefaultEdge>> connectedGraphsGC = new ArrayList<>();
    public List<String> connectedGraphsGCTypes = new ArrayList<>();
    private List<List<Integer>> isomorphicGraphsGC = new ArrayList<>();
    public List<String> isomorphicGraphsGCTypes = new ArrayList<>();

    public HashMap<Integer, HashMap<String, Integer>> patterns = new HashMap<>();
    public HashMap<Integer, Double> patternDiameter = new HashMap<>();
    public HashMap<Integer, HashMap<Integer, List<String>>> coloredPatterns = new HashMap<>();
    public HashMap<Integer, List<String>> colorIsomorphicPatterns = new HashMap<>();
    public HashMap<Integer, HashMap<String, Integer>> patternsGC = new HashMap<>();
    public List<String> patternsConnectedComponents = new ArrayList<>();

    public HashMap<String, HashMap<Integer, Integer>> patternCounts = new HashMap<>();

    public List<String> isomorphicGraphsTypes = new ArrayList<>();
    public HashMap<Integer, HashMap<Integer, List<String>>> coloredPatternsGC = new HashMap<>();
    public HashMap<Integer, List<String>> colorIsomorphicPatternsGC = new HashMap<>();
    public List<String> patternsWithSurroundingGC = new ArrayList<>();

    private HashMap<String, List<Integer>> verticesInPatterns = new HashMap<>();

    public HashMap<Integer, Double> patternDiameterGC = new HashMap<>();

    public JSONObject nodeDegreeDistribution;
    public JSONObject highestIndegrees;
    public JSONObject highestOutdegrees;
    public double averageLinks;
    public List<Integer> connectedGraphSizes = new ArrayList<>();
    public List<Integer> stronglyconnectedGraphSizes = new ArrayList<>();
    private HashMap<String, HashMap<String, Integer>> stats = new HashMap<>();
    private BufferedWriter outStatsCsv;
    private BufferedWriter outStatsInboundCsv;
    private BufferedWriter outStatsOutboundCsv;
    public Integer bigComponentSize;

    public static GraphLOD fromArguments(Dataset dataset, ArgumentParser arguments) {
        GraphLOD g = new GraphLOD(arguments.getName(), arguments.isSkipChromatic(), arguments.isSkipGraphviz(), arguments.isExportJson(), arguments.isExportGrami(),
                                  arguments.getMinImportantSubgraphSize(), arguments.getImportantDegreeCount(), arguments.getBigComponentSize(), dataset, arguments.getOutput(),
                                  arguments.getThreadcount(), arguments.isApiOnly(), true, arguments.isNumbersOnly());
        return g;
    }

    public GraphLOD(String name, boolean skipChromaticNumber, boolean skipGraphviz, boolean exportJson, boolean exportGrami,
                    int minImportantSubgraphSize, int importantDegreeCount, int bigComponentSize, Dataset dataset, String output,
                    int threadCount, boolean apiOnly, boolean analyzeAlso, boolean numbersOnly) {
        this.output = output;
        this.name = name;
        this.dataset = dataset;
        this.exportJson = exportJson;
        this.exportGrami = exportGrami;
        this.apiOnly = apiOnly;
        this.numbersOnly = numbersOnly;

        if (!skipGraphviz && !exportJson && !exportGrami) {
            this.graphRenderer = new GraphRenderer(name, this.output, threadCount);
        } else {
            this.graphRenderer = null;
        }

        if (bigComponentSize > 0) {
            this.bigComponentSize = bigComponentSize;
        } else {
            this.bigComponentSize = MAX_SIZE_FOR_PROLOD;
        }
        graphFeatures = processDataset(dataset);

        createComponents();

        if (apiOnly) {

            int vertexCount = dataset.getGraph().vertexSet().size();
            if (vertexCount < this.bigComponentSize) {
                this.bigComponentSize = vertexCount;
            }
            if (analyzeAlso) {
                analyze(graphFeatures, minImportantSubgraphSize, skipChromaticNumber, importantDegreeCount);
            }
        } else {
            if (analyzeAlso) {
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

                if (!exportJson && !exportGrami) {
                    analyze(graphFeatures, minImportantSubgraphSize, skipChromaticNumber, importantDegreeCount);

                    graphCsvOutput.close();
                    vertexCsvOutput.close();

                    if (graphRenderer != null) {
                        Stopwatch sw = Stopwatch.createStarted();
                        this.htmlFiles = graphRenderer.render();
                        logger.debug("visualization took " + sw);
                    }
                    HtmlDocument htmlDocument = new HtmlDocument(this);
                    htmlDocument.createHtmlStructures();
                    htmlDocument.createHtmlConnectedSets();
                }
                graphFeatures = null;
            }
        }
    }

    private GraphFeatures processDataset(Dataset dataset) {
        Stopwatch sw = Stopwatch.createStarted();
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

        logger.info("Processing the dataset took " + sw + " to execute.");
        return graphFeatures;
    }

    private String replaceNamespace(String uri, String namespace) {
        return uri.replace(namespace, "");
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


        getStatistics();

        findPatternsInAllComponents();
        renderGCPatterns();

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

        if (this.connectedGraphFeatures.size() > 1) {
            for (GraphFeatures subGraph : connectedGraphFeatures) {
                if (subGraph.getVertices().size() >= this.bigComponentSize) continue;
                // getWalks(subGraph.getSimpleGraph());
                // String pattern = getMinimizedPatterns(subGraph.getSimpleGraph());

                this.connectedGraphSizes.add(subGraph.getVertexCount());

                if (subGraph.getVertexCount() < minImportantSubgraphSize) {
                    continue;
                }

                if (connectedGraphFeatures.size() == 1) {
                    logger.info("Graph: " + subGraph.getVertexCount());
                } else {
                    logger.info("Subgraph: " + subGraph.getVertexCount());
                }
                logger.info("  " + subGraph.getVertexCount() + " vertices");

                analyzeConnectedGraph(subGraph, importantDegreeCount, i++);

                /*
                boolean cycles = subGraph.containsCycles();
                logger.info("\tContains cycles: {}", cycles);

                boolean isTree = false;
                if (!cycles) {
                    logger.debug("Checking for tree graph.");
                    isTree = subGraph.isTree();
                    if (isTree) {
                        logger.info("\tTree: {}", isTree);
                        treeGraphs.add(subGraph);
                    }
                }

                logger.debug("Checking for completeness.");
                boolean isCompleteGraph = subGraph.isCompleteGraph();
                if (isCompleteGraph) {
                    logger.info("\tComplete graph: {}", isCompleteGraph);
                    completeGraphs.add(subGraph);
                }

                */

                boolean isBipartiteGraph = false;
                /* TODO Takes too long for some graphs and results in OutOfMemoryError. Deal with that first.
                boolean isBipartiteGraph = subGraph.isBipartite();
                if (isBipartiteGraph) {
                    System.out.printf("\tBipartite graph: %s\n", isBipartiteGraph);
                    bipartiteGraphs.add(subGraph);
                }
                */

                /*
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
                        logger.debug("Checking for star graph.");
                        isStarGraph = subGraph.isStarGraph();
                        if (isStarGraph) {
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
                                } else {
                                    isMixedDirectedStarGraph = subGraph.isMixedDirectedStarGraph();
                                    if (isMixedDirectedStarGraph) {
                                        logger.info(
                                                "\tMixed directed star graph: {}",
                                                isMixedDirectedStarGraph);
                                        mixedDirectedStarGraphs.add(subGraph);
                                    }
                                }
                            }
                        }
                    }
                }

                if (!isTree && !isBipartiteGraph && !isCaterpillar && !isLobster && !isCompleteGraph && !isPathGraph && !isMixedDirectedStarGraph && !isOutboundStarGraph && !isInboundStarGraph) {
                    unrecognizedStructure.add(subGraph);
                }
                */
            }
        } else {
            // if (!this.numbersOnly) {
                this.connectedGraphSizes.add(this.connectedGraphFeatures.get(0).getVertexCount());
            // }
        }

        if (graphRenderer != null) {
/*
            graphRenderer.writeDotFiles("connected", connectedGraphFeatures, true);
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
            */
        }

        logger.debug("Analysing the components took " + sw + " to execute.");

        logger.info("Vertex Degrees:");
        logger.info("\tAverage indegree: {}", graphFeatures.getAverageIndegree());
        logger.info("\tMax indegree: " + graphFeatures.getMaxIndegree());
        logger.info("\tMin indegree: " + graphFeatures.getMinIndegree());

        this.nodeDegreeDistribution = new JSONObject(graphFeatures.getDegreeDistribution());
        logger.info("\tNode degree distribution: {}", this.nodeDegreeDistribution);

        List<Integer> outdegrees = graphFeatures.getOutdegrees();
        logger.info("\tAverage outdegree: {}", graphlod.utils.CollectionUtils.average(outdegrees));
        logger.info("\tMax outdegree: " + graphlod.utils.CollectionUtils.max(outdegrees));
        logger.info("\tMin outdegree: " + graphlod.utils.CollectionUtils.min(outdegrees));

        ArrayList<Integer> edgeCounts = graphFeatures.getEdgeCounts();
        this.averageLinks = graphlod.utils.CollectionUtils.average(edgeCounts);
        logger.info("\tAverage links: {}", averageLinks);

        if (!skipChromaticNumber) {
            sw = Stopwatch.createStarted();
            int cN = graphFeatures.getChromaticNumber();
            logger.info("Chromatic Number: {}", cN);
            logger.debug("Getting the Chromatic Number took " + sw + " to execute.");
        }
    }

    private void renderGCPatterns() {
        if (graphRenderer != null) {
            graphRenderer.writeDotFilesGC("gcpatterns", this.connectedGraphsGC, true);
        }
    }

    private void createStatsCsv() {
        try {
            File file = new File(this.output + this.name + "_mixedstar_correlations.csv");
            Files.createParentDirs(file);
            outStatsCsv = Files.newWriter(file, Charsets.UTF_8);
            file = new File(this.output + this.name + "_inboundstar_correlations.csv");
            outStatsInboundCsv = Files.newWriter(file, Charsets.UTF_8);
            file = new File(this.output + this.name + "_outboundstar_correlations.csv");
            outStatsOutboundCsv = Files.newWriter(file, Charsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void closeStatsCsv() {
        try {
            outStatsCsv.close();
            outStatsInboundCsv.close();
            outStatsOutboundCsv.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<GraphFeatures> getConnectedComponents() {
        return connectedGraphFeatures;
    }

    public String getMinimizedPatterns(SimpleGraph<String, DefaultEdge> g) {
        String pattern = "";
        int highestDegree = 0;
        List<String> vertices = new ArrayList<>();
        HashMap<String, String> allClasses = new HashMap<>();
        HashMap<String, String> classes = new HashMap<>();
        for (String vertex : g.vertexSet()) {
            allClasses.put(vertex, this.dataset.getClassForSubject(vertex));
            Set<DefaultEdge> edges = g.edgesOf(vertex);

            Integer edgeCount = g.degreeOf(vertex);
            if (edgeCount > highestDegree) {
                vertices = new ArrayList<>();
                classes = new HashMap<>();
                vertices.add(vertex);
                classes.put(vertex, this.dataset.getClassForSubject(vertex));
                highestDegree = edges.size();
            } else if (edges.size() == highestDegree) {
                vertices.add(vertex);
                classes.put(vertex, this.dataset.getClassForSubject(vertex));
            }
        }

        String lowestClass = "";
        for (Map.Entry<String, String> entry : MapUtil.sortByValue(classes).entrySet()) {
            String vertex = entry.getKey();
            String className = entry.getValue();
            if (!lowestClass.equals("") && !className.equals(lowestClass)) {
                break;
            }
            BFSMinimizingOrderedIterator bfs = new BFSMinimizingOrderedIterator<>(g, vertex, dataset);
            while (bfs.hasNext()) {
                String currentVertex = bfs.next().toString();
                System.out.println(currentVertex + " " + replaceNamespace(this.dataset.getClassForSubject(currentVertex), this.dataset.getOntologyNamespace()) + " ");
            }

            SimpleGraph minimizedGraph = bfs.getMinimizedGraph();
            pattern = JsonOutput.getJsonColored(g, dataset, allClasses).toString().replaceAll("\\\\/", "/");

            lowestClass = className;

            // change if walks are needed!
            break;
        }

        return pattern;
    }

    public void findPatternsInAllComponents() {
        findPatterns(true, true);
    }

    public void findPatternsInGiantComponent() {
        findPatterns(true, false);
    }

    public void findPatternsInSatelliteComponents() {
        findPatterns(false, true);
    }

    private void findPatterns(boolean inGiantComponent, boolean inSatelliteComponents) {
        createStatsCsv();

        boolean giantComponent = false;
        for (GraphFeatures connectedSet : this.connectedGraphFeatures) {
            boolean added = false;
            DirectedGraph<String, DefaultEdge> graph = connectedSet.getGraph();

            if (connectedSet.getVertexCount() < this.bigComponentSize) {
                if (this.connectedGraphFeatures.size() == 1) {
                    if (!inGiantComponent) break;
                    giantComponent = true;
                } else {
                    if (!inSatelliteComponents) continue;
                }
            } else {
                if (!inGiantComponent) continue;
                giantComponent = true;
            }

            if (giantComponent) {
                this.gcNodes = graph.vertexSet().size();
                this.gcEdges = graph.edgeSet().size();
            }

            Set<String> verticesInPaths = new HashSet<>();
            Set<String> verticesInCaterpillars = new HashSet<>();
            Set<String> verticesInLobsters = new HashSet<>();
            Set<String> verticesInCircles = new HashSet<>();
            Set<String> verticesInDoublyLinkedLists = new HashSet<>();
            Set<String> verticesInAntennas = new HashSet<>();
            Set<String> verticesInWindmills = new HashSet<>();
            Set<String> verticesInWheels = new HashSet<>();
            Set<String> verticesInStars = new HashSet<>();
            Set<String> verticeCentreOfStar = new HashSet<>();
            List<Set<String>> stars = new ArrayList<>();
            // List<String> verticesInOtherPatterns = new ArrayList<>();

            // TODO add later getStronglyConnectedComponentsFromGC(graph);

            int i = 1;
            for (String v : connectedSet.getVertices()) {
                if (i % 1000 == 0) logger.info(i++ + "/" + connectedSet.getVertices().size());
                boolean vCentreOfStar = false;
                boolean vStartOfPath = false;
                if  ((connectedSet.incomingEdgesOf(v).size() == 0) && (connectedSet.outgoingEdgesOf(v).size() >= 4)) {
                    vCentreOfStar = checkVertexAsCentreOfOutboundStar(graph, v, verticesInStars, giantComponent);
                } else if  ((connectedSet.outgoingEdgesOf(v).size() == 0) && (connectedSet.incomingEdgesOf(v).size() >= 4)) {
                    vCentreOfStar = checkVertexAsCentreOfInboundStar(graph, v, verticesInStars, giantComponent);
                } else  if  ((connectedSet.incomingEdgesOf(v).size() > 0) && (connectedSet.outgoingEdgesOf(v).size() > 0)) {
                    vCentreOfStar = checkVertexAsCentreOfMixedStar(graph, v, verticesInStars, giantComponent);
                }
                if (vCentreOfStar) {
                    verticeCentreOfStar.add(v);
                    if (this.numbersOnly) {
                        Set starVertices = new HashSet<>();
                        starVertices.add(v);
                        starVertices.addAll(GraphUtils.getNeighboursOfV(graph, v));
                        stars.add(starVertices);
                    }
                    added = true;
                }

                // TODO if no vertice already on circle
                if (!verticesInCircles.contains(v)) {
                    boolean isCircle = checkVertexAsStartOfCircle(graph, v, verticesInCircles, giantComponent);
                    // verticesInOtherPatterns.addAll(verticesInCircles);
                    if (isCircle) {
                        added = true;
                    }
                }

                if (!verticesInPaths.contains(v)) {
                    vStartOfPath = checkVertexAsStartOfPath(graph, v, verticesInPaths, verticesInCircles, giantComponent, new ArrayList<>());
                    if (vStartOfPath) {
                        added = true;
                    }
                }

                // Caterpillar
                if (!verticesInDoublyLinkedLists.contains(v) && !verticesInPaths.contains(v) && !verticesInCaterpillars.contains(v) && !verticesInLobsters.contains(v)) {
                    boolean isCaterpillar = checkVertexAsStartOfCaterpillar(graph, v, verticesInCaterpillars, giantComponent);
                    if (isCaterpillar) {
                        added = true;
                    }
                }

                // Lobster
                if (!verticesInDoublyLinkedLists.contains(v) && !verticesInPaths.contains(v) && !verticesInCaterpillars.contains(v) && !verticesInLobsters.contains(v)) {
                    boolean isLobster = checkVertexAsStartOfLobster(graph, v, verticesInLobsters, giantComponent);
                    if (isLobster) {
                        added = true;
                    }
                }

                // Antenna
                if (!verticeCentreOfStar.contains(v) && !verticesInDoublyLinkedLists.contains(v) && !verticesInPaths.contains(v) && !verticesInCaterpillars.contains(v) && !verticesInAntennas.contains(v)) {
                    boolean isAntenna = checkVertexAsCentreOfAntenna(graph, v, verticesInAntennas, giantComponent);
                    if (isAntenna) {
                        added = true;
                    }
                }

                if (!verticeCentreOfStar.contains(v)) {
                    if (!verticesInWindmills.contains(v)) {
                        boolean isWindmill = checkVertexAsCentreOfWindmill(graph, v, verticesInWindmills, giantComponent);
                        // verticesInOtherPatterns.addAll(verticesInWindmills);
                        if (isWindmill) {
                            added = true;
                        }
                    }
                    if (!verticesInWindmills.contains(v) && !verticesInWheels.contains(v)) {
                        boolean isWheel = checkVertexAsCentreOfWheel(graph, v, verticesInWheels, giantComponent);
                        // verticesInOtherPatterns.addAll(verticesInWheels);
                        if (isWheel) {
                            added = true;
                        }
                    }
                    if (!verticesInDoublyLinkedLists.contains(v)) {
                        boolean isDoublyLinkedList = checkDoublyLinkedPathsFromGC(graph, v, verticesInDoublyLinkedLists, giantComponent);
                        if (isDoublyLinkedList) {
                            added = true;
                        }
                    }
                        }
                    }

            // this works different for not GC

            getCompleteGraphs(graph, giantComponent);


            if (giantComponent) {
                getSiameseStarsFromGC(graph, giantComponent, stars);
            }

            if (!giantComponent && !added && !this.numbersOnly) {
                addUnrecognizedPatterns(graph, UNRECOGNIZED, giantComponent);
            }

            // Add patterns for giant component of remaining high degree vertices
            if (giantComponent && inGiantComponent) {
                /*
                List<Degree> maxDegrees = graphFeatures.maxDegrees(5);
                HashMap<String, Integer> highestDegreeMap = new HashMap<>();
                HashMap<String, Integer> highestOutdegreeMap = new HashMap<>();
                for (Degree degree : maxDegrees) {
                    // TODO if (degree.vertex) not in pattern
                    // highestIndegreeMap.put(degree.vertex, degree.degree);
                    logger.info("Adding pattern around node of degree {} - {}", degree.degree, degree.vertex);
                    addPatternsAroundVertex(graph, degree.vertex, degree.degree);
        }
                */
            }

            // TODO Divide giant component and run pattern analysis again

        }

        closeStatsCsv();

        if (!this.numbersOnly) {
            if (giantComponent && inGiantComponent) {
                logger.info("Isomorphism groups for GC patterns");
                groupIsomorphicGraphs(this.connectedGraphsGC, this.connectedGraphsGCTypes, this.isomorphicGraphsGC, this.isomorphicGraphsGCTypes, this.colorIsomorphicPatternsGC, this.patternsGC, this.coloredPatternsGC, this.patternsWithSurroundingGC);
            }

            if (inSatelliteComponents) {
                logger.info("Isomorphism groups for patterns");
                groupIsomorphicGraphs(this.connectedGraphs, this.connectedGraphsTypes, this.isomorphicGraphs, this.isomorphicGraphsTypes, this.colorIsomorphicPatterns, this.patterns, this.coloredPatterns, this.patternsConnectedComponents);
            }
        }

        if (this.numbersOnly) {
            printNumbers();
        }
    }

    private void printNumbers() {
        for (Map.Entry<String, HashMap<Integer, Integer>> entry : this.patternCounts.entrySet()) {
            String patternName = entry.getKey();
            logger.info(patternName);
            Integer overall = 0;
            for (Map.Entry<Integer, Integer> counts : entry.getValue().entrySet()) {
                Integer patternSize = counts.getKey();
                Integer patternCount = counts.getValue();
                logger.info(" " + patternCount + " x " + patternSize);
                overall  += patternCount;
            }
            logger.info(" = " + overall);
        }
    }

    private void getCompleteGraphs(DirectedGraph<String, DefaultEdge> graph, boolean giantComponent) {
        // TODO if not giant component...
        /*
        BronKerboschCliqueFinder cliqueFinder = new BronKerboschCliqueFinder<>(graph);
        Collection<Set> cliques = cliqueFinder.getAllMaximalCliques();
        List<Set> cliquesToAdd = new ArrayList<>();
        for (Set clique: cliques) {
            if (clique.size() > 3) {
                for (Set cliqueAdded: cliquesToAdd) {
                    if (cliqueAdded.containsAll(clique) && (clique.size() > cliqueAdded.size())) {
                        cliquesToAdd.remove(cliqueAdded);
                    }
                }
                cliquesToAdd.add(clique);
            }
        }
        if (!giantComponent && (cliquesToAdd.size() != 1)) {
            return;
        } else if (!giantComponent && (cliquesToAdd.size() == 1)) {
            if (cliquesToAdd.get(0).size() != graph.vertexSet().size()) return;
        }
        for (Set clique: cliquesToAdd) {
            addPatterns((List<String>) clique, graph, COMPLETE, giantComponent);
        }
        */
    }

    private void getStronglyConnectedComponentsFromGC(DirectedGraph<String, DefaultEdge> graph, boolean giantComponent) {
        StrongConnectivityInspector<String, DefaultEdge> sci = new StrongConnectivityInspector<>(graph);
        List<DirectedSubgraph<String, DefaultEdge>> stronglyConnectedComponents = sci.stronglyConnectedSubgraphs();
        for (DirectedSubgraph<String, DefaultEdge> subGraph : stronglyConnectedComponents) {
            if (subGraph.vertexSet().size() >= 4) {
                addPatterns(graph, subGraph, STRONGLY_CONNECTED, giantComponent);
            }
        }
    }

    private void getSiameseStarsFromGC(DirectedGraph<String, DefaultEdge> graph, boolean giantComponent, List<Set<String>> verticesInStars) {
        List<String> verticesinSiameseStars = new ArrayList<>();
        if (this.numbersOnly) {
            for (Set<String> starVertices1 : verticesInStars) {
                int index1 = verticesInStars.indexOf(starVertices1);
                for (Set<String> starVertices2 : verticesInStars) {
                    int index2 = verticesInStars.indexOf(starVertices2);
                    if (index1 == index2) continue;
                    if (verticesinSiameseStars.containsAll(starVertices1)) continue;
                    Set<String> intersection = new HashSet<>(starVertices1);
                    intersection.retainAll(starVertices2);
                    if (!intersection.isEmpty()) {
                        Set<String> siameseStar = new HashSet<>();
                        siameseStar.addAll(starVertices1);
                        siameseStar.addAll(starVertices2);
                        verticesinSiameseStars.addAll(siameseStar);
                        int patternSize = siameseStar.size();
                        HashMap<Integer, Integer> count = new HashMap<>();
                        Integer patternCount = 1;
                        if (this.patternCounts.containsKey(SIAMESE_STAR)) {
                            count = this.patternCounts.get(SIAMESE_STAR);
                            if (count.containsKey(patternSize)) {
                                patternCount += count.get(patternSize);
                            }
                        }
                        count.put(patternSize, patternCount);
                        this.patternCounts.put(SIAMESE_STAR, count);
                    }
                }
            }
            return;
        }
        List<String> typesToAdd = new ArrayList<>();
        for (ListIterator<String> iterator = this.connectedGraphsGCTypes.listIterator(); iterator.hasNext(); ) {
            String patternType = iterator.next();
            int index = this.connectedGraphsGCTypes.indexOf(patternType);
            SimpleGraph<String, DefaultEdge> simpleGraph = this.connectedGraphsGC.get(index);
            if (patternType.equals(STAR)) {
                boolean alsoInOtherStar = checkIfAnyVerticeInStarIsAlsoInAnotherStar(simpleGraph.vertexSet(), verticesinSiameseStars);
                if (!alsoInOtherStar) continue;
                for (Object v: simpleGraph.vertexSet()) {
                    if ((this.verticesInPatterns.get(v.toString()).size() > 1) && (!verticesinSiameseStars.contains(v.toString()))) {
                        DirectedGraph<String, DefaultEdge> doublyLinkedPath = new DefaultDirectedGraph<>(DefaultEdge.class);
                        SimpleGraph<String, DefaultEdge> simpleDoublyLinkedPath = new SimpleGraph<>(DefaultEdge.class);
                        DirectedGraph<String, DefaultEdge> doublyLinkedPath2 = new DefaultDirectedGraph<>(DefaultEdge.class);
                        for (Integer id: this.verticesInPatterns.get(v.toString())) {
                            String secondPatternType = this.connectedGraphsGCTypes.get(id);
                            if (secondPatternType.equals(STAR)) {
                                SimpleGraph star = this.connectedGraphsGC.get(id);
                                for (Object vertexInStarO : star.vertexSet()) {
                                    String vertexInStar = vertexInStarO.toString();
                                    if (!simpleDoublyLinkedPath.containsVertex(vertexInStar)) {
                                        simpleDoublyLinkedPath.addVertex(vertexInStar);
                                        doublyLinkedPath.addVertex(vertexInStar);
                                        verticesinSiameseStars.add(vertexInStar);
                                    }
                                    if (simpleDoublyLinkedPath.vertexSet().size() >= 1) {
                                        for (Object vertexInStarAlreadyO : simpleDoublyLinkedPath.vertexSet()) {
                                            String vertexInStarAlready = vertexInStarAlreadyO.toString();
                                            addEdges(doublyLinkedPath, this.dataset.getGraph().getAllEdges(vertexInStar, vertexInStarAlready), vertexInStar, vertexInStarAlready);
                                            addEdges(doublyLinkedPath, this.dataset.getGraph().getAllEdges(vertexInStarAlready, vertexInStar), vertexInStarAlready, vertexInStar);
                                            addEdges(simpleDoublyLinkedPath, this.dataset.getGraph().getAllEdges(vertexInStar, vertexInStarAlready), vertexInStar, vertexInStarAlready);
                                            addEdges(simpleDoublyLinkedPath, this.dataset.getGraph().getAllEdges(vertexInStarAlready, vertexInStar), vertexInStarAlready, vertexInStar);
                                        }
                                    }
                                }
                            }

                        }
                        if (simpleDoublyLinkedPath.vertexSet().size() > 0) {
                            logger.info("Adding siamese star of size " + simpleDoublyLinkedPath.vertexSet().size());
                            // TODO
                            Set<String> verticesInCurrentSiameseStar = new HashSet<>();
                            verticesInCurrentSiameseStar.addAll(simpleDoublyLinkedPath.vertexSet());

                            addPatterns(verticesInCurrentSiameseStar, graph, doublyLinkedPath, simpleDoublyLinkedPath, doublyLinkedPath2, SIAMESE_STAR, giantComponent);
                            typesToAdd.add(SIAMESE_STAR);
                            break;
                        }
                    }
                }
            }
        }
        for (String type: typesToAdd) {
            this.connectedGraphsGCTypes.add(type);
        }
    }

    private boolean checkIfAnyVerticeInStarIsAlsoInAnotherStar(Set<String> vertices, List verticesinSiameseStars) {
        Set<Integer> stars = new HashSet<>();
        for (String v: vertices) {
            if ((this.verticesInPatterns.get(v).size() > 1) && (!verticesinSiameseStars.contains(v))) {
                for (Integer id : this.verticesInPatterns.get(v)) {
                    if (this.connectedGraphsGCTypes.get(id).equals(STAR)) {
                       stars.add(id);
                    }
                }
            }
        }
        if (stars.size() > 1) {
            return true;
        }
        return false;
    }

    /*
    private boolean checkIfAnyVerticeInStarIsAlsoInAnotherStar(Set<String> vertices, List<Set<String> vertices, List verticesinSiameseStars) {
        Set<Integer> stars = new HashSet<>();
        for (String v: vertices) {
            if ((this.verticesInPatterns.get(v).size() > 1) && (!verticesinSiameseStars.contains(v))) {
                for (Integer id : this.verticesInPatterns.get(v)) {
                    if (this.connectedGraphsGCTypes.get(id).equals(STAR)) {
                        stars.add(id);
                    }
                }
            }
        }
        if (stars.size() > 1) {
            return true;
        }
        return false;
    }
    */

    private boolean checkDoublyLinkedPathsFromGC(DirectedGraph<String, DefaultEdge> graph, String v, Set<String> verticesInDoublyLinkedLists, boolean giantComponent) {
        // Check if this is not the first vertex of a doubly linked list
        List<String> neighbourVertices = GraphUtils.getNeighboursOfV(graph, v);
        if (neighbourVertices.size() == 2) {
            boolean firstNeighbourVCouldBeStart = false;
            boolean secondNeighbourVCouldBeStart = false;
            int i = 0;
            for (String neighbourV: neighbourVertices) {
                Set<DefaultEdge> outgoingEdges = graph.getAllEdges(v, neighbourV);
                Set<DefaultEdge> incomingEdges = graph.getAllEdges(neighbourV, v);
                if ((outgoingEdges.size() == 1) && (incomingEdges.size() == 1)) {
                    if (i == 0) {
                        firstNeighbourVCouldBeStart = true;
                    } else {
                        secondNeighbourVCouldBeStart = true;
                    }
                    i++;
                }
            }
            if (firstNeighbourVCouldBeStart && secondNeighbourVCouldBeStart) {
                try {
                return checkDoublyLinkedPathsFromGC(graph, neighbourVertices.get(0), verticesInDoublyLinkedLists, giantComponent);
                } catch (StackOverflowError e) {
                    return false;
            }
        }
        }

        List<String> doublyLinkedList = checkVertexInLinkedList(graph, v, new ArrayList<String>());

        if (!giantComponent && (doublyLinkedList.size() < graph.vertexSet().size())) {
            return false;
        }

        if (doublyLinkedList.size() >= 4) {
            DirectedGraph<String, DefaultEdge> doublyLinkedPath = new DefaultDirectedGraph<>(DefaultEdge.class);
            SimpleGraph<String, DefaultEdge> simpleDoublyLinkedPath = new SimpleGraph<>(DefaultEdge.class);
            DirectedGraph<String, DefaultEdge> doublyLinkedPath2 = new DefaultDirectedGraph<>(DefaultEdge.class);
            String lastVertex = null;
            for (String vertex : doublyLinkedList) {
                doublyLinkedPath.addVertex(vertex);
                simpleDoublyLinkedPath.addVertex(vertex);
                if (lastVertex != null) {
                    addEdges(doublyLinkedPath, graph.getAllEdges(vertex, lastVertex), vertex, lastVertex);
                    addEdges(doublyLinkedPath, graph.getAllEdges(lastVertex, vertex), lastVertex, vertex);
                    addEdges(simpleDoublyLinkedPath, graph.getAllEdges(vertex, lastVertex), vertex, lastVertex);
                    addEdges(simpleDoublyLinkedPath, graph.getAllEdges(lastVertex, vertex), lastVertex, vertex);
                }
                lastVertex = vertex;
            }

            addPatterns(verticesInDoublyLinkedLists, new HashSet<String>(doublyLinkedList), graph, doublyLinkedPath, simpleDoublyLinkedPath, doublyLinkedPath2, DOUBLY_LINKED_PATH, giantComponent);
        } else  if (doublyLinkedList.size() >= 3) {
            // logger.info("Doubly linked path of length {} found", doublyLinkedList.size());
            return false;
        }
        return true;
    }

    private List<String> checkVertexInLinkedList(DirectedGraph<String, DefaultEdge> graph, String v, List<String> visited) {
        visited.add(v);
        Set<DefaultEdge> outgoing = graph.outgoingEdgesOf(v);
        String nextVertex = null;
        for (DefaultEdge outgoingEdge : outgoing) {
            // TODO for the first vertex there might be more options for the next vertex to consider
            // TODO also the last vertex can have more than 2 outgoing vertices!
            /*
            if ((visited.size() > 1) && (outgoing.size() > 2)) {
                break;
            }
            */
            String oppositeVertex1 = outgoingEdge.getTarget().toString(); // Graphs.getOppositeVertex(graph, v, outgoingEdge).toString();
            if (visited.contains(oppositeVertex1)) {
                 continue;
            }
            Set<DefaultEdge> outgoingEdges = graph.getAllEdges(v, oppositeVertex1);
            Set<DefaultEdge> incomingEdges = graph.getAllEdges(oppositeVertex1, v);
            if ((outgoingEdges.size() == 1) && (incomingEdges.size() == 1)) {
                nextVertex = oppositeVertex1;
                break;
            }
        }
        List<String> linkedList = new ArrayList<>();
        if (nextVertex != null) {
            linkedList = checkVertexInLinkedList(graph, nextVertex, visited);
        }
        linkedList.add(v);
        return linkedList;
    }

    private boolean checkVertexAsCentreOfWindmill(DirectedGraph<String, DefaultEdge> graph, String v_center, Set<String> verticesInWindmills, boolean giantComponent) {
        List<String> neighbourVertices = Graphs.neighborListOf(graph, v_center);

        if (!giantComponent && ((neighbourVertices.size() + 1) < graph.vertexSet().size())) {
            return false;
        }

        boolean oneVertexWithNoNeighbourHere = false;
        String dontAddThisVertex = null;
        if (neighbourVertices.size() < 4) {
            return false;
        }

        for (String neighborV: neighbourVertices) {
            List<String> secondNeighbourVertices = Graphs.neighborListOf(graph, neighborV);
            secondNeighbourVertices.retainAll(neighbourVertices);
            if (secondNeighbourVertices.size() > 1) {
                return false;
            } else if (secondNeighbourVertices.size() == 0) {
                if (!oneVertexWithNoNeighbourHere) {
                    oneVertexWithNoNeighbourHere = true;
                    dontAddThisVertex = neighborV;
                } else {
                    return false;
                }

            }
        }
        DirectedGraph<String, DefaultEdge> windmillGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
        SimpleGraph<String, DefaultEdge> simpleWindmillGraph = new SimpleGraph<>(DefaultEdge.class);
        DirectedGraph<String, DefaultEdge> windmillGraph2 = new DefaultDirectedGraph<>(DefaultEdge.class);
        windmillGraph.addVertex(v_center);
        simpleWindmillGraph.addVertex(v_center);
        for (String vertex : neighbourVertices) {
            if (vertex.equals(dontAddThisVertex)) {
                continue;
            }
            windmillGraph.addVertex(vertex);
            simpleWindmillGraph.addVertex(vertex);
            for (String alreadyAdded : simpleWindmillGraph.vertexSet()) {
                addEdges(windmillGraph, graph.getAllEdges(vertex, alreadyAdded), vertex, alreadyAdded);
                addEdges(windmillGraph, graph.getAllEdges(alreadyAdded, vertex), alreadyAdded, vertex);
                addEdges(simpleWindmillGraph, graph.getAllEdges(vertex, alreadyAdded), vertex, alreadyAdded);
                addEdges(simpleWindmillGraph, graph.getAllEdges(alreadyAdded, vertex), alreadyAdded, vertex);
            }
        }
        addPatterns(verticesInWindmills, new HashSet<String>(neighbourVertices), graph, windmillGraph, simpleWindmillGraph, windmillGraph2, WINDMILL, giantComponent);
        logger.info("Adding windmill of size {}", windmillGraph.vertexSet().size());
        verticesInWindmills.add(v_center);
        return true;
    }

    private boolean checkVertexAsCentreOfWheel(DirectedGraph<String, DefaultEdge> graph, String v_center, Set<String> verticesinWheels, boolean giantComponent) {
        List<String> neighbourVertices = Graphs.neighborListOf(graph, v_center);
        if (neighbourVertices.size() < 4) {
            return false;
        }

        if (!giantComponent && ((neighbourVertices.size() + 1) < graph.vertexSet().size())) {
            return false;
        }

        boolean oneVertexWithNoNeighbourHere = false;
        String dontAddThisVertex = null;
        for (String neighborV: neighbourVertices) {
            List<String> secondNeighbourVertices = Graphs.neighborListOf(graph, neighborV);
            secondNeighbourVertices.retainAll(neighbourVertices);
            if (secondNeighbourVertices.size() != 2) {
                if (!oneVertexWithNoNeighbourHere && (secondNeighbourVertices.size() == 0)) {
                    oneVertexWithNoNeighbourHere = true;
                    dontAddThisVertex = neighborV;
                } else {
                    return false;
                }
            }
        }
        DirectedGraph<String, DefaultEdge> windmillGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
        SimpleGraph<String, DefaultEdge> simpleWindmillGraph = new SimpleGraph<>(DefaultEdge.class);
        DirectedGraph<String, DefaultEdge> windmillGraph2 = new DefaultDirectedGraph<>(DefaultEdge.class);
        windmillGraph.addVertex(v_center);
        simpleWindmillGraph.addVertex(v_center);
        for (String vertex : neighbourVertices) {
            if (vertex.equals(dontAddThisVertex)) {
                continue;
            }
            windmillGraph.addVertex(vertex);
            simpleWindmillGraph.addVertex(vertex);
            for (String alreadyAdded : simpleWindmillGraph.vertexSet()) {
                addEdges(windmillGraph, graph.getAllEdges(vertex, alreadyAdded), vertex, alreadyAdded);
                addEdges(windmillGraph, graph.getAllEdges(alreadyAdded, vertex), alreadyAdded, vertex);
                addEdges(simpleWindmillGraph, graph.getAllEdges(vertex, alreadyAdded), vertex, alreadyAdded);
                addEdges(simpleWindmillGraph, graph.getAllEdges(alreadyAdded, vertex), alreadyAdded, vertex);
            }
        }
        addPatterns(verticesinWheels, new HashSet<String>(neighbourVertices), graph, windmillGraph, simpleWindmillGraph, windmillGraph2, WHEEL, giantComponent);
        logger.info("Adding wheel of size {}", windmillGraph.vertexSet().size());
        verticesinWheels.add(v_center);
        return true;
    }

    private boolean checkVertexAsCentreOfMixedStar(DirectedGraph<String, DefaultEdge> graph, String v_center, Set<String> verticesInStars, boolean giantComponent) {
        List<String> neighbourVertices = GraphUtils.getNeighboursOfV(graph, v_center);
        if (neighbourVertices.size() < 4) {
            return false;
        }
        if (!giantComponent && ((neighbourVertices.size() + 1) < graph.vertexSet().size())) {
            return false;
        }
        Set<DefaultEdge> surroundingEdges = graph.outgoingEdgesOf(v_center);
        Set<DefaultEdge> sei = graph.incomingEdgesOf(v_center);
        if (((surroundingEdges.size() +sei.size()) >= 4) && (surroundingEdges.size() >= 1) && (sei.size() >= 1)) {
            Set<String> surroundingVertices = new HashSet<>();
            Set<String> vertices = new HashSet<>();
            int numberOfEdgesForSurrounding = 0;
            DirectedGraph<String, DefaultEdge> outgoingStar = new DefaultDirectedGraph<>(DefaultEdge.class);
            SimpleGraph<String, DefaultEdge> simpleStar = new SimpleGraph<>(DefaultEdge.class);
            DirectedGraph<String, DefaultEdge> outgoingStarLevel2 = new DefaultDirectedGraph<>(DefaultEdge.class);
            outgoingStar.addVertex(v_center);
            simpleStar.addVertex(v_center);
            for (DefaultEdge sE : surroundingEdges) {
                String v_level1 = sE.getTarget().toString();
                Set<DefaultEdge> incomingEdges2 = graph.incomingEdgesOf(v_level1);
                Set<DefaultEdge> outgoingEdges2 = graph.outgoingEdgesOf(v_level1);
                numberOfEdgesForSurrounding += incomingEdges2.size();
                numberOfEdgesForSurrounding += outgoingEdges2.size();
                outgoingStar.addVertex(v_level1);
                simpleStar.addVertex(v_level1);
                addEdge(outgoingStar, sE.toString(), v_center, v_level1);
                addEdge(simpleStar, sE.toString(), v_center, v_level1);
                vertices.add(v_level1);
                for (DefaultEdge se2 : incomingEdges2) {
                    String v_level2 = se2.getSource().toString();
                    if (v_level2.equals(v_center)) continue;
                    if (neighbourVertices.contains(v_level2)) {
                        // TODO count connectivity and allow some
                        return false;
                    }
                    surroundingVertices.add(v_level2);
                }
                for (DefaultEdge se2 : outgoingEdges2) {
                    String v_level2 = se2.getTarget().toString();
                    if (neighbourVertices.contains(v_level2)) {
                        // TODO count connectivity and allow some
                        return false;
                    }
                    surroundingVertices.add(v_level2);
                }
            }
            for (DefaultEdge sE : sei) {
                String v_level1 = sE.getSource().toString();
                Set<DefaultEdge> incomingEdges2 = graph.incomingEdgesOf(v_level1);
                Set<DefaultEdge> outgoingEdges2 = graph.outgoingEdgesOf(v_level1);
                numberOfEdgesForSurrounding += incomingEdges2.size();
                numberOfEdgesForSurrounding += outgoingEdges2.size();
                outgoingStar.addVertex(v_level1);
                simpleStar.addVertex(v_level1);
                addEdge(outgoingStar, sE.toString(), v_level1, v_center);
                addEdge(simpleStar, sE.toString(), v_level1, v_center);
                vertices.add(v_level1);
                for (DefaultEdge se2 : incomingEdges2) {
                    String v_level2 = se2.getSource().toString();
                    if (neighbourVertices.contains(v_level2)) {
                        // TODO count connectivity and allow some
                        return false;
                    }
                    surroundingVertices.add(v_level2);
                }
                for (DefaultEdge se2 : outgoingEdges2) {
                    String v_level2 = se2.getTarget().toString();
                    if (v_level2.equals(v_center)) continue;
                    if (neighbourVertices.contains(v_level2)) {
                        // TODO count connectivity and allow some
                        return false;
                    }
                    surroundingVertices.add(v_level2);
                }
            }
            //addEdgesOnLevel2Vertices(connectedSet, outgoingStarLevel2, surroundingVertices);
            if ((numberOfEdgesForSurrounding <= surroundingEdges.size()) || (surroundingVertices.size() <= (vertices.size() + 1))) {
                addPatterns(verticesInStars, new HashSet<String>(neighbourVertices), graph, outgoingStar, simpleStar, outgoingStarLevel2, MIXED_STAR, giantComponent);
                // addStats(v_center, neighbourVertices, outStatsCsv);
                logger.info("Adding mixed star of size {}", outgoingStar.vertexSet().size());
                return true;
            }
        }
        return false;
    }

    private void addStats(String v_center, List<String> surroundingVertices, BufferedWriter writer) {
        String centerNodeClass = dataset.getClassForSubject(v_center);
        HashMap<String, Integer> classesSurrounding = new HashMap<>();
        for (String v : surroundingVertices) {
            String nodeClass = dataset.getClassForSubject(v);
            Integer subjectsPerClass = 1;
            if (classesSurrounding.containsKey(nodeClass)) subjectsPerClass += classesSurrounding.get(nodeClass);
            classesSurrounding.put(nodeClass, subjectsPerClass);
        }
        Map<String, Integer> sortedMap = new TreeMap<>(classesSurrounding);
        try {
            writer.write(centerNodeClass);
            for (Map.Entry<String, Integer> entry : sortedMap.entrySet()) {
                String key = entry.getKey();
                Integer value = entry.getValue();
                writer.write(";" + key + ";" + value);
            }
            writer.write(System.lineSeparator());
        } catch (IOException e) {
            e.printStackTrace();
        }
        //stats.put(centerNodeClass, classesSurrounding);
    }

    private boolean checkVertexAsStartOfCircle(DirectedGraph<String, DefaultEdge> graph, String v, Set<String> verticesInCircles, boolean giantComponent) {
        List<String> path = checkVertexAsPartOfCircle(graph, v, new ArrayList<String>());

        if (!giantComponent && (path.size() < graph.vertexSet().size())) {
            return false;
        }

        if (path.size() == 0) {
            return false;
        } else if (path.size() >= 4) {
            DirectedGraph<String, DefaultEdge> doublyLinkedPath = new DefaultDirectedGraph<>(DefaultEdge.class);
            SimpleGraph<String, DefaultEdge> simpleDoublyLinkedPath = new SimpleGraph<>(DefaultEdge.class);
            DirectedGraph<String, DefaultEdge> doublyLinkedPath2 = new DefaultDirectedGraph<>(DefaultEdge.class);
            String lastVertex = null;
            for (String vertex : path) {
                doublyLinkedPath.addVertex(vertex);
                simpleDoublyLinkedPath.addVertex(vertex);
                // TODO don't add edges we have added before!
                if (lastVertex != null) {
                    addEdges(doublyLinkedPath, graph.getAllEdges(vertex, lastVertex), vertex, lastVertex);
                    addEdges(doublyLinkedPath, graph.getAllEdges(lastVertex, vertex), lastVertex, vertex);
                    addEdges(simpleDoublyLinkedPath, graph.getAllEdges(vertex, lastVertex), vertex, lastVertex);
                    addEdges(simpleDoublyLinkedPath, graph.getAllEdges(lastVertex, vertex), lastVertex, vertex);
                }
                lastVertex = vertex;
            }
            addEdges(doublyLinkedPath, graph.getAllEdges(path.get(0), lastVertex), path.get(0), lastVertex);
            addEdges(doublyLinkedPath, graph.getAllEdges(lastVertex, path.get(0)), lastVertex, path.get(0));
            addEdges(simpleDoublyLinkedPath, graph.getAllEdges(path.get(0), lastVertex), path.get(0), lastVertex);
            addEdges(simpleDoublyLinkedPath, graph.getAllEdges(lastVertex, path.get(0)), lastVertex, path.get(0));

            addPatterns(verticesInCircles, new HashSet<>(path), graph, doublyLinkedPath, simpleDoublyLinkedPath, doublyLinkedPath2, CIRCLE, giantComponent);
            logger.info("Adding circle of size {}", doublyLinkedPath.vertexSet().size());
            return true;
        } else if (path.size() >= 3) {
            //logger.info("Circle of length {} found", path.size());
        }
        return false;
    }

    private void addPatternsAroundVertex(DirectedGraph<String, DefaultEdge> graph, String v, int degree) {
        List<String> neighbourVertices = GraphUtils.getNeighboursOfV(graph, v);
        Set<String> vertexList = new HashSet<>();
        vertexList.addAll(neighbourVertices);
        vertexList.add(v);
        DirectedGraph<String, DefaultEdge> directedGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
        SimpleGraph<String, DefaultEdge> simpleGraph = new SimpleGraph<>(DefaultEdge.class);
        DirectedGraph<String, DefaultEdge> directedGraphSurrounding = new DefaultDirectedGraph<>(DefaultEdge.class);
        for (String vertex : vertexList) {
            directedGraph.addVertex(vertex);
            simpleGraph.addVertex(vertex);
            for (String alreadyAdded : simpleGraph.vertexSet()) {
                addEdges(directedGraph, graph.getAllEdges(vertex, alreadyAdded), vertex, alreadyAdded);
                addEdges(directedGraph, graph.getAllEdges(alreadyAdded, vertex), alreadyAdded, vertex);
                addEdges(simpleGraph, graph.getAllEdges(vertex, alreadyAdded), vertex, alreadyAdded);
                addEdges(simpleGraph, graph.getAllEdges(alreadyAdded, vertex), alreadyAdded, vertex);
            }
        }
        addPatterns(new HashSet<>(), vertexList, graph, directedGraph, simpleGraph, directedGraphSurrounding, "Degree "+degree, true);
    }

    private void addUnrecognizedPatterns(DirectedGraph<String, DefaultEdge> graph, String patternType, boolean giantComponent) {
        Set<String> vertices = graph.vertexSet();
        Set<String> vertexList = new HashSet<>();
        vertexList.addAll(vertices);
        DirectedGraph<String, DefaultEdge> directedGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
        SimpleGraph<String, DefaultEdge> simpleGraph = new SimpleGraph<>(DefaultEdge.class);
        DirectedGraph<String, DefaultEdge> directedGraphSurrounding = new DefaultDirectedGraph<>(DefaultEdge.class);
        for (String vertex : vertices) {
            directedGraph.addVertex(vertex);
            simpleGraph.addVertex(vertex);
            for (String alreadyAdded : simpleGraph.vertexSet()) {
                addEdges(directedGraph, graph.getAllEdges(vertex, alreadyAdded), vertex, alreadyAdded);
                addEdges(directedGraph, graph.getAllEdges(alreadyAdded, vertex), alreadyAdded, vertex);
                addEdges(simpleGraph, graph.getAllEdges(vertex, alreadyAdded), vertex, alreadyAdded);
                addEdges(simpleGraph, graph.getAllEdges(alreadyAdded, vertex), alreadyAdded, vertex);
            }
        }
        addPatterns(new HashSet<>(), vertexList, graph, directedGraph, simpleGraph, directedGraphSurrounding, patternType, giantComponent);
    }

    private void addPatterns(Set<String> verticesInPattern, DirectedGraph<String, DefaultEdge> graph, DirectedSubgraph<String, DefaultEdge> subgraph, String patternType, boolean giantComponent) {
        Set<String> vertices = subgraph.vertexSet();
        Set<String> vertexList = new HashSet<>();
        vertexList.addAll(vertices);
        DirectedGraph<String, DefaultEdge> directedGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
        SimpleGraph<String, DefaultEdge> simpleGraph = new SimpleGraph<>(DefaultEdge.class);
        DirectedGraph<String, DefaultEdge> directedGraphSurrounding = new DefaultDirectedGraph<>(DefaultEdge.class);
        for (String vertex : vertices) {
            directedGraph.addVertex(vertex);
            simpleGraph.addVertex(vertex);
            for (String alreadyAdded : simpleGraph.vertexSet()) {
                addEdges(directedGraph, graph.getAllEdges(vertex, alreadyAdded), vertex, alreadyAdded);
                addEdges(directedGraph, graph.getAllEdges(alreadyAdded, vertex), alreadyAdded, vertex);
                addEdges(simpleGraph, graph.getAllEdges(vertex, alreadyAdded), vertex, alreadyAdded);
                addEdges(simpleGraph, graph.getAllEdges(alreadyAdded, vertex), alreadyAdded, vertex);
            }
        }
        addPatterns(verticesInPattern, vertexList, graph, directedGraph, simpleGraph, directedGraphSurrounding, patternType, giantComponent);
    }

    private void addPatterns(Set<String> vertices, DirectedGraph<String, DefaultEdge> graph, String patternType, boolean giantComponent) {
        Set<String> vertexList = new HashSet<>();
        vertexList.addAll(vertices);
        DirectedGraph<String, DefaultEdge> directedGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
        SimpleGraph<String, DefaultEdge> simpleGraph = new SimpleGraph<>(DefaultEdge.class);
        DirectedGraph<String, DefaultEdge> directedGraphSurrounding = new DefaultDirectedGraph<>(DefaultEdge.class);
        for (String vertex : vertices) {
            directedGraph.addVertex(vertex);
            simpleGraph.addVertex(vertex);
            for (String alreadyAdded : simpleGraph.vertexSet()) {
                addEdges(directedGraph, graph.getAllEdges(vertex, alreadyAdded), vertex, alreadyAdded);
                addEdges(directedGraph, graph.getAllEdges(alreadyAdded, vertex), alreadyAdded, vertex);
                addEdges(simpleGraph, graph.getAllEdges(vertex, alreadyAdded), vertex, alreadyAdded);
                addEdges(simpleGraph, graph.getAllEdges(alreadyAdded, vertex), alreadyAdded, vertex);
            }
        }
        addPatterns(new HashSet<>(), vertexList, graph, directedGraph, simpleGraph, directedGraphSurrounding, patternType, giantComponent);
    }

    private void addPatterns(DirectedGraph<String, DefaultEdge> graph, DirectedSubgraph<String, DefaultEdge> subgraph, String patternType, boolean giantComponent) {
        addPatterns(new HashSet<>(), graph, subgraph, patternType, giantComponent);
    }

    private void addPatterns(Set<String> vertices, DirectedGraph<String, DefaultEdge> graph, DirectedGraph<String, DefaultEdge> directedGraph, SimpleGraph<String, DefaultEdge> simpleGraph, DirectedGraph<String, DefaultEdge> directedGraphSurrounding, String patternType, boolean giantComponent) {
        addPatterns(new HashSet<>(), vertices, graph, directedGraph, simpleGraph, directedGraphSurrounding, patternType, giantComponent);
    }

    private void addPatterns(Set<String> verticesInPattern, Set<String> vertices, DirectedGraph<String, DefaultEdge> graph, DirectedGraph<String, DefaultEdge> directedGraph, SimpleGraph<String, DefaultEdge> simpleGraph, DirectedGraph<String, DefaultEdge> directedGraphSurrounding, String patternType, boolean giantComponent) {
        String mainPatternType = patternType;
        if (patternType.equals(OUTBOUND_STAR) || patternType.equals(INBOUND_STAR) || patternType.equals(MIXED_STAR)) {
            mainPatternType = STAR;
        }
        if (!this.numbersOnly) {
            getNeighbourVerticesAndEdges(graph, directedGraph, directedGraphSurrounding);
        } else {
            int patternSize = vertices.size();
            HashMap<Integer, Integer> count = new HashMap<>();
            Integer patternCount = 1;
            if (this.patternCounts.containsKey(patternType)) {
                count = this.patternCounts.get(patternType);
                if (count.containsKey(patternSize)) {
                    patternCount += count.get(patternSize);
                }
                count.remove(patternSize);
            }
            count.put(patternSize, patternCount);
            this.patternCounts.put(patternType, count);
        }
        if (giantComponent) {
            if (!this.numbersOnly) {
                this.patternsWithSurroundingGC.add(JsonOutput.getJson(directedGraph, directedGraphSurrounding, patternType, this.dataset).toString());
                this.connectedGraphsGC.add(simpleGraph);
                addVerticesForPatterns(simpleGraph, this.connectedGraphsGC.indexOf(simpleGraph));
            }
            if (!patternType.equals(SIAMESE_STAR)) {
                this.connectedGraphsGCTypes.add(mainPatternType);
            }
        } else {
            if (!this.numbersOnly) {
                this.patternsConnectedComponents.add(JsonOutput.getJson(directedGraph, new DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge.class), patternType, this.dataset).toString());
                this.connectedGraphs.add(simpleGraph);
                addVerticesForPatterns(simpleGraph, this.connectedGraphs.indexOf(simpleGraph));
            }
            if (!patternType.equals(SIAMESE_STAR)) {
                this.connectedGraphsTypes.add(mainPatternType);
            }
        }
        verticesInPattern.addAll(vertices);
    }


    private void getNeighbourVerticesAndEdges(DirectedGraph<String, DefaultEdge> graph, DirectedGraph<String, DefaultEdge> pattern, DirectedGraph<String, DefaultEdge> patternLevel2) {
        for (String v: pattern.vertexSet()) {
            patternLevel2.addVertex(v);
            List<String> neighbourVertices = GraphUtils.getNeighboursOfV(graph, v);
            for (String neighbourV: neighbourVertices) {
                if (!pattern.vertexSet().contains(neighbourV) && !patternLevel2.containsVertex(neighbourV)) {
                    patternLevel2.addVertex(neighbourV);
                    addEdges(patternLevel2, graph.getAllEdges(v, neighbourV), v, neighbourV);
                    addEdges(patternLevel2, graph.getAllEdges(neighbourV, v), neighbourV, v);
                }
            }
        }
    }

    private List<String> checkVertexAsPartOfCircle(DirectedGraph<String, DefaultEdge> graph, String v, List<String> visited) {
        // TODO allow more vertices on other than first node!
        String nextVertex = null;
        boolean lastVertex = false;
        List<String> neighbourV = GraphUtils.getNeighboursOfV(graph, v);
        if (((visited.size() > 0) && (neighbourV.size() > 1)) || ((neighbourV.size() == 1) && (visited.size() > 1))) {
            int lastVertexIndex = visited.size() - 1;
            neighbourV.remove(visited.get(lastVertexIndex));
        }
        visited.add(v);
        if (visited.size() == 1) {
            // TODO there could be more than one option here
            for (String oppositeVertex : neighbourV) {
                if (graph.edgesOf(oppositeVertex).size() > 2) {
                    continue;
                }
                nextVertex = oppositeVertex;
                break;
            }
        } else {
            if (neighbourV.size() == 1) {
                List<String> linksAlreadyVisited = new ArrayList<>();
                linksAlreadyVisited.addAll(visited);
                linksAlreadyVisited.retainAll(neighbourV);
                if (neighbourV.get(0).equals(visited.get(0))) {
                    lastVertex = true;
                /*
                } else if (linksAlreadyVisited.size() > 0) {
                    for (String vertex: visited) {
                        if (!linksAlreadyVisited.contains(vertex)) {
                            visited.remove(vertex);
                        } else {
                            break;
                        }
                    }
                    lastVertex = true;
                    // TODO if other narrower circle...
                    //if (neighbourV.size() > 1)
                */
                } else {
                    nextVertex = neighbourV.get(0);
                }
            } else {
                return new ArrayList<>();
            }
        }
        List<String> path = new ArrayList<>();
        if ((nextVertex != null) && !lastVertex) {
            path = checkVertexAsPartOfCircle(graph, nextVertex, visited);
            if (path.isEmpty()) {
                return new ArrayList<>();
            }
        }
        path.add(v);
        return path;
    }

    private boolean checkVertexAsStartOfPath(DirectedGraph<String, DefaultEdge> graph, String v, Set<String> verticesInPaths, Set<String> verticesInOtherPatterns, boolean giantComponent, List<String> iteratedVertices) {
        // Check if this is not the first vertex of a path
        List<String> neighbourV = GraphUtils.getNeighboursOfV(graph, v);
        if (GraphUtils.getNeighboursOfV(graph, v).size() == 2) {
            if (!iteratedVertices.contains(v) && !iteratedVertices.contains(neighbourV.get(0))) {
                iteratedVertices.add(v);
                return checkVertexAsStartOfPath(graph, neighbourV.get(0), verticesInPaths, verticesInOtherPatterns, giantComponent, iteratedVertices);
            }
        }
        Set<String> path = checkVertexAsPartOfPath(graph, v, new ArrayList<>(), verticesInOtherPatterns);

        if (!giantComponent && (path.size() < graph.vertexSet().size())) {
            return false;
        }

        if (path.size() >= 4) {
            DirectedGraph<String, DefaultEdge> doublyLinkedPath = new DefaultDirectedGraph<>(DefaultEdge.class);
            SimpleGraph<String, DefaultEdge> simpleDoublyLinkedPath = new SimpleGraph<>(DefaultEdge.class);
            DirectedGraph<String, DefaultEdge> doublyLinkedPath2 = new DefaultDirectedGraph<>(DefaultEdge.class);
            String lastVertex = null;
            for (String vertex : path) {
                doublyLinkedPath.addVertex(vertex);
                simpleDoublyLinkedPath.addVertex(vertex);
                if (lastVertex != null) {
                    // TODO don't add edges we have added before!
                    addEdges(doublyLinkedPath, graph.getAllEdges(vertex, lastVertex), vertex, lastVertex);
                    addEdges(doublyLinkedPath, graph.getAllEdges(lastVertex, vertex), lastVertex, vertex);
                    addEdges(simpleDoublyLinkedPath, graph.getAllEdges(vertex, lastVertex), vertex, lastVertex);
                    addEdges(simpleDoublyLinkedPath, graph.getAllEdges(lastVertex, vertex), lastVertex, vertex);
                }
                lastVertex = vertex;
            }
            addPatterns(verticesInPaths, path, graph, doublyLinkedPath, simpleDoublyLinkedPath, doublyLinkedPath2, PATH, giantComponent);
            logger.info("Adding path of length {}", path.size());
            return true;
        } else if (path.size() >= 3) {
            // logger.info("Path of length {} found", path.size());
        }
        return false;
    }

    private Set<String> checkVertexAsPartOfPath(DirectedGraph<String, DefaultEdge> graph, String v, List<String> visited, Set<String> verticesInOtherPatterns) {
        if (visited.contains(v)) return new HashSet<>();

        String nextVertex = null;
        boolean lastVertex = false;

        List<String> neighbourV = GraphUtils.getNeighboursOfV(graph, v);
        if (((visited.size() > 0) && (neighbourV.size() > 1)) || ((neighbourV.size() == 1) && (visited.size() > 1))) {
            int lastVertexIndex = visited.size() - 1;
            neighbourV.remove(visited.get(lastVertexIndex));
        }
        visited.add(v);
        if (visited.size() == 1) {
            // TODO there could be more than one option here
            for (String oppositeVertex : neighbourV) {
                if (graph.edgesOf(oppositeVertex).size() > 2) {
                    continue;
                }
                nextVertex = oppositeVertex;
                break;
            }
        } else {
            if (verticesInOtherPatterns.contains(v)) {
                return new HashSet<>();
            }
            if ((neighbourV.size() > 1) || (neighbourV.size() == 0) || (verticesInOtherPatterns.contains(v))) {
                lastVertex = true;
            } else {
                nextVertex = neighbourV.get(0);
                if (nextVertex.equals(visited.get(0))) {
                    return new HashSet<>();
                }
            }
        }
        Set<String> path = new HashSet<>();
        if ((nextVertex != null) && !lastVertex) {
            path = checkVertexAsPartOfPath(graph, nextVertex, visited, verticesInOtherPatterns);
        }
        path.add(v);
        return path;
    }

    private boolean checkVertexAsStartOfLobster(DirectedGraph<String, DefaultEdge> graph, String v, Set<String> verticesInLobsters, boolean giantComponent) {
        Set<String> pathInLobster = new HashSet<>();
        Set<String> path = checkVertexAsPartOfLobster(graph, v, new ArrayList<>(), pathInLobster);
        if (path.size() <= pathInLobster.size()) return false;

        if (!giantComponent && (path.size() < graph.vertexSet().size())) {
            return false;
        }

        if (pathInLobster.size() >= 4) {
            DirectedGraph<String, DefaultEdge> doublyLinkedPath = new DefaultDirectedGraph<>(DefaultEdge.class);
            SimpleGraph<String, DefaultEdge> simpleDoublyLinkedPath = new SimpleGraph<>(DefaultEdge.class);
            DirectedGraph<String, DefaultEdge> doublyLinkedPath2 = new DefaultDirectedGraph<>(DefaultEdge.class);
            for (String vertex : path) {
                doublyLinkedPath.addVertex(vertex);
                simpleDoublyLinkedPath.addVertex(vertex);
                for (String alreadyAdded : simpleDoublyLinkedPath.vertexSet()) {
                    addEdges(doublyLinkedPath, graph.getAllEdges(vertex, alreadyAdded), vertex, alreadyAdded);
                    addEdges(doublyLinkedPath, graph.getAllEdges(alreadyAdded, vertex), alreadyAdded, vertex);
                    addEdges(simpleDoublyLinkedPath, graph.getAllEdges(vertex, alreadyAdded), vertex, alreadyAdded);
                    addEdges(simpleDoublyLinkedPath, graph.getAllEdges(alreadyAdded, vertex), alreadyAdded, vertex);
                }
            }
            addPatterns(verticesInLobsters, path, graph, doublyLinkedPath, simpleDoublyLinkedPath, doublyLinkedPath2, LOBSTER, giantComponent);
            logger.info("Adding lobster of length {}", path.size());
        } else if (pathInLobster.size() >= 3) {
            // logger.info("Lobster of length {} found", path.size());
            return false;
        }
        return true;
    }

    private boolean checkVertexAsStartOfCaterpillar(DirectedGraph<String, DefaultEdge> graph, String v, Set<String> verticesInCaterpillars, boolean giantComponent) {
        Set<String> pathInCaterpillar = new HashSet<>();
        Set<String> path = checkVertexAsPartOfCaterpillar(graph, v, new ArrayList<>(), pathInCaterpillar);
        if (path.size() <= pathInCaterpillar.size()) return false;

        if (!giantComponent && (path.size() < graph.vertexSet().size())) {
            return false;
        }

        if (pathInCaterpillar.size() >= 4) {
            DirectedGraph<String, DefaultEdge> doublyLinkedPath = new DefaultDirectedGraph<>(DefaultEdge.class);
            SimpleGraph<String, DefaultEdge> simpleDoublyLinkedPath = new SimpleGraph<>(DefaultEdge.class);
            DirectedGraph<String, DefaultEdge> doublyLinkedPath2 = new DefaultDirectedGraph<>(DefaultEdge.class);
            for (String vertex : path) {
                doublyLinkedPath.addVertex(vertex);
                simpleDoublyLinkedPath.addVertex(vertex);
                for (String alreadyAdded : simpleDoublyLinkedPath.vertexSet()) {
                    addEdges(doublyLinkedPath, graph.getAllEdges(vertex, alreadyAdded), vertex, alreadyAdded);
                    addEdges(doublyLinkedPath, graph.getAllEdges(alreadyAdded, vertex), alreadyAdded, vertex);
                    addEdges(simpleDoublyLinkedPath, graph.getAllEdges(vertex, alreadyAdded), vertex, alreadyAdded);
                    addEdges(simpleDoublyLinkedPath, graph.getAllEdges(alreadyAdded, vertex), alreadyAdded, vertex);
                }
            }
            addPatterns(verticesInCaterpillars, path, graph, doublyLinkedPath, simpleDoublyLinkedPath, doublyLinkedPath2, CATERPILLAR, giantComponent);
            logger.info("Adding caterpillar of length {}", path.size());
        } else if (pathInCaterpillar.size() >= 3) {
            // logger.info("Caterpillar of length {} found", path.size());
            return false;
        }
        return true;
    }

    private Set<String> checkVertexAsPartOfCaterpillar(DirectedGraph<String, DefaultEdge> graph, String v, List<String> visited, Set<String> mainPath) {
        if (visited.contains(v)) return new HashSet<>();

        String nextVertex = null;
        boolean lastVertex = false;
        List<String> neighbourV = GraphUtils.getNeighboursOfV(graph, v);
        if (((visited.size() > 0) && (neighbourV.size() > 1)) || ((neighbourV.size() == 1) && (visited.size() > 1))) {
            int lastVertexIndex = visited.size() - 1;
            neighbourV.remove(visited.get(lastVertexIndex));
        }
        visited.add(v);
        mainPath.add(v);
        Set<String> path = new HashSet<>();
        if (visited.size() == 1) {
            // TODO there could be more than one option here
            for (String oppositeVertex : neighbourV) {
                if (graph.edgesOf(v).size() == 2) {
                    return new HashSet<>();
                }
                if (graph.edgesOf(oppositeVertex).size() > 2) {
                    continue;
                }
                nextVertex = oppositeVertex;
                break;
            }
        } else {
            if (neighbourV.size() == 1) {
                nextVertex = neighbourV.get(0);
                if (nextVertex.equals(visited.get(0))) {
                    return new HashSet<>();
                }
            } else if (neighbourV.size() == 0) {
                lastVertex = true;
            } else if (neighbourV.size() > 1) {
                nextVertex = checkIfAllNeighboursCouldBePartOfCaterpillar(graph, neighbourV, v, visited);
                if (nextVertex != null) {
                    if (nextVertex.equals(visited.get(0))) {
                        return new HashSet<>();
                    }
                    for (String neighbourVertex: neighbourV) {
                        if (!v.equals(neighbourVertex)) {
                            path.add(neighbourVertex);
                        }
                    }
                } else {
                    lastVertex = true;
                }
            }
        }
        if ((nextVertex != null) && !lastVertex) {
            Set<String> restPath = checkVertexAsPartOfCaterpillar(graph, nextVertex, visited, mainPath);
            if (restPath.isEmpty()) return new HashSet<>();
            restPath.removeAll(path);
            path.addAll(restPath);
        }
        path.add(v);
        return path;
    }

    private Set<String> checkVertexAsPartOfLobster(DirectedGraph<String, DefaultEdge> graph, String v, List<String> visited, Set<String> mainPath) {
        if (visited.contains(v)) return new HashSet<>();

        String nextVertex = null;
        boolean lastVertex = false;
        List<String> neighbourV = GraphUtils.getNeighboursOfV(graph, v);
        if (((visited.size() > 0) && (neighbourV.size() > 1)) || ((neighbourV.size() == 1) && (visited.size() > 1))) {
            int lastVertexIndex = visited.size() - 1;
            neighbourV.remove(visited.get(lastVertexIndex));
        }
        visited.add(v);
        mainPath.add(v);
        Set<String> path = new HashSet<>();
        if (visited.size() == 1) {
            // TODO there could be more than one option here
            if (graph.edgesOf(v).size() == 2) {
                return new HashSet<>();
            }
            for (String oppositeVertex : neighbourV) {
                if (graph.edgesOf(oppositeVertex).size() > 2) {
                    continue;
                }
                nextVertex = oppositeVertex;
                break;
            }
        } else {
            if (neighbourV.size() == 1) {
                nextVertex = neighbourV.get(0);
                if (nextVertex.equals(visited.get(0))) {
                    return new HashSet<>();
                }
            } else if (neighbourV.size() == 0) {
                lastVertex = true;
            } else if (neighbourV.size() > 1) {
                nextVertex = checkIfAllNeighboursCouldBePartOfLobster(graph, neighbourV, v, visited);
                if (nextVertex != null) {
                    if (nextVertex.equals(visited.get(0))) {
                        return new HashSet<>();
                    }
                    for (String neighbourVertex: neighbourV) {
                        if (!v.equals(neighbourVertex)) {
                            path.add(neighbourVertex);
                        }
                        List<String> neighboursOfNeighbour = GraphUtils.getNeighboursOfV(graph, neighbourVertex);
                        for (String neighbour: neighboursOfNeighbour) {
                            if (!neighbourVertex.equals(neighbour)) {
                                path.add(neighbour);
                            }
                        }
                    }
                } else {
                    lastVertex = true;
                }
            }
        }
        if ((nextVertex != null) && !lastVertex) {
            Set<String> restPath = checkVertexAsPartOfLobster(graph, nextVertex, visited, mainPath);
            if (restPath.isEmpty()) return new HashSet<>();
            restPath.removeAll(path);
            path.addAll(restPath);
        }
        path.add(v);
        return path;
    }

    private String checkIfAllNeighboursCouldBePartOfCaterpillar(DirectedGraph<String, DefaultEdge> graph, List<String> neighbourV, String vertex, List<String> visited) {
        boolean alreadyOnVerticeWithNeighbour = false;
        String nextV = null;
        Set<Integer> neighbourList = new HashSet<>();
        for (String v: neighbourV) {
            // TODO check
            if (visited.contains(v)) {
                return null;
            }
            List<String> neighboursOfV = GraphUtils.getNeighboursOfV(graph, v);
            neighboursOfV.remove(vertex);
            if (neighboursOfV.size() > 0) {
                for (String n: neighboursOfV) {
                    if (visited.contains(n)) {
                        return null;
                    }
                }
                if (alreadyOnVerticeWithNeighbour) {
                    return null;
                }
                alreadyOnVerticeWithNeighbour = true;
                nextV = v;
            }
            neighbourList.add(neighboursOfV.size());
        }
        // if end contains only paths of length 1
        if (neighbourList.contains(0) && (neighbourList.size() == 1)) {
            nextV = neighbourV.get(0);
        }
        return nextV;
    }

    private String checkIfAllNeighboursCouldBePartOfLobster(DirectedGraph<String, DefaultEdge> graph, List<String> neighbourV, String vertex, List<String> visited) {
        boolean alreadyOneVerticeWithNeighbour = false;
        String nextV = null;
        Set<Integer> neighbourList = new HashSet<>();
        for (String v: neighbourV) {
            if (visited.contains(v)) {
                return null;
            }
            List<String> neighboursOfV = GraphUtils.getNeighboursOfV(graph, v);
            neighboursOfV.remove(vertex);
            for (String neighbour: neighboursOfV) {
                if (visited.contains(neighbour)) {
                    return null;
                }
                List<String> neighboursOfNeighbour = GraphUtils.getNeighboursOfV(graph, neighbour);
                neighboursOfNeighbour.remove(v);
                // either no neighbours but v (caterpillar part)
                // TODO it's ok when both ends have same length and there is no main stalk anymore

                // 1 neighbour that has no other neighbour
                // or 1 neighbour with more
                if (neighboursOfNeighbour.size() >= 1) {
                    // TODO check if no loops
                    if (alreadyOneVerticeWithNeighbour) {
                        return null;
                    }
                    alreadyOneVerticeWithNeighbour = true;
                    nextV = v;
                }
                neighbourList.add(neighboursOfNeighbour.size());
            }
            // TODO rather neighboursOfNeighbour?
            // always take stalk
            // neighbourList.add(neighboursOfV.size());
        }
        // if end contains only paths of length 2
        if (neighbourList.contains(0) && (neighbourList.size() == 1)) {
            nextV = neighbourV.get(0);
        }
        return nextV;
    }

    private boolean checkVertexAsCentreOfAntenna(DirectedGraph<String, DefaultEdge> graph, String v_center, Set<String> verticesInAntennas, boolean giantComponent) {
        List<String> neighbourVertices = GraphUtils.getNeighboursOfV(graph, v_center);
        if (neighbourVertices.size() < 4) {
            return false;
        }
        int nrOfVerticesWith3Neighbours = 0;
        for (String neighbourVertex: neighbourVertices) {
            List<String> neighbourVertices2 = GraphUtils.getNeighboursOfV(graph, neighbourVertex);
            neighbourVertices2.retainAll(neighbourVertices);
            if (neighbourVertices2.size() == 1) {
                nrOfVerticesWith3Neighbours += 1;
                if (nrOfVerticesWith3Neighbours > 2) {
                    return false;
                }
            } else if (neighbourVertices2.size() != 0) {
                return false;
            }
        }
        if (nrOfVerticesWith3Neighbours != 2) {
            return false;
        }

        DirectedGraph<String, DefaultEdge> directedGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
        SimpleGraph<String, DefaultEdge> simpleGraph = new SimpleGraph<>(DefaultEdge.class);
        DirectedGraph<String, DefaultEdge> surroundingGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
        directedGraph.addVertex(v_center);
        simpleGraph.addVertex(v_center);
        for (String vertex : neighbourVertices) {
            directedGraph.addVertex(vertex);
            simpleGraph.addVertex(vertex);
            for (String alreadyAdded : simpleGraph.vertexSet()) {
                addEdges(directedGraph, graph.getAllEdges(vertex, alreadyAdded), vertex, alreadyAdded);
                addEdges(directedGraph, graph.getAllEdges(alreadyAdded, vertex), alreadyAdded, vertex);
                addEdges(simpleGraph, graph.getAllEdges(vertex, alreadyAdded), vertex, alreadyAdded);
                addEdges(simpleGraph, graph.getAllEdges(alreadyAdded, vertex), alreadyAdded, vertex);
            }
        }
        addPatterns(verticesInAntennas, new HashSet<String>(neighbourVertices), graph, directedGraph, simpleGraph, surroundingGraph, ANTENNA, giantComponent);
        verticesInAntennas.add(v_center);
        logger.info("Adding antenna of size {}", directedGraph.vertexSet().size());
        return true;
    }

    private boolean checkVertexAsCentreOfInboundStar(DirectedGraph<String, DefaultEdge> graph, String v_center, Set<String> verticesInStars, boolean giantComponent) {
        if (graph.outgoingEdgesOf(v_center).size() > 0) {
            return false;
        }
        List<String> neighbourVertices = GraphUtils.getNeighboursOfV(graph, v_center);
        if (neighbourVertices.size() < 4) {
            return false;
        }
        if (!giantComponent && ((neighbourVertices.size() + 1) < graph.vertexSet().size())) {
            return false;
        }
        Set<String> vertices = new HashSet<>();
        Set<String> surroundingVertices = new HashSet<>();
        Set<DefaultEdge> surroundingIEdges = graph.incomingEdgesOf(v_center);
        if (surroundingIEdges.size() >= 4) {
            int numberOfEdgesForSurrounding = 0;
            DirectedGraph<String, DefaultEdge> outgoingStar = new DefaultDirectedGraph<>(DefaultEdge.class);
            SimpleGraph<String, DefaultEdge> simpleStar = new SimpleGraph<>(DefaultEdge.class);
            DirectedGraph<String, DefaultEdge> outgoingStarLevel2 = new DefaultDirectedGraph<>(DefaultEdge.class);
            outgoingStar.addVertex(v_center);
            simpleStar.addVertex(v_center);
            for (DefaultEdge surroundingEdge : surroundingIEdges) {
                String v_level1 = surroundingEdge.getSource().toString();
                Set<DefaultEdge> incomingEdges2 = graph.incomingEdgesOf(v_level1);
                Set<DefaultEdge> outgoingEdges2 = graph.outgoingEdgesOf(v_level1);
                numberOfEdgesForSurrounding += incomingEdges2.size();
                numberOfEdgesForSurrounding += outgoingEdges2.size();
                outgoingStar.addVertex(v_level1);
                simpleStar.addVertex(v_level1);
                addEdge(outgoingStar, surroundingEdge.toString(), v_level1, v_center);
                addEdge(simpleStar, surroundingEdge.toString(), v_level1, v_center);
                vertices.add(v_level1);
                for (DefaultEdge se2 : incomingEdges2) {
                    String v_level2 = se2.getSource().toString();
                    if (neighbourVertices.contains(v_level2)) {
                        // TODO count connectivity and allow some
                        return false;
                    }
                    surroundingVertices.add(v_level2);
                }
                for (DefaultEdge se2 : outgoingEdges2) {
                    String v_level2 = se2.getTarget().toString();
                    if (v_level2.equals(v_center)) continue;
                    if (neighbourVertices.contains(v_level2)) {
                        // TODO count connectivity and allow some
                        return false;
                    }
                    surroundingVertices.add(v_level2);
                }
            }
            if ((numberOfEdgesForSurrounding <= surroundingIEdges.size()) || (surroundingVertices.size() <= (surroundingIEdges.size() + 1))) {
                addPatterns(verticesInStars, new HashSet<String>(neighbourVertices), graph, outgoingStar, simpleStar, outgoingStarLevel2, INBOUND_STAR, giantComponent);
                // addStats(v_center, neighbourVertices, this.outStatsInboundCsv);
                logger.info("Adding inbound star of size {}", outgoingStar.vertexSet().size());

                return true;
            }
        }
        return false;
    }

    private void addVerticesForPatterns(SimpleGraph<String, DefaultEdge> simpleStar, int patternId) {
        for (String vertex: simpleStar.vertexSet()) {
            List patternIds = new ArrayList();
            if (this.verticesInPatterns.containsKey(vertex)) {
                patternIds.addAll(this.verticesInPatterns.get(vertex));
            }
            patternIds.add(patternId);
            this.verticesInPatterns.put(vertex, patternIds);
        }
    }

    private void addEdgesOnLevel2Vertices(GraphFeatures graphFeatures, DirectedGraph<String, DefaultEdge> graph, Set<String> verticesLevel2) {
        for (String v : verticesLevel2) {
            Set<DefaultEdge> incomingEdges = graphFeatures.incomingEdgesOf(v);
            Set<DefaultEdge> outgoingEdges = graphFeatures.outgoingEdgesOf(v);
            // TODO don't add in- & outcoming links twice
            for (DefaultEdge edge : incomingEdges) {
                String linkedVertice = edge.getSource().toString();
                if (verticesLevel2.contains(linkedVertice)) {
                    addEdge(graph, edge.toString(), linkedVertice, v);
                }
            }
            for (DefaultEdge edge : outgoingEdges) {
                String linkedVertice = edge.getTarget().toString();
                if (verticesLevel2.contains(linkedVertice)) {
                    addEdge(graph, edge.toString(), v, linkedVertice);
                }
            }
        }

    }

    private void getWalks(SimpleGraph<String, DefaultEdge>  g) {
        int highestDegree = 0;
        List<String> vertices = new ArrayList<>();
        HashMap<String, String> classes = new HashMap<>();
        for (String vertex : g.vertexSet()) {
            Set<DefaultEdge> edges = g.edgesOf(vertex);

            Integer edgeCount = g.degreeOf(vertex);
            if (edgeCount > highestDegree) {
                vertices = new ArrayList<>();
                classes = new HashMap<>();
                vertices.add(vertex);
                classes.put(vertex, this.dataset.getClassForSubject(vertex));
                highestDegree = edges.size();
            } else if (edges.size() == highestDegree) {
                vertices.add(vertex);
                classes.put(vertex, this.dataset.getClassForSubject(vertex));
            }
        }

        String lowestClass = "";
        for (Map.Entry<String, String> entry : MapUtil.sortByValue(classes).entrySet()) {
            String vertex = entry.getKey();
            String className = entry.getValue();
            if (!lowestClass.equals("") && !className.equals(lowestClass)) {
                break;
            }
            BFSOrderedIterator bfs = new BFSOrderedIterator<>(g, vertex, dataset);
            while (bfs.hasNext()) {
                String currentVertex = bfs.next().toString();
                System.out.println(currentVertex + " " + replaceNamespace(this.dataset.getClassForSubject(currentVertex), this.dataset.getOntologyNamespace()) + " ");
            }
            lowestClass = className;
        }

    }

    private boolean checkVertexAsCentreOfOutboundStar(DirectedGraph<String, DefaultEdge> graph, String v_center, Set<String> verticesInStars, boolean giantComponent) {
        if (graph.incomingEdgesOf(v_center).size() > 0) {
            return false;
        }
        List<String> neighbourVertices = GraphUtils.getNeighboursOfV(graph, v_center);
        if (neighbourVertices.size() < 4) {
            return false;
        }
        if (!giantComponent && ((neighbourVertices.size() + 1) < graph.vertexSet().size())) {
            return false;
        }
        Set<DefaultEdge> surroundingEdges = graph.outgoingEdgesOf(v_center);
        Set<String> surroundingVertices = new HashSet<>();
        Set<String> vertices = new HashSet<>();
        if (surroundingEdges.size() >= 4) {
            int numberOfEdgesForSurrounding = 0;
            DirectedGraph<String, DefaultEdge> outgoingStar = new DefaultDirectedGraph<>(DefaultEdge.class);
            SimpleGraph<String, DefaultEdge> simpleStar = new SimpleGraph<>(DefaultEdge.class);
            DirectedGraph<String, DefaultEdge> outgoingStarLevel2 = new DefaultDirectedGraph<>(DefaultEdge.class);
            outgoingStar.addVertex(v_center);
            simpleStar.addVertex(v_center);
            for (DefaultEdge surroundingEdge : surroundingEdges) {
                String v_level1 = surroundingEdge.getTarget().toString();
                Set<DefaultEdge> incomingEdges2 = graph.incomingEdgesOf(v_level1);
                Set<DefaultEdge> outgoingEdges2 = graph.outgoingEdgesOf(v_level1);
                numberOfEdgesForSurrounding += incomingEdges2.size();
                numberOfEdgesForSurrounding += outgoingEdges2.size();
                outgoingStar.addVertex(v_level1);
                simpleStar.addVertex(v_level1);
                addEdge(outgoingStar, surroundingEdge.toString(), v_center, v_level1);
                addEdge(simpleStar, surroundingEdge.toString(), v_center, v_level1);
                vertices.add(v_level1);
                for (DefaultEdge se2 : incomingEdges2) {
                    String v_level2 = se2.getSource().toString();
                    if (v_level2.equals(v_center)) continue;
                    if (neighbourVertices.contains(v_level2)) {
                        // TODO count connectivity and allow some
                        return false;
                    }
                    surroundingVertices.add(v_level2);
                }
                for (DefaultEdge se2 : outgoingEdges2) {
                    String v_level2 = se2.getTarget().toString();
                    if (neighbourVertices.contains(v_level2)) {
                        // TODO count connectivity and allow some
                        return false;
                    }
                    surroundingVertices.add(v_level2);
                }
            }
            if ((numberOfEdgesForSurrounding <= surroundingEdges.size()) || (surroundingVertices.size() <= (surroundingEdges.size() + 1))) {
                addPatterns(verticesInStars, new HashSet<String>(neighbourVertices), graph, outgoingStar, simpleStar, outgoingStarLevel2, OUTBOUND_STAR, giantComponent);
                // addStats(v_center, neighbourVertices, this.outStatsOutboundCsv);
                logger.info("Adding outbound star of size {}", outgoingStar.vertexSet().size());
                return true;
            }
        }
        return false;
    }

    private void addEdge(Graph<String, DefaultEdge> graph, String name, String source, String target) {
        DefaultEdge e = new DefaultEdge(name);
        e.setSource(source);
        e.setTarget(target);
        graph.addEdge(source, target, e);
    }

    private void addEdges(Graph<String, DefaultEdge> graph, Set<DefaultEdge> edges, String source, String target) {
        for (DefaultEdge e: edges) {
            graph.addEdge(source, target, e);
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

    private void groupIsomorphicGraphs(List<SimpleGraph<String, DefaultEdge>> connectedGraphs, List<String> connectedGraphsTypes, List<List<Integer>> isomorphicGraphs, List<String> isomorphicGraphsTypes, HashMap<Integer, List<String>> colorIsomorphicPatterns, HashMap<Integer, HashMap<String, Integer>> patterns, HashMap<Integer, HashMap<Integer, List<String>>> coloredPatterns, List<String> patternsWithSurrounding) {
        int i = 0;
        String type;
        for (SimpleGraph connectedSet : connectedGraphs) {
            int graphIndex = connectedGraphs.indexOf(connectedSet);
            type = connectedGraphsTypes.get(graphIndex);
            int connectedSetVertexSetSize = connectedSet.vertexSet().size();
            if (connectedSetVertexSetSize > MAX_SIZE_FOR_ISO) continue;
            logger.debug("\tChecking graph {}/{} ({} vertices).", ++i, connectedGraphs.size(), connectedSetVertexSetSize);
            int putIntoBag = -1;

            for (List<Integer> isomorphicGraphList : isomorphicGraphs) {
                int isomorphicListIndex = isomorphicGraphs.indexOf(isomorphicGraphList);
                String isomorphicListType = isomorphicGraphsTypes.get(isomorphicListIndex);
                if (!type.equals(isomorphicListType)) continue;
                SimpleGraph firstGraph = connectedGraphs.get(isomorphicGraphList.get(0));
                if ((type.equals(STAR) || type.equals(PATH) || type.equals(DOUBLY_LINKED_PATH) || type.equals(CIRCLE) || type.equals(WHEEL) || type.equals(WINDMILL)) && (firstGraph.vertexSet().size() == connectedSet.vertexSet().size())) {
                    putIntoBag = isomorphicListIndex;
                    break;
                }
                try {
                    if (firstGraph.vertexSet().size() != connectedSetVertexSetSize || (firstGraph.edgeSet().size() != connectedSet.edgeSet().size())) continue;
                    GraphIsomorphismInspector inspector = createIsomorphismInspector(connectedSet, firstGraph);
                    if (inspector.isIsomorphic()) {
                        putIntoBag = isomorphicListIndex;
                        break;
                    }
                } catch (ArrayIndexOutOfBoundsException e) {
                    putIntoBag = -1;
                    logger.warn(e.getMessage());
                }
            }
            List<Integer> isomorphicGraphList = new ArrayList<>();
            isomorphicGraphList.add(graphIndex);

            // if there is already a isomorphism group
            if (putIntoBag >= 0) {
                isomorphicGraphList.addAll(isomorphicGraphs.get(putIntoBag));
                isomorphicGraphs.remove(putIntoBag);
                isomorphicGraphs.add(putIntoBag, isomorphicGraphList);
                logger.debug("\t\tAdding graph of size {} to isomorphism group bag #{} of size {}.", connectedSetVertexSetSize, putIntoBag, isomorphicGraphList.size()-1);
            } else {
                isomorphicGraphs.add(isomorphicGraphList);
                isomorphicGraphsTypes.add(type);
                logger.debug("\t\tCreating new isomorphism bag for graphs of size {}.", connectedSetVertexSetSize);
            }
        }

        groupIsomorphicGraphsByColor(connectedGraphs, isomorphicGraphs, isomorphicGraphsTypes, colorIsomorphicPatterns, patterns, coloredPatterns, patternsWithSurrounding);
    }

    /*
    private void groupIsomorphicGraphs(List<SimpleGraph<String, DefaultEdge>> connectedGraphs, List<String> connectedGraphsTypes, List<List<Integer>> isomorphicGraphs, List<String> isomorphicGraphsTypes, HashMap<Integer, List<String>> colorIsomorphicPatterns, HashMap<Integer, HashMap<String, Integer>> patterns, HashMap<Integer, HashMap<Integer, List<String>>> coloredPatterns, List<String> patternsWithSurrounding) {
        int i = 0;
        String type;
        for (SimpleGraph connectedSet : connectedGraphs) {
            int graphIndex = connectedGraphs.indexOf(connectedSet);
            type = connectedGraphsTypes.get(graphIndex);
            int connectedSetVertexSetSize = connectedSet.vertexSet().size();
            if (connectedSetVertexSetSize > MAX_SIZE_FOR_ISO) continue;
            logger.debug("\tChecking graph {}/{} ({} vertices).", ++i, connectedGraphs.size(), connectedSetVertexSetSize);
            int putIntoBag = -1;

            for (List<Integer> isomorphicGraphList : isomorphicGraphs) {
                int isomorphicListIndex = isomorphicGraphs.indexOf(isomorphicGraphList);
                String isomorphicListType = isomorphicGraphsTypes.get(isomorphicListIndex);
                if (!type.equals(isomorphicListType)) continue;
                SimpleGraph firstGraph = connectedGraphs.get(isomorphicGraphList.get(0));
                if ((type.equals(STAR) || type.equals(PATH) || type.equals(DOUBLY_LINKED_PATH) || type.equals(CIRCLE) || type.equals(WHEEL) || type.equals(WINDMILL)) && (firstGraph.vertexSet().size() == connectedSet.vertexSet().size())) {
                    putIntoBag = isomorphicListIndex;
                    break;
                }
                try {
                    if (firstGraph.vertexSet().size() != connectedSetVertexSetSize || (firstGraph.edgeSet().size() != connectedSet.edgeSet().size())) continue;
                    GraphIsomorphismInspector inspector = createIsomorphismInspector(connectedSet, firstGraph);
                    if (inspector.isIsomorphic()) {
                        putIntoBag = isomorphicListIndex;
                        break;
                    }
                } catch (ArrayIndexOutOfBoundsException e) {
                    putIntoBag = -1;
                    logger.warn(e.getMessage());
                }
            }
            List<Integer> isomorphicGraphList = new ArrayList<>();
            isomorphicGraphList.add(graphIndex);

            // if there is already a isomorphism group
            if (putIntoBag >= 0) {
                isomorphicGraphList.addAll(isomorphicGraphs.get(putIntoBag));
                isomorphicGraphs.remove(putIntoBag);
                isomorphicGraphs.add(putIntoBag, isomorphicGraphList);
                logger.debug("\t\tAdding graph of size {} to isomorphism group bag #{} of size {}.", connectedSetVertexSetSize, putIntoBag, isomorphicGraphList.size()-1);
            } else {
                isomorphicGraphs.add(isomorphicGraphList);
                isomorphicGraphsTypes.add(type);
                logger.debug("\t\tCreating new isomorphism bag for graphs of size {}.", connectedSetVertexSetSize);
            }
        }

        groupIsomorphicGraphsByColor(connectedGraphs, isomorphicGraphs, isomorphicGraphsTypes, colorIsomorphicPatterns, patterns, coloredPatterns, patternsWithSurrounding);
    }
    */

    private void groupIsomorphicGraphsByColor(List<SimpleGraph<String, DefaultEdge>> connectedGraphs, List<List<Integer>> isomorphicGraphs, List<String> isomorphicGraphsTypes, HashMap<Integer, List<String>> colorIsomorphicPatterns, HashMap<Integer, HashMap<String, Integer>> patterns, HashMap<Integer, HashMap<Integer, List<String>>> coloredPatterns, List<String> patternsWithSurrounding) {
        logger.info("Color isomorphism groups for patterns");
        for (List<Integer> isomorphicGraphList : isomorphicGraphs) {
            Integer indexIsomorphicList = isomorphicGraphs.indexOf(isomorphicGraphList);
            String isomorphicListType = isomorphicGraphsTypes.get(indexIsomorphicList);
            logger.debug("\tChecking color isomorphism for {} graphs in bag #{} ({} vertices).", isomorphicGraphList.size(), indexIsomorphicList, connectedGraphs.get(isomorphicGraphList.get(0)).vertexSet().size());
            /*
            if (graphRenderer != null) {
                this.graphRenderer.writeDotFile(index.toString(), graphFeatures.get(isomorphicGraphList.get(0)), false);
            }
            */

            // List<SimpleGraph<String, DefaultEdge>> isomorphicGraphsTemp = new ArrayList<>();
            List<Integer> isomorphicGraphsTemp = new ArrayList<>();

            //List<SimpleGraph<String, DefaultEdge>> colorIsoGF = new ArrayList<>();
            List<Integer> colorIsoGF = new ArrayList<>();

            Integer colorIsoGroupIndex = -1;
            for (Integer graphNr : isomorphicGraphList) {
                logger.debug("\tChecking color isomorphism for graph {}/{}.", graphNr, isomorphicGraphList.size());

                SimpleGraph<String, DefaultEdge> gf = connectedGraphs.get(graphNr);
                isomorphicGraphsTemp.add(graphNr);

                boolean newColorIsomorphismGroup = true;
                for (Integer coloredGraphFeatureId: colorIsoGF) {
                    SimpleGraph<String, DefaultEdge> coloredGF = connectedGraphs.get(coloredGraphFeatureId);
                    Set<String> verticesOfGraph = gf.vertexSet();
                    Set<String> verticesOfGraphToCheck = coloredGF.vertexSet();
                    List<String> classesGraph = new ArrayList<>();
                    List<String> classesGraphToCheck = new ArrayList<>();
                    for (String v: verticesOfGraph) {
                        classesGraph.add(this.dataset.getClassForSubject(v));
                    }
                    for (String v: verticesOfGraphToCheck) {
                        classesGraphToCheck.add(this.dataset.getClassForSubject(v));
                    }
                    Collections.sort(classesGraph);
                    Collections.sort(classesGraphToCheck);
                    if (!classesGraph.equals(classesGraphToCheck)) {
                        continue;
                    }

                    boolean colorIsomorph = true;
                    if (isomorphicListType.equals(ANTENNA)) {
                        colorIsomorph = checkColorIsomorphismForAntennas(coloredGF, gf);
                    } else if (isomorphicListType.equals(STAR) || isomorphicListType.equals(INBOUND_STAR) || isomorphicListType.equals(OUTBOUND_STAR) || isomorphicListType.equals(MIXED_STAR)) {
                        colorIsomorph = checkColorIsomorphismForStars(coloredGF, gf);
                    } else {
                        PermutationClassIsomorphismInspector inspector = new PermutationClassIsomorphismInspector(coloredGF, gf); // , new VertexDegreeEquivalenceComparator(), null
                        while (inspector.hasNext()) {
                            IsomorphismRelation graphMapping = inspector.nextIsoRelation();
                            colorIsomorph = true;
                            for (Object o : gf.vertexSet()) {
                                String v = o.toString();
                                String correspondingVertex = graphMapping.getVertexCorrespondence(v, false).toString();
                                String vClass = this.dataset.getClassForSubject(v);
                                if (!this.dataset.getClassForSubject(correspondingVertex).equals(vClass)) {
                                    colorIsomorph = false;
                                }
                            }
                            if (colorIsomorph) {
                                break;
                            }
                        }
                    }
                    if (colorIsomorph) {
                        newColorIsomorphismGroup = false;
                        colorIsoGroupIndex = colorIsoGF.indexOf(coloredGF);
                        logger.debug("\t\tAdding to color isomorphism bag #{}.", colorIsoGroupIndex);
                    }
                }
                if (newColorIsomorphismGroup) {
                    List<String> coloredIso = new ArrayList<>();
                    if (colorIsomorphicPatterns.containsKey(indexIsomorphicList)) {
                        coloredIso = colorIsomorphicPatterns.get(indexIsomorphicList);
                    }
                    coloredIso.add(JsonOutput.getJsonColoredGroup(gf, this.dataset, isomorphicListType).toString());
                    colorIsomorphicPatterns.put(indexIsomorphicList, coloredIso);
                    //colorIsoGroupIndex = colorIsoGF.size();
                    colorIsoGF.add(graphNr);
                    // colorIsoGroupIndex = colorIsoGF.indexOf(gf);
                    colorIsoGroupIndex = colorIsoGF.size() - 1;
                    logger.debug("\t\tCreating new color isomorphism bag #{}.", colorIsoGroupIndex);
                }

                HashMap<Integer, List<String>> colored = new HashMap<>();
                List<String> coloredList = new ArrayList<>();
                if (coloredPatterns.containsKey(indexIsomorphicList)) {
                    colored = coloredPatterns.get(indexIsomorphicList);
                    if (colored.containsKey(colorIsoGroupIndex)) {
                        coloredList = colored.get(colorIsoGroupIndex);
                    }
                }
                //colored.add(JsonOutput.getJsonColored(gf, this.dataset).toString());
                // differed from not GC
                coloredList.add(patternsWithSurrounding.get(graphNr));
                colored.put(colorIsoGroupIndex, coloredList);
                coloredPatterns.put(indexIsomorphicList, colored);
            }
            if (connectedGraphs.get(isomorphicGraphsTemp.get(0)).edgeSet().size() <= MAX_SIZE_FOR_ISO) {
                logger.info(isomorphicGraphsTemp.size() + " x " + isomorphicListType);
                // TODO type
                String json = JsonOutput.getJson(connectedGraphs.get(isomorphicGraphList.get(0)), isomorphicListType).toString();
                logger.info(json);
                HashMap<String, Integer> patternTemp = new HashMap<>();
                patternTemp.put(json, isomorphicGraphList.size());
                patterns.put(indexIsomorphicList, patternTemp);
                // TODO
                // patternDiameter.put(index, graphs.get(isomorphicGraphList.get(0)).getDiameter());

                /*
                if (graphRenderer != null) {
                    this.graphRenderer.writeDotFiles(index.toString() + "_detailed", isomorphicGraphsTemp, true);
                }
                */
            }
        }
    }

    /*
    private void groupIsomorphicGraphsByColor(List<SimpleGraph<String, DefaultEdge>> connectedGraphs, List<List<Integer>> isomorphicGraphs, List<String> isomorphicGraphsTypes, HashMap<Integer, List<String>> colorIsomorphicPatterns, HashMap<Integer, HashMap<String, Integer>> patterns, HashMap<Integer, HashMap<Integer, List<String>>> coloredPatterns, List<String> patternsWithSurrounding) {
        logger.info("Color isomorphism groups for patterns");
        for (List<Integer> isomorphicGraphList : isomorphicGraphs) {
            Integer indexIsomorphicList = isomorphicGraphs.indexOf(isomorphicGraphList);
            String isomorphicListType = isomorphicGraphsTypes.get(indexIsomorphicList);
            logger.debug("\tChecking color isomorphism for {} graphs in bag #{} ({} vertices).", isomorphicGraphList.size(), indexIsomorphicList, connectedGraphs.get(isomorphicGraphList.get(0)).vertexSet().size());

//            if (graphRenderer != null) {
//                this.graphRenderer.writeDotFile(index.toString(), graphFeatures.get(isomorphicGraphList.get(0)), false);
//            }
    List<SimpleGraph<String, DefaultEdge>> isomorphicGraphsTemp = new ArrayList<>();

    List<SimpleGraph<String, DefaultEdge>> colorIsoGF = new ArrayList<>();
    Integer colorIsoGroupIndex = -1;
    for (Integer graphNr : isomorphicGraphList) {
        logger.debug("\tChecking color isomorphism for graph {}/{}.", graphNr, isomorphicGraphList.size());

        SimpleGraph<String, DefaultEdge> gf = connectedGraphs.get(graphNr);
        isomorphicGraphsTemp.add(gf);

        boolean newColorIsomorphismGroup = true;
        for (SimpleGraph<String, DefaultEdge> coloredGF: colorIsoGF) {
            Set<String> verticesOfGraph = gf.vertexSet();
            Set<String> verticesOfGraphToCheck = coloredGF.vertexSet();
            List<String> classesGraph = new ArrayList<>();
            List<String> classesGraphToCheck = new ArrayList<>();
            for (String v: verticesOfGraph) {
                classesGraph.add(this.dataset.getClassForSubject(v));
            }
            for (String v: verticesOfGraphToCheck) {
                classesGraphToCheck.add(this.dataset.getClassForSubject(v));
            }
            Collections.sort(classesGraph);
            Collections.sort(classesGraphToCheck);
            if (!classesGraph.equals(classesGraphToCheck)) {
                continue;
            }

            boolean colorIsomorph = true;
            if (isomorphicListType.equals(ANTENNA)) {
                colorIsomorph = checkColorIsomorphismForAntennas(coloredGF, gf);
            } else if (isomorphicListType.equals(STAR) || isomorphicListType.equals(INBOUND_STAR) || isomorphicListType.equals(OUTBOUND_STAR) || isomorphicListType.equals(MIXED_STAR)) {
                colorIsomorph = checkColorIsomorphismForStars(coloredGF, gf);
            } else {
                PermutationClassIsomorphismInspector inspector = new PermutationClassIsomorphismInspector(coloredGF, gf); // , new VertexDegreeEquivalenceComparator(), null
                while (inspector.hasNext()) {
                    IsomorphismRelation graphMapping = inspector.nextIsoRelation();
                    colorIsomorph = true;
                    for (Object o : gf.vertexSet()) {
                        String v = o.toString();
                        String correspondingVertex = graphMapping.getVertexCorrespondence(v, false).toString();
                        String vClass = this.dataset.getClassForSubject(v);
                        if (!this.dataset.getClassForSubject(correspondingVertex).equals(vClass)) {
                            colorIsomorph = false;
                        }
                    }
                    if (colorIsomorph) {
                        break;
                    }
                }

//                    ExecutorService executorService = Executors.newSingleThreadExecutor();
//
//                    Callable<Boolean> callable = () -> {
//                        boolean colorIsomorph = true;
//                        try {
//                            while (!Thread.currentThread().isInterrupted() && inspector.hasNext()) {
//                            //while (!Thread.currentThread().isInterrupted() && true) {
//                                IsomorphismRelation graphMapping = inspector.nextIsoRelation();
//                                if (Thread.currentThread().interrupted()) {
//                                    logger.debug("throw new InterruptedException() inside while");
//                                    throw new RuntimeException();
//                                }
//                                for (Object o : gf.vertexSet()) {
//                                    String v = o.toString();
//                                    String correspondingVertex = graphMapping.getVertexCorrespondence(v, false).toString();
//                                    String vClass = this.dataset.getClassForSubject(v);
//                                    if (!this.dataset.getClassForSubject(correspondingVertex).equals(vClass)) {
//                                        colorIsomorph = false;
//                                    }
//                                }
//                                if (colorIsomorph) {
//                                    break;
//                                }
//                            }
//                            if (Thread.currentThread().interrupted()) {
//                                logger.debug("throw new InterruptedException() outside while");
//                                throw new InterruptedException();
//                            }
//                            if (!Thread.currentThread().interrupted()) {
//                                return colorIsomorph;
//                            }
//                            return false;
//                        }
//                        catch (InterruptedException e) {
//                            throw new IllegalStateException("task interrupted", e);
//                        }
//                        catch (NoSuchElementException e) {
//                            return colorIsomorph;
//                        }
//                    };
//
//                    // Submit the task for execution
//                    Future<Boolean> future = executorService.submit(callable);
//
//                    try {
//                        Boolean colorIsomorph = future.get(1, TimeUnit.MINUTES);
//                        if (colorIsomorph) {
//                            newColorIsomorphismGroup = false;
//                            colorIsoGroupIndex = colorIsoGF.indexOf(coloredGF);
//                            logger.debug("\t\tAdding to color isomorphism bag #{}.", colorIsoGroupIndex);
//                        }
//                    }
//                    catch (TimeoutException e) {
//                        System.out.println("Timeout");
//                        future.cancel(true);
//                        continue;
//                    }
//                    catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                    catch (ExecutionException e) {
//                        e.printStackTrace();
//                    }
//
////
//                    while (inspector.hasNext()) {
//                        IsomorphismRelation graphMapping = inspector.nextIsoRelation();
//                        boolean colorIsomorph = true;
//                        for (Object o : gf.vertexSet()) {
//                            String v = o.toString();
//                            String correspondingVertex = graphMapping.getVertexCorrespondence(v, false).toString();
//                            String vClass = this.dataset.getClassForSubject(v);
//                            if (!this.dataset.getClassForSubject(correspondingVertex).equals(vClass)) {
//                                colorIsomorph = false;
//                            }
//                        }
//                        if (colorIsomorph) {
//                            newColorIsomorphismGroup = false;
//                            colorIsoGroupIndex = colorIsoGF.indexOf(coloredGF);
//                            logger.debug("\t\tAdding to color isomorphism bag #{}.", colorIsoGroupIndex);
//                            break;
//                        }
//                    }
            }

            if (colorIsomorph) {
                newColorIsomorphismGroup = false;
                colorIsoGroupIndex = colorIsoGF.indexOf(coloredGF);
                logger.debug("\t\tAdding to color isomorphism bag #{}.", colorIsoGroupIndex);
            }
        }
        if (newColorIsomorphismGroup) {
            List<String> coloredIso = new ArrayList<>();
            if (colorIsomorphicPatterns.containsKey(indexIsomorphicList)) {
                coloredIso = colorIsomorphicPatterns.get(indexIsomorphicList);
            }
            coloredIso.add(JsonOutput.getJsonColoredGroup(gf, this.dataset, isomorphicListType).toString());
            colorIsomorphicPatterns.put(indexIsomorphicList, coloredIso);
            //colorIsoGroupIndex = colorIsoGF.size();
            colorIsoGF.add(gf);
            // colorIsoGroupIndex = colorIsoGF.indexOf(gf);
            colorIsoGroupIndex = colorIsoGF.size() - 1;
            logger.debug("\t\tCreating new color isomorphism bag #{}.", colorIsoGroupIndex);
        }

        HashMap<Integer, List<String>> colored = new HashMap<>();
        List<String> coloredList = new ArrayList<>();
        if (coloredPatterns.containsKey(indexIsomorphicList)) {
            colored = coloredPatterns.get(indexIsomorphicList);
            if (colored.containsKey(colorIsoGroupIndex)) {
                coloredList = colored.get(colorIsoGroupIndex);
            }
        }
        //colored.add(JsonOutput.getJsonColored(gf, this.dataset).toString());
        // differed from not GC
        coloredList.add(patternsWithSurrounding.get(graphNr));
        colored.put(colorIsoGroupIndex, coloredList);
        coloredPatterns.put(indexIsomorphicList, colored);
    }
    if (isomorphicGraphsTemp.get(0).edgeSet().size() <= MAX_SIZE_FOR_ISO) {
        logger.info(isomorphicGraphsTemp.size() + " x " + isomorphicListType);
        // TODO type
        String json = JsonOutput.getJson(connectedGraphs.get(isomorphicGraphList.get(0)), isomorphicListType).toString();
        logger.info(json);
        HashMap<String, Integer> patternTemp = new HashMap<>();
        patternTemp.put(json, isomorphicGraphList.size());
        patterns.put(indexIsomorphicList, patternTemp);
        // TODO
        // patternDiameter.put(index, graphs.get(isomorphicGraphList.get(0)).getDiameter());


//                if (graphRenderer != null) {
//                    this.graphRenderer.writeDotFiles(index.toString() + "_detailed", isomorphicGraphsTemp, true);
//                }

    }
}
}

        */

    private boolean checkColorIsomorphismForAntennas(SimpleGraph coloredGF, SimpleGraph gf) {
        List<String> classes1 = dataset.getClassForSubjects(GraphUtils.getVerticesWithDegreee(gf, 2));
        List<String> classes2 = dataset.getClassForSubjects(GraphUtils.getVerticesWithDegreee(coloredGF, 2));
        Collections.sort(classes1);
        Collections.sort(classes2);
        if(!classes1.equals(classes2)) {
            return false;
        }
        classes1 = dataset.getClassForSubjects(GraphUtils.getVerticesWithDegreee(gf, (gf.vertexSet().size()-1)));
        classes2 = dataset.getClassForSubjects(GraphUtils.getVerticesWithDegreee(coloredGF, (coloredGF.vertexSet().size()-1)));
        Collections.sort(classes1);
        Collections.sort(classes2);
        if(!classes1.equals(classes2)) {
            return false;
        }
        return true;
    }

    private boolean checkColorIsomorphismForStars(SimpleGraph coloredGF, SimpleGraph gf) {
        List<String> classes1 = dataset.getClassForSubjects(GraphUtils.getVerticesWithDegreee(gf, (gf.vertexSet().size()-1)));
        List<String> classes2 = dataset.getClassForSubjects(GraphUtils.getVerticesWithDegreee(coloredGF, (coloredGF.vertexSet().size()-1)));
        Collections.sort(classes1);
        Collections.sort(classes2);
        if(!classes1.equals(classes2)) {
            return false;
        }
        return true;
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

    /**
     * Loads a dataset from files.
     * For ProLOD++.
     */
    public static GraphLOD loadDataset(String name, Dataset dataset) {
        return new GraphLOD(name, true, true, false, false, 1, 1, 0, dataset, "", 4, true, true, false);
    }

    public static GraphLOD loadDataset(String name, Dataset dataset, boolean numbersOnly) {
        return new GraphLOD(name, true, true, false, false, 1, 1, 0, dataset, "", 4, true, true, numbersOnly);
    }

    public static GraphLOD loadGraph(String name, Dataset dataset, boolean analyze) {
        return new GraphLOD(name, true, true, false, false, 1, 1, 0, dataset, "", 4, true, analyze, false);
        /* GraphStatistics graphStats = new GraphStatistics();
        return graphStats;
        */
    }

    public static void main(final String[] args) {
        ArgumentParser arguments = new ArgumentParser(args, MAX_SIZE_FOR_PROLOD);

        /*
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        if (debugMode) {
            root.setLevel(Level.DEBUG);
        } else {
            root.setLevel(Level.INFO);
        }
        */

        //BasicConfigurator.configure();
        Locale.setDefault(Locale.US);

        Dataset dataset = Dataset.fromFiles(arguments.getDataset(), arguments.getName(), arguments.getNamespace(), arguments.getOntns(), arguments.getExcludedNamespaces());

        if (arguments.isRunGrami()) {
            GramiAnalysis g = new GramiAnalysis();
            List<GramiAnalysis.GraphPattern> result = g.run(dataset);
            System.out.println(Joiner.on("\n").join(result));
        } else if (arguments.isRunGspan()) {
          GspanAnalysis g = new GspanAnalysis();
            g.run(dataset);
        } else {
            GraphLOD.fromArguments(dataset, arguments);
        }
    }

    private void getStatistics() {
        this.nodes = this.dataset.getGraph().vertexSet().size();
        this.edges = this.dataset.getGraph().edgeSet().size();
    }

    public void createComponents() {
        List<Set<String>> sets = graphFeatures.getConnectedSets();
        logger.info("Connected sets: " + formatInt(sets.size()));
        printComponentSizeAndCount(sets);

        if (graphFeatures.isConnected()) {
            connectedGraphFeatures.add(graphFeatures);
        } else {
            connectedGraphFeatures = graphFeatures.createSubGraphFeatures(graphFeatures.getConnectedSets());
        }
    }

    public class GraphLODComparator implements Comparator<List<?>>{
        public GraphLODComparator() {

        }

        public int compare(List<?>a1,List<?>a2) {
            return a2.size()-a1.size();
        }
    }

}

