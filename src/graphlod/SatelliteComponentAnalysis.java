package graphlod;

import graphlod.algorithms.GraphFeatures;
import graphlod.dataset.Dataset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class SatelliteComponentAnalysis {
    private static Logger logger = LoggerFactory.getLogger(SatelliteComponentAnalysis.class);
    private GraphLOD graphLod;

    public SatelliteComponentAnalysis(ArgumentParser arguments) {
        this(arguments.getName(), Dataset.fromFiles(arguments.getDataset(), arguments.getName(), arguments.getNamespace(), arguments.getOntns(), arguments.getExcludedNamespaces()));
    }

    public SatelliteComponentAnalysis(String name, Dataset dataset) {
        logger.info("Satellite component analysis");

        graphLod = GraphLOD.loadGraph(name, dataset, true);
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