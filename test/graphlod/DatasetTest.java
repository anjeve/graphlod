package graphlod;


import graphlod.dataset.Dataset;
import graphlod.dataset.GraphMLHandler;
import graphlod.dataset.SWTGraphMLHandler;

import javax.activation.DataSource;
import javax.xml.crypto.Data;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.jgraph.graph.DefaultEdge;
import org.jgraph.graph.Edge;
import org.jgrapht.DirectedGraph;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Vertex;

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
        Edge edge = graph.getEdge(url("a"), url("b"));
        assertThat(edge.getSource(), equalTo((Object) url("a")));
        assertThat(edge.getTarget(), equalTo((Object) url("b")));
    }

    public void testNamespace() {
        lines.add(createStatement("a/Thing", "p1", "b/Other"));
        lines.add(createStatement("a/Thing", "p1", "a/NotOther"));

        Dataset dataset = Dataset.fromLines(lines, "", "http://a/", "http://a/", Arrays.asList("http://classes/"));
        assertThat(dataset.getGraph().vertexSet(), containsInAnyOrder(url("a/Thing"), url("a/NotOther")));
        assertThat(dataset.getGraph().edgeSet().size(), equalTo(1));
        assertThat(dataset.getGraph().getEdge(url("a/Thing"), url("a/NotOther")), notNullValue());
    }

    @Test
    public void testExcludedNamespace() {
        lines.add(createStatement("classes/Thing", "p1", "b"));
        lines.add(createStatement("a", "p1", "classes/Thing"));
        lines.add(createStatement("a", "p1", "b"));

        Dataset dataset = Dataset.fromLines(lines, "", "http://", "", Arrays.asList("http://classes/"));
        assertThat(dataset.getGraph().vertexSet(), containsInAnyOrder(url("a"), url("b")));
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

    public class TestGraphMLHandler implements GraphMLHandler {
        @Override
        public String getVertex(Vertex vertex) {
            return vertex.getId().toString();
        }

        @Override
        public String getLabel(Vertex vertex) {
            return vertex.getProperty("url");
        }

        @Override
        public String getClass(Vertex vertex) {
            return vertex.getProperty("type");
        }

        @Override
        public String getSubject(com.tinkerpop.blueprints.Edge edge) {
            return edge.getVertex(Direction.OUT).getId().toString();
        }

        @Override
        public String getObject(com.tinkerpop.blueprints.Edge edge) {
            return  edge.getVertex(Direction.IN).getId().toString();
        }

        @Override
        public String getProperty(com.tinkerpop.blueprints.Edge edge) {
            return edge.getProperty("property");
        }
    }


    @Test
    public void testGraphMl() throws IOException {
        String data = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\"\n" +
                "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "    xsi:schemaLocation=\"http://graphml.graphdrawing.org/xmlns\n" +
                "     http://graphml.graphdrawing.org/xmlns/1.0/graphml.xsd\">\n" +

                "  <key id=\"type\" for=\"node\" attr.name=\"type\" attr.type=\"string\"/>\n" +
                "  <key id=\"property\" for=\"edge\" attr.name=\"property\" attr.type=\"string\"/>" +
                "  <key id=\"url\" for=\"edge\" attr.name=\"url\" attr.type=\"string\"/>" +

                "  <graph id=\"G\" edgedefault=\"directed\">\n" +
                "    <node id=\"A\">\n" +
                "      <data key=\"type\">C1</data>\n" +
                "      <data key=\"url\">http://A</data>\n" +
                "    </node>"+
                "    <node id=\"B\">" +
                "      <data key=\"type\">C2</data>\n" +
                "    </node>"+
                "    <node id=\"C\"/>\n" +
                "    <node id=\"D\"/>\n" +
                "    <edge id=\"ab\" source=\"A\" target=\"B\" >\n" +
                "       <data key=\"property\">p1</data>\n" +
                "    </edge>\n" +
                "    <edge id=\"ac\" source=\"A\" target=\"C\" />\n" +
                "    <edge id=\"bc\" source=\"B\" target=\"C\" />\n" +
                "    <edge id=\"ca\" source=\"C\" target=\"A\" />\n" +
                "    <edge id=\"cd\" source=\"C\" target=\"D\" />\n" +
                "  </graph>\n" +
                "</graphml>";

        Dataset ds = Dataset.fromGraphML(new ByteArrayInputStream(data.getBytes()), "GraphMLTest", new TestGraphMLHandler());

        assertThat(ds.getSimpleGraph().vertexSet(), containsInAnyOrder("A", "B", "C", "D", "C1", "C2"));
        assertThat(ds.getGraph().vertexSet(), containsInAnyOrder("A", "B", "C", "D", "C1", "C2"));

        assertThat(ds.getOntologyClasses(), containsInAnyOrder("C1", "C2"));
        assertThat(ds.getOntologySubclasses().asMap(), hasEntry("http://www.w3.org/2002/07/owl#Thing",
                (Collection<String>) ImmutableList.of("C1", "C2")));

        assertThat(ds.getLabel("A"), equalTo("http://A"));

        assertThat(ds.getClassForSubject("A"), equalTo("C1"));
        assertThat(ds.getClassForSubject("B"), equalTo("C2"));

        assertThat(ds.getGraph().edgesOf("A"), hasSize(4)); // ab, ac, ca, type
        assertThat(ds.getGraph().edgesOf("B"), hasSize(3)); // ab, ba, type
        assertThat(ds.getGraph().edgesOf("C"), hasSize(4)); // ac, bc, ca, cd
        assertThat(ds.getGraph().edgesOf("D"), hasSize(1)); // cd

        assertThat(ds.getSimpleGraph().edgesOf("A"), hasSize(3)); // ab, ca, type
        assertThat(ds.getSimpleGraph().edgesOf("B"), hasSize(3)); // ab, ba, type
        assertThat(ds.getSimpleGraph().edgesOf("C"), hasSize(3)); // ac, bc, cd
        assertThat(ds.getSimpleGraph().edgesOf("D"), hasSize(1)); // cd
    }

    @Ignore
    @Test
    public void testSWT() {
        String file = "E:\\repos\\ProLOD2\\swt2_2015_cleaned.graphml";
        Dataset ds = Dataset.fromGraphML(file, "SWTGraphML", new SWTGraphMLHandler());

        assertThat(ds.getLabel("https://api.github.com/users/mandyklingbeil"), equalTo("https://api.github.com/users/mandyklingbeil"));
        assertThat(ds.getClassForSubject("https://api.github.com/users/mandyklingbeil"), equalTo(":GithubUser"));

        DefaultEdge edge = ds.getGraph().getEdge("https://api.github.com/repos/hpi-swt2/wimi-portal/issues/58", "https://api.github.com/users/mandyklingbeil");
        assertThat((String)edge.getUserObject(), equalTo("user"));
    }
}