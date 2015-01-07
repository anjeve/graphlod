package graphlod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * I couldn't find something like this in other libraries.
 */
public class CollectionUtils {

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

    public static <T extends Comparable<T>> T min(Collection<T> collection) {
        if(collection.isEmpty()) {
            return null;
        }
        T result = collection.iterator().next();
        for (T element : collection) {
            result = result.compareTo(element) > 0 ? result : element;
        }
        return result;
    }

    public static <T extends Comparable<T>> T max(Collection<T> collection) {
        if(collection.isEmpty()) {
            return null;
        }
        T result = collection.iterator().next();
        for (T element : collection) {
            result = result.compareTo(element) < 0 ? result : element;
        }
        return result;
    }

    public static <T  extends Comparable<T>> List<T> maxValues(List<T> values, int i) {
        List<T> copy = new ArrayList<>(values);
        Collections.sort(copy);
        if (copy.size() > i) {
            copy = copy.subList(copy.size() - i, copy.size());
        }
        return copy;
    }
}
