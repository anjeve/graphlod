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
        JSONObject obj = getJsonObject(this.dataset.getGraph().vertexSet(), this.dataset.getGraph().edgeSet(), true, this.dataset);

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

    private static JSONObject getJsonObject(Set<String> vertices, Set<DefaultEdge> edges, boolean addClass, Dataset dataset) {
        JSONObject obj = new JSONObject();

        JSONArray jsonNodes = new JSONArray();
        int id = 1;
        HashMap<String, Integer> vertexIds = new HashMap<>();
        JSONArray jsonLinks = new JSONArray();

        for (String vertex : vertices) {
            vertexIds.put(vertex, id);
            JSONObject vertexObject = new JSONObject();
            vertexObject.put("id", id++);
            if (addClass) {
                vertexObject.put("uri", vertex);
                vertexObject.put("group", dataset.getClass(vertex));
            }
            jsonNodes.add(vertexObject);
        }

        obj.put("nodes", jsonNodes);

        for (DefaultEdge edge : edges) {
            JSONObject vertexObject = new JSONObject();
            if (addClass) {
                vertexObject.put("uri", edge.toString());
            }
            vertexObject.put("source", new Integer(vertexIds.get(edge.getSource().toString())));
            vertexObject.put("target", new Integer(vertexIds.get(edge.getTarget().toString())));
            jsonLinks.add(vertexObject);
        }
        obj.put("links", jsonLinks);
        return obj;
    }

    public static JSONObject getJson(GraphFeatures graphFeatures) {
        return getJsonObject(graphFeatures.getVertices(), graphFeatures.getEdges(), false, null);
    }

    public static JSONObject getJsonColored(GraphFeatures graphFeatures, Dataset dataset) {
        return getJsonObject(graphFeatures.getVertices(), graphFeatures.getEdges(), true, dataset);
    }
}
