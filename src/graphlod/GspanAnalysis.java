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
import java.util.Map;
import java.util.Set;

import org.jgraph.graph.DefaultEdge;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;



public class GspanAnalysis {

    void run(Dataset dataset) {
//        Settings.printUsage(err);
        GraphParser<String, String> parser = new SimpleDirectedGraphParser(new StringLabelParser(), new StringLabelParser());
        Settings<String, String> settings = Settings.parse(new String[]{"--graphFile=null", "--minimumFrequency=20"}, parser); // --threads=int
        if (settings == null) {
            Settings.printUsage(err);
        }
        HPMutableGraph<String, String> graph = (HPMutableGraph<String, String>) settings.factory.newGraph(dataset.getName()).toHPGraph();

        final Map<String, Integer> nodes = Maps.newHashMap();

        // Add nodes to graph.
        Set<String> vertices = dataset.getGraph().vertexSet();
        for (String vertex : vertices) {
            nodes.put(vertex, graph.addNodeIndex(dataset.getClassForSubject(vertex)));
        }

        // Add edges to graph.
        Set<DefaultEdge> edges = dataset.getGraph().edgeSet();
        for (final DefaultEdge e : edges) {
            graph.addEdgeIndex(nodes.get(e.getSource().toString()), nodes.get(e.getTarget().toString()), "edgeType", Edge.OUTGOING);
        }

        Collection<Graph<String,String>> graphs = ImmutableList.of(graph.toGraph());
        Collection<Fragment<String, String>> result = Miner.mine(graphs, settings);
        System.out.println("done - " + result.size());
        for (Fragment<String, String> fragment : result) {
            System.out.println(fragment);
        }
        //TODO output
    }
}
