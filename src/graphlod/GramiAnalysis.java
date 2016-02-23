package graphlod;

import graphlod.dataset.Dataset;

import java.util.BitSet;
import java.util.List;
import java.util.Map;

import org.jgraph.graph.DefaultEdge;

import com.google.common.base.MoreObjects;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.grami.undirected_patterns.dataStructures.Graph;
import com.grami.undirected_patterns.dataStructures.HPListGraph;
import com.grami.undirected_patterns.search.Searcher;

public class GramiAnalysis {

    int shortestDistance = 1;
    int frequencyThreshold = 1000;

    public List<GraphPattern> run(Dataset dataset) {
        List<Graph.Node> nodes = Lists.newArrayList();
        BiMap<String, Integer> nodeMap = HashBiMap.create();
        Map<String, Integer> classMap = createClassMap(dataset);
        int i = 0;
        for (String vertex : dataset.getGraph().vertexSet()) {
            int label = classMap.get(dataset.getClassForSubject(vertex));
            nodes.add(new Graph.Node(i, label));
            nodeMap.put(vertex, i++);
        }
        List<Graph.Edge> edges = createEdges(dataset, nodeMap);

        Graph graph = new Graph(1, frequencyThreshold);

        Searcher<String, String> sr;
        try {
            graph.loadFromData(nodes, edges);
            sr = new Searcher<>(graph, frequencyThreshold, shortestDistance);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        sr.initialize();
        sr.search();

        List<GraphPattern> pattern = Lists.newArrayList();
        for (HPListGraph<String, String> srGraph : sr.result) {
            pattern.add(getGraphPattern(srGraph, nodeMap.inverse()));
        }

        return pattern;
    }

    private Map<String, Integer> createClassMap(Dataset dataset) {
        Map<String, Integer> classMap;
        classMap = Maps.newHashMap();
        classMap.put("null", 0);
        int i = 1;
        for (String s : dataset.getOntologyClasses()) {
            classMap.put(s, i);
        }
        return classMap;
    }

    private List<Graph.Edge> createEdges(Dataset dataset, BiMap<String, Integer> nodeMap) {
        List<Graph.Edge> edges;
        edges = Lists.newArrayList();
        for (DefaultEdge edge : dataset.getGraph().edgeSet()) {
            String source = edge.getSource().toString();
            String target = edge.getTarget().toString();
            double label = 1;
            edges.add(new Graph.Edge(nodeMap.get(source), nodeMap.get(target), label));
        }
        return edges;
    }

    private GraphPattern getGraphPattern(HPListGraph<String, String> graph, Map<Integer, String> nodeMap) {
        List<String> pNodes = Lists.newArrayList();
        Map<String, String> pEdges = Maps.newHashMap();

        BitSet nodes = graph.getNodes();
        BitSet edges = graph.getEdges();
        for (int nodeIdx = nodes.nextSetBit(0); nodeIdx >= 0; nodeIdx = nodes.nextSetBit(nodeIdx + 1)) {
            String label = nodeMap.get(nodeIdx);
            pNodes.add(label);
        }

        for (int edgeIdx = edges.nextSetBit(0); edgeIdx >= 0; edgeIdx = edges.nextSetBit(edgeIdx + 1)) {
            String node1 = nodeMap.get(graph.getNodeA(edgeIdx));
            String node2 = nodeMap.get(graph.getNodeB(edgeIdx));

            if (graph.getDirection(edgeIdx) >= 0) {
                pEdges.put(node1, node2);
            } else {
                pEdges.put(node2, node1);
            }
        }
        return new GraphPattern(pNodes, pEdges);
    }

    public static class GraphPattern {
        public List<String> nodes;
        public Map<String, String> edges;

        public GraphPattern(List<String> nodes, Map<String, String> edges) {
            this.nodes = nodes;
            this.edges = edges;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("nodes", nodes)
                    .add("edges", edges)
                    .toString();
        }
    }
}
