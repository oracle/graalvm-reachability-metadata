/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.objects.Reference2ShortOpenHashMap;
import it_unimi_dsi.fastutil.FastutilTestSupport.Key;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it_unimi_dsi.fastutil.FastutilTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Reference2ShortOpenHashMapTest {
    @Test
    void persistsEntriesAndRemainsMutable() throws IOException, ClassNotFoundException {
        int size = 5;
        Reference2ShortOpenHashMap<Key> original = new Reference2ShortOpenHashMap<>();
        original.defaultReturnValue((short) 30);
        for (int i = size - 1; i >= 0; i--) {
            original.put(Key.values()[i], (short) (i + 10));
        }
        original.put(null, (short) 10);

        Reference2ShortOpenHashMap<Key> copy = roundTrip(original);
        assertEquals(original.size(), copy.size());
        assertEquals((short) 30, copy.getShort(Key.MISSING));
        for (int i = 0; i < size; i++) {
            assertTrue(copy.containsKey(Key.values()[i]));
            assertEquals((short) (i + 10), copy.getShort(Key.values()[i]));
        }
        assertTrue(copy.containsKey(null));
        assertEquals((short) 10, copy.getShort(null));

        copy.put(Key.EXTRA, (short) 15);
        assertEquals((short) 15, copy.getShort(Key.EXTRA));
        copy.removeShort(Key.EXTRA);
        assertFalse(copy.containsKey(Key.EXTRA));
        assertEquals(original.size(), copy.size());
    }
}
