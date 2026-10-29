/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss.jboss_common_core;

import org.jboss.util.Objects;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ObjectsTest {
    @Test
    void deepCopiesNestedSerializableCollections() throws Exception {
        ArrayList<String> row = new ArrayList<>(List.of("orders", "pending"));
        ArrayList<ArrayList<String>> original = new ArrayList<>();
        original.add(row);

        List<?> copy = (List<?>) Objects.copy(original);
        assertEquals(original, copy);
        assertNotSame(original, copy);
        assertNotSame(row, copy.get(0));

        row.set(1, "complete");
        assertEquals(List.of("orders", "pending"), copy.get(0));
    }

    @Test
    void wrapsAScalarInAnArrayAndPreservesAnExistingObjectArray() {
        String[] wrapped = (String[]) Objects.toArray("orders");
        assertArrayEquals(new String[]{"orders"}, wrapped);

        String[] values = {"a", "b"};
        assertSame(values, Objects.toArray(values));
    }
}
