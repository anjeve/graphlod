package graphlod;


import graphlod.dataset.Dataset;
import org.jgraph.graph.DefaultEdge;
import org.jgraph.graph.Edge;
import org.jgrapht.DirectedGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

import com.google.common.collect.ImmutableMultimap;

import static graphlod.TestUtils.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

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
        Dataset dataset = Dataset.fromLines(lines, "", "", "", excluded);

        assertThat(dataset.getGraph().vertexSet().size(), equalTo(2));
        assertThat(dataset.getGraph().edgeSet().size(), equalTo(1));
    }

    @Test
    public void testGetGraph() throws Exception {
        lines.add(createStatement("a", "p1", "b"));
        Dataset dataset = Dataset.fromLines(lines, "", "", "", excluded);

        DirectedGraph<String, DefaultEdge> graph = dataset.getGraph();
        Edge edge = graph.getEdge(url("a"),url("b"));
        assertThat(edge.getSource(), equalTo((Object)url("a")));
        assertThat(edge.getTarget(), equalTo((Object)url("b")));
    }

    public void testNamespace() {
        lines.add(createStatement("a/Thing", "p1", "b/Other"));
        lines.add(createStatement("a/Thing", "p1", "a/NotOther"));

        Dataset dataset = Dataset.fromLines(lines, "", "http://a/", "http://a/", Arrays.asList("http://classes/"));
        assertThat(dataset.getGraph().vertexSet(), containsInAnyOrder(url("a/Thing"),url("a/NotOther")));
        assertThat(dataset.getGraph().edgeSet().size(), equalTo(1));
        assertThat(dataset.getGraph().getEdge(url("a/Thing"), url("a/NotOther")), notNullValue());
    }

    @Test
    public void testExcludedNamespace() {
        lines.add(createStatement("classes/Thing", "p1", "b"));
        lines.add(createStatement("a", "p1", "classes/Thing"));
        lines.add(createStatement("a", "p1", "b"));

        Dataset dataset = Dataset.fromLines(lines, "", "http://", "", Arrays.asList("http://classes/"));
        assertThat(dataset.getGraph().vertexSet(), containsInAnyOrder(url("a"),url("b")));
        assertThat(dataset.getGraph().edgeSet().size(), equalTo(1));

        assertThat(dataset.getGraph().getEdge(url("a"), url("b")), notNullValue());
    }

    @Test
    public void testSubclasses() {
        lines.add(createStatement("c1", "www.w3.org/2000/01/rdf-schema#subClassOf", "c0"));
        lines.add(createStatement("c2", "www.w3.org/2000/01/rdf-schema#subClassOf", "c0"));
        lines.add(createStatement("c11", "www.w3.org/2000/01/rdf-schema#subClassOf", "c1"));

        Dataset dataset = Dataset.fromLines(lines, "", "http://", "", excluded);

        assertThat(dataset.getOntologySubclasses().keySet(), containsInAnyOrder(Dataset.OWL_THING, url("c0"), url("c1")));
        assertThat(dataset.getOntologySubclasses().get(Dataset.OWL_THING), containsInAnyOrder(url("c0")));
        assertThat(dataset.getOntologySubclasses().get(url("c0")), containsInAnyOrder(url("c1"), url("c2")));
        assertThat(dataset.getOntologySubclasses().get(url("c1")), containsInAnyOrder(url("c11")));
    }
}