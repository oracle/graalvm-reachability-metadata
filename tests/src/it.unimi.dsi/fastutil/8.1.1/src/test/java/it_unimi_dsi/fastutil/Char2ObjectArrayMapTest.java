/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.chars.Char2ObjectArrayMap;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it_unimi_dsi.fastutil.FastutilTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Char2ObjectArrayMapTest {
    @Test
    void persistsEntriesAndRemainsMutable() throws IOException, ClassNotFoundException {
        int size = 5;
        Char2ObjectArrayMap<String> original = new Char2ObjectArrayMap<>();
        original.defaultReturnValue("missing");
        for (int i = size - 1; i >= 0; i--) {
            original.put((char) i, (i == 1 ? null : "item-" + i));
        }

        Char2ObjectArrayMap<String> copy = roundTrip(original);
        assertEquals(original.size(), copy.size());
        assertEquals("missing", copy.get((char) 6));
        for (int i = 0; i < size; i++) {
            assertTrue(copy.containsKey((char) i));
            assertEquals((i == 1 ? null : "item-" + i), copy.get((char) i));
        }

        copy.put((char) 5, "item-5");
        assertEquals("item-5", copy.get((char) 5));
        copy.remove((char) 5);
        assertFalse(copy.containsKey((char) 5));
        assertEquals(original.size(), copy.size());
    }
}
