package graphlod;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;

import org.jgraph.graph.DefaultEdge;
import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.semanticweb.yars.nx.Node;
import org.semanticweb.yars.nx.parser.NxParser;

public class Dataset {
	private DirectedGraph<String, DefaultEdge> g = new DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge.class);
	private int nrVertex;
	private int nrEdges;

	public Dataset(String dataset) {
		readTriples(dataset);
	}

	private void readTriples(String dataset) {
		NxParser nxp;
		try {
			nxp = new NxParser(new FileInputStream(dataset)/*, false*/);
			
			ArrayList<String> removeVertices = new ArrayList<String>();
			
			while (nxp.hasNext()) {
			  Node[] ns = nxp.next();
			  if (ns.length == 3) {
				  
				  try {
					  /*
					  for (Node n: ns) 
					    {
					      System.out.print(n.toN3());
					      System.out.print(" ");
					    }
					    System.out.println(".");
					    */
					  String objectUri = (new URL(ns[2].toString())).toString();
					  String subjectUri = (new URL(ns[0].toString())).toString();
					  String propertyUri = (new URL(ns[1].toString())).toString();
					  
					  if (!subjectUri.equals(objectUri)) {
						  if (propertyUri.equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")) {
							  if (objectUri.equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#Property")) {
								  removeVertices.add(subjectUri);
								  removeVertices.add(objectUri);
							  } else if (objectUri.equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#Class")) {
								  removeVertices.add(subjectUri);
								  removeVertices.add(objectUri);
							  }
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
								  g.addEdge(subjectUri, objectUri);
								  nrEdges += 1;
							  } else {
								  if (!g.containsEdge(subjectUri, objectUri) && !g.containsEdge(objectUri, subjectUri)) {
									  g.addEdge(subjectUri, objectUri);
									  nrEdges += 1;
								  }
							  }
						  }
					  }
				  
				  } catch (MalformedURLException e) {
						//e.printStackTrace();
					}
			  }
			}
			
			for (Iterator<String> iterator = removeVertices.iterator(); iterator.hasNext();) {
				String vertex = (String) iterator.next();
				if (g.containsVertex(vertex)) {
					g.removeVertex(vertex);
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
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
