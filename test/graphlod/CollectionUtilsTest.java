package graphlod;

import java.util.Collection;

import org.junit.Test;

import com.google.common.collect.Lists;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;

public class CollectionUtilsTest {

	@Test
	public void testMaxValues() throws Exception {
		assertThat(CollectionUtils.maxValues(Lists.newArrayList(5,2,9,2), 2), contains(5,9));
	}
}