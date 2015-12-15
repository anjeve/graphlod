package graphlod.output;

import graphlod.algorithms.GraphFeatures;
import graphlod.dataset.Dataset;
import org.jgraph.graph.DefaultEdge;
import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.SimpleGraph;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class JsonOutput {
    private static Logger logger = LoggerFactory.getLogger(JsonOutput.class);

    private final Dataset dataset;

    public JsonOutput(Dataset dataset) {
        this.dataset = dataset;
    }

    public void write(String output) {
        JSONObject obj = getJsonObject(this.dataset.getGraph().vertexSet(), this.dataset.getGraph().edgeSet(), new HashSet<String>(), new HashSet<DefaultEdge>(), true, true, this.dataset, null, null);

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

    private static JSONObject getJsonObject(Set<String> vertices, Set<DefaultEdge> edges, Set<String> surroundingVertices, Set<DefaultEdge> surroundingEdges, boolean addClass, boolean addUris, Dataset dataset, String type, HashMap<String, String> classes) {
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
                if (addUris) {
                    if (classes != null) {
                        vertexObject.put("uri", classes.get(vertex));
                    } else {
                        vertexObject.put("uri", vertex);
                        vertexObject.put("label", dataset.getLabel(vertex));
                    }
                }
                if (classes != null) {
                    vertexObject.put("group", classes.get(vertex));
                } else {
                    vertexObject.put("group", dataset.getClass(vertex));
                }
            }
            jsonNodes.add(vertexObject);
        }
        for (String vertex : surroundingVertices) {
            if (vertices.contains(vertex)) continue;
            vertexIds.put(vertex, id);
            JSONObject vertexObject = new JSONObject();
            vertexObject.put("id", id++);
            if (addClass) {
                vertexObject.put("uri", vertex);
                //vertexObject.put("group", dataset.getClass(vertex));
                vertexObject.put("label", dataset.getLabel(vertex));
                vertexObject.put("surrounding", true);
            }
            jsonNodes.add(vertexObject);
        }

        obj.put("nodes", jsonNodes);

        for (DefaultEdge edge : edges) {
            try {
                JSONObject edgeObject = new JSONObject();
                if (addClass && addUris) {
                    edgeObject.put("uri", edge.toString());
                    edgeObject.put("label", dataset.getLabel(edge.toString()));
                }
                edgeObject.put("source", new Integer(vertexIds.get(edge.getSource().toString())));
                edgeObject.put("target", new Integer(vertexIds.get(edge.getTarget().toString())));
                jsonLinks.add(edgeObject);
            } catch (NullPointerException e) {
                logger.debug("Error: " + edge.toString());
            }
        }
        for (DefaultEdge edge : surroundingEdges) {
            try {
                JSONObject edgeObject = new JSONObject();
                if (addClass) {
                    edgeObject.put("uri", edge.toString());
                    edgeObject.put("label", dataset.getLabel(edge.toString()));
                }
                edgeObject.put("source", new Integer(vertexIds.get(edge.getSource().toString())));
                edgeObject.put("target", new Integer(vertexIds.get(edge.getTarget().toString())));
                edgeObject.put("surrounding", true);
                jsonLinks.add(edgeObject);
            } catch (NullPointerException e) {
                logger.debug("Error: " + edge.toString());
            }
        }

        obj.put("links", jsonLinks);
            return obj;
    }

    public static JSONObject getJson(GraphFeatures graphFeatures) {
        return getJsonObject(graphFeatures.getVertices(), graphFeatures.getEdges(), new HashSet<String>(), new HashSet<DefaultEdge>(), false, false, null, graphFeatures.getType(), null);
    }

    public static JSONObject getJson(SimpleGraph graph, String type) {
        return getJsonObject(graph.vertexSet(), graph.edgeSet(), new HashSet<String>(), new HashSet<DefaultEdge>(), false, false, null, type, null);
    }

    public static JSONObject getJson(DirectedGraph graph, String type, Dataset dataset) {
        return getJsonObject(graph.vertexSet(), graph.edgeSet(), new HashSet<String>(), new HashSet<DefaultEdge>(), true, true, dataset, type, null);
    }

    public static JSONObject getJson(DirectedGraph graph, DirectedGraph surroundingGraph, String type, Dataset dataset) {
        return getJsonObject(graph.vertexSet(), graph.edgeSet(), surroundingGraph.vertexSet(), surroundingGraph.edgeSet(), true, true, dataset, type, null);
    }

    public static JSONObject getJsonColored(GraphFeatures graphFeatures, Dataset dataset) {
        return getJsonObject(graphFeatures.getVertices(), graphFeatures.getEdges(), new HashSet<String>(), new HashSet<DefaultEdge>(), true, true, dataset, null, null);
    }

    public static JSONObject getJsonColored(SimpleGraph graph, Dataset dataset) {
        return getJsonObject(graph.vertexSet(), graph.edgeSet(), new HashSet<String>(), new HashSet<DefaultEdge>(), true, true, dataset, null, null);
    }

    public static JSONObject getJsonColored(SimpleGraph graph, Dataset dataset, HashMap<String, String> classes) {
        return getJsonObject(graph.vertexSet(), graph.edgeSet(), new HashSet<String>(), new HashSet<DefaultEdge>(), true, true, dataset, null, classes);
    }

    public static JSONObject getJsonColoredGroup(GraphFeatures graphFeatures, Dataset dataset) {
        return getJsonObject(graphFeatures.getVertices(), graphFeatures.getEdges(), new HashSet<String>(), new HashSet<DefaultEdge>(), true, false, dataset, null, null);
    }

    public static JSONObject getJsonColoredGroup(SimpleGraph graph, Dataset dataset, String type) {
        return getJsonObject(graph.vertexSet(), graph.edgeSet(), new HashSet<String>(), new HashSet<DefaultEdge>(), true, false, dataset, type, null);
    }

    public static JSONObject getJsonColoredGroup(SimpleGraph graph, Dataset dataset) {
        return getJsonObject(graph.vertexSet(), graph.edgeSet(), new HashSet<String>(), new HashSet<DefaultEdge>(), true, false, dataset, null, null);
    }
}
