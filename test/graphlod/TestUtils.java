package graphlod;

public class TestUtils {
    public static String createStatement(String subject, String predicate, String object) {
        return String.format("<http://%s> <http://%s> <http://%s> .", subject, predicate, object);
    }

    public static String createLiteralStatement(String subject, String predicate, String literal) {
        return String.format("<http://%s> <http://%s> \"%s\" .", subject, predicate, literal);
    }
    public static String url(String name) {
        return String.format("http://%s", name);
    }
}
