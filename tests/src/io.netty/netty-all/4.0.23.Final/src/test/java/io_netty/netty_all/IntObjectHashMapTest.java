/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import io.netty.util.collection.IntObjectHashMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class IntObjectHashMapTest {
    @Test
    void valuesCreatesTypedArrayContainingStoredValues() {
        IntObjectHashMap<String> map = new IntObjectHashMap<String>();
        map.put(7, "seven");
        map.put(11, "eleven");
        map.put(19, "nineteen");

        String[] values = map.values(String.class);

        Assertions.assertEquals(String[].class, values.getClass());
        Assertions.assertEquals(map.size(), values.length);
        Assertions.assertEquals(expectedValues(), new HashSet<String>(Arrays.asList(values)));
    }

    private static Set<String> expectedValues() {
        Set<String> expected = new HashSet<String>();
        expected.add("seven");
        expected.add("eleven");
        expected.add("nineteen");
        return expected;
    }
}
