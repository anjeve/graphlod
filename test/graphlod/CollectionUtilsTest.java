package graphlod;

import java.util.Arrays;

import org.junit.Test;

import com.google.common.collect.Lists;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.hamcrest.Matchers.contains;

public class CollectionUtilsTest {

	@Test
	public void testMaxValues() throws Exception {
		assertThat(graphlod.utils.CollectionUtils.maxValues(Lists.newArrayList(5, 2, 9, 2), 2), contains(5,9));
	}

	@Test
	public void testMax() {
		assertThat(graphlod.utils.CollectionUtils.max(Arrays.asList(1, 5, 3)), is(5));
	}
	@Test
	public void testMin() {
		assertThat(graphlod.utils.CollectionUtils.min(Arrays.asList(1, 5, 3)), is(1));
	}
	@Test
	public void testAvg() {
		assertThat(graphlod.utils.CollectionUtils.average(Arrays.asList(1, 5, 3)), is(3.0));
	}
}