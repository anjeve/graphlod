package graphlod;

import static de.parsemis.miner.environment.Debug.err;

import de.parsemis.Miner;
import de.parsemis.graph.Edge;
import de.parsemis.graph.Graph;
import de.parsemis.graph.HPMutableGraph;
import de.parsemis.miner.environment.Settings;
import de.parsemis.miner.general.Fragment;
import de.parsemis.parsers.GraphParser;
import de.parsemis.parsers.IntLabelParser;
import de.parsemis.parsers.SimpleDirectedGraphParser;
import de.parsemis.parsers.StringLabelParser;
import de.parsemis.parsers.antlr.DotParser;
import graphlod.dataset.Dataset;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.jgraph.graph.DefaultEdge;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;



public class GspanAnalysis {

    void run(Dataset dataset) {
        Settings.printUsage(err);
        GraphParser<String, String> parser = new SimpleDirectedGraphParser(new StringLabelParser(), new StringLabelParser());
        Settings<String, String> settings = Settings.parse(new String[]{"--graphFile=null", "--minimumFrequency=1", "--maximumNodeCount=3", "--findPathsOnly=true"}, parser); // --threads=int
        if (settings == null) {
            Settings.printUsage(err);
        }
        HPMutableGraph<String, String> graph = (HPMutableGraph<String, String>) settings.factory.newGraph(dataset.getName()).toHPGraph();

        final BiMap<String, Integer> nodes = HashBiMap.create();

        // Add nodes to graph.
        Set<String> vertices = dataset.getGraph().vertexSet();
        for (String vertex : vertices) {
            String vertexClass = dataset.getClassForSubject(vertex);
            int id = graph.addNodeIndex(vertexClass);
            nodes.put(vertex, id);
//            graph.setNodeLabel(id, vertexClass);
        }

        // Add edges to graph.
        Set<DefaultEdge> edges = dataset.getGraph().edgeSet();
        for (final DefaultEdge e : edges) {
            graph.addEdgeIndex(nodes.get(e.getSource().toString()), nodes.get(e.getTarget().toString()), e.getUserObject().toString(), Edge.OUTGOING);
        }


        Collection<Graph<String,String>> graphs = ImmutableList.of(graph.toGraph());

        Collection<Fragment<String, String>> result = Miner.mine(graphs, settings);

        BiMap<Integer, String> invNodes = nodes.inverse();

        String ns = "http://www4.wiwiss.fu-berlin.de/diseasome/resource/";

        System.out.println("done - " + result.size());
        for (Fragment<String, String> fragment : result) {
            System.out.println(fragment);
            Iterator<Edge<String, String>> it = fragment.toGraph().edgeIterator();
            while(it.hasNext()) {
                Edge<String, String> e = it.next();
                String a = Integer.toString(e.getNodeA().getIndex());
                String b = Integer.toString(e.getNodeB().getIndex());
                String c1 = e.getNodeA().getLabel().replace(ns, "");
                String c2 = e.getNodeB().getLabel().replace(ns, "");
                String p = e.getLabel().replace(ns, "");
                System.out.println(a + " (" + c1 + ") " + p + " " + b + " (" + c2 + ")");
            }
        }
        //TODO output
    }
}
