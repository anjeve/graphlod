package graphlod;

import graphlod.algorithms.GraphFeatures;
import graphlod.graph.Edge;
import graphlod.output.JsonOutput;
import org.jgraph.graph.DefaultEdge;
import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.SimpleGraph;

import java.util.*;

public class EdgeSimilarity {
    private List<List<GraphFeatures>> similarityBags = new ArrayList<>();
    public List<List<String>> similarityLists = new ArrayList<>();
    public List<String> similarityPaths = new ArrayList<>();
    public HashMap<Integer, HashMap<Edge, Integer>> differenceToFirstElement = new HashMap<>();

    public EdgeSimilarity(ArgumentParser arguments) {
        new EdgeSimilarity(arguments.getName(), arguments.getDataset(), arguments.getNamespace(), arguments.getOntns(), arguments.getExcludedNamespaces());
    }

    public EdgeSimilarity(String name, Collection<String> datasetFiles, String namespace, String ontologyNS, Collection<String> excludedNamespaces) {
        GraphLOD graphLod = GraphLOD.loadGraph(name, datasetFiles, namespace, ontologyNS, excludedNamespaces);
        GraphFeatures graphFeatures = graphLod.graphFeatures;
        List<GraphFeatures> connectedGraphs = new ArrayList<>();
        if (graphFeatures.isConnected()) {
            connectedGraphs.add(graphFeatures);
        } else {
            connectedGraphs = graphFeatures.createSubGraphFeatures(graphFeatures.getConnectedSets());
        }

        for (GraphFeatures connectedComponent : connectedGraphs) {
            if (connectedComponent.getVertexCount() > 200) continue;
            if (connectedComponent.getVertexCount() == 1) continue;
            if (similarityBags.isEmpty()) {
                List<GraphFeatures> list = new ArrayList<>();
                list.add(connectedComponent);
                similarityBags.add(list);
                continue;
            }
            // 1. check for size

            ListIterator<List<GraphFeatures>> iter = similarityBags.listIterator();
            boolean putIntoBag = false;
            while(iter.hasNext()) {
                List<GraphFeatures> similarityBag = iter.next();
                // repeat for all ?
                // TODO find "centroid" element

                DirectedGraph<String, DefaultEdge> graph = connectedComponent.getGraph();
                DirectedGraph<String, DefaultEdge> graphFromBag = similarityBag.get(0).getGraph();

                // 2. check for vertices
                HashMap<Edge, Integer> edges1 = new HashMap<>();
                HashMap<Edge, Integer> edges2 = new HashMap<>();
                for (DefaultEdge edge : graph.edgeSet()) {
                    Edge edgeClasses = new Edge(graphLod.dataset.getClassForSubject(edge.getSource().toString()), graphLod.dataset.getClassForSubject(edge.getTarget().toString()));
                    Integer count = 1;
                    if (edges1.containsKey(edgeClasses)) {
                        count = edges1.get(edgeClasses) + count;
                    }
                    edges1.put(edgeClasses, count);
                }
                for (DefaultEdge edge : graphFromBag.edgeSet()) {
                    Edge edgeClasses = new Edge(graphLod.dataset.getClassForSubject(edge.getSource().toString()), graphLod.dataset.getClassForSubject(edge.getTarget().toString()));
                    Integer count = 1;
                    if (edges2.containsKey(edgeClasses)) {
                        count = edges2.get(edgeClasses) + count;
                    }
                    edges2.put(edgeClasses, count);
                }

                if (sameEdges(new ArrayList<>(edges1.keySet()), new ArrayList<>(edges2.keySet()))) {
                    iter.remove();
                    similarityBag.add(connectedComponent);
                    //similarityBags.add(list);
                    iter.add(similarityBag);
                    putIntoBag = true;
                    if (!same(new ArrayList<>(edges1.values()), new ArrayList<>(edges2.values()))) {
                        for(Map.Entry<Edge, Integer> entry : edges1.entrySet()) {
                            Edge edge = entry.getKey();
                            Integer count1 = entry.getValue();
                            Integer count2 = edges2.get(edge);
                            if (count1 != count2) {
                                // TODO index
                                int indexOfBag = 0;
                                HashMap<Edge, Integer> map = new HashMap<>();
                                if (differenceToFirstElement.containsKey(indexOfBag)) {
                                     map = differenceToFirstElement.get(indexOfBag);
                                }
                                map.put(edge, Math.abs(count1 - count2));
                                differenceToFirstElement.put(indexOfBag, map);
                            }
                        }
                    }
                    continue;
                }
                // TODO make this configurable

                /*
                int maxVertexDiff = 1;
                int vertexDiff = simpleGraph.vertexSet().size() - simpleGraphFromBag.vertexSet().size();

                if (Math.abs(vertexDiff) <= maxVertexDiff) {

                } else {
                    // TODO difference too high
                }
                */
            }
            if (!putIntoBag) {
                List<GraphFeatures> list = new ArrayList<>();
                list.add(connectedComponent);
                similarityBags.add(list);
            }
        }

        System.out.println(similarityBags.size());
        for (List<GraphFeatures> similarityBag : similarityBags) {
            Set<Edge> edges = new HashSet<>();
            for (DefaultEdge edge : similarityBag.get(0).getSimpleGraph().edgeSet()) {
                edges.add(new Edge(graphLod.dataset.getClassForSubject(edge.getSource().toString()), graphLod.dataset.getClassForSubject(edge.getTarget().toString())));
            }

        }
        for (List<GraphFeatures> similarityBag : similarityBags) {
            List<String> jsonList = new ArrayList<>();
            for (GraphFeatures graph: similarityBag) {
                jsonList.add(JsonOutput.getJsonColored(graph, graphLod.dataset).toString().replaceAll("\\\\/", "/"));
            }
            this.similarityLists.add(jsonList);

            SimpleGraph<String, DefaultEdge> simpleGraph = new SimpleGraph<>(DefaultEdge.class);
            Set<Edge> edges1 = new HashSet<>();
            Integer vertexCount = 0;
            Integer vertexCount1 = 1;
            HashMap<String, String> classes = new HashMap();
            for (DefaultEdge edge : similarityBag.get(0).getGraph().edgeSet()) {
                Edge edgeClasses = new Edge(graphLod.dataset.getClassForSubject(edge.getSource().toString()), graphLod.dataset.getClassForSubject(edge.getTarget().toString()));
                if (!edges1.contains(edgeClasses)) {
                    edges1.add(edgeClasses);
                    simpleGraph.addVertex(vertexCount.toString());
                    simpleGraph.addVertex(vertexCount1.toString());
                    DefaultEdge e = new DefaultEdge("");
                    e.setSource(vertexCount.toString());
                    e.setTarget(vertexCount1.toString());
                    simpleGraph.addEdge(vertexCount.toString(), vertexCount1.toString().toString(), e);
                    System.out.print(edgeClasses);
                    classes.put(vertexCount.toString(), edgeClasses.sourceClass);
                    classes.put(vertexCount1.toString(), edgeClasses.targetClass);
                    vertexCount += 2;
                    vertexCount1 += 2;
                }
            }
            /*
            if (similarityBag.get(0).getGraph().edgeSet().size() == 0) {
                Set<String> vertexSet = similarityBag.get(0).getGraph().vertexSet();
                String vertex = vertexSet.iterator().next().toString();
                simpleGraph.addVertex(vertex);
                classes.put(vertex, graphLod.dataset.getClassForSubject(vertex));
            }
            */
            if (simpleGraph.edgeSet().size() > 0) {
                this.similarityPaths.add(JsonOutput.getJsonColored(simpleGraph, graphLod.dataset, classes).toString().replaceAll("\\\\/", "/"));
                System.out.println("");
            }

        }
    }

    private boolean sameEdges(List<Edge> set1, List<Edge> set2){
        if (set1.size() != set2.size())
            return false;
        List<Edge> symmetricDiff = new ArrayList<>(set1);
        symmetricDiff.addAll(set2);
        List<Edge> tmp = new ArrayList<>(set1);
        tmp.retainAll(set2);
        symmetricDiff.removeAll(tmp);
        if (symmetricDiff.size() > 0 ) return false;
        return true;
    }

    private boolean same(List<Integer> set1, List<Integer> set2){
        if (set1.size() != set2.size())
            return false;
        List<Integer> symmetricDiff = new ArrayList<>(set1);
        symmetricDiff.addAll(set2);
        List<Integer> tmp = new ArrayList<>(set1);
        tmp.retainAll(set2);
        symmetricDiff.removeAll(tmp);
        if (symmetricDiff.size() > 0 ) return false;
        return true;
    }

    public static void main(final String[] args) {
        ArgumentParser arguments = new ArgumentParser(args, 3000);
        Locale.setDefault(Locale.US);
        new EdgeSimilarity(arguments);
    }


}
