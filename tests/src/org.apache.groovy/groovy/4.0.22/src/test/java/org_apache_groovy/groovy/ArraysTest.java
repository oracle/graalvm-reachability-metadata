/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.apache.groovy.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ArraysTest {
    @Test
    void concatenatesTypedArraysAndIgnoresNullSegments() {
        String[] result = Arrays.concat(new String[]{"a"}, null, new String[]{"b", "c"});
        assertArrayEquals(new String[]{"a", "b", "c"}, result);
        assertEquals(String[].class, result.getClass());
    }
}
