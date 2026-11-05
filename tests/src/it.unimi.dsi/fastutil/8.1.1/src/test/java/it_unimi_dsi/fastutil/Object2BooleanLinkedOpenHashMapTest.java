/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.objects.Object2BooleanLinkedOpenHashMap;
import it_unimi_dsi.fastutil.FastutilTestSupport.Key;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it_unimi_dsi.fastutil.FastutilTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Object2BooleanLinkedOpenHashMapTest {
    @Test
    void persistsEntriesAndRemainsMutable() throws IOException, ClassNotFoundException {
        int size = 5;
        Object2BooleanLinkedOpenHashMap<Key> original = new Object2BooleanLinkedOpenHashMap<>();
        original.defaultReturnValue(true);
        for (int i = size - 1; i >= 0; i--) {
            original.put(Key.values()[i], (i % 2 == 0));
        }
        original.put(null, true);

        Object2BooleanLinkedOpenHashMap<Key> copy = roundTrip(original);
        assertEquals(original.size(), copy.size());
        assertEquals(true, copy.getBoolean(Key.MISSING));
        for (int i = 0; i < size; i++) {
            assertTrue(copy.containsKey(Key.values()[i]));
            assertEquals((i % 2 == 0), copy.getBoolean(Key.values()[i]));
        }
        assertTrue(copy.containsKey(null));
        assertEquals(true, copy.getBoolean(null));
        Object[] expectedKeys = new Object[size + 1];
        for (int i = 0; i < size; i++) {
            expectedKeys[i] = Key.values()[(size - i - 1)];
        }
        assertArrayEquals(expectedKeys, copy.keySet().toArray());

        copy.put(Key.EXTRA, false);
        assertEquals(false, copy.getBoolean(Key.EXTRA));
        copy.removeBoolean(Key.EXTRA);
        assertFalse(copy.containsKey(Key.EXTRA));
        assertEquals(original.size(), copy.size());
    }
}
