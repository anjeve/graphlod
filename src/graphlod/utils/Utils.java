package graphlod.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class Utils {
    public static double calculateAverage(List<Integer> sizes) {
        Integer sum = 0;
        if (!sizes.isEmpty()) {
            for (Integer size : sizes) {
                sum += size;
            }
            return round(sum.doubleValue() / sizes.size(), 2);
        }
        return sum;
    }

    private static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}
