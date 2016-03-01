package graphlod.utils;

import org.jgraph.graph.DefaultEdge;
import org.jgrapht.DirectedGraph;
import org.jgrapht.Graphs;
import org.jgrapht.graph.SimpleGraph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GraphUtils {
    public static List<String> getNeighboursOfV(DirectedGraph<String, DefaultEdge> graph, String v) {
        try {
            List<String> neighbourVertexList = Graphs.neighborListOf(graph, v);
            Set<String> hs = new HashSet<>();
            hs.addAll(neighbourVertexList);
            neighbourVertexList.clear();
            neighbourVertexList.addAll(hs);
            return neighbourVertexList;
        } catch (StackOverflowError e) {
            return new ArrayList<>();
        }
    }

    public static List<String> getVerticesWithDegreee(SimpleGraph graph, int degree) {
        List<String> vertices = new ArrayList<>();
        for (Object o : graph.vertexSet()) {
            String v = o.toString();
            if (graph.degreeOf(v) == degree) {
                vertices.add(v);
            }
        }
        return vertices;
    }
}
