package graphlod.dataset;


public interface GraphMLHandler {
    String getLabel(com.tinkerpop.blueprints.Vertex vertex);

    String getClass(com.tinkerpop.blueprints.Vertex vertex);

    String getPropertyName(com.tinkerpop.blueprints.Edge edge);
}
