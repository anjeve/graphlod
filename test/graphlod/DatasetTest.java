package graphlod;


import static graphlod.TestUtils.createLiteralStatement;
import static graphlod.TestUtils.createStatement;
import static graphlod.TestUtils.url;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import org.jgraph.graph.DefaultEdge;
import org.jgraph.graph.Edge;
import org.jgrapht.DirectedGraph;
import org.junit.Before;
import org.junit.Test;

public class DatasetTest {

    ArrayList<String> lines;
    ArrayList<String> excluded;

    @Before
    public void setUp() {
        lines = new ArrayList<>();
        excluded = new ArrayList<>();
    }

    @Test
    public void literalsDontCount() {
        lines.add(createStatement("a", "p1", "b"));
        lines.add(createLiteralStatement("a", "p1", "some literal"));
        Dataset dataset = Dataset.fromLines(lines, "", "", excluded);

        assertThat(dataset.getGraph().vertexSet().size(), equalTo(2));
        assertThat(dataset.getGraph().edgeSet().size(), equalTo(1));
    }

    @Test
    public void testGetGraph() throws Exception {
        lines.add(createStatement("a", "p1", "b"));
        Dataset dataset = Dataset.fromLines(lines, "", "", excluded);

        DirectedGraph<String, DefaultEdge> graph = dataset.getGraph();
        Edge edge = graph.getEdge(url("a"),url("b"));
        assertThat(edge.getSource(), equalTo((Object)url("a")));
        assertThat(edge.getTarget(), equalTo((Object)url("b")));
    }

    public void testNamespace() {
        lines.add(createStatement("a/Thing", "p1", "b/Other"));
        lines.add(createStatement("a/Thing", "p1", "a/NotOther"));

        Dataset dataset = Dataset.fromLines(lines, "http://a/", "http://a/", Arrays.asList("http://classes/"));
        assertThat(dataset.getGraph().vertexSet(), containsInAnyOrder(url("a/Thing"),url("a/NotOther")));
        assertThat(dataset.getGraph().edgeSet().size(), equalTo(1));
        assertThat(dataset.getGraph().getEdge(url("a/Thing"), url("a/NotOther")), notNullValue());
    }

    @Test
    public void testExcludedNamespace() {
        lines.add(createStatement("classes/Thing", "p1", "b"));
        lines.add(createStatement("a", "p1", "classes/Thing"));
        lines.add(createStatement("a", "p1", "b"));

        Dataset dataset = Dataset.fromLines(lines, "", "", Arrays.asList("http://classes/"));
        assertThat(dataset.getGraph().vertexSet(), containsInAnyOrder(url("a"),url("b")));
        assertThat(dataset.getGraph().edgeSet().size(), equalTo(1));

        assertThat(dataset.getGraph().getEdge(url("a"), url("b")), notNullValue());
    }
}