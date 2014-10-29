package graphlod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.jgraph.graph.DefaultEdge;
import org.jgrapht.DirectedGraph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.ChromaticNumber;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.alg.DijkstraShortestPath;
import org.jgrapht.alg.FloydWarshallShortestPaths;
import org.jgrapht.alg.StrongConnectivityInspector;
import org.jgrapht.graph.AsUndirectedGraph;

public class GraphFeatures {
	private Dataset dataset;
	private DirectedGraph<String, DefaultEdge> graph;
	private ConnectivityInspector<String, DefaultEdge> c;
	private ArrayList<Integer> indegrees = new ArrayList<Integer>();
	private ArrayList<Integer> outdegrees = new ArrayList<Integer>();
	private Set<String> vertices;
	private AsUndirectedGraph<String, DefaultEdge> undirectedG;
	
	public GraphFeatures(Dataset dataset) {
		this.dataset = dataset;
		this.graph = this.dataset.getGraph();
		this.vertices = this.graph.vertexSet();
	}

	public boolean isConnected() {
		if (this.c == null) { 
			this.c = new ConnectivityInspector<String, DefaultEdge>(this.graph);
		}
		return this.c.isGraphConnected();
	}
	
	public double getDiameter() {
		FloydWarshallShortestPaths<String, DefaultEdge> fw = new FloydWarshallShortestPaths<String, DefaultEdge>(graph);
		return fw.getDiameter();
	}

	public <V, E>GraphPath<String, DefaultEdge> diameterPath(){
	  DijkstraShortestPath<String, DefaultEdge> d;
	  if (!isConnected()) return null;
	  GraphPath<String, DefaultEdge> longestPath=null;
	  for (String v : this.vertices) {
	    for (String u : this.vertices) {
	      if (v != u) {
	        d = new DijkstraShortestPath<String, DefaultEdge>(this.graph, v, u);
	        GraphPath<String, DefaultEdge> currentPath = d.getPath();
	        if (longestPath == null || longestPath.getEdgeList().size() < currentPath.getEdgeList().size()) {
	          longestPath=currentPath;
	        }
	      }
	    }
	  }
	  return longestPath;
	}

	public List<Set<String>> getConnectedSets() {
		isConnected();
		return this.c.connectedSets();
	}
	
	public List<Set<String>> getStronglyConnectedSets() {
		StrongConnectivityInspector<String, DefaultEdge> sci = new StrongConnectivityInspector<String, DefaultEdge>(this.graph);
		return sci.stronglyConnectedSets();
	}

	public List<Integer> getIndegrees() {
		if (this.indegrees.size() == 0) {
			for (Iterator<String> iterator = this.vertices.iterator(); iterator.hasNext();) {
				String vertex = (String) iterator.next();
				this.indegrees.add(this.graph.inDegreeOf(vertex));
			}
		}
		return this.indegrees;
	}

	public List<Integer> getOutdegrees() {
		if (this.outdegrees.size() == 0) {
			for (Iterator<String> iterator = this.vertices.iterator(); iterator.hasNext();) {
				String vertex = (String) iterator.next();
				this.outdegrees.add(this.graph.outDegreeOf(vertex));
			}
		}
		return this.outdegrees;
	}
	
	public ArrayList<Integer> getEdgeCounts() {
		if (this.undirectedG == null) {
			this.undirectedG = new AsUndirectedGraph<String, DefaultEdge>(this.graph);
		}
		ArrayList<Integer> edgeCounts = new ArrayList<Integer>();
		for (Iterator<String> iterator = this.vertices.iterator(); iterator.hasNext();) {
			String vertex = (String) iterator.next();
			edgeCounts.add(graph.edgesOf(vertex).size());
		}
		return edgeCounts;
	}

	public int getChromaticNumber() {
		if (this.undirectedG == null) {
			this.undirectedG = new AsUndirectedGraph<String, DefaultEdge>(this.graph);
		}
		return ChromaticNumber.findGreedyChromaticNumber(this.undirectedG);
	}	
}