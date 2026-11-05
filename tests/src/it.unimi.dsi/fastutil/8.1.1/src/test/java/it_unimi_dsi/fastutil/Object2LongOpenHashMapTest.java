/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it_unimi_dsi.fastutil.FastutilTestSupport.Key;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it_unimi_dsi.fastutil.FastutilTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Object2LongOpenHashMapTest {
    @Test
    void persistsEntriesAndRemainsMutable() throws IOException, ClassNotFoundException {
        int size = 5;
        Object2LongOpenHashMap<Key> original = new Object2LongOpenHashMap<>();
        original.defaultReturnValue(30L);
        for (int i = size - 1; i >= 0; i--) {
            original.put(Key.values()[i], (i + 10L));
        }
        original.put(null, 10L);

        Object2LongOpenHashMap<Key> copy = roundTrip(original);
        assertEquals(original.size(), copy.size());
        assertEquals(30L, copy.getLong(Key.MISSING));
        for (int i = 0; i < size; i++) {
            assertTrue(copy.containsKey(Key.values()[i]));
            assertEquals((i + 10L), copy.getLong(Key.values()[i]));
        }
        assertTrue(copy.containsKey(null));
        assertEquals(10L, copy.getLong(null));

        copy.put(Key.EXTRA, 15L);
        assertEquals(15L, copy.getLong(Key.EXTRA));
        copy.removeLong(Key.EXTRA);
        assertFalse(copy.containsKey(Key.EXTRA));
        assertEquals(original.size(), copy.size());
    }
}
