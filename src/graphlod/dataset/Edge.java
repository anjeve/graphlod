package graphlod.dataset;

import org.jgrapht.graph.DefaultEdge;

/**
 * Extends DefaultEdge to add a label to the graph. The label is the
 * relationId that relates the entities the edge connects.
 */
public class Edge extends DefaultEdge {
    private String uri;

    public Edge() {
        super();
    }

    public Edge(String uri) {
        this();
        setUri(uri);
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }
}