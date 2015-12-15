package graphlod.output;

import graphlod.dataset.Dataset;
import org.jgraph.graph.DefaultEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class GramiOutput {
    private static Logger logger = LoggerFactory.getLogger(GramiOutput.class);

    private final Dataset dataset;

    public GramiOutput(Dataset dataset) {
        this.dataset = dataset;
    }

    public void write(String output) {
        File file = new File(output + this.dataset.getName() + ".lg");
        File classMappingFile = new File(output + this.dataset.getName() + "_class_mapping.csv");
        try {
            Writer writer = new BufferedWriter(new FileWriter(file));
            Writer classMappingWriter = new BufferedWriter(new FileWriter(classMappingFile));
            HashMap<String, Integer> vertexIds = new HashMap<>();
            List<String> classIds = new ArrayList<>();
            int id = 0;
            writer.write("# t 1" + System.lineSeparator());
            for (String vertex : this.dataset.getGraph().vertexSet()) {
                String classUri = this.dataset.getClass(vertex);
                if (!classIds.contains(classUri)) {
                    classIds.add(classUri);
                }
                vertexIds.put(vertex, id);
                writer.write("v " + id++ + " " + classIds.indexOf(classUri));
                writer.write(System.lineSeparator());
            }

            for (DefaultEdge edge : this.dataset.getGraph().edgeSet()) {
                writer.write("e " + new Integer(vertexIds.get(edge.getSource().toString())) + " "  + new Integer(vertexIds.get(edge.getTarget().toString())) + " 1");
                writer.write(System.lineSeparator());
            }
            writer.close();
            logger.debug("Successfully written Grami file");
            for (String classUri : classIds) {
                classMappingWriter.write(classIds.indexOf(classUri) + "," + classUri + System.lineSeparator());
            }
            classMappingWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
