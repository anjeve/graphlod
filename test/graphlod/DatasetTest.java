package graphlod;


import static graphlod.TestUtils.createLiteralStatement;
import static graphlod.TestUtils.createStatement;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;

import org.junit.Before;
import org.junit.Test;

public class DatasetTest {

    ArrayList<String> lines;

    @Before
    public void setUp() {
        lines = new ArrayList<>();
    }

    @Test
    public void testGraphParse() {
        lines.add(createStatement("a", "p1", "b"));
        lines.add(createStatement("a", "p2", "c"));
        Dataset dataset = new Dataset(lines);

        assertThat(dataset.getVertices(), equalTo(3));
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
}