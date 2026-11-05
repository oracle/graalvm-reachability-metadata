/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.bytes.Byte2ObjectLinkedOpenHashMap;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it_unimi_dsi.fastutil.FastutilTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Byte2ObjectLinkedOpenHashMapTest {
    @Test
    void persistsEntriesAndRemainsMutable() throws IOException, ClassNotFoundException {
        int size = 5;
        Byte2ObjectLinkedOpenHashMap<String> original = new Byte2ObjectLinkedOpenHashMap<>();
        original.defaultReturnValue("missing");
        for (int i = size - 1; i >= 0; i--) {
            original.put((byte) i, (i == 1 ? null : "item-" + i));
        }

        Byte2ObjectLinkedOpenHashMap<String> copy = roundTrip(original);
        assertEquals(original.size(), copy.size());
        assertEquals("missing", copy.get((byte) 6));
        for (int i = 0; i < size; i++) {
            assertTrue(copy.containsKey((byte) i));
            assertEquals((i == 1 ? null : "item-" + i), copy.get((byte) i));
        }
        Object[] expectedKeys = new Object[size];
        for (int i = 0; i < size; i++) {
            expectedKeys[i] = (byte) (size - i - 1);
        }
        assertArrayEquals(expectedKeys, copy.keySet().toArray());

        copy.put((byte) 5, "item-5");
        assertEquals("item-5", copy.get((byte) 5));
        copy.remove((byte) 5);
        assertFalse(copy.containsKey((byte) 5));
        assertEquals(original.size(), copy.size());
    }
}
