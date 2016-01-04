package graphlod.utils;

import org.jgraph.graph.DefaultEdge;
import org.jgrapht.DirectedGraph;
import org.jgrapht.Graphs;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GraphUtils {
    public static List<String> getNeighboursOfV(DirectedGraph<String, DefaultEdge> graph, String v) {
        List<String> neighbourVertexList = Graphs.neighborListOf(graph, v);
        Set<String> hs = new HashSet<>();
        hs.addAll(neighbourVertexList);
        neighbourVertexList.clear();
        neighbourVertexList.addAll(hs);
        return neighbourVertexList;
    }


}
