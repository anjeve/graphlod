package graphlod.dataset;

import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;
import org.jgraph.graph.DefaultEdge;
import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.SimpleGraph;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.semanticweb.yars.nx.Node;
import org.semanticweb.yars.nx.parser.NxParser;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class Dataset {
    private static final Logger logger = Logger.getLogger(Dataset.class);
    private final DirectedGraph<String, DefaultEdge> g = new DefaultDirectedGraph<>(DefaultEdge.class);
    private String namespace;
    private String ontologyNamespace;
    private final SimpleGraph<String, DefaultEdge> simpleGraph = new SimpleGraph<>(DefaultEdge.class);
    private final Collection<String> excludedNamespaces;
    private Set<String> removeVertices = new HashSet<>();
    private static HashMap<String, String> classes = new HashMap<>();
    private String name;

    private Dataset(String name, String namespace, String ontologyNamespace, Collection<String> excludedNamespaces) {
        Validate.notNull(namespace, "namespace must not be null");
        Validate.notNull(excludedNamespaces, "excludedNamespaces must not be null");
        this.name = name;
        this.namespace = namespace;
        this.ontologyNamespace = ontologyNamespace;
        this.excludedNamespaces = excludedNamespaces;
    }

    public static Dataset fromLines(Iterable<String> lines, String name, String namespace, String ontologyNamespace, Collection<String> excludedNamespaces) {
        Dataset s = new Dataset(name, namespace, ontologyNamespace, excludedNamespaces);
        s.readTriples(new NxParser(lines));
        s.cleanup();
        return s;
    }

    public static Dataset fromFiles(Collection<String> datasets, String name, String namespace, String ontologyNamespace, Collection<String> excludedNamespaces, boolean exportJson, String output) {
        Validate.notNull(datasets, "datasets must not be null");
        Dataset s = new Dataset(name, namespace, ontologyNamespace, excludedNamespaces);

        for (String dataset : datasets) {
            Validate.isTrue(new File(dataset).exists(), "dataset not found: %s", dataset);
            NxParser nxp;
            try {
                nxp = new NxParser(new FileInputStream(dataset));
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
            s.readTriples(nxp);
            logger.info("Finished reading " + dataset);
        }
        s.cleanup();

        if (exportJson) {
            s.exportJson(output);
        }
        return s;
    }

    private void exportJson(String output) {
        JSONObject obj = new JSONObject();

        JSONArray jsonNodes = new JSONArray();
        int id = 1;
        HashMap<String, Integer> vertexIds = new HashMap<>();
        JSONArray jsonLinks = new JSONArray();

        for (String vertex : g.vertexSet()) {
            vertexIds.put(vertex, id);
            JSONObject vertexObject = new JSONObject();
            vertexObject.put("id", id++);
            vertexObject.put("uri", vertex);
            vertexObject.put("class", getClass(vertex));
            jsonNodes.add(vertexObject);
        }

        obj.put("nodes", jsonNodes);

        for (DefaultEdge edge : g.edgeSet()) {
            JSONObject vertexObject = new JSONObject();
            vertexObject.put("uri", edge.toString());
            vertexObject.put("source", new Integer(vertexIds.get(edge.getSource().toString())));
            vertexObject.put("target", new Integer(vertexIds.get(edge.getTarget().toString())));
            jsonLinks.add(vertexObject);
        }
        obj.put("links", jsonLinks);

        try {
            FileWriter file = new FileWriter(output + this.name + ".json");
            file.write(obj.toJSONString());
            logger.debug("Successfully Copied JSON Object to File...");
            // logger.debug("\nJSON Object: " + obj);
            file.flush();
            file.close();
        } catch (IOException e) {
            e.printStackTrace();

        }

    }

    private void readTriples(NxParser nxp) {

        while (nxp.hasNext()) {
            Node[] nodes = nxp.next();
            if (nodes.length != 3) {
                continue;
            }
            String subjectUri = nodes[0].toString();
            String propertyUri = nodes[1].toString();
            String objectUri = nodes[2].toString();

            if (!isValid(subjectUri) || !isValid(propertyUri) || !isValid(objectUri)) {
                continue;
            }

            if (subjectUri.equals(objectUri)) {
                continue;
            }

            if (propertyUri.equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")) {
                if (objectUri.equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#Property")) {
                    removeVertices.add(subjectUri);
                    removeVertices.add(objectUri);
                } else if (objectUri.equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#Class")) {
                    removeVertices.add(subjectUri);
                    removeVertices.add(objectUri);
                } else if (objectUri.startsWith(ontologyNamespace) && !classes.containsKey(subjectUri)) {
                	// TODO find top classes for each class hierarchy tree path and only save top one
                	classes.put(subjectUri, objectUri);
                }
                // owl:DatatypeProperty
                // owl:ObjectProperty
            } else if (propertyUri.equals("http://www.w3.org/2002/07/owl#equivalentClass")) {
                removeVertices.add(subjectUri);
                removeVertices.add(objectUri);
            } else if (!subjectUri.startsWith(namespace)) {
                removeVertices.add(subjectUri);
            } else if (!objectUri.startsWith(namespace)) {
                removeVertices.add(objectUri);
            } else {
                boolean skip = false;
                for (String s : excludedNamespaces) {
                    if (subjectUri.startsWith(s)) {
                        removeVertices.add(subjectUri);
                        skip = true;
                        break;
                    }
                    if (objectUri.startsWith(s)) {
                        removeVertices.add(objectUri);
                        skip = true;
                        break;
                    }
                }
                if (skip) {
                    continue;
                }

                if (!g.containsVertex(subjectUri)) {
                    g.addVertex(subjectUri);
                    simpleGraph.addVertex(subjectUri);
                }
                if (!g.containsVertex(objectUri)) {
                    g.addVertex(objectUri);
                    simpleGraph.addVertex(objectUri);
                }
                if (g instanceof DirectedGraph) {
                    DefaultEdge e = new DefaultEdge(propertyUri);
                    e.setSource(subjectUri);
                    e.setTarget(objectUri);
                    g.addEdge(subjectUri, objectUri, e);
                } else {
                    if (!g.containsEdge(subjectUri, objectUri) && !g.containsEdge(objectUri, subjectUri)) {
                        g.addEdge(subjectUri, objectUri);
                        simpleGraph.addEdge(subjectUri, objectUri);
                    }
                }
            }
        }
    }
    
    public static String getClass(String subjectUri) {
    	return classes.get(subjectUri);
    }

    private void cleanup() {
        for (String vertex : removeVertices) {
            if (g.containsVertex(vertex)) {
                g.removeVertex(vertex);
            }
        }
    }

    private boolean isValid(String url) {
        try {
            URL checked = new URL(url);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    public DirectedGraph<String, DefaultEdge> getGraph() {
        return this.g;
    }

    public SimpleGraph<String, DefaultEdge> getSimpleGraph() {
        return this.simpleGraph;
    }
}
