package graphlod;


import static graphlod.TestUtils.createLiteralStatement;
import static graphlod.TestUtils.createStatement;
import static graphlod.TestUtils.url;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;

import org.jgraph.graph.DefaultEdge;
import org.jgraph.graph.Edge;
import org.jgrapht.DirectedGraph;
import org.junit.Before;
import org.junit.Test;

public class DatasetTest {

    ArrayList<String> lines;

    @Before
    public void setUp() {
        lines = new ArrayList<>();
    }

    @Test
    public void testGetVertices() {
        lines.add(createStatement("a", "p1", "b"));
        Dataset dataset = new Dataset(lines);

        assertThat(dataset.getVertices(), equalTo(2));
    }

    @Test
    public void testGetEdges() {
        lines.add(createStatement("a", "p1", "b"));
        lines.add(createStatement("b", "p1", "c"));
        Dataset dataset = new Dataset(lines);

        assertThat(dataset.getEdges(), equalTo(2));
    }

    @Test
    public void literalsDontCount() {
        lines.add(createStatement("a", "p1", "b"));
        lines.add(createLiteralStatement("a", "p1", "some literal"));
        Dataset dataset = new Dataset(lines);

        assertThat(dataset.getVertices(), equalTo(2));
        assertThat(dataset.getEdges(), equalTo(1));
    }

    @Test
    public void testGetGraph() throws Exception {
        lines.add(createStatement("a", "p1", "b"));
        Dataset dataset = new Dataset(lines);

        DirectedGraph<String, DefaultEdge> graph = dataset.getGraph();
        Edge edge = graph.getEdge(url("a"),url("b"));
        assertThat(edge.getSource(), equalTo((Object)url("a")));
        assertThat(edge.getTarget(), equalTo((Object)url("b")));


    }
}