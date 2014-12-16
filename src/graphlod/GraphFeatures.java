package graphlod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.jgraph.graph.DefaultEdge;
import org.jgrapht.DirectedGraph;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.BiconnectivityInspector;
import org.jgrapht.alg.ChromaticNumber;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.alg.DijkstraShortestPath;
import org.jgrapht.alg.FloydWarshallShortestPaths;
import org.jgrapht.alg.StrongConnectivityInspector;
import org.jgrapht.graph.AsUndirectedGraph;
import org.jgrapht.graph.DefaultDirectedGraph;

public class GraphFeatures {
	private DirectedGraph<String, DefaultEdge> graph;
	private ConnectivityInspector<String, DefaultEdge> connectivity;
	private ArrayList<Integer> indegrees = new ArrayList<>();
	private ArrayList<Integer> outdegrees = new ArrayList<>();
	private Set<String> vertices;
	private final Set<DefaultEdge> edges;
	private AsUndirectedGraph<String, DefaultEdge> undirectedG;

	public GraphFeatures(DirectedGraph<String, DefaultEdge> graph) {
		this.graph = graph;
		this.vertices = this.graph.vertexSet();
		this.edges = this.graph.edgeSet();
		this.connectivity = new ConnectivityInspector<>(this.graph);
		this.undirectedG = new AsUndirectedGraph<>(this.graph);
	}

	public boolean isConnected() {
		return this.connectivity.isGraphConnected();
	}

	public double getDiameter() {
		FloydWarshallShortestPaths<String, DefaultEdge> fw = new FloydWarshallShortestPaths<>(graph);
		return fw.getDiameter();
	}

	public <V, E> GraphPath<String, DefaultEdge> diameterPath() {
		DijkstraShortestPath<String, DefaultEdge> d;
		if (!isConnected()) return null;
		GraphPath<String, DefaultEdge> longestPath = null;
		for (String v : this.vertices) {
			for (String u : this.vertices) {
				if (v != u) {
					d = new DijkstraShortestPath<>(this.graph, v, u);
					GraphPath<String, DefaultEdge> currentPath = d.getPath();
					if (currentPath != null && (longestPath == null || longestPath.getEdgeList().size() < currentPath.getEdgeList().size())) {
						longestPath = currentPath;
					}
				}
			}
		}
		return longestPath;
	}

	public List<Set<String>> getConnectedSets() {
		return this.connectivity.connectedSets();
	}

	/**
	 * Creates a new graph for each connected component and adds each to a new GraphFeature instance.
	 */
	public List<GraphFeatures> getConnectedSubGraphFeatures() {
		List<Set<String>> sets = this.connectivity.connectedSets();
		if (sets.size() <= 1) {
			return Arrays.asList(this);
		}
		List<GraphFeatures> connectedSubgraphFeatures = new ArrayList<>();

		for (Set<String> set : sets) {
			DirectedGraph<String, DefaultEdge> subgraph = new DefaultDirectedGraph<>(DefaultEdge.class);
			for (String vertex : set) {
				subgraph.addVertex(vertex);
			}
			for (String vertex: set) {
				Set<DefaultEdge> edges = graph.outgoingEdgesOf(vertex);
				for (DefaultEdge edge : edges) {
					subgraph.addEdge(vertex, (String)edge.getTarget(), edge);
				}
			}
			connectedSubgraphFeatures.add(new GraphFeatures(subgraph));
		}
		return connectedSubgraphFeatures;
	}

	public List<Set<String>> getStronglyConnectedSets() {
		StrongConnectivityInspector<String, DefaultEdge> sci = new StrongConnectivityInspector<>(this.graph);
		return sci.stronglyConnectedSets();
	}

	public Set<Set<String>> getBiConnectedSets() {
		if(!isConnected()) {
			return null;
		}
		BiconnectivityInspector<String, DefaultEdge> bici = new BiconnectivityInspector<>(this.undirectedG);
		return bici.getBiconnectedVertexComponents();
	}

	public List<Integer> getIndegrees() {
		if (this.indegrees.size() == 0) {
			for (String vertex : this.vertices) {
				this.indegrees.add(this.graph.inDegreeOf(vertex));
			}
		}
		return this.indegrees;
	}

	public List<Integer> getOutdegrees() {
		if (this.outdegrees.size() == 0) {
			for (String vertex : this.vertices) {
				this.outdegrees.add(this.graph.outDegreeOf(vertex));
			}
		}
		return this.outdegrees;
	}

	public ArrayList<Integer> getEdgeCounts() {
		ArrayList<Integer> edgeCounts = new ArrayList<>();
		for (String vertex : this.vertices) {
			edgeCounts.add(graph.edgesOf(vertex).size());
		}
		return edgeCounts;
	}

	public int getVertexCount() {
		return this.vertices.size();
	}

	public int getEdgeCount() {
		return this.edges.size();
	}

	public int getChromaticNumber() {
		return ChromaticNumber.findGreedyChromaticNumber(this.undirectedG);
	}
}