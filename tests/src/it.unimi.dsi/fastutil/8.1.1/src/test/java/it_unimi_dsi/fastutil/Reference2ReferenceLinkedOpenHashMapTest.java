/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceLinkedOpenHashMap;
import it_unimi_dsi.fastutil.FastutilTestSupport.Key;
import it_unimi_dsi.fastutil.FastutilTestSupport.Value;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it_unimi_dsi.fastutil.FastutilTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Reference2ReferenceLinkedOpenHashMapTest {
    @Test
    void persistsEntriesAndRemainsMutable() throws IOException, ClassNotFoundException {
        int size = 5;
        Reference2ReferenceLinkedOpenHashMap<Key, Value> original = new Reference2ReferenceLinkedOpenHashMap<>();
        original.defaultReturnValue(Value.MISSING);
        for (int i = size - 1; i >= 0; i--) {
            original.put(Key.values()[i], (i == 1 ? null : Value.SHARED));
        }
        original.put(null, Value.SHARED);

        Reference2ReferenceLinkedOpenHashMap<Key, Value> copy = roundTrip(original);
        assertEquals(original.size(), copy.size());
        assertSame(Value.MISSING, copy.get(Key.MISSING));
        for (int i = 0; i < size; i++) {
            assertTrue(copy.containsKey(Key.values()[i]));
            assertSame((i == 1 ? null : Value.SHARED), copy.get(Key.values()[i]));
        }
        assertTrue(copy.containsKey(null));
        assertSame(Value.SHARED, copy.get(null));
        Object[] expectedKeys = new Object[size + 1];
        for (int i = 0; i < size; i++) {
            expectedKeys[i] = Key.values()[(size - i - 1)];
        }
        assertArrayEquals(expectedKeys, copy.keySet().toArray());

        copy.put(Key.EXTRA, Value.SHARED);
        assertSame(Value.SHARED, copy.get(Key.EXTRA));
        copy.remove(Key.EXTRA);
        assertFalse(copy.containsKey(Key.EXTRA));
        assertEquals(original.size(), copy.size());
    }
}
