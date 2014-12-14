package graphlod;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.jgraph.graph.DefaultEdge;
import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.semanticweb.yars.nx.Node;
import org.semanticweb.yars.nx.parser.NxParser;

public class Dataset {
	private final DirectedGraph<String, DefaultEdge> g = new DefaultDirectedGraph<>(DefaultEdge.class);
	private final Collection<String> excludedNamespaces;
	private int vertexCount = 0;
	private int edgeCount = 0;
	private Set<String> removeVertices = new HashSet<>();

	public Dataset(Collection<String> datasets, Collection<String> excludedNamespaces) {
		Validate.notNull(datasets);
		Validate.notNull(excludedNamespaces);
		this.excludedNamespaces = excludedNamespaces;

		for(String dataset: datasets) {
			Validate.isTrue(new File(dataset).exists(), "dataset not found: %s", dataset);
			NxParser nxp;
			try {
				nxp = new NxParser(new FileInputStream(dataset));
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
			readTriples(nxp);
			System.out.println("finished reading " + dataset);
		}
		cleanup();
	}

	Dataset(Iterable<String> lines, Collection<String> excludedNamespaces) {
		NxParser nxp = new NxParser(lines);
		this.excludedNamespaces = excludedNamespaces;
		readTriples(nxp);
		cleanup();
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
				continue; // TODO: why that?
			}

			if(propertyUri.equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")) {
				if (objectUri.equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#Property")) {
                    removeVertices.add(subjectUri);
					removeVertices.add(objectUri);
				} else if (objectUri.equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#Class")) {
					removeVertices.add(subjectUri);
					removeVertices.add(objectUri);
				}
                // owl:DatatypeProperty
                // owl:ObjectProperty
			} else if (propertyUri.equals("http://www.w3.org/2002/07/owl#equivalentClass")) {
				removeVertices.add(subjectUri);
				removeVertices.add(objectUri);
			} else {
				boolean skip = false;
				for (String s : excludedNamespaces) {
					if(subjectUri.startsWith(s)) {
						removeVertices.add(subjectUri);
						skip = true;
					}
					if (objectUri.startsWith(s)){
						removeVertices.add(objectUri);
						skip = true;
					}
				}
				if (skip) {
					continue;
				}

				if (!g.containsVertex(subjectUri)) {
					g.addVertex(subjectUri);
					vertexCount += 1;
				}
				if (!g.containsVertex(objectUri)) {
					g.addVertex(objectUri);
					vertexCount += 1;
				}
				if (g instanceof DirectedGraph) {
					DefaultEdge e = new DefaultEdge();
					e.setSource(subjectUri);
					e.setTarget(objectUri);
					g.addEdge(subjectUri, objectUri,e);
					edgeCount += 1;
				} else {
					if (!g.containsEdge(subjectUri, objectUri) && !g.containsEdge(objectUri, subjectUri)) {
						g.addEdge(subjectUri, objectUri);
						edgeCount += 1;
					}
				}
			}
		}
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

	public int getEdges() {
		return this.edgeCount;
	}

	public int getVertices() {
		return this.vertexCount;
	}
}
