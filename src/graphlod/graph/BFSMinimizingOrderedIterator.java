package graphlod.graph;

import graphlod.dataset.Dataset;
import org.jgraph.graph.DefaultEdge;
import org.jgrapht.Graph;
import org.jgrapht.graph.SimpleGraph;

import java.util.ArrayDeque;
import java.util.Deque;


public class BFSMinimizingOrderedIterator<V, E>
        extends BFSMinimizingCrossComponentIterator<V, E, Object>
{

    private Deque<V> queue = new ArrayDeque<V>();
    private SimpleGraph<String, DefaultEdge> minimizedGraph;


    /**
     * Creates a new breadth-first iterator for the specified graph.
     *
     * @param g the graph to be iterated.
     */
    public BFSMinimizingOrderedIterator(Graph<V, E> g, Dataset dataset)
    {
        this(g, null, dataset);
    }

    /**
     * Creates a new breadth-first iterator for the specified graph. Iteration
     * will start at the specified start vertex and will be limited to the
     * connected component that includes that vertex. If the specified start
     * vertex is <code>null</code>, iteration will start at an arbitrary vertex
     * and will not be limited, that is, will be able to traverse all the graph.
     *
     * @param g the graph to be iterated.
     * @param startVertex the vertex iteration to be started.
     */
    public BFSMinimizingOrderedIterator(Graph<V, E> g, V startVertex, Dataset dataset)
    {
        super(g, startVertex, dataset);
    }



    /**
     * @see BFSMinimizingCrossComponentIterator#isConnectedComponentExhausted()
     */
    @Override protected boolean isConnectedComponentExhausted()
    {
        return queue.isEmpty();
    }

    /**
     * @see BFSMinimizingCrossComponentIterator#encounterVertex(Object, Object)
     */
    @Override protected void encounterVertex(V vertex, E edge)
    {
        putSeenData(vertex, null);
        queue.add(vertex);

        if (edge == null) {
            this.minimizedGraph = new SimpleGraph<>(DefaultEdge.class);
            this.minimizedGraph.addVertex(vertex.toString());
        } else {
            this.minimizedGraph.addVertex(vertex.toString());
            this.minimizedGraph.addEdge(graph.getEdgeSource(edge).toString(), graph.getEdgeTarget(edge).toString());
        }
    }

    /**
     * @see BFSMinimizingCrossComponentIterator#encounterVertexAgain(Object, Object)
     */
    @Override protected void encounterVertexAgain(V vertex, E edge)
    {
    }

    /**
     * @see BFSMinimizingCrossComponentIterator#provideNextVertex()
     */
    @Override protected V provideNextVertex()
    {
        return queue.removeFirst();
    }

    public SimpleGraph<String,DefaultEdge> getMinimizedGraph() {
        return minimizedGraph;
    }
}