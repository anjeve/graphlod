package graphlod.output;

import graphlod.dataset.Dataset;


public class ExportDot {

    public ExportDot(Dataset dataset) {
         /*
        final HPMutableGraph<NodeType, EdgeType> g = (HPMutableGraph<NodeType, EdgeType>) factory.newGraph(p.getName()).toHPGraph();

        final Map<String, Integer> nodes = new HashMap<String, Integer>();

        // Add nodes to graph.
        final Map<String, String> nodeLabels = p.getNodeMap();
        for (final Map.Entry<String, String> e : nodeLabels.entrySet()) {
            nodes.put(e.getKey(), g.addNodeIndex(np.parse(e.getValue())));
        }

        // Add edges to graph.
        final Collection<DotParser.EdgeDesc> edges = p.getEdges();
        for (final DotParser.EdgeDesc e : edges) {
            g.addEdgeIndex(nodes.get(e.nodeA), nodes.get(e.nodeB), ep
                    .parse(e.label), (e.undirected ? Edge.UNDIRECTED
                    : Edge.OUTGOING));
        }

        return g.toGraph();


        DOTExporter exporter = new DOTExporter();
        exporter.export(new BufferedWriter(new OutputStreamWriter(System.out)), dataset.getGraph());
        */
    }




}
