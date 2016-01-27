package graphlod;

import graphlod.algorithms.GraphFeatures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class SatelliteComponentAnalysis {
    private static Logger logger = LoggerFactory.getLogger(SatelliteComponentAnalysis.class);
    private GraphLOD graphLod;

    public SatelliteComponentAnalysis(ArgumentParser arguments) {
        new SatelliteComponentAnalysis(arguments.getName(), arguments.getDataset(), arguments.getNamespace(), arguments.getOntns(), arguments.getExcludedNamespaces());
    }

    public SatelliteComponentAnalysis(String name, Collection<String> datasetFiles, String namespace, String ontologyNS, Collection<String> excludedNamespaces) {
        logger.info("Satellite component analysis");

        graphLod = GraphLOD.loadGraph(name, datasetFiles, namespace, ontologyNS, excludedNamespaces, true);
        List<GraphFeatures> connectedComponents = graphLod.getConnectedComponents();

        if (connectedComponents.size() > 1) {
            for (GraphFeatures connectedComponent : connectedComponents) {
                if (connectedComponent.getVertexCount() >= graphLod.bigComponentSize) continue;
                graphLod.findPatternsInSatelliteComponents();
            }
        }
    }

    public GraphLOD getGraphLodInstance() {
        return this.graphLod;
    }

    public static void main(final String[] args) {
        ArgumentParser arguments = new ArgumentParser(args, 3000);
        Locale.setDefault(Locale.US);
        new SatelliteComponentAnalysis(arguments);
    }
}