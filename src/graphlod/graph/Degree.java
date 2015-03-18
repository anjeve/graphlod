package graphlod.graph;

import com.google.common.base.MoreObjects;

public class Degree implements Comparable<Degree> {
    public String vertex;
    public int degree;

    public Degree(String vertex, int degree) {
        this.vertex = vertex;
        this.degree = degree;
    }

    @Override
    public int compareTo(Degree other) {
        return Integer.compare(degree, other.degree);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("vertex", vertex).add("degree", degree).toString();
    }

}
