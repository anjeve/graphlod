package graphlod.dataset;


public interface GraphMLHandler {

    String getVertex(com.tinkerpop.blueprints.Vertex vertex);

    String getLabel(com.tinkerpop.blueprints.Vertex vertex);

    String getClass(com.tinkerpop.blueprints.Vertex vertex);

    String getSubject(com.tinkerpop.blueprints.Edge edge);
    String getProperty(com.tinkerpop.blueprints.Edge edge);
    String getObject(com.tinkerpop.blueprints.Edge edge);

}
