package graphlod.dataset;

import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;
import org.jgraph.graph.DefaultEdge;
import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.SimpleGraph;
import org.semanticweb.yars.nx.Node;
import org.semanticweb.yars.nx.parser.NxParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class Dataset {
    private static final Logger logger = Logger.getLogger(Dataset.class);
    private DirectedGraph<String, DefaultEdge> g = new DefaultDirectedGraph<>(DefaultEdge.class);
    private String namespace;
    public String ontologyNamespace;
    private final SimpleGraph<String, DefaultEdge> simpleGraph = new SimpleGraph<>(DefaultEdge.class);
    private final Collection<String> excludedNamespaces;
    private Set<String> removeVertices = new HashSet<>();
    private static HashMap<String, String> classes = new HashMap<>();
    public List<String> ontologyClasses = new ArrayList<>();
    private static HashMap<String, String> labels = new HashMap<>();
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

    public static Dataset fromFiles(Collection<String> datasets, String name, String namespace, String ontologyNamespace, Collection<String> excludedNamespaces) {
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
        return s;
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

            if (propertyUri.equals("http://www.w3.org/2000/01/rdf-schema#label")) {
                labels.put(subjectUri, objectUri);
            }

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
                    if (!this.ontologyClasses.contains(objectUri)) {
                        this.ontologyClasses.add(objectUri);
                    }
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

    public String getClassForSubject(String subjectUri) {
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

    public String getName() {
        return this.name;
    }

    public String getLabel(String uri) {
        return (labels.get(uri));
    }
}
