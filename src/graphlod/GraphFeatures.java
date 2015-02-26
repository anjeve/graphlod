package graphlod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.jgraph.graph.DefaultEdge;
import org.jgrapht.DirectedGraph;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.alg.BiconnectivityInspector;
import org.jgrapht.alg.ChromaticNumber;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.alg.CycleDetector;
import org.jgrapht.alg.DijkstraShortestPath;
import org.jgrapht.alg.FloydWarshallShortestPaths;
import org.jgrapht.alg.StrongConnectivityInspector;
import org.jgrapht.event.EdgeTraversalEvent;
import org.jgrapht.event.TraversalListenerAdapter;
import org.jgrapht.event.VertexTraversalEvent;
import org.jgrapht.experimental.GraphTests;
import org.jgrapht.graph.AsUndirectedGraph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.SimpleGraph;
import org.jgrapht.traverse.DepthFirstIterator;
import org.jgrapht.traverse.GraphIterator;

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
	private List<DefaultEdge> edgesDeletedTemp = new ArrayList<>();
	private List<String> verticesDeletedTemp = new ArrayList<>();
	private Boolean isPathGraph;
	private Boolean isTree;
	private Boolean containsCycle;

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

	public double getDiameterUndirected() {
		FloydWarshallShortestPaths<String, DefaultEdge> fw = new FloydWarshallShortestPaths<>(this.undirectedG);
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
	public List<GraphFeatures> createSubGraphFeatures(Collection<Set<String>> sets) {
		List<GraphFeatures> subgraphFeatures = new ArrayList<>();
		int i = 0;
		for (Set<String> set : sets) {
			DirectedGraph<String, DefaultEdge> subgraph = new DefaultDirectedGraph<>(DefaultEdge.class);
			for (String vertex : set) {
				subgraph.addVertex(vertex);
			}
			for (String vertex : set) {
				Set<DefaultEdge> edges = graph.outgoingEdgesOf(vertex);
				for (DefaultEdge edge : edges) {
					String target = (String) edge.getTarget();
					if (set.contains(target)) {
						subgraph.addEdge(vertex, target, edge);
					}
				}
			}
			subgraphFeatures.add(new GraphFeatures("subgraph" + i, subgraph));
			i++;
		}
		Collections.sort(subgraphFeatures, new Comparator<GraphFeatures>() {
			@Override
			public int compare(GraphFeatures g1, GraphFeatures g2) {
				return Integer.compare(g1.getVertexCount(), g2.getVertexCount());
			}
		});

		return subgraphFeatures;
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

	public boolean containsCycles() {
		CycleDetector<String, DefaultEdge> cycleDetector = new CycleDetector<>(this.graph);
		this.containsCycle = cycleDetector.detectCycles();
		return this.containsCycle;
	}

	public boolean isBipartite() {
		if (GraphTests.isBipartite(this.undirectedG)) {
			return true;
		}
		return false;
	}
	
	public boolean isPathGraph() {
		if (this.isPathGraph == null) {
			double diameter = getDiameterUndirected();
			if (this.getVertexCount() == diameter + 1) {
				this.isPathGraph = true;
			} else {
				this.isPathGraph = false;
			}
		}
		return this.isPathGraph;
	}

	public boolean isDirectedPathGraph() {
		// TODO fix directed in both directions = false atm
		if (!isPathGraph()) {
			return false;
		}
		for (String v : this.vertices) {
			if ( this.graph.inDegreeOf(v) > 1) {
				return false;
			}
		}
		return true;
	}
	
	public boolean isOutboundStarGraph() {
		for (String v : this.vertices) {
			if ((this.graph.inDegreeOf(v) == 0) && (this.graph.outDegreeOf(v) == this.getEdgeCount())) {
				return true;
			}
		}
		return false;
	}

	public boolean isInboundStarGraph() {
		for (String v : this.vertices) {
			if ((this.graph.inDegreeOf(v) == this.getEdgeCount()) && (this.graph.outDegreeOf(v) == 0)) {
				return true;
			}
		}
		return false;
	}

	public boolean isMixedDirectedStarGraph() {
		for (String v : this.undirectedG.vertexSet()) {
			if (this.undirectedG.degreeOf(v) == this.getEdgeCount()) {
				return true;
			}
		}
		return false;
	}

	public boolean isCompleteGraph() {
		if (GraphTests.isComplete(this.undirectedG)) {
			return true;
		}
		return false;
	}

	public boolean isTree() {
		if (this.isTree == null) {
			return isTree(this.undirectedG, true);
		}
		return this.isTree;
	}

	public boolean isTree(UndirectedGraph<String, DefaultEdge> g, boolean overwriteCheck) {
		boolean isTree = GraphTests.isTree(g);
		if (isTree) {
			if (overwriteCheck) {
	    		this.isTree = true;
	    	}
			return true;
		}
		if (overwriteCheck) {
    		this.isTree = false;
    	}
		return false;
	}

	public boolean isCaterpillar() {
		if (!isTree() || isPathGraph()) {
			return false;
		}
	    
		UndirectedGraph<String, DefaultEdge> tempG = new SimpleGraph<>(DefaultEdge.class);

		GraphIterator<String, DefaultEdge> iterator = new DepthFirstIterator<>(this.undirectedG);
		iterator.addTraversalListener(new CaterpillarListener(this.undirectedG, this));
		while (iterator.hasNext()) {
			iterator.next();
        }
		
		for (String v : this.vertices) {
			if (!verticesDeletedTemp.contains(v)) {
				tempG.addVertex(v);
			}
		}
		for (DefaultEdge e : this.edges) {
			if (!edgesDeletedTemp.contains(e)) {
				tempG.addEdge(e.getSource().toString(), e.getTarget().toString());
			}
		}
		if (!isTree(tempG, false)) {
			return false;
		}
        return true;
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
		if (this.indegrees2 == null) {
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
		if (this.outdegrees2 == null) {
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
	
	
	class CaterpillarListener extends TraversalListenerAdapter<String, DefaultEdge> {
		
		String lastSeenVertex;
		DefaultEdge lastSeenEdge;
		
		UndirectedGraph<String, DefaultEdge> g;
//		private boolean newComponent;
//		private String reference;
		private GraphFeatures gF;
		
		public CaterpillarListener(UndirectedGraph<String, DefaultEdge> g, GraphFeatures gF) {
			this.g = g;
			this.gF = gF;
		}
		
		
		@Override
		public void edgeTraversed(EdgeTraversalEvent<String, DefaultEdge> e) {
			lastSeenEdge = e.getEdge();
		}

		@Override
		public void vertexTraversed(VertexTraversalEvent<String> e) {
			String vertex = e.getVertex();
			/*
			if (newComponent) {
				reference = vertex;
				newComponent = false;
			}
			
			int l = DijkstraShortestPath.findPathBetween( g, reference, vertex).size();
			String x = "";
			for (int i=0; i<l; i++) x+= "\t";
			System.out.println( x + "vertex: " + vertex);
			*/
			this.lastSeenVertex = vertex;
		}

		@Override
		public void vertexFinished(VertexTraversalEvent<String> e) {
			String vertex = e.getVertex();
			// System.out.println("finished vertex: " + vertex);
			if (lastSeenVertex.equals(vertex)) {
				gF.addForDeletion(lastSeenVertex, lastSeenEdge);
			}
		}
	}
	
	class TreeDepthFirstIterator extends DepthFirstIterator<String, DefaultEdge> {
		private List<String> alreadySeenVertices = new ArrayList<>();
		private GraphFeatures graphFeature;
		
		public TreeDepthFirstIterator(Graph<String, DefaultEdge> g, GraphFeatures graphFeature) {
			super(g);
			this.graphFeature = graphFeature;
		}
		
		protected void encounterVertexAgain(String vertex,DefaultEdge edge) {
			if (this.alreadySeenVertices.contains(vertex)) {
				this.graphFeature.containsCycle();
			}
			this.alreadySeenVertices.add(vertex);
		}
	}
	
	private void addForDeletion(String v, DefaultEdge e) {
		this.edgesDeletedTemp.add(e);
		this.verticesDeletedTemp.add(v);
	}

	public void containsCycle() {
		this.containsCycle = true;
	}
}