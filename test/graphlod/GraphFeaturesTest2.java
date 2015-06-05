package graphlod;

import graphlod.algorithms.GraphFeatures;
import graphlod.dataset.Dataset;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static graphlod.TestUtils.createStatement;
import static graphlod.TestUtils.url;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

public class GraphFeaturesTest2 {

    private GraphFeatures features;

    @Before
    public void setup() {
        /*             / e <
            a -> b    v     \
                     c  ->  d
         */
        Dataset ds = Dataset.fromLines(Arrays.asList(
                createStatement("a", "p1", "b"),
                createStatement("c", "p1", "d"),
                createStatement("d", "p1", "e"),
                createStatement("e", "p1", "c")), "", "", "", new ArrayList<String>());
        features = new GraphFeatures("", ds.getGraph(), ds.getSimpleGraph());
    }

    @Test
    public void testGetEdgeCount() throws Exception {
        assertThat(features.getEdgeCount(), equalTo(4));
    }

    @Test
    public void testGetVertexCount() throws Exception {
        assertThat(features.getVertexCount(), equalTo(5));
    }

    @Test
    public void testIsConnected() throws Exception {
        assertThat(features.isConnected(), equalTo(false));
    }

    @Test
    public void testGetDiameter() throws Exception {
        assertThat(features.getDiameter(), equalTo(2.0));
    }

    @Test
    public void testGetDiameterPath() throws Exception {
        assertThat(features.diameterPath(), equalTo(null));
    }

    @Test
    public void testGetEdgeCounts() throws Exception {
        assertThat(features.getEdgeCounts(), contains(1, 1, 2, 2, 2));
    }

    @Test
    public void testGetIndegrees() throws Exception {
        assertThat(features.getIndegrees(), contains(0, 1, 1, 1, 1));
    }

    @Test
    public void getOutdegrees() throws Exception {
        assertThat(features.getOutdegrees(), contains(1, 0, 1, 1, 1));
    }

    @Test
    public void getStronglyConnectedSets() throws Exception {
        assertThat(features.getStronglyConnectedSets(),
                containsInAnyOrder(
                        contains(url("a")),
                        contains(url("b")),
                        containsInAnyOrder(url("c"), url("d"), url("e"))));
    }

    @Test
    public void testGetConnectedSets() throws Exception {
        List<Set<String>> connectedSets = features.getConnectedSets();
        assertThat(connectedSets, containsInAnyOrder(
                containsInAnyOrder(url("a"), url("b")),
                containsInAnyOrder(url("c"), url("d"), url("e"))));
    }

    @Test
    public void testDiameterPath() throws Exception {
        assertThat(features.diameterPath(), is(nullValue()));
    }

    @Test
    public void testGetChromaticNumber() throws Exception {
        assertThat(features.getChromaticNumber(), equalTo(3));
    }

    @Test
    public void testGetConnectedGraphFeatures() throws Exception {
        List<GraphFeatures> components = features.createSubGraphFeatures(features.getConnectedSets());
        assertThat(components, hasSize(2));
        assertThat(components.get(0).getDiameter(), equalTo(1.0));
        assertThat(components.get(0).isConnected(), equalTo(true));

        assertThat(components.get(1).getDiameter(), equalTo(2.0));
        assertThat(components.get(1).isConnected(), equalTo(true));
        assertThat(components.get(1).getBiConnectedSets(), contains(containsInAnyOrder(url("c"), url("d"), url("e"))));
    }

    @Test
    public void testGetBiConnectedSets() throws Exception {
        assertThat(features.getBiConnectedSets(), is(nullValue()));
    }
}