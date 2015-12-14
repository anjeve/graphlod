package graphlod;

import graphlod.algorithms.GraphFeatures;
import graphlod.output.JsonOutput;
import org.jgrapht.graph.SimpleGraph;

import java.util.*;

public class MinimizeGraphs {
    public List<String> components = new ArrayList<>();
    public List<String> minimizedPatterns = new ArrayList<>();
    public HashMap<Integer, Integer> isoGroups = new HashMap<>();

    public MinimizeGraphs(ArgumentParser arguments) {
        new MinimizeGraphs(arguments.getName(), arguments.getDataset(), arguments.getNamespace(), arguments.getOntns(), arguments.getExcludedNamespaces());
    }

    public MinimizeGraphs(String name, Collection<String> datasetFiles, String namespace, String ontologyNS, Collection<String> excludedNamespaces) {
        GraphLOD graphLod = GraphLOD.loadGraph(name, datasetFiles, namespace, ontologyNS, excludedNamespaces);
        GraphFeatures graphFeatures = graphLod.graphFeatures;
        List<GraphFeatures> connectedGraphs = new ArrayList<>();
        if (graphFeatures.isConnected()) {
            connectedGraphs.add(graphFeatures);
        } else {
            connectedGraphs = graphFeatures.createSubGraphFeatures(graphFeatures.getConnectedSets());
        }

        for (GraphFeatures connectedComponent : connectedGraphs) {
            if (connectedComponent.getVertexCount() > 200) continue;
            if (connectedComponent.getVertexCount() == 1) continue;

            SimpleGraph g = connectedComponent.getSimpleGraph();
            String minimizedGraphPattern = graphLod.getMinimizedPatterns(g);

            String json = JsonOutput.getJsonColored(g, graphLod.dataset).toString().replaceAll("\\\\/", "/");
            components.add(json);

            boolean added = false;
            for (String jsonPattern: minimizedPatterns) {
                if (jsonPattern.equals(minimizedGraphPattern)) {
                    isoGroups.put(components.indexOf(json), minimizedPatterns.indexOf(jsonPattern));
                    added = true;
                }
            }
            if (!added) {
                minimizedPatterns.add(minimizedGraphPattern);
                isoGroups.put(components.indexOf(json), minimizedPatterns.indexOf(minimizedGraphPattern));
            }
        }

        System.out.println(isoGroups);
    }

    public static void main(final String[] args) {
        ArgumentParser arguments = new ArgumentParser(args, 3000);
        Locale.setDefault(Locale.US);
        new MinimizeGraphs(arguments);
    }


}
