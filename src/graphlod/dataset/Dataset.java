package graphlod.dataset;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.jgraph.graph.DefaultEdge;
import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DirectedPseudograph;
import org.jgrapht.graph.SimpleGraph;
import org.semanticweb.yars.nx.Node;
import org.semanticweb.yars.nx.parser.NxParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.impls.tg.TinkerGraph;
import com.tinkerpop.blueprints.util.io.graphml.GraphMLReader;

public class Dataset {
    private static Logger logger = LoggerFactory.getLogger(Dataset.class);

    private static final Map<String, String> classes = new HashMap<>(); // mapping from entities to their class

    private final DirectedGraph<String, DefaultEdge> g = new DirectedPseudograph<>(DefaultEdge.class);
    private final String namespace;
    private final String ontologyNamespace;
    private final SimpleGraph<String, DefaultEdge> simpleGraph = new SimpleGraph<>(DefaultEdge.class);
    private final Collection<String> excludedNamespaces;
    private final Set<String> removeVertices = new HashSet<>();
    private final Set<String> ontologyClasses = new HashSet<>(); // list of all classes
    private final Multimap<String, String> ontologySubclasses = ArrayListMultimap.create(); // classes and their subclasses
    private final Map<String, String> labels = new HashMap<>();
    private final String name;

    public static final String OWL_THING = "http://www.w3.org/2002/07/owl#Thing";

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

    public static Dataset fromGraphML(String file, String name, String propertyKey, String classkey) {
        try {
            InputStream input = Files.newInputStream(Paths.get(file));
            return fromGraphML(input, name, propertyKey, classkey);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Dataset fromGraphML(InputStream dataset, String name, String propertyKey, String classKey) throws IOException {
        Graph graph = new TinkerGraph();
        GraphMLReader reader = new GraphMLReader(graph);
        reader.inputGraph(dataset);

        Dataset ds = new Dataset(name, "", "", Collections.<String>emptyList());
        ds.readGraph(graph, propertyKey, classKey);
        return ds;

    }

    private void readGraph(Graph inputGraph, String propertyKey, String classKey) {
        for (Vertex vertex : inputGraph.getVertices()) {
            String v = vertex.getId().toString();

            simpleGraph.addVertex(v);
            g.addVertex(v);
            String clazz = (String) vertex.getProperty(classKey);
            if (clazz != null) {
                classes.put(v, clazz);
                ontologyClasses.add(clazz);
            }
            String label = vertex.getProperty("url");
            if (label != null) {
                labels.put(v, label);
            }
        }
        for (Edge edge : inputGraph.getEdges()) {
            String source = edge.getVertex(Direction.OUT).getId().toString();
            String target = edge.getVertex(Direction.IN).getId().toString();
            String property = edge.getProperty(propertyKey);

            simpleGraph.addEdge(source, target);

            DefaultEdge e = new DefaultEdge(property);
            e.setSource(source);
            e.setTarget(target);
            g.addEdge(source, target, e);
        }

        postProcessClassHierarchy();
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
                } else if (objectUri.equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#Class") ||
                        objectUri.equals("http://www.w3.org/2002/07/owl#Class")) {
                    removeVertices.add(subjectUri);
                    removeVertices.add(objectUri);
                } else if (objectUri.startsWith(ontologyNamespace) && !classes.containsKey(subjectUri)) {
                    // TODO find top classes for each class hierarchy tree path and only save top one
                    classes.put(subjectUri, objectUri);
                    if (!g.containsVertex(subjectUri)) {
                        g.addVertex(subjectUri);
                        simpleGraph.addVertex(subjectUri);
                    }
                    if (!this.ontologyClasses.contains(objectUri)) {
                        this.ontologyClasses.add(objectUri);
                    }
                }
                // owl:DatatypeProperty
                // owl:ObjectProperty
            } else if (propertyUri.equals("http://www.w3.org/2002/07/owl#equivalentClass")) {
                removeVertices.add(subjectUri);
                removeVertices.add(objectUri);
            } else if (propertyUri.equals("http://www.w3.org/2000/01/rdf-schema#subClassOf")) {
                ontologySubclasses.put(objectUri, subjectUri);
                if (!this.ontologyClasses.contains(objectUri)) {
                    this.ontologyClasses.add(objectUri);
                }
                if (!this.ontologyClasses.contains(subjectUri)) {
                    this.ontologyClasses.add(subjectUri);
                }
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
                DefaultEdge e = new DefaultEdge(propertyUri);
                e.setSource(subjectUri);
                e.setTarget(objectUri);
                g.addEdge(subjectUri, objectUri, e);
                //g.addEdge(subjectUri, objectUri);
                if (!simpleGraph.containsEdge(subjectUri, objectUri) && !simpleGraph.containsEdge(objectUri, subjectUri)) {
                    DefaultEdge e1 = new DefaultEdge(propertyUri);
                    e1.setSource(subjectUri);
                    e1.setTarget(objectUri);
                    simpleGraph.addEdge(subjectUri, objectUri, e1);
                    // simpleGraph.addEdge(subjectUri, objectUri);
                }
            }
        }

        postProcessClassHierarchy();
    }

    /**
     * Sets all classes that don't have a superclass as subclass of OWL-Thing
     * SubclassOfOwlThing = (Classes + SuperClasses) - SubClasses
     */
    private void postProcessClassHierarchy() {
        Set<String> classesWithoutSuperClass = Sets.newHashSet();
        classesWithoutSuperClass.addAll(ontologyClasses);
        classesWithoutSuperClass.addAll(ontologySubclasses.keys());
        classesWithoutSuperClass.removeAll(ontologySubclasses.values());
        classesWithoutSuperClass.remove(OWL_THING);
        ontologySubclasses.get(OWL_THING).addAll(classesWithoutSuperClass);
    }

    public static String getClass(String subjectUri) {
        return classes.get(subjectUri);
    }

    public String getClassForSubject(String subjectUri) {
        if (!classes.containsKey(subjectUri)) return "null";
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

    public String getOntologyNamespace() {
        return ontologyNamespace;
    }

    public Set<String> getOntologyClasses() {
        return ontologyClasses;
    }

    public Multimap<String, String> getOntologySubclasses() {
        return ontologySubclasses;
    }
}
