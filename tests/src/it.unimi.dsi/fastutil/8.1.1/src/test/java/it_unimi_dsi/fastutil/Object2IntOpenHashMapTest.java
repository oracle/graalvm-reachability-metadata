/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it_unimi_dsi.fastutil.FastutilTestSupport.Key;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it_unimi_dsi.fastutil.FastutilTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Object2IntOpenHashMapTest {
    @Test
    void persistsEntriesAndRemainsMutable() throws IOException, ClassNotFoundException {
        int size = 5;
        Object2IntOpenHashMap<Key> original = new Object2IntOpenHashMap<>();
        original.defaultReturnValue(30);
        for (int i = size - 1; i >= 0; i--) {
            original.put(Key.values()[i], (i + 10));
        }
        original.put(null, 10);

        Object2IntOpenHashMap<Key> copy = roundTrip(original);
        assertEquals(original.size(), copy.size());
        assertEquals(30, copy.getInt(Key.MISSING));
        for (int i = 0; i < size; i++) {
            assertTrue(copy.containsKey(Key.values()[i]));
            assertEquals((i + 10), copy.getInt(Key.values()[i]));
        }
        assertTrue(copy.containsKey(null));
        assertEquals(10, copy.getInt(null));

        copy.put(Key.EXTRA, 15);
        assertEquals(15, copy.getInt(Key.EXTRA));
        copy.removeInt(Key.EXTRA);
        assertFalse(copy.containsKey(Key.EXTRA));
        assertEquals(original.size(), copy.size());
    }
}
