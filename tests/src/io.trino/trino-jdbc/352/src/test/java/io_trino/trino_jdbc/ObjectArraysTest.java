/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.collect.ObjectArrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ObjectArraysTest {
    @Test
    void concatenatesTypedArrays() {
        String[] combined = ObjectArrays.concat(new String[] {"a"}, new String[] {"b", "c"}, String.class);
        assertEquals(String[].class, combined.getClass());
        assertArrayEquals(new String[] {"a", "b", "c"}, combined);
    }
}
