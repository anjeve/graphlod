package graphlod.dataset;

import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Vertex;

public class SWTGraphMLHandler implements GraphMLHandler {
    @Override
    public String getLabel(Vertex vertex) {
        return vertex.getProperty("url");
    }

    @Override
    public String getClass(Vertex vertex) {
        return vertex.getProperty("labels");
    }

    @Override
    public String getPropertyName(Edge edge) {
        return edge.getProperty("label_");
    }
}
