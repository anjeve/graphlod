package graphlod.dataset;

import java.util.HashMap;
import java.util.Map;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Collections2;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Vertex;

public class SWTGraphMLHandler implements GraphMLHandler {

    Map<String, String> vertexMapping = new HashMap<>();

    @Override
    public String getVertex(Vertex vertex) {
        String id = vertex.getId().toString();
        String label = getLabel(vertex);
        vertexMapping.put(id, label);
        return label;
    }

    @Override
    public String getLabel(Vertex vertex) {
        String label = vertex.getProperty("url");
        return label == null ? vertex.getId().toString() : label;
    }

    @Override
    public String getClass(Vertex vertex) {
        return vertex.getProperty("labels");
    }

    @Override
    public String getSubject(Edge edge) {
        String subjectId = edge.getVertex(Direction.OUT).getId().toString();
        return vertexMapping.get(subjectId);
    }

    @Override
    public String getObject(Edge edge) {
        String objectId = edge.getVertex(Direction.IN).getId().toString();
        return vertexMapping.get(objectId);
    }

    @Override
    public String getProperty(Edge edge) {
        return edge.getProperty("label_");
    }
}
