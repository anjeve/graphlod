package graphlod;

import graphlod.algorithms.GraphFeatures;
import graphlod.dataset.Dataset;
import org.jgraph.graph.DefaultEdge;
import org.jgrapht.GraphPath;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static graphlod.TestUtils.createStatement;
import static graphlod.TestUtils.url;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

public class GraphFeaturesTest {

    private GraphFeatures features;

    @Before
    public void setup() {
        /*          -> c -> e
                  /
            a -> b <-> d
         */
        Dataset ds = Dataset.fromLines(Arrays.asList(
                createStatement("a", "p1", "b"),
                createStatement("b", "p1", "c"),
                createStatement("b", "p1", "d"),
                createStatement("d", "p1", "b"),
                createStatement("c", "p1", "e")), "", "", "", new ArrayList<String>());
        features = new GraphFeatures("" , ds.getGraph(), ds.getSimpleGraph());

    }

    @Test
    public void testGetEdgeCount() throws Exception {
        assertThat(features.getEdgeCount(), equalTo(5));
    }

    @Test
    public void testGetVertexCount() throws Exception {
        assertThat(features.getVertexCount(), equalTo(5));
    }

    @Test
    public void testIsConnected() throws Exception {
        assertThat(features.isConnected(), equalTo(true));
    }

    @Test
    public void testGetDiameter() throws Exception {
        assertThat(features.getDiameter(), equalTo(3.0));
    }

    @Test
    public void testGetDiameterPath() throws Exception {
        GraphPath<String, DefaultEdge> path = features.diameterPath();
        assertThat(path.getStartVertex(), equalTo(url("a")));
        assertThat(path.getEndVertex(), equalTo(url("e")));
    }

    @Test
    public void testGetEdgeCounts() throws Exception {
        assertThat(features.getEdgeCounts(), contains(1, 4, 2, 2, 1));
    }

    @Test
    public void testGetIndegrees() throws Exception {
        assertThat(features.getIndegrees(), contains(0, 2, 1, 1, 1));
    }

    @Test
    public void getOutdegrees() throws Exception {
        assertThat(features.getOutdegrees(), contains(1, 2, 1, 1, 0));
    }

    @Test
    public void getStronglyConnectedSets() throws Exception {
        assertThat(features.getStronglyConnectedSets(),
                containsInAnyOrder(
                        contains(url("a")),
                        containsInAnyOrder(url("b"), url("d")),
                        contains(url("c")),
                        contains(url("e"))));
    }

    @Test
    public void testGetConnectedSets() throws Exception {
        assertThat(features.getConnectedSets(), contains(
                containsInAnyOrder(url("a"), url("b"), url("c"), url("d"), url("e"))));
    }

    @Test
    public void testDiameterPath() throws Exception {
        assertThat(features.diameterPath().getStartVertex(), equalTo(url("a")));
        assertThat(features.diameterPath().getEndVertex(), equalTo(url("e")));
        assertThat(features.diameterPath().getEdgeList(), hasSize(3));
    }

    @Test
    public void testGetChromaticNumber() throws Exception {
        assertThat(features.getChromaticNumber(), equalTo(2));
    }

    @Test
    public void testGetConnectedGraphFeatures() throws Exception {
        List<GraphFeatures> subGraphFeatures = features.createSubGraphFeatures(features.getConnectedSets());
        assertThat(subGraphFeatures, hasSize(1));
        assertThat(subGraphFeatures.get(0).getVertices(), equalTo(features.getVertices()));
        assertThat(subGraphFeatures.get(0).getEdges(), equalTo(features.getEdges()));
    }

    @Test
    public void testGetBiConnectedSets() throws Exception {
        assertThat(features.getBiConnectedSets(), containsInAnyOrder(
                contains(url("a"), url("b")),
                contains(url("b"), url("c")),
                contains(url("b"), url("d")),
                contains(url("c"), url("e"))));
    }
}