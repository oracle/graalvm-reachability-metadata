/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it_unimi_dsi.fastutil.FastutilTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Int2ObjectRBTreeMapTest {
    @Test
    void persistsEntriesAndRemainsMutable() throws IOException, ClassNotFoundException {
        for (int size : new int[]{0, 1, 2, 5}) {
            Int2ObjectRBTreeMap<String> original = new Int2ObjectRBTreeMap<>();
            original.defaultReturnValue("missing");
            for (int i = size - 1; i >= 0; i--) {
                original.put(i, (i == 1 ? null : "item-" + i));
            }

            Int2ObjectRBTreeMap<String> copy = roundTrip(original);
            assertEquals(original.size(), copy.size());
            assertEquals("missing", copy.get(6));
            for (int i = 0; i < size; i++) {
                assertTrue(copy.containsKey(i));
                assertEquals((i == 1 ? null : "item-" + i), copy.get(i));
            }
            Object[] expectedKeys = new Object[size];
            for (int i = 0; i < size; i++) {
                expectedKeys[i] = i;
            }
            assertArrayEquals(expectedKeys, copy.keySet().toArray());

            copy.put(5, "item-5");
            assertEquals("item-5", copy.get(5));
            copy.remove(5);
            assertFalse(copy.containsKey(5));
            assertEquals(original.size(), copy.size());
        }
    }
}
