/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.objects.Object2FloatLinkedOpenHashMap;
import it_unimi_dsi.fastutil.FastutilTestSupport.Key;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it_unimi_dsi.fastutil.FastutilTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Object2FloatLinkedOpenHashMapTest {
    @Test
    void persistsEntriesAndRemainsMutable() throws IOException, ClassNotFoundException {
        int size = 5;
        Object2FloatLinkedOpenHashMap<Key> original = new Object2FloatLinkedOpenHashMap<>();
        original.defaultReturnValue(20.5f);
        for (int i = size - 1; i >= 0; i--) {
            original.put(Key.values()[i], (i + 0.5f));
        }
        original.put(null, 0.5f);

        Object2FloatLinkedOpenHashMap<Key> copy = roundTrip(original);
        assertEquals(original.size(), copy.size());
        assertEquals(20.5f, copy.getFloat(Key.MISSING));
        for (int i = 0; i < size; i++) {
            assertTrue(copy.containsKey(Key.values()[i]));
            assertEquals((i + 0.5f), copy.getFloat(Key.values()[i]));
        }
        assertTrue(copy.containsKey(null));
        assertEquals(0.5f, copy.getFloat(null));
        Object[] expectedKeys = new Object[size + 1];
        for (int i = 0; i < size; i++) {
            expectedKeys[i] = Key.values()[(size - i - 1)];
        }
        assertArrayEquals(expectedKeys, copy.keySet().toArray());

        copy.put(Key.EXTRA, 5.5f);
        assertEquals(5.5f, copy.getFloat(Key.EXTRA));
        copy.removeFloat(Key.EXTRA);
        assertFalse(copy.containsKey(Key.EXTRA));
        assertEquals(original.size(), copy.size());
    }
}
