/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.collect.ImmutableList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class GuavaPlatformTest {
    @Test
    void convertsCollectionToTypedUndersizedArray() {
        String[] values = ImmutableList.of("red", "blue").toArray(new String[0]);
        assertEquals(String[].class, values.getClass());
        assertArrayEquals(new String[] {"red", "blue"}, values);
    }
}
