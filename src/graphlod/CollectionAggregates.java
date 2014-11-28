package graphlod;

import java.util.Collection;

/**
 * I couldn't find something like this in other libraries.
 */
public class CollectionAggregates {

    public static int sum(Collection<Integer> collection) {
        int result = 0;
        for (int element : collection) {
            result += element;
        }
        return result;
    }

    public static double average(Collection<Integer> collection) {
        if (collection.isEmpty()) {
            return 0;
        }
        return (double)sum(collection) / collection.size();
    }

    public static int min(Collection<Integer> collection) {
        int result = Integer.MAX_VALUE;
        for (int element : collection) {
            result = Math.min(result,element);
        }
        return result;
    }

    public static int max(Collection<Integer> collection) {
        int result = Integer.MIN_VALUE;
        for (int element : collection) {
            result = Math.max(result, element);
        }
        return result;
    }

}
