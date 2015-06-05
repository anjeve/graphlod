package graphlod.algorithms;

import graphlod.CollectionUtils;
import graphlod.dataset.Dataset;
import graphlod.graph.Degree;
import org.jgraph.graph.DefaultEdge;
import org.jgrapht.DirectedGraph;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.alg.*;
import org.jgrapht.event.EdgeTraversalEvent;
import org.jgrapht.event.TraversalListenerAdapter;
import org.jgrapht.event.VertexTraversalEvent;
import org.jgrapht.experimental.GraphTests;
import org.jgrapht.graph.AsUndirectedGraph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.SimpleGraph;
import org.jgrapht.traverse.DepthFirstIterator;
import org.jgrapht.traverse.GraphIterator;

import java.util.*;

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
	private SimpleGraph<String, DefaultEdge> simpleGraph;

	public SimpleGraph<String, DefaultEdge> getSimpleGraph() {
		return simpleGraph;
	}

	public GraphFeatures(String id, DirectedGraph<String, DefaultEdge> graph, SimpleGraph<String, DefaultEdge> simpleGraph) {
		this.id = id;
		this.graph = graph;
		this.simpleGraph = simpleGraph;
		this.vertices = this.graph.vertexSet();
		this.edges = this.graph.edgeSet();
		this.connectivity = new ConnectivityInspector<>(this.graph);
		this.undirectedG = new AsUndirectedGraph<>(this.graph);
	}

	public Integer getHashCode() {
		return this.simpleGraph.hashCode();
	}
	
	public boolean isConnected() {
		return this.connectivity.isGraphConnected();
	}

	public double getDiameter() {
		FloydWarshallShortestPaths<String, DefaultEdge> fw = new FloydWarshallShortestPaths<>(graph);
		return fw.getDiameter();
	}

	public double getDiameterUndirected() {
		FloydWarshallShortestPaths<String, DefaultEdge> fw = new FloydWarshallShortestPaths<>(this.simpleGraph);
		return fw.getDiameter();
	}

	public double getDiameterUndirected(Graph<String, DefaultEdge> graph) {
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
	public List<GraphFeatures> createSubGraphFeatures(Collection<Set<String>> sets) {
		List<GraphFeatures> subgraphFeatures = new ArrayList<>();
		int i = 0;
		for (Set<String> set : sets) {
			DirectedGraph<String, DefaultEdge> subgraph = new DefaultDirectedGraph<>(DefaultEdge.class);
			SimpleGraph<String, DefaultEdge> simpleSubgraph = new SimpleGraph<>(DefaultEdge.class);
			for (String vertex : set) {
				subgraph.addVertex(vertex);
				simpleSubgraph.addVertex(vertex);
			}
			for (String vertex : set) {
				Set<DefaultEdge> edges = graph.outgoingEdgesOf(vertex);
				for (DefaultEdge edge : edges) {
					String target = (String) edge.getTarget();
					if (set.contains(target)) {
						subgraph.addEdge(vertex, target, edge);
						simpleSubgraph.addEdge(vertex, target, edge);
					}
				}
			}
			subgraphFeatures.add(new GraphFeatures("subgraph" + i, subgraph, simpleSubgraph));
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
		return GraphTests.isBipartite(this.undirectedG);
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

	public boolean isPath(Graph<String, DefaultEdge> graph) {
		double diameter = getDiameterUndirected(graph);
		if (graph.vertexSet().size() == diameter + 1) {
			return true;
		}
		return false;
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
		for (String v : this.simpleGraph.vertexSet()) {
			if (this.simpleGraph.degreeOf(v) == this.getEdgeCount()) {
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
			return isTree(this.simpleGraph, true);
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

		GraphIterator<String, DefaultEdge> iterator = new DepthFirstIterator<>(this.simpleGraph);
		iterator.addTraversalListener(new CaterpillarListener(this.simpleGraph, this));
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
		if (!isPath(tempG)) {
			return false;
		}
        return true;
	}

	public boolean isCaterpillar(SimpleGraph<String, DefaultEdge> graph) {
		UndirectedGraph<String, DefaultEdge> tempG = new SimpleGraph<>(DefaultEdge.class);

		GraphIterator<String, DefaultEdge> iterator = new DepthFirstIterator<>(graph);
		iterator.addTraversalListener(new CaterpillarListener(graph, this));
		while (iterator.hasNext()) {
			iterator.next();
        }
		
		for (String v : graph.vertexSet()) {
			if (!verticesDeletedTemp.contains(v)) {
				tempG.addVertex(v);
			}
		}
		for (DefaultEdge e : graph.edgeSet()) {
			if (!edgesDeletedTemp.contains(e)) {
				if ((e.getSource() != null) && (e.getTarget() != null)) {
					tempG.addEdge(e.getSource().toString(), e.getTarget().toString());
				}
			}
		}
		if (!isPath(tempG)) {
			return false;
		}
        return true;
	}
	
	public boolean isLobster() {
		if (!isTree() || isPathGraph()) {
			return false;
		}
	    
		SimpleGraph<String, DefaultEdge> tempG = new SimpleGraph<>(DefaultEdge.class);

		GraphIterator<String, DefaultEdge> iterator = new DepthFirstIterator<>(this.simpleGraph);
		iterator.addTraversalListener(new CaterpillarListener(this.simpleGraph, this));
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
		// this check was isTree but should be isPath
		if (!isCaterpillar(tempG)) {
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

    public double getAverageIndegree() {
        if (this.indegrees == null) {
            getIndegrees();
        }
        return CollectionUtils.average(this.indegrees);
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
		return ChromaticNumber.findGreedyChromaticNumber(this.simpleGraph);
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

    public int getMaxIndegree() {
        if (this.indegrees == null) {
            getIndegrees();
        }
        return CollectionUtils.max(this.indegrees);
    }

    public int getMinIndegree() {
        if (this.indegrees == null) {
            getIndegrees();
        }
        return CollectionUtils.min(this.indegrees);
    }

    public boolean checkColorIsomorphism(GraphFeatures target) {
        SimpleGraph<String, DefaultEdge> targetSimpleGraph = target.getSimpleGraph();
        for (String vertex : this.simpleGraph.vertexSet()) {
            String classUri = Dataset.getClass(vertex);
            List<String> linkedVerticesClassUris = new ArrayList<>();
            for (DefaultEdge edge : this.simpleGraph.edgesOf(vertex)) {
                String linkedVertex = edge.getTarget().toString();
                if (linkedVertex.equals(vertex)) {
                    linkedVertex = edge.getSource().toString();
                }
                linkedVerticesClassUris.add(Dataset.getClass(linkedVertex));
            }
            boolean foundCurrentVertexEquivalent = false;
            for (String targetVertex : targetSimpleGraph.vertexSet()) {
                String targetClassUri = Dataset.getClass(vertex);
                if (!classUri.equals(targetClassUri)) continue;
                List<String> targetLinkedVerticesClassUris = new ArrayList<>();
                for (DefaultEdge edge : targetSimpleGraph.edgesOf(targetVertex)) {
                    String linkedVertex = edge.getTarget().toString();
                    if (linkedVertex.equals(targetVertex)) {
                        linkedVertex = edge.getSource().toString();
                    }
                    targetLinkedVerticesClassUris.add(Dataset.getClass(linkedVertex));
                }
                if (!linkedVerticesClassUris.equals(targetLinkedVerticesClassUris)) return false;
            }
            if (!foundCurrentVertexEquivalent) return false;
        }
        return true;
    }

	public HashMap<Integer, Integer> getDegreeDistribution() {
		HashMap<Integer, Integer> degreeCounts = new HashMap<>();
		for (String vertex : this.graph.vertexSet()) {
			Set<DefaultEdge> edges = this.graph.edgesOf(vertex);
			if (degreeCounts.containsKey(edges.size())) {
				int oldDegreeCount = degreeCounts.get(edges.size());
				degreeCounts.put(edges.size(), oldDegreeCount+1);
			} else {
				degreeCounts.put(edges.size(), 1);
			}
		}
		return degreeCounts;
	}

	class CaterpillarListener extends TraversalListenerAdapter<String, DefaultEdge> {
		private String lastSeenVertex;
		private DefaultEdge lastSeenEdge;
		private UndirectedGraph<String, DefaultEdge> g;
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
			this.lastSeenVertex = vertex;
		}

		@Override
		public void vertexFinished(VertexTraversalEvent<String> e) {
			String vertex = e.getVertex();
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