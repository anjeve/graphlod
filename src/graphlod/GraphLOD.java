package graphlod;

import com.google.common.base.Charsets;
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
    public static final String CATERPILLAR = "Caterpillar Graph";
    public static final String LOBSTER = "Lobster Graph";
    public static final String CIRCLE = "Circle";
    public static final String WINDMILL = "Windmill Graph";
    public static final String WHEEL = "Wheel Graph";
    public static final String STRONGLY_CONNECTED = "Strongly Connected Component";

    private static Logger logger = LoggerFactory.getLogger(GraphLOD.class);

    public static final int MAX_SIZE_FOR_DIAMETER = 50;
    public static final int MAX_SIZE_FOR_CS_PRINT = 500000000;
    public static final int MAX_SIZE_FOR_PROLOD = 5000;
    public static final int MAX_SIZE_FOR_ISO = 1000;

    private GraphCsvOutput graphCsvOutput = null;
    private VertexCsvOutput vertexCsvOutput = null;
    public final GraphRenderer graphRenderer;
    public String name;
    public Dataset dataset;
    private boolean exportJson;
    private boolean exportGrami;
    private boolean apiOnly;
    public List<GraphFeatures> connectedGraphs = new ArrayList<>();
    public List<GraphFeatures> stronglyConnectedGraphs = new ArrayList<>();
    public List<GraphFeatures> pathGraphs = new ArrayList<>();
    public List<GraphFeatures> directedPathGraphs = new ArrayList<>();
    public List<GraphFeatures> outboundStarGraphs = new ArrayList<>();
    public List<GraphFeatures> inboundStarGraphs = new ArrayList<>();
    public List<GraphFeatures> mixedDirectedStarGraphs = new ArrayList<>();
    public List<GraphFeatures> treeGraphs = new ArrayList<>();
    public List<GraphFeatures> caterpillarGraphs = new ArrayList<>();
    public List<GraphFeatures> lobsterGraphs = new ArrayList<>();
    public List<GraphFeatures> completeGraphs = new ArrayList<>();
    private List<GraphFeatures> bipartiteGraphs = new ArrayList<>();
    public List<GraphFeatures> unrecognizedStructure = new ArrayList<>();
    public List<String> htmlFiles = new ArrayList<>();
    public String output;
    public List<List<Integer>> isomorphicGraphs = new ArrayList<>();
    public List<SimpleGraph> connectedGraphsGC = new ArrayList<>();
    public List<String> connectedGraphsGCTypes = new ArrayList<>();
    private List<List<Integer>> isomorphicGraphsGC = new ArrayList<>();
    public List<String> isomorphicGraphsGCTypes = new ArrayList<>();
    private HashMap<Integer, List<List<Integer>>> colorPreservingIsomorphicGraphs = new HashMap<>();
    public GraphFeatures graphFeatures;

    private HashMap<String, List<Integer>> verticesInPatterns = new HashMap<>();

    public HashMap<Integer, HashMap<String, Integer>> patterns = new HashMap<>();
    public HashMap<Integer, Double> patternDiameter = new HashMap<>();
    public HashMap<Integer, HashMap<Integer, List<String>>> coloredPatterns = new HashMap<>();
    public HashMap<Integer, List<String>> colorIsomorphicPatterns = new HashMap<>();
    public HashMap<Integer, HashMap<String, Integer>> patternsGC = new HashMap<>();
    public HashMap<Integer, Double> patternDiameterGC = new HashMap<>();
    public HashMap<Integer, HashMap<Integer, List<String>>> coloredPatternsGC = new HashMap<>();
    public HashMap<Integer, List<String>> colorIsomorphicPatternsGC = new HashMap<>();
    public List<String> patternsWithSurroundingGC = new ArrayList<>();
    public List<String> outboundStars = new ArrayList<>();
    public List<String> inboundStars = new ArrayList<>();
    public List<String> mixedStars = new ArrayList<>();
    public List<String> circleGraphs = new ArrayList<>();
    public List<String> doublyLinkedPaths = new ArrayList<>();
    private List<SimpleGraph> outboundStarSimpleGraphs = new ArrayList<>();
    private List<SimpleGraph> inboundStarSimpleGraphs = new ArrayList<>();
    private List<SimpleGraph> mixedStarSimpleGraphs = new ArrayList<>();
    public HashMap<Integer, List<Integer>> isomorphicOutboundStars = new HashMap<>();
    public HashMap<Integer, List<Integer>> isomorphicInboundStars = new HashMap<>();
    public HashMap<Integer, List<Integer>> isomorphicMixedStars = new HashMap<>();
    public HashMap<Integer, HashMap<String, Integer>> outboundStarsPattern =  new HashMap<>();
    public HashMap<Integer, HashMap<String, Integer>> inboundStarsPattern = new HashMap<>();
    public HashMap<Integer, HashMap<String, Integer>> mixedStarsPattern = new HashMap<>();
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
    private Integer bigComponentSize;

    public GraphLOD(ArgumentParser arguments) {
        this.graphRenderer = null;
        new GraphLOD(arguments.getName(), arguments.getDataset(), arguments.isSkipChromatic(), arguments.isSkipGraphviz(), arguments.isExportJson(),
                arguments.isExportGrami(), arguments.getNamespace(), arguments.getOntns(), arguments.getExcludedNamespaces(), arguments.getMinImportantSubgraphSize(),
                arguments.getImportantDegreeCount(), arguments.getBigComponentSize(), arguments.getOutput(), arguments.getThreadcount(), arguments.isApiOnly(), true);
    }

    public GraphLOD(String name, Collection<String> datasetFiles, boolean skipChromaticNumber, boolean skipGraphviz,
                    boolean exportJson, boolean exportGrami, String namespace, String ontologyNS, Collection<String> excludedNamespaces, int minImportantSubgraphSize,
                    int importantDegreeCount, int bigComponentSize, String output, int threadCount, boolean apiOnly, boolean analyzeAlso) {
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

        if (bigComponentSize > 0) {
            this.bigComponentSize = bigComponentSize;
        } else {
            this.bigComponentSize = MAX_SIZE_FOR_PROLOD;
        }


        if (apiOnly) {
            graphFeatures = readDataset(datasetFiles, namespace, ontologyNS, excludedNamespaces);
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
                    HtmlDocument htmlDocument = new HtmlDocument(this);
                    htmlDocument.createHtmlStructures();
                    htmlDocument.createHtmlConnectedSets();
                }
                graphFeatures = null;
            }
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

        searchGiantComponent();
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

        if (this.connectedGraphs.size() > 1) {
            for (GraphFeatures subGraph : connectedGraphs) {
                if (subGraph.getVertices().size() >= this.bigComponentSize) continue;
                // getWalks(subGraph.getSimpleGraph());
                // String pattern = getMinimizedPatterns(subGraph.getSimpleGraph());

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
                boolean isStarGraph = false;
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
            }
        } else {
            this.connectedGraphSizes.add(this.connectedGraphs.get(0).getVertexCount());
        }

        logger.debug("Checking for isomorphisms of connected components.");
        sw = Stopwatch.createStarted();
        groupIsomorphicGraphFeatures();
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

    private void searchGiantComponent() {
        createStatsCsv();

        for (GraphFeatures connectedSet : this.connectedGraphs) {
            DirectedGraph<String, DefaultEdge> graph = connectedSet.getGraph();
            if (connectedSet.getVertexCount() < this.bigComponentSize) {
                if (this.connectedGraphs.size() > 1) continue;
            }
            List<String> verticesInPaths = new ArrayList<>();
            List<String> verticesInCompleteGraph = new ArrayList<>();
            List<String> verticesInCaterpillars = new ArrayList<>();
            List<String> verticesInCircles = new ArrayList<>();
            List<String> verticesInDoublyLinkedLists = new ArrayList<>();
            List<String> verticesInWindmills = new ArrayList<>();
            List<String> verticesInWheels = new ArrayList<>();
            List<String> verticesInStars = new ArrayList<>();
            List<String> verticeCentreOfStar = new ArrayList<>();
            List<String> verticesInOtherPatterns = new ArrayList<>();

            getStronglyConnectedComponentsFromGC(graph);

            int i = 1;
            for (String v : connectedSet.getVertices()) {
                if (i % 1000 == 0) logger.info(i++ + "/" + connectedSet.getVertices().size());
                boolean vPartOfStar = false;
                boolean vStartOfPath = false;
                if  ((connectedSet.incomingEdgesOf(v).size() == 0) && (connectedSet.outgoingEdgesOf(v).size() >= 4)) {
                    vPartOfStar = checkVertexAsCentreOfOutboundStar(connectedSet, v, verticesInStars);
                } else if  ((connectedSet.outgoingEdgesOf(v).size() == 0) && (connectedSet.incomingEdgesOf(v).size() >= 4)) {
                    vPartOfStar = checkVertexAsCentreOfInboundStar(connectedSet, v, verticesInStars);
                } else  if  ((connectedSet.incomingEdgesOf(v).size() > 0) && (connectedSet.outgoingEdgesOf(v).size() > 0)) {
                    vPartOfStar = checkVertexAsCentreOfMixedStar(connectedSet, v, verticesInStars);
                }
                if (vPartOfStar) {
                    verticeCentreOfStar.add(v);
                }

                // TODO if no vertice already on circle
                if (!verticesInCircles.contains(v)) {
                    checkVertexAsStartOfCircle(connectedSet, v, verticesInCircles);
                    verticesInOtherPatterns.addAll(verticesInCircles);
                }

                if (!verticeCentreOfStar.contains(v)) {
                    if (!verticesInWindmills.contains(v)) {
                        checkVertexAsCentreOfWindmill(graph, v, verticesInWindmills);
                        verticesInOtherPatterns.addAll(verticesInWindmills);
                    }
                    if (!verticesInWindmills.contains(v) && !verticesInWheels.contains(v)) {
                        checkVertexAsCentreOfWheel(graph, v, verticesInWheels);
                        verticesInOtherPatterns.addAll(verticesInWheels);
                    }
                    checkDoublyLinkedPathsFromGC(connectedSet, v);
                    if (!verticesInPaths.contains(v)) {
                        vStartOfPath = checkVertexAsStartOfPath(graph, v, verticesInPaths, verticesInCircles);
                    }
                    if (!verticesInPaths.contains(v) && !verticesInCaterpillars.contains(v)) {
                        checkVertexAsStartOfCaterpillar(connectedSet, v, verticesInCaterpillars);
                    }

                    if (!verticesInCompleteGraph.contains(v)) {
                        checkVertexinCompleteGraph(graph, v, verticesInCompleteGraph);
                    }
                }

            }
            getSiameseStarsFromGC(connectedSet);
        }
        closeStatsCsv();

        groupIsomorphicGCGraphs();
    }

    private void getStronglyConnectedComponentsFromGC(DirectedGraph<String, DefaultEdge> graph) {
        StrongConnectivityInspector<String, DefaultEdge> sci = new StrongConnectivityInspector<>(graph);
        List<DirectedSubgraph<String, DefaultEdge>> stronglyConnectedComponents = sci.stronglyConnectedSubgraphs();
        for (DirectedSubgraph<String, DefaultEdge> subGraph : stronglyConnectedComponents) {
            if (subGraph.vertexSet().size() >= 4) {
                addPatterns(graph, subGraph, STRONGLY_CONNECTED);
            }
        }
    }

    private void getSiameseStarsFromGC(GraphFeatures connectedSet) {
        logger.info("Siamese Stars");
        List<String> typesToAdd = new ArrayList();
        List verticesinSiameseStars = new ArrayList();
        for (ListIterator<String> iterator = this.connectedGraphsGCTypes.listIterator(); iterator.hasNext(); ) {
            String patternType = iterator.next();
            int index = this.connectedGraphsGCTypes.indexOf(patternType);
            SimpleGraph graph = this.connectedGraphsGC.get(index);
            if (patternType.equals(STAR)) {
                for (Object v: graph.vertexSet()) {
                    if ((this.verticesInPatterns.get(v.toString()).size() > 1) && (!verticesinSiameseStars.contains(v.toString()))) {
                        DirectedGraph<String, DefaultEdge> doublyLinkedPath = new DefaultDirectedGraph<>(DefaultEdge.class);
                        SimpleGraph<String, DefaultEdge> simpleDoublyLinkedPath = new SimpleGraph<>(DefaultEdge.class);
                        DirectedGraph<String, DefaultEdge> doublyLinkedPath2 = new DefaultDirectedGraph<>(DefaultEdge.class);
                        for (Integer id: this.verticesInPatterns.get(v.toString())) {
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
                        logger.info("Adding 1 siamese star");

                        // TODO
                        List<String> verticesInCurrentSiameseStar = new ArrayList<>();
                        verticesInCurrentSiameseStar.addAll(simpleDoublyLinkedPath.vertexSet());

                        addPatterns(verticesInCurrentSiameseStar, connectedSet.getGraph(), doublyLinkedPath, simpleDoublyLinkedPath, doublyLinkedPath2, SIAMESE_STAR);
                        typesToAdd.add(SIAMESE_STAR);
                        break;
                    }
                }
            }
        }
        for (String type: typesToAdd) {
            this.connectedGraphsGCTypes.add(type);
        }
    }

    private void checkDoublyLinkedPathsFromGC(GraphFeatures connectedSet, String v) {
        DirectedGraph<String, DefaultEdge> graph = connectedSet.getGraph();
        List<String> doublyLinkedList = checkVertexInLinkedList(graph, v, new ArrayList<String>());
        if (doublyLinkedList.size() >= 5) {
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

            addPatterns(doublyLinkedList, graph, doublyLinkedPath, simpleDoublyLinkedPath, doublyLinkedPath2, DOUBLY_LINKED_PATH);
        } else  if (doublyLinkedList.size() >= 3) {
            logger.info("Doubly linked path of length {} found", doublyLinkedList.size());
        }
    }

    private List<String> checkVertexInLinkedList(DirectedGraph graph, String v, List<String> visited) {
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

    private void checkVertexinCompleteGraph(DirectedGraph<String, DefaultEdge> graph, String v, List<String> verticesInCompleteGraph) {
        List<String> neighbourVertices = Graphs.neighborListOf(graph, v);
        for (String vertex: neighbourVertices) {
            // TODO
        }
        //addPatterns(verticesInWindmills, neighbourVertices, graph, windmillGraph, simpleWindmillGraph, windmillGraph2, WINDMILL);
    }

    private boolean checkVertexAsCentreOfWindmill(DirectedGraph graph, String v_center, List<String> verticesInWindmills) {
        List<String> neighbourVertices = Graphs.neighborListOf(graph, v_center);
        if ((neighbourVertices.size() < 4) || (neighbourVertices.size() % 2 != 0)) {
            return false;
        }
        for (String neighborV: neighbourVertices) {
            List<String> secondNeighbourVertices = Graphs.neighborListOf(graph, neighborV);
            secondNeighbourVertices.retainAll(neighbourVertices);
            if (secondNeighbourVertices.size() != 1) {
                return false;
            }
        }
        DirectedGraph<String, DefaultEdge> windmillGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
        SimpleGraph<String, DefaultEdge> simpleWindmillGraph = new SimpleGraph<>(DefaultEdge.class);
        DirectedGraph<String, DefaultEdge> windmillGraph2 = new DefaultDirectedGraph<>(DefaultEdge.class);
        windmillGraph.addVertex(v_center);
        simpleWindmillGraph.addVertex(v_center);
        for (String vertex : neighbourVertices) {
            windmillGraph.addVertex(vertex);
            simpleWindmillGraph.addVertex(vertex);
            for (String alreadyAdded : simpleWindmillGraph.vertexSet()) {
                addEdges(windmillGraph, graph.getAllEdges(vertex, alreadyAdded), vertex, alreadyAdded);
                addEdges(windmillGraph, graph.getAllEdges(alreadyAdded, vertex), alreadyAdded, vertex);
                addEdges(simpleWindmillGraph, graph.getAllEdges(vertex, alreadyAdded), vertex, alreadyAdded);
                addEdges(simpleWindmillGraph, graph.getAllEdges(alreadyAdded, vertex), alreadyAdded, vertex);
            }
        }
        addPatterns(verticesInWindmills, neighbourVertices, graph, windmillGraph, simpleWindmillGraph, windmillGraph2, WINDMILL);
        verticesInWindmills.add(v_center);
        return true;
    }

    private boolean checkVertexAsCentreOfWheel(DirectedGraph graph, String v_center, List<String> verticesinWheels) {
        List<String> neighbourVertices = Graphs.neighborListOf(graph, v_center);
        if (neighbourVertices.size() < 4) {
            return false;
        }
        boolean oneVertexWithNoNeighbourHere = false;
        for (String neighborV: neighbourVertices) {
            List<String> secondNeighbourVertices = Graphs.neighborListOf(graph, neighborV);
            secondNeighbourVertices.retainAll(neighbourVertices);
            if (secondNeighbourVertices.size() != 2) {
                if (!oneVertexWithNoNeighbourHere && (secondNeighbourVertices.size() == 0)) {
                    oneVertexWithNoNeighbourHere = true;
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
            windmillGraph.addVertex(vertex);
            simpleWindmillGraph.addVertex(vertex);
            for (String alreadyAdded : simpleWindmillGraph.vertexSet()) {
                addEdges(windmillGraph, graph.getAllEdges(vertex, alreadyAdded), vertex, alreadyAdded);
                addEdges(windmillGraph, graph.getAllEdges(alreadyAdded, vertex), alreadyAdded, vertex);
                addEdges(simpleWindmillGraph, graph.getAllEdges(vertex, alreadyAdded), vertex, alreadyAdded);
                addEdges(simpleWindmillGraph, graph.getAllEdges(alreadyAdded, vertex), alreadyAdded, vertex);
            }
        }
        addPatterns(verticesinWheels, neighbourVertices, graph, windmillGraph, simpleWindmillGraph, windmillGraph2, WHEEL);
        verticesinWheels.add(v_center);
        return true;
    }

    private boolean checkVertexAsCentreOfMixedStar(GraphFeatures connectedSet, String v_center, List<String> verticesInStars) {
        DirectedGraph<String, DefaultEdge> graph = connectedSet.getGraph();
        List<String> neighbourVertices = GraphUtils.getNeighboursOfV(graph, v_center);
        if (neighbourVertices.size() < 4) {
            return false;
        }
        Set<DefaultEdge> surroundingEdges = connectedSet.outgoingEdgesOf(v_center);
        Set<DefaultEdge> sei = connectedSet.incomingEdgesOf(v_center);
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
                Set<DefaultEdge> incomingEdges2 = connectedSet.incomingEdgesOf(v_level1);
                Set<DefaultEdge> outgoingEdges2 = connectedSet.outgoingEdgesOf(v_level1);
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
                Set<DefaultEdge> incomingEdges2 = connectedSet.incomingEdgesOf(v_level1);
                Set<DefaultEdge> outgoingEdges2 = connectedSet.outgoingEdgesOf(v_level1);
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
                addPatterns(verticesInStars, neighbourVertices, connectedSet.getGraph(), outgoingStar, simpleStar, outgoingStarLevel2, OUTBOUND_STAR);
                addStats(v_center, neighbourVertices, outStatsCsv);
                return true;
            }
        }
        return false;
    }

    private void addStats(String v_center, List<String> surroundingVertices, BufferedWriter writer) {
        String centerNodeClass = dataset.getClassForSubject(v_center);
        HashMap<String, Integer> classesSurrounding = new HashMap<String, Integer>();
        for (String v : surroundingVertices) {
            String nodeClass = dataset.getClassForSubject(v);
            Integer subjectsPerClass = 1;
            if (classesSurrounding.containsKey(nodeClass)) subjectsPerClass += classesSurrounding.get(nodeClass);
            classesSurrounding.put(nodeClass, subjectsPerClass);
        }
        Map<String, Integer> sortedMap = new TreeMap<String, Integer>(classesSurrounding);
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

    private boolean checkVertexAsStartOfCircle(GraphFeatures gf, String v, List<String> verticesInCircles) {
        List<String> path = checkVertexAsPartOfCircle(gf.getGraph(), v, new ArrayList<String>());
        if (path.size() == 0) {
            return false;
        } else if (path.size() >= 4) {
            DirectedGraph graph = gf.getGraph();
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

            addPatterns(verticesInCircles, path, graph, doublyLinkedPath, simpleDoublyLinkedPath, doublyLinkedPath2, CIRCLE);
            return true;
        } else if (path.size() >= 3) {
            logger.info("Circle of length {} found", path.size());
        }
        return false;
    }

    private void addPatterns(DirectedGraph<String, DefaultEdge> graph, DirectedSubgraph<String, DefaultEdge> subgraph, String patternType) {
        Set<String> vertices = subgraph.vertexSet();
        List<String> vertexList = new ArrayList<>();
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
        addPatterns(new ArrayList<String>(), vertexList, graph, directedGraph, simpleGraph, directedGraphSurrounding, patternType);
    }

    private void addPatterns(List<String> vertices, DirectedGraph<String, DefaultEdge> graph, DirectedGraph<String, DefaultEdge> directedGraph, SimpleGraph<String, DefaultEdge> simpleGraph, DirectedGraph<String, DefaultEdge> directedGraphSurrounding, String patternType) {
        addPatterns(new ArrayList<String>(), vertices, graph, directedGraph, simpleGraph, directedGraphSurrounding, patternType);
    }

    private void addPatterns(List<String> verticesInPattern, List<String> vertices, DirectedGraph<String, DefaultEdge> graph, DirectedGraph<String, DefaultEdge> directedGraph, SimpleGraph<String, DefaultEdge> simpleGraph, DirectedGraph<String, DefaultEdge> directedGraphSurrounding, String patternType) {
        getNeighbourVerticesAndEdges(graph, directedGraph, directedGraphSurrounding);
        this.patternsWithSurroundingGC.add(JsonOutput.getJson(directedGraph, directedGraphSurrounding, patternType, this.dataset).toString());
        this.connectedGraphsGC.add(simpleGraph);
        if (patternType.equals(OUTBOUND_STAR) || patternType.equals(INBOUND_STAR) || patternType.equals(MIXED_STAR)) {
            patternType = STAR;
        }
        if (!patternType.equals(SIAMESE_STAR)) {
            this.connectedGraphsGCTypes.add(patternType);
        }
        addVerticesForPatterns(simpleGraph, this.connectedGraphsGC.indexOf(simpleGraph));
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

    private List<String> checkVertexAsPartOfCircle(DirectedGraph graph, String v, List<String> visited) {
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

    private boolean checkVertexAsStartOfPath(DirectedGraph graph, String v, List<String> verticesInPaths, List<String> verticesInOtherPatterns) {
        if (GraphUtils.getNeighboursOfV(graph, v).size() == 2) {
            return false;
        }
        List<String> path = checkVertexAsPartOfPath(graph, v, new ArrayList<String>(), verticesInOtherPatterns);
        if (path.size() >= 5) {
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
            addPatterns(verticesInPaths, path, graph, doublyLinkedPath, simpleDoublyLinkedPath, doublyLinkedPath2, PATH);
            return true;
        } else if (path.size() >= 3) {
            logger.info("Path of length {} found", path.size());
        }
        return false;
    }

    private List<String> checkVertexAsPartOfPath(DirectedGraph<String, DefaultEdge> graph, String v, List<String> visited, List<String> verticesInOtherPatterns) {
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
                return new ArrayList<>();
            }
            if ((neighbourV.size() > 1) || (neighbourV.size() == 0) || (verticesInOtherPatterns.contains(v))) {
                lastVertex = true;
            } else {
                nextVertex = neighbourV.get(0);
            }
        }
        List<String> path = new ArrayList<>();
        if ((nextVertex != null) && !lastVertex) {
            path = checkVertexAsPartOfPath(graph, nextVertex, visited, verticesInOtherPatterns);
        }
        path.add(v);
        return path;
    }

    private boolean checkVertexAsStartOfCaterpillar(GraphFeatures gf, String v, List<String> verticesInPaths) {
        List<String> path = checkVertexAsPartOfCaterpillar(gf.getGraph(), v, new ArrayList<String>());
        if (path.size() >= 5) {
            DirectedGraph graph = gf.getGraph();
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
            addPatterns(verticesInPaths, path, graph, doublyLinkedPath, simpleDoublyLinkedPath, doublyLinkedPath2, CATERPILLAR);
            return true;
        } else if (path.size() >= 3) {
            logger.info("Caterpillar of length {} found", path.size());
        }
        return false;
    }

    private List<String> checkVertexAsPartOfCaterpillar(DirectedGraph graph, String v, List<String> visited) {
        String nextVertex = null;
        boolean lastVertex = false;
        List<String> neighbourV = GraphUtils.getNeighboursOfV(graph, v);
        if (((visited.size() > 0) && (neighbourV.size() > 1)) || ((neighbourV.size() == 1) && (visited.size() > 1))) {
            int lastVertexIndex = visited.size() - 1;
            neighbourV.remove(visited.get(lastVertexIndex));
        }
        visited.add(v);
        List<String> path = new ArrayList<>();
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
                nextVertex = neighbourV.get(0);
            } else if (neighbourV.size() == 0) {
                lastVertex = true;
            } else if (neighbourV.size() > 1) {
                nextVertex = checkIfAllNeighboursCouldBePartOfCaterpillar(graph, neighbourV, v);
                if (nextVertex != null) {
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
            List<String> restPath = checkVertexAsPartOfCaterpillar(graph, nextVertex, visited);
            restPath.removeAll(path);
            path.addAll(restPath);
        }
        path.add(v);
        return path;
    }

    private String checkIfAllNeighboursCouldBePartOfCaterpillar(DirectedGraph graph, List<String> neighbourV, String vertex) {
        boolean alreadyOnVerticeWithNeighbour = false;
        String nextV = null;
        Set<Integer> neighbourList = new HashSet<>();
        for (String v: neighbourV) {
            List<String> neighboursOfV = Graphs.neighborListOf(graph, v);
            neighboursOfV.remove(vertex);
            if (neighboursOfV.size() > 0) {
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

    private boolean checkVertexAsCentreOfInboundStar(GraphFeatures connectedSet, String v_center, List<String> verticesInStars) {
        Set<String> vertices = new HashSet<>();
        Set<String> surroundingVertices = new HashSet<>();
        Set<DefaultEdge> surroundingIEdges = connectedSet.incomingEdgesOf(v_center);
        if (surroundingIEdges.size() >= 4) {
            int numberOfEdgesForSurrounding = 0;
            List<String> neighbourVertices = connectedSet.getNeighbourVertices(v_center);
            DirectedGraph<String, DefaultEdge> outgoingStar = new DefaultDirectedGraph<>(DefaultEdge.class);
            SimpleGraph<String, DefaultEdge> simpleStar = new SimpleGraph<>(DefaultEdge.class);
            DirectedGraph<String, DefaultEdge> outgoingStarLevel2 = new DefaultDirectedGraph<>(DefaultEdge.class);
            outgoingStar.addVertex(v_center);
            simpleStar.addVertex(v_center);
            for (DefaultEdge surroundingEdge : surroundingIEdges) {
                String v_level1 = surroundingEdge.getSource().toString();
                Set<DefaultEdge> incomingEdges2 = connectedSet.incomingEdgesOf(v_level1);
                Set<DefaultEdge> outgoingEdges2 = connectedSet.outgoingEdgesOf(v_level1);
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
                addPatterns(verticesInStars, neighbourVertices, connectedSet.getGraph(), outgoingStar, simpleStar, outgoingStarLevel2, INBOUND_STAR);
                addStats(v_center, neighbourVertices, this.outStatsInboundCsv);
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

    private boolean checkVertexAsCentreOfOutboundStar(GraphFeatures connectedSet, String v_center, List<String> verticesInStars) {
        Set<DefaultEdge> surroundingEdges = connectedSet.outgoingEdgesOf(v_center);
        Set<String> surroundingVertices = new HashSet<>();
        Set<String> vertices = new HashSet<>();
        if (surroundingEdges.size() >= 4) {
            int numberOfEdgesForSurrounding = 0;
            List<String> neighbourVertices = connectedSet.getNeighbourVertices(v_center);
            DirectedGraph<String, DefaultEdge> outgoingStar = new DefaultDirectedGraph<>(DefaultEdge.class);
            SimpleGraph<String, DefaultEdge> simpleStar = new SimpleGraph<>(DefaultEdge.class);
            DirectedGraph<String, DefaultEdge> outgoingStarLevel2 = new DefaultDirectedGraph<>(DefaultEdge.class);
            outgoingStar.addVertex(v_center);
            simpleStar.addVertex(v_center);
            for (DefaultEdge surroundingEdge : surroundingEdges) {
                String v_level1 = surroundingEdge.getTarget().toString();
                Set<DefaultEdge> incomingEdges2 = connectedSet.incomingEdgesOf(v_level1);
                Set<DefaultEdge> outgoingEdges2 = connectedSet.outgoingEdgesOf(v_level1);
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
                addPatterns(verticesInStars, neighbourVertices, connectedSet.getGraph(), outgoingStar, simpleStar, outgoingStarLevel2, OUTBOUND_STAR);
                addStats(v_center, neighbourVertices, this.outStatsOutboundCsv);
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

    private void groupIsomorphicGCGraphs() {
        int i = 0;
        String type;
        for (SimpleGraph connectedSet : this.connectedGraphsGC) {
            int graphIndex = this.connectedGraphsGC.indexOf(connectedSet);
            type = this.connectedGraphsGCTypes.get(graphIndex);
            int connectedSetVertexSetSize = connectedSet.vertexSet().size();
            if (connectedSetVertexSetSize > MAX_SIZE_FOR_ISO) continue;
            logger.debug("\tChecking graph {}/{} ({} vertices).", ++i, this.connectedGraphsGC.size(), connectedSetVertexSetSize);
            int putIntoBag = -1;

            for (List<Integer> isomorphicGraphList : this.isomorphicGraphsGC) {
                int isomorphicListIndex = this.isomorphicGraphsGC.indexOf(isomorphicGraphList);
                String isomorphicListType = this.isomorphicGraphsGCTypes.get(isomorphicListIndex);
                if (!type.equals(isomorphicListType)) continue;
                SimpleGraph firstGraph = this.connectedGraphsGC.get(isomorphicGraphList.get(0));
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
                isomorphicGraphList.addAll(this.isomorphicGraphsGC.get(putIntoBag));
                this.isomorphicGraphsGC.remove(putIntoBag);
                this.isomorphicGraphsGC.add(putIntoBag, isomorphicGraphList);
                logger.debug("\t\tAdding graph of size {} to isomorphism group bag #{} of size {}.", connectedSetVertexSetSize, putIntoBag, isomorphicGraphList.size()-1);
            } else {
                this.isomorphicGraphsGC.add(isomorphicGraphList);
                this.isomorphicGraphsGCTypes.add(type);
                logger.debug("\t\tCreating new isomorphism bag for graphs of size {}.", connectedSetVertexSetSize);
            }
        }

        groupIsomorphicGCGraphsByColor();
    }

    private void groupIsomorphicGCGraphsByColor() {
        logger.info("GC Patterns");
        Collections.sort(this.isomorphicGraphsGC, new GraphLODComparator());
        for (List<Integer> isomorphicGraphList : this.isomorphicGraphsGC) {
            Integer indexIsomorphicList = this.isomorphicGraphsGC.indexOf(isomorphicGraphList);
            String isomorphicListType = this.isomorphicGraphsGCTypes.get(indexIsomorphicList);
            logger.debug("\tChecking color isomorphism for graphs in bag #{}.", indexIsomorphicList);
            /*
            if (graphRenderer != null) {
                this.graphRenderer.writeDotFile(index.toString(), graphFeatures.get(isomorphicGraphList.get(0)), false);
            }
            */
            List<SimpleGraph> isomorphicGraphsTemp = new ArrayList<>();

            List<SimpleGraph> colorIsoGF = new ArrayList();
            // TODO right here?
            Integer colorIsoGroupIndex = -1;
            for (Integer graphNr : isomorphicGraphList) {
                logger.debug("\tChecking color isomorphism for graph {}.", graphNr);
                SimpleGraph gf = this.connectedGraphsGC.get(graphNr);
                isomorphicGraphsTemp.add(gf);

                boolean newColorIsomorphismGroup = true;
                for (SimpleGraph coloredGF: colorIsoGF) {
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

                    PermutationClassIsomorphismInspector inspector = new PermutationClassIsomorphismInspector(coloredGF, gf); // , new VertexDegreeEquivalenceComparator(), null
                    while (inspector.hasNext()) {
                        boolean colorIsomorph = true;
                        IsomorphismRelation graphMapping = inspector.nextIsoRelation();
                        for (Object o : gf.vertexSet()) {
                            String v = o.toString();
                            String correspondingVertex = graphMapping.getVertexCorrespondence(v, false).toString();
                            String vClass = this.dataset.getClassForSubject(v);
                            if (!this.dataset.getClassForSubject(correspondingVertex).equals(vClass)) {
                                colorIsomorph = false;
                            }
                        }
                        if (colorIsomorph) {
                            newColorIsomorphismGroup = false;
                            colorIsoGroupIndex = colorIsoGF.indexOf(coloredGF);
                            logger.debug("\t\tAdding to color isomorphism bag #{}.", colorIsoGroupIndex);
                            break;
                        }
                    }
                }
                if (newColorIsomorphismGroup) {
                    List<String> coloredIso = new ArrayList<>();
                    if (this.colorIsomorphicPatternsGC.containsKey(indexIsomorphicList)) {
                        coloredIso = this.colorIsomorphicPatternsGC.get(indexIsomorphicList);
                    }
                    coloredIso.add(JsonOutput.getJsonColoredGroup(gf, this.dataset, isomorphicListType).toString());
                    this.colorIsomorphicPatternsGC.put(indexIsomorphicList, coloredIso);
                    //colorIsoGroupIndex = colorIsoGF.size();
                    colorIsoGF.add(gf);
                    // colorIsoGroupIndex = colorIsoGF.indexOf(gf);
                    colorIsoGroupIndex = colorIsoGF.size() - 1;
                    logger.debug("\t\tCreating new color isomorphism bag #{}.", colorIsoGroupIndex);
                }

                HashMap<Integer, List<String>> colored = new HashMap<>();
                List<String> coloredList = new ArrayList<>();
                if (this.coloredPatternsGC.containsKey(indexIsomorphicList)) {
                    colored = this.coloredPatternsGC.get(indexIsomorphicList);
                    if (colored.containsKey(colorIsoGroupIndex)) {
                        coloredList = colored.get(colorIsoGroupIndex);
                    }
                }
                //colored.add(JsonOutput.getJsonColored(gf, this.dataset).toString());
                // differs from not GC
                coloredList.add(this.patternsWithSurroundingGC.get(graphNr));
                colored.put(colorIsoGroupIndex, coloredList);
                this.coloredPatternsGC.put(indexIsomorphicList, colored);
            }
            if (isomorphicGraphsTemp.get(0).edgeSet().size() <= MAX_SIZE_FOR_ISO) {
                logger.info(isomorphicGraphsTemp.size() + " x " + isomorphicListType);
                // TODO type
                String json = JsonOutput.getJson(this.connectedGraphsGC.get(isomorphicGraphList.get(0)), isomorphicListType).toString();
                logger.info(json);
                HashMap patternTemp = new HashMap<>();
                patternTemp.put(json, isomorphicGraphList.size());
                patternsGC.put(indexIsomorphicList, patternTemp);
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

    private void groupIsomorphicGraphFeatures() {
        int i = 0;
        for (GraphFeatures connectedSet : this.connectedGraphs) {
            if (connectedSet.getVertexCount() > MAX_SIZE_FOR_ISO) continue;
            logger.debug("\tChecking graph {}/{} {}.", ++i, this.connectedGraphs.size(), connectedSet.getVertexCount());
            int putIntoBag = -1;

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
            List<Integer> isomorphicGraphList = new ArrayList<>();
            int graphIndex = this.connectedGraphs.indexOf(connectedSet);
            isomorphicGraphList.add(graphIndex);

            // if there is already a isomorphism group
            if (putIntoBag >= 0) {
                isomorphicGraphList.addAll(this.isomorphicGraphs.get(putIntoBag));
                this.isomorphicGraphs.remove(putIntoBag);
                this.isomorphicGraphs.add(putIntoBag, isomorphicGraphList);
                logger.debug("\tAdding graph of size {} to isomorphism group bag of size {}.", connectedSet.getVertexCount(), isomorphicGraphList.size()-1);

            } else {
                this.isomorphicGraphs.add(isomorphicGraphList);
                logger.debug("\tCreating new isomorphism bags for graph of size {} and coloring.", connectedSet.getVertexCount());
            }
        }

        groupIsomorphicGraphsByColor();
    }

    private void groupIsomorphicGraphsByColor() {
        Collections.sort(this.isomorphicGraphs, new GraphLODComparator());
        for (List<Integer> isomorphicGraphList : this.isomorphicGraphs) {
            Integer indexIsomorphicList = this.isomorphicGraphs.indexOf(isomorphicGraphList);
            logger.debug("\tChecking color isomorphism for graphs in bag #{}.", indexIsomorphicList);
            if (this.graphRenderer != null) {
                this.graphRenderer.writeDotFile(indexIsomorphicList.toString(), this.connectedGraphs.get(isomorphicGraphList.get(0)), false);
            }
            List<GraphFeatures> isomorphicGraphsTemp = new ArrayList<>();

            List<GraphFeatures> colorIsoGF = new ArrayList();
            Integer colorIsoGroupIndex = -1;
            for (Integer graphNr : isomorphicGraphList) {
                GraphFeatures gf = this.connectedGraphs.get(graphNr);
                logger.debug("\tChecking color isomorphism for graph {}.", graphNr);
                isomorphicGraphsTemp.add(gf);

                boolean createNewColorIsomorphismBag = true;
                for (GraphFeatures coloredGF: colorIsoGF) {
                    Set<String> verticesOfGraph = gf.getSimpleGraph().vertexSet();
                    Set<String> verticesOfGraphToCheck = coloredGF.getSimpleGraph().vertexSet();
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

                    PermutationClassIsomorphismInspector inspector = new PermutationClassIsomorphismInspector(coloredGF.getSimpleGraph(), gf.getSimpleGraph()); // , new VertexDegreeEquivalenceComparator(), null
                    while (inspector.hasNext()) {
                        boolean colorIsomorph = true;
                        IsomorphismRelation graphMapping = inspector.nextIsoRelation();
                        for (String v : gf.getVertices()) {
                            String correspondingVertex = graphMapping.getVertexCorrespondence(v, false).toString();
                            String vClass = dataset.getClassForSubject(v);
                            if (!dataset.getClassForSubject(correspondingVertex).equals(vClass)) {
                                colorIsomorph = false;
                            }
                        }
                        if (colorIsomorph) {
                            createNewColorIsomorphismBag = false;
                            colorIsoGroupIndex = colorIsoGF.indexOf(coloredGF);
                            logger.debug("\t\tAdding to color isomorphism bag #{}.", colorIsoGroupIndex);
                            break;
                        }
                    }
                }
                if (createNewColorIsomorphismBag) {
                    List<String> coloredIso = new ArrayList<>();
                    if (colorIsomorphicPatterns.containsKey(isomorphicGraphs.indexOf(isomorphicGraphList))) {
                        coloredIso = colorIsomorphicPatterns.get(isomorphicGraphs.indexOf(isomorphicGraphList));
                    }
                    coloredIso.add(JsonOutput.getJsonColoredGroup(gf, this.dataset).toString());
                    colorIsomorphicPatterns.put(indexIsomorphicList, coloredIso);
                    colorIsoGF.add(gf);
                    colorIsoGroupIndex = colorIsoGF.size() - 1;
                    logger.debug("\t\tCreating new color isomorphism bag #{}.", colorIsoGroupIndex);
                }

                HashMap<Integer, List<String>> colored = new HashMap<>();
                List<String> coloredList = new ArrayList<>();
                if (this.coloredPatterns.containsKey(indexIsomorphicList)) {
                    colored = this.coloredPatterns.get(indexIsomorphicList);
                    if (colored.containsKey(colorIsoGroupIndex)) {
                        coloredList = colored.get(colorIsoGroupIndex);
                    }
                }
                coloredList.add(JsonOutput.getJsonColored(gf, this.dataset).toString());
                colored.put(colorIsoGroupIndex, coloredList);
                this.coloredPatterns.put(indexIsomorphicList, colored);
            }
            logger.info("Patterns");
                if (isomorphicGraphsTemp.get(0).getEdgeCount() <= MAX_SIZE_FOR_ISO) {
                logger.info(isomorphicGraphsTemp.size() + " x ");
                logger.info(JsonOutput.getJson(this.connectedGraphs.get(isomorphicGraphList.get(0))).toString());
                HashMap patternTemp = new HashMap<>();
                patternTemp.put(JsonOutput.getJson(this.connectedGraphs.get(isomorphicGraphList.get(0))).toString(), isomorphicGraphList.size());
                patterns.put(indexIsomorphicList, patternTemp);
                patternDiameter.put(indexIsomorphicList, this.connectedGraphs.get(isomorphicGraphList.get(0)).getDiameter());

                if (this.graphRenderer != null) {
                    this.graphRenderer.writeDotFiles(indexIsomorphicList.toString() + "_detailed", isomorphicGraphsTemp, true);
                }
            }
        }
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
        return new GraphLOD(name, datasetFiles, true, true, false, false, namespace, ontologyNS, excludedNamespaces, 1, 1, 0, "", 4, true, true);
        /* GraphStatistics graphStats = new GraphStatistics();
        return graphStats;
        */
    }

    public static GraphLOD loadGraph(String name, Collection<String> datasetFiles, String namespace, String ontologyNS, Collection<String> excludedNamespaces) {
        return new GraphLOD(name, datasetFiles, true, true, false, false, namespace, ontologyNS, excludedNamespaces, 1, 1, 0, "", 4, true, false);
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

        new GraphLOD(arguments);
    }

    public class GraphLODComparator implements Comparator<List<?>>{
        public GraphLODComparator() {

        }

        public int compare(List<?>a1,List<?>a2) {
            return a2.size()-a1.size();
        }
    }

}

