package tests.common;

import java.util.Arrays;
import java.util.List;

public class Fruits {

    private static final List<String> fruits = Arrays.asList("blackberry", "raspberry", "strawberry");

    public static String getSomeFruit() {
        int index = Math.random() > 0.5 ? 1 : 0;
        return fruits.get(index);
    }

    private String getBlackberry() {
        return fruits.get(0);
    }

}
