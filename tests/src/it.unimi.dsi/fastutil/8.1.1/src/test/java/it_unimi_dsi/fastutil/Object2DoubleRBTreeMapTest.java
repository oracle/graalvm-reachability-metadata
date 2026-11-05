/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.objects.Object2DoubleRBTreeMap;
import it_unimi_dsi.fastutil.FastutilTestSupport.Key;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it_unimi_dsi.fastutil.FastutilTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Object2DoubleRBTreeMapTest {
    @Test
    void persistsEntriesAndRemainsMutable() throws IOException, ClassNotFoundException {
        for (int size : new int[]{0, 1, 2, 5}) {
            Object2DoubleRBTreeMap<Key> original = new Object2DoubleRBTreeMap<>();
            original.defaultReturnValue(20.5);
            for (int i = size - 1; i >= 0; i--) {
                original.put(Key.values()[i], (i + 0.5));
            }

            Object2DoubleRBTreeMap<Key> copy = roundTrip(original);
            assertEquals(original.size(), copy.size());
            assertEquals(20.5, copy.getDouble(Key.MISSING));
            for (int i = 0; i < size; i++) {
                assertTrue(copy.containsKey(Key.values()[i]));
                assertEquals((i + 0.5), copy.getDouble(Key.values()[i]));
            }
            Object[] expectedKeys = new Object[size];
            for (int i = 0; i < size; i++) {
                expectedKeys[i] = Key.values()[i];
            }
            assertArrayEquals(expectedKeys, copy.keySet().toArray());

            copy.put(Key.EXTRA, 5.5);
            assertEquals(5.5, copy.getDouble(Key.EXTRA));
            copy.removeDouble(Key.EXTRA);
            assertFalse(copy.containsKey(Key.EXTRA));
            assertEquals(original.size(), copy.size());
        }
    }
}
