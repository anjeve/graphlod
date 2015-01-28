package graphlod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.jgraph.graph.DefaultEdge;
import org.jgrapht.DirectedGraph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.BiconnectivityInspector;
import org.jgrapht.alg.ChromaticNumber;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.alg.DijkstraShortestPath;
import org.jgrapht.alg.FloydWarshallShortestPaths;
import org.jgrapht.alg.StrongConnectivityInspector;
import org.jgrapht.graph.AsUndirectedGraph;
import org.jgrapht.graph.DefaultDirectedGraph;

import com.google.common.base.MoreObjects;

public class GraphFeatures {
	private DirectedGraph<String, DefaultEdge> graph;
	private ConnectivityInspector<String, DefaultEdge> connectivity;
	private List<Integer> indegrees = null;
	private List<Integer> outdegrees = null;
	private List<Degree> indegrees2 = null;
	private List<Degree> outdegrees2 = null;
	private Set<String> vertices;
	private final Set<DefaultEdge> edges;
	private AsUndirectedGraph<String, DefaultEdge> undirectedG;
	private String id;

	public GraphFeatures(String id, DirectedGraph<String, DefaultEdge> graph) {
		this.id = id;
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
			return Collections.emptyList();
		}
		List<GraphFeatures> connectedSubgraphFeatures = new ArrayList<>();
		int i = 0;
		for (Set<String> set : sets) {
			DirectedGraph<String, DefaultEdge> subgraph = new DefaultDirectedGraph<>(DefaultEdge.class);
			for (String vertex : set) {
				subgraph.addVertex(vertex);
			}
			for (String vertex : set) {
				Set<DefaultEdge> edges = graph.outgoingEdgesOf(vertex);
				for (DefaultEdge edge : edges) {
					subgraph.addEdge(vertex, (String) edge.getTarget(), edge);
				}
			}
			connectedSubgraphFeatures.add(new GraphFeatures("subgraph" + i, subgraph));
			i++;
		}
		Collections.sort(connectedSubgraphFeatures, new Comparator<GraphFeatures>() {
			@Override
			public int compare(GraphFeatures g1, GraphFeatures g2) {
				return Integer.compare(g1.getVertexCount(), g2.getVertexCount());
			}
		});

		return connectedSubgraphFeatures;
	}

	public List<Set<String>> getStronglyConnectedSets() {
		StrongConnectivityInspector<String, DefaultEdge> sci = new StrongConnectivityInspector<>(this.graph);
		return sci.stronglyConnectedSets();
	}

	public Set<Set<String>> getBiConnectedSets() {
		if (!isConnected()) {
			return null;
		}
		BiconnectivityInspector<String, DefaultEdge> bici = new BiconnectivityInspector<>(this.undirectedG);
		return bici.getBiconnectedVertexComponents();
	}

	public List<Integer> getIndegrees() {
		if (this.indegrees == null) {
			this.indegrees = new ArrayList<>();
			this.indegrees2 = new ArrayList<>();
			for (String vertex : this.vertices) {
				int d = this.graph.inDegreeOf(vertex);
				this.indegrees.add(d);
				this.indegrees2.add(new Degree(vertex, d));
			}
		}
		return this.indegrees;
	}

	public List<Degree> getIndegrees2() {
		if(this.indegrees2 == null) {
			getIndegrees();
		}
		return this.indegrees2;
	}

	public List<Integer> getOutdegrees() {
		if (this.outdegrees == null) {
			this.outdegrees = new ArrayList<>();
			this.outdegrees2 = new ArrayList<>();
			for (String vertex : this.vertices) {
				int d = this.graph.outDegreeOf(vertex);
				this.outdegrees.add(d);
				this.outdegrees2.add(new Degree(vertex, d));
			}
		}
		return this.outdegrees;
	}


	public List<Degree> getOutdegrees2() {
		if(this.outdegrees2 == null) {
			getOutdegrees();
		}
		return this.outdegrees2;
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

	public Set<String> getVertices() {
		return vertices;
	}

	public String getId() {
		return id;
	}

	public Set<DefaultEdge> getEdges() {
		return edges;
	}

	static class Degree implements Comparable<Degree> {
		public String vertex;
		public int degree;

		public Degree(String vertex, int degree) {
			this.vertex = vertex;
			this.degree = degree;
		}

		@Override
		public int compareTo(Degree other) {
			return Integer.compare(degree, other.degree);
		}

		@Override
		public String toString() {
			return MoreObjects.toStringHelper(this).add("vertex", vertex).add("degree", degree).toString();
		}
	}

	public List<Degree> maxOutDegrees(int count) {
		if (outdegrees2 == null) {
			getOutdegrees();
		}
		return CollectionUtils.maxValues(outdegrees2, count);
	}

	public List<Degree> maxInDegrees(int count) {
		if (indegrees2 == null) {
			getIndegrees();
		}
		return CollectionUtils.maxValues(indegrees2, count);
	}
}