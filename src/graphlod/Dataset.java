package graphlod;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import org.jgraph.graph.DefaultEdge;
import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.semanticweb.yars.nx.Node;
import org.semanticweb.yars.nx.parser.NxParser;

public class Dataset {
	private DirectedGraph<String, DefaultEdge> g = new DefaultDirectedGraph<>(DefaultEdge.class);
	private int nrVertex;
	private int nrEdges;

	public Dataset(String dataset) {
		try {
			NxParser nxp = new NxParser(new FileInputStream(dataset));
			readTriples(nxp);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	public Dataset(Iterable<String> lines) {
		NxParser nxp = new NxParser(lines);
		readTriples(nxp);
	}

	private void readTriples(NxParser nxp) {
		ArrayList<String> removeVertices = new ArrayList<>();

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

			if (propertyUri.equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")) {
				if (objectUri.equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#Property")) {
					removeVertices.add(subjectUri);  // TODO: why remove the subject?
					removeVertices.add(objectUri);
				} else if (objectUri.equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#Class")) {
					removeVertices.add(subjectUri);
					removeVertices.add(objectUri);
				}
			} else if (propertyUri.equals("http://www.w3.org/2002/07/owl#equivalentClass")) {
				removeVertices.add(subjectUri);
				removeVertices.add(objectUri);
			} else {
				// TODO exclude schema (properties, classes)
				// list of subject uris that have one of these properties:

				// remove them also afterwards

				if (!g.containsVertex(subjectUri)) {
					g.addVertex(subjectUri);
					nrVertex += 1;
				}
				if (!g.containsVertex(objectUri)) {
					g.addVertex(objectUri);
					nrVertex += 1;
				}
				if (g instanceof DirectedGraph) {
					DefaultEdge e = new DefaultEdge();
					e.setSource(subjectUri);
					e.setTarget(objectUri);
					g.addEdge(subjectUri, objectUri,e);
					nrEdges += 1;
				} else {
					if (!g.containsEdge(subjectUri, objectUri) && !g.containsEdge(objectUri, subjectUri)) {
						g.addEdge(subjectUri, objectUri);
						nrEdges += 1;
					}
				}
			}
		}
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
		return this.nrEdges;
	}

	public int getVertices() {
		return this.nrVertex;
	}
}
