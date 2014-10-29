package graphlod;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.log4j.Logger;

public class GraphLOD {
	// private UndirectedGraph<String, DefaultEdge> g = new SimpleGraph<String, DefaultEdge>(DefaultEdge.class);
	private String datasetLocation = "/Users/anjeve/Desktop/keket backup/mappingbased_properties_en.nt";
	private static final Logger logger = Logger.getLogger(GraphLOD.class);
	
	public GraphLOD(String[] args) {
		
		long t1 = System.currentTimeMillis();
		for (int i = 0; i < args.length; i++) {
			datasetLocation = args[i];
		}

		Dataset dataset = new Dataset(datasetLocation);
		//DirectedGraph<String, DefaultEdge> graph = dataset.getGraph();
		GraphFeatures graphFeatures = new GraphFeatures(dataset);
		
		long t2 = System.currentTimeMillis();
		System.out.println("Loading the dataset took " + (t2 - t1) + " milliseconds to execute.");
				
		System.out.println("Vertices: "+ formatInt(dataset.getVertices()));
		System.out.println("Edges: "+ formatInt(dataset.getEdges()));

		long t5 = System.currentTimeMillis();
		if (graphFeatures.isConnected()) {
			System.out.println("Connectivity: yes");
		} else {
			System.out.println("Connectivity: no");
		}
		
		List<Set<String>> sets = graphFeatures.getConnectedSets();
		System.out.println("Connected sets: " + formatInt(sets.size()));

		ArrayList<Integer> componentSizes = new ArrayList<Integer>();
		for (Iterator<Set<String>> iterator = sets.iterator(); iterator.hasNext();) {
			Set<String> component = iterator.next();
			componentSizes.add(component.size());
		}
		Collections.sort(componentSizes);
		
		Set<Integer> uniqComponentSizes = new HashSet<Integer>(componentSizes);
		System.out.println("  Components (and sizes): "); 
		for (Iterator<Integer> iterator = uniqComponentSizes.iterator(); iterator
				.hasNext();) {
			Integer integer = (Integer) iterator.next();
			int freq = Collections.frequency(componentSizes, integer);
			System.out.println("    " + freq + " x " + integer);
		}
		
//		System.out.println(" Set sizes: " + StringUtils.join(componentSizes,","));
		
		List<Set<String>> sci_sets = graphFeatures.getStronglyConnectedSets();
		System.out.println("Strongly connected components: " + formatInt(sci_sets.size()));
		
		ArrayList<Integer> sciSizes = new ArrayList<Integer>();
		for (Iterator<Set<String>> iterator = sci_sets.iterator(); iterator.hasNext();) {
			Set<String> component = iterator.next();
			sciSizes.add(component.size());
		}
		Collections.sort(componentSizes);

		Set<Integer> uniqSCSizes = new HashSet<Integer>(sciSizes);
		System.out.println("  Components (and sizes): "); 
		for (Iterator<Integer> iterator = uniqSCSizes.iterator(); iterator
				.hasNext();) {
			Integer integer = (Integer) iterator.next();
			int freq = Collections.frequency(sciSizes, integer);
			System.out.println("    " + freq + " x " + integer);
		}

	
		long t6 = System.currentTimeMillis();
		System.out.println("Getting the connectivity took " + (t6 - t5) + " milliseconds to execute.");
		
		if (graphFeatures.isConnected()) {
			long t3 = System.currentTimeMillis();
			double diameter = graphFeatures.getDiameter();
			System.out.println("Diameter: " + diameter);
			long t4 = System.currentTimeMillis();
			System.out.println("Getting the diameter took " + (t4 - t3) + " milliseconds to execute.");
		}

		System.out.println("Average indegree: " + calculateAverage(graphFeatures.getIndegrees()));
		System.out.println("Average outdegree: " + calculateAverage(graphFeatures.getOutdegrees()));

		ArrayList<Integer> edgeCounts = graphFeatures.getEdgeCounts();
		System.out.println("Average links: " + calculateAverage(edgeCounts));
		
		
		long t7 = System.currentTimeMillis();
		int cN = graphFeatures.getChromaticNumber();
		System.out.println("Chromatic Number: " + cN);
		long t8 = System.currentTimeMillis();
		System.out.println("Getting the Chromatic Number took " + (t8 - t7) + " milliseconds to execute.");
	       
	}

	private double calculateAverage(List <Integer> marks) {
	  Integer sum = 0;
	  if(!marks.isEmpty()) {
	    for (Integer mark : marks) {
	        sum += mark;
	    }
	    return sum.doubleValue() / marks.size();
	  }
	  return sum;
	}
	
	private String formatInt(int integer) {
		return NumberFormat.getNumberInstance(Locale.US).format(integer);
	}

	public static void main (final String[] args) {
		if (args.length > 1) {
			logger.error("Too many paramaters. Please provide one dataset location only.");
			throw new IllegalArgumentException();
		}
		new GraphLOD(args);
	}

}
