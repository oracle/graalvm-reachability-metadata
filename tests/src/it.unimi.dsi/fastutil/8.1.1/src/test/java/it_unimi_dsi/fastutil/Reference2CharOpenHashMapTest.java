/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.objects.Reference2CharOpenHashMap;
import it_unimi_dsi.fastutil.FastutilTestSupport.Key;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it_unimi_dsi.fastutil.FastutilTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Reference2CharOpenHashMapTest {
    @Test
    void persistsEntriesAndRemainsMutable() throws IOException, ClassNotFoundException {
        int size = 5;
        Reference2CharOpenHashMap<Key> original = new Reference2CharOpenHashMap<>();
        original.defaultReturnValue('u');
        for (int i = size - 1; i >= 0; i--) {
            original.put(Key.values()[i], (char) ('a' + i));
        }
        original.put(null, 'a');

        Reference2CharOpenHashMap<Key> copy = roundTrip(original);
        assertEquals(original.size(), copy.size());
        assertEquals('u', copy.getChar(Key.MISSING));
        for (int i = 0; i < size; i++) {
            assertTrue(copy.containsKey(Key.values()[i]));
            assertEquals((char) ('a' + i), copy.getChar(Key.values()[i]));
        }
        assertTrue(copy.containsKey(null));
        assertEquals('a', copy.getChar(null));

        copy.put(Key.EXTRA, 'f');
        assertEquals('f', copy.getChar(Key.EXTRA));
        copy.removeChar(Key.EXTRA);
        assertFalse(copy.containsKey(Key.EXTRA));
        assertEquals(original.size(), copy.size());
    }
}
