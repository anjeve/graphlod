package graphlod;

import static graphlod.TestUtils.createStatement;
import static graphlod.TestUtils.url;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.NotImplementedException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class GraphFeaturesTest {

    private GraphFeatures features1;
    private GraphFeatures features2;

    @Before
    public void setup() {
        /*          -> c
                  /
            a -> b <-> d
         */
        Dataset ds1 = new Dataset(Arrays.asList(
                createStatement("a", "p1", "b"),
                createStatement("b", "p1", "c"),
                createStatement("b", "p1", "d"),
                createStatement("d", "p1", "b")));
        features1 = new GraphFeatures(ds1.getGraph());

        /*             / e <
                      v     \
            a -> b   c  ->  d
         */
        Dataset ds2 = new Dataset(Arrays.asList(
                createStatement("a", "p1", "b"),
                createStatement("c", "p1", "d"),
                createStatement("d", "p1", "e"),
                createStatement("e", "p1", "c")));
        features2 = new GraphFeatures(ds2.getGraph());
    }

    @Test
    public void testIsConnected() throws Exception {
        assertThat(features1.isConnected(), equalTo(true));
        assertThat(features2.isConnected(), equalTo(false));

    }

    @Test
    public void testGetDiameter() throws Exception {
        assertThat(features1.getDiameter(), equalTo(2.0));
        assertThat(features1.getDiameter(), equalTo(2.0));

    }

    @Test
    public void testGetEdgeCounts() throws Exception {
        assertThat(features1.getEdgeCounts(), contains(1, 4, 1, 2));
        assertThat(features2.getEdgeCounts(), contains(1, 1, 2, 2, 2));

    }

    @Test
    public void testGetIndegrees() throws Exception {
        assertThat(features1.getIndegrees(), contains(0, 2, 1, 1));
        assertThat(features2.getIndegrees(), contains(0, 1, 1, 1, 1));
    }

    @Test
    public void getOutdegrees() throws Exception {
        assertThat(features1.getOutdegrees(), contains(1, 2, 0, 1));
        assertThat(features2.getOutdegrees(), contains(1, 0, 1, 1, 1));
    }

    @Test
    public void getStronglyConnectedSets() throws Exception {
        assertThat(features1.getStronglyConnectedSets(),
                containsInAnyOrder(
                        contains(url("a")),
                        contains(url("b"), url("d")),
                        contains(url("c"))));
        assertThat(features2.getStronglyConnectedSets(),
                containsInAnyOrder(
                        contains(url("a")),
                        contains(url("b")),
                        contains(url("c"), url("d"), url("e"))));
    }

    @Test
    public void testGetConnectedSets() throws Exception {
        assertThat(features1.getConnectedSets(), contains(containsInAnyOrder(url("a"), url("b"), url("c"), url("d"))));
        assertThat(features2.getConnectedSets(), contains(
                containsInAnyOrder(url("a"), url("b")),
                containsInAnyOrder(url("c"), url("d"), url("e"))));
    }

    @Test
    public void testDiameterPath() throws Exception {
        assertThat(features1.diameterPath(), is(not(nullValue())));
        assertThat(features2.diameterPath(), is(nullValue()));
    }

    @Test
    public void testGetChromaticNumber() throws Exception {
        assertThat(features1.getChromaticNumber(), equalTo(2));
        assertThat(features1.getChromaticNumber(), equalTo(2));
    }

    @Test
    public void testGetConnectedGraphFeatures() throws Exception {
        assertThat(features1.getConnectedGraphFeatures(), contains(features1));
    }

    @Test
    public void testGetConnectedGraphFeatures2() throws Exception {
        List<GraphFeatures> components = features2.getConnectedGraphFeatures();
        assertThat(components, hasSize(2));
    }
}