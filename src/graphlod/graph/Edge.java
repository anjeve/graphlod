package graphlod.graph;

import org.apache.commons.lang3.builder.HashCodeBuilder;

public class Edge {
    public String sourceClass;
    public String targetClass;
    //private List classes = new ArrayList<>();

    public Edge(String sourceClass, String targetClass) {
        this.sourceClass = sourceClass;
        this.targetClass = targetClass;
        /*
        this.classes.add(sourceClass);
        this.classes.add(targetClass);
        Collections.sort(this.classes);
        */
    }

    @Override
    public String toString() {
        return "(" + this.sourceClass + ", " + this.targetClass + ")";
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 31).
                append(this.sourceClass).
                append(this.targetClass).
                toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Edge))
            return false;
        if (obj == this)
            return true;
        Edge edge = (Edge) obj;
        if (edge.sourceClass.equals(this.sourceClass) && edge.targetClass.equals(this.targetClass))
            return true;
       /*
        if (edge.sourceClass.equals(this.targetClass) && edge.targetClass.equals(this.sourceClass))
            return true;
            */
        return false;
    }
}
