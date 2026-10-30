/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import it_unimi_dsi.fastutil.FastutilTestSupport.Key;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it_unimi_dsi.fastutil.FastutilTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Reference2ObjectLinkedOpenHashMapTest {
    @Test
    void persistsEntriesAndRemainsMutable() throws IOException, ClassNotFoundException {
        int size = 5;
        Reference2ObjectLinkedOpenHashMap<Key, String> original = new Reference2ObjectLinkedOpenHashMap<>();
        original.defaultReturnValue("missing");
        for (int i = size - 1; i >= 0; i--) {
            original.put(Key.values()[i], (i == 1 ? null : "item-" + i));
        }
        original.put(null, "item-0");

        Reference2ObjectLinkedOpenHashMap<Key, String> copy = roundTrip(original);
        assertEquals(original.size(), copy.size());
        assertEquals("missing", copy.get(Key.MISSING));
        for (int i = 0; i < size; i++) {
            assertTrue(copy.containsKey(Key.values()[i]));
            assertEquals((i == 1 ? null : "item-" + i), copy.get(Key.values()[i]));
        }
        assertTrue(copy.containsKey(null));
        assertEquals("item-0", copy.get(null));
        Object[] expectedKeys = new Object[size + 1];
        for (int i = 0; i < size; i++) {
            expectedKeys[i] = Key.values()[(size - i - 1)];
        }
        assertArrayEquals(expectedKeys, copy.keySet().toArray());

        copy.put(Key.EXTRA, "item-5");
        assertEquals("item-5", copy.get(Key.EXTRA));
        copy.remove(Key.EXTRA);
        assertFalse(copy.containsKey(Key.EXTRA));
        assertEquals(original.size(), copy.size());
    }
}
