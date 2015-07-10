package graphlod.output;

import graphlod.algorithms.GraphFeatures;
import graphlod.dataset.Dataset;
import org.apache.log4j.Logger;
import org.jgraph.graph.DefaultEdge;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Set;

public class JsonOutput {
    private static final Logger logger = Logger.getLogger(JsonOutput.class);

    private final Dataset dataset;

    public JsonOutput(Dataset dataset) {
        this.dataset = dataset;
    }

    public void write(String output) {
        JSONObject obj = getJsonObject(this.dataset.getGraph().vertexSet(), this.dataset.getGraph().edgeSet(), true, this.dataset, null);

        try {
            FileWriter file = new FileWriter(output + this.dataset.getName() + ".json");
            file.write(obj.toJSONString());
            logger.debug("Successfully Copied JSON Object to File...");
            // logger.debug("\nJSON Object: " + obj);
            file.flush();
            file.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static JSONObject getJsonObject(Set<String> vertices, Set<DefaultEdge> edges, boolean addClass, Dataset dataset, String type) {
        JSONObject obj = new JSONObject();

        JSONArray jsonNodes = new JSONArray();
        int id = 1;
        HashMap<String, Integer> vertexIds = new HashMap<>();
        JSONArray jsonLinks = new JSONArray();

        if (type != null) {
            obj.put("name", type);
        }
        for (String vertex : vertices) {
            vertexIds.put(vertex, id);
            JSONObject vertexObject = new JSONObject();
            vertexObject.put("id", id++);
            if (addClass) {
                vertexObject.put("uri", vertex);
                vertexObject.put("group", dataset.getClass(vertex));
                vertexObject.put("label", dataset.getLabel(vertex));
            }
            jsonNodes.add(vertexObject);
        }

        obj.put("nodes", jsonNodes);

        for (DefaultEdge edge : edges) {
            JSONObject edgeObject = new JSONObject();
            if (addClass) {
                edgeObject.put("uri", edge.toString());
                edgeObject.put("label", dataset.getLabel(edge.toString()));
            }
            edgeObject.put("source", new Integer(vertexIds.get(edge.getSource().toString())));
            edgeObject.put("target", new Integer(vertexIds.get(edge.getTarget().toString())));
            jsonLinks.add(edgeObject);
        }
        obj.put("links", jsonLinks);
        return obj;
    }

    public static JSONObject getJson(GraphFeatures graphFeatures) {
        return getJsonObject(graphFeatures.getVertices(), graphFeatures.getEdges(), false, null, graphFeatures.getType());
    }

    public static JSONObject getJsonColored(GraphFeatures graphFeatures, Dataset dataset) {
        return getJsonObject(graphFeatures.getVertices(), graphFeatures.getEdges(), true, dataset, null);
    }
}
