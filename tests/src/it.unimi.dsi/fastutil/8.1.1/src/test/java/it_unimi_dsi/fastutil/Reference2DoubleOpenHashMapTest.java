/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.objects.Reference2DoubleOpenHashMap;
import it_unimi_dsi.fastutil.FastutilTestSupport.Key;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it_unimi_dsi.fastutil.FastutilTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Reference2DoubleOpenHashMapTest {
    @Test
    void persistsEntriesAndRemainsMutable() throws IOException, ClassNotFoundException {
        int size = 5;
        Reference2DoubleOpenHashMap<Key> original = new Reference2DoubleOpenHashMap<>();
        original.defaultReturnValue(20.5);
        for (int i = size - 1; i >= 0; i--) {
            original.put(Key.values()[i], (i + 0.5));
        }
        original.put(null, 0.5);

        Reference2DoubleOpenHashMap<Key> copy = roundTrip(original);
        assertEquals(original.size(), copy.size());
        assertEquals(20.5, copy.getDouble(Key.MISSING));
        for (int i = 0; i < size; i++) {
            assertTrue(copy.containsKey(Key.values()[i]));
            assertEquals((i + 0.5), copy.getDouble(Key.values()[i]));
        }
        assertTrue(copy.containsKey(null));
        assertEquals(0.5, copy.getDouble(null));

        copy.put(Key.EXTRA, 5.5);
        assertEquals(5.5, copy.getDouble(Key.EXTRA));
        copy.removeDouble(Key.EXTRA);
        assertFalse(copy.containsKey(Key.EXTRA));
        assertEquals(original.size(), copy.size());
    }
}
