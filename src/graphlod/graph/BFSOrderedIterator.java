package graphlod.graph;

import graphlod.dataset.Dataset;
import org.jgrapht.Graph;

import java.util.ArrayDeque;
import java.util.Deque;


public class BFSOrderedIterator<V, E>
        extends BFSCrossComponentIterator<V, E, Object>
{


    private Deque<V> queue = new ArrayDeque<V>();

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
    public BFSOrderedIterator(Graph<V, E> g, V startVertex, Dataset dataset)
    {
        super(g, startVertex, dataset);
    }

    /**
     * @see BFSCrossComponentIterator#isConnectedComponentExhausted()
     */
    @Override protected boolean isConnectedComponentExhausted()
    {
        return queue.isEmpty();
    }

    /**
     * @see BFSCrossComponentIterator#encounterVertex(Object, Object)
     */
    @Override protected void encounterVertex(V vertex, E edge)
    {
        putSeenData(vertex, null);
        queue.add(vertex);
        System.out.println("vertex: " + vertex.toString());
    }

    /**
     * @see BFSCrossComponentIterator#encounterVertexAgain(Object, Object)
     */
    @Override protected void encounterVertexAgain(V vertex, E edge)
    {
        System.out.println("again: " + vertex.toString());
    }

    protected void encounterVertexAgain2(V vertex, E edge)
    {
        queue.add(vertex);
        putSeenAgainData(vertex, null);
        System.out.println("again2: " + vertex.toString());
    }

    /**
     * @see BFSCrossComponentIterator#provideNextVertex()
     */
    @Override protected V provideNextVertex()
    {
        return queue.removeFirst();
    }
}