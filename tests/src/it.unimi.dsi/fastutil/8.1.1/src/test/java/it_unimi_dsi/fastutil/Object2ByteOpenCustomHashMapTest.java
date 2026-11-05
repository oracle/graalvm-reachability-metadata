/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.objects.Object2ByteOpenCustomHashMap;
import it_unimi_dsi.fastutil.FastutilTestSupport.Key;
import it_unimi_dsi.fastutil.FastutilTestSupport.KeyStrategy;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it_unimi_dsi.fastutil.FastutilTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Object2ByteOpenCustomHashMapTest {
    @Test
    void persistsEntriesAndRemainsMutable() throws IOException, ClassNotFoundException {
        int size = 5;
        Object2ByteOpenCustomHashMap<Key> original = new Object2ByteOpenCustomHashMap<>(KeyStrategy.INSTANCE);
        original.defaultReturnValue((byte) 30);
        for (int i = size - 1; i >= 0; i--) {
            original.put(Key.values()[i], (byte) (i + 10));
        }
        original.put(null, (byte) 10);

        Object2ByteOpenCustomHashMap<Key> copy = roundTrip(original);
        assertEquals(original.size(), copy.size());
        assertEquals((byte) 30, copy.getByte(Key.MISSING));
        for (int i = 0; i < size; i++) {
            assertTrue(copy.containsKey(Key.values()[i]));
            assertEquals((byte) (i + 10), copy.getByte(Key.values()[i]));
        }
        assertTrue(copy.containsKey(null));
        assertEquals((byte) 10, copy.getByte(null));
        assertSame(KeyStrategy.INSTANCE, copy.strategy());

        copy.put(Key.EXTRA, (byte) 15);
        assertEquals((byte) 15, copy.getByte(Key.EXTRA));
        copy.removeByte(Key.EXTRA);
        assertFalse(copy.containsKey(Key.EXTRA));
        assertEquals(original.size(), copy.size());
    }
}
