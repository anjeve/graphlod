package graphlod.output;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import graphlod.GraphLOD;
import graphlod.algorithms.GraphFeatures;
import graphlod.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class HtmlDocument {
    private static Logger logger = LoggerFactory.getLogger(HtmlDocument.class);
    private BufferedWriter out;
    private GraphLOD graphLod;

    public HtmlDocument(GraphLOD graphLod) {
        this.graphLod = graphLod;
    }

    public HtmlDocument(String fileName) throws IOException {
        File file = new File(fileName);
        Files.createParentDirs(file);
        out = Files.newWriter(file, Charsets.UTF_8);
        out.close();
    }

    public void print(String text) {

    }

    public void close() throws IOException {
        out.close();
    }


    private void printStats(BufferedWriter out, List<GraphFeatures> graphs, String string, int times) throws IOException {
        out.write("<tr>\n");
        out.write("<td>");
        for (int i = 1; i <= times; i++) {
            out.write("&nbsp;&nbsp;");
        }
        out.write(string + "</td>\n");
        logger.info(string + ": " + graphs.size());
        out.write("<td>" + graphs.size() + "</td>\n");
        if (graphs.size() == 0) {
            out.write("<td></td>\n");
            out.write("<td></td>\n");
            out.write("<td></td>\n");
            out.write("<td></td>\n");
            out.write("</tr>\n");
            return;
        }
        int min = 0;
        int max = 0;
        List<Integer> sizes = new ArrayList<>();
        for (GraphFeatures graph : graphs) {
            sizes.add(graph.getVertexCount());
            if (min == 0) {
                min = graph.getVertexCount();
            } else if (graph.getVertexCount() < min) {
                min = graph.getVertexCount();
            }
            if (graph.getVertexCount() > max) {
                max = graph.getVertexCount();
            }
        }
        double avg = Utils.calculateAverage(sizes);
        out.write("<td>" + min + "</td>\n");
        out.write("<td>" + max + "</td>\n");
        out.write("<td>" + avg + "</td>\n");
        logger.info("min #v: " + min + ", max #v: " + max + ", avg #v: " + avg);

        String stringNormalized = string.toLowerCase().replace(" ", "");
        if (stringNormalized.startsWith("connected")) {
            stringNormalized = "_connected";
        }
        if (stringNormalized.startsWith("strongly")) {
            stringNormalized = "strongly-connected";
        }
        //exportEntities(graphs, stringNormalized);

        out.write("<td>\n");
        for (String file : graphLod.htmlFiles) {
            String htmlFile = new File(file).getName();
            if (htmlFile.contains(stringNormalized)) {
                out.write("<a href=dot/" + htmlFile + ">");
                String number = htmlFile.replaceFirst(".*dotgraph", "").replaceAll("\\.txt\\.html", "");
                out.write(number);
                out.write("</a> \n");
            }
        }
        out.write("</td>\n");

        out.write("</tr>\n");
    }

    public void createHtmlConnectedSets() {
        try {
            File file = new File(graphLod.output + graphLod.name + "_cs_statistics.html");
            Files.createParentDirs(file);
            BufferedWriter out = Files.newWriter(file, Charsets.UTF_8);
            printTableHeader(out);
            printStats(out, graphLod.connectedGraphFeatures, "Connected sets", 0);
            printStats(out, graphLod.stronglyConnectedGraphs, "Strongly connected sets", 0);
            printTableFooter(out);
            printGroups(out);
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void printGroups(BufferedWriter out) {
        for (List<Integer> graphs : graphLod.isomorphicGraphs) {
            try {
                out.write("<p>" + graphs.size() + " x ");
                int index = graphLod.isomorphicGraphs.indexOf(graphs);
                int vertices = graphLod.connectedGraphFeatures.get(index).getVertexCount();
                String imgName = "dot/" + graphLod.graphRenderer.getFileName() + "_" + index + "_dotgraph"  + vertices + ".txt.png";
                String imgDetailedName = "dot/" + graphLod.graphRenderer.getFileName() + "_" + index + "_detailed_dotgraph"  + vertices + ".txt.html";
                out.write("<a href=\"" + imgDetailedName + "\"><img src=\"" + imgName + "\"></a></p>");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void createHtmlStructures() {
        logger.info("Structure Statistics");

        try {
            File file = new File(graphLod.output + graphLod.name + "_statistics.html");
            Files.createParentDirs(file);
            BufferedWriter out = Files.newWriter(file, Charsets.UTF_8);
            printTableHeader(out);
            // TODO printStats(out, bipartiteGraphs, "Bipartite graphs", 0);
            /*
            printStats(out, graphLod.completeGraphs, "Complete graphs", 0);
            printStats(out, graphLod.treeGraphs, "Trees", 0);
            printStats(out, graphLod.caterpillarGraphs, "Caterpillar graphs", 1);
            printStats(out, graphLod.lobsterGraphs, "Lobster graphs", 1);
            printStats(out, graphLod.pathGraphs, "Path graphs", 1);
            printStats(out, graphLod.directedPathGraphs, "Directed path graphs", 2);
            printStats(out, graphLod.mixedDirectedStarGraphs, "Star graphs", 1);
            printStats(out, graphLod.inboundStarGraphs, "Inbound star graphs", 2);
            printStats(out, graphLod.outboundStarGraphs, "Outbound star graphs", 2);
            printStats(out, graphLod.unrecognizedStructure, "Unrecognized", 0);
            */
            printTableFooter(out);
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void printTableFooter(BufferedWriter out) throws IOException {
        out.write("</table></p>\n");
    }

    private void printTableHeader(BufferedWriter out) throws IOException {
        out.write("<p><table>\n");
        out.write("<tr>\n");
        out.write("<td>Structure</td>\n");
        out.write("<td>Number</td>\n");
        out.write("<td># Vertices (min)</td>\n");
        out.write("<td># Vertices (max)</td>\n");
        out.write("<td># Vertices (avg)</td>\n");
        out.write("<td>Graphs (# vertices)</td>\n");
        out.write("</tr>\n");
    }
}