/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.objects.Object2ByteRBTreeMap;
import it_unimi_dsi.fastutil.FastutilTestSupport.Key;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it_unimi_dsi.fastutil.FastutilTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Object2ByteRBTreeMapTest {
    @Test
    void persistsEntriesAndRemainsMutable() throws IOException, ClassNotFoundException {
        for (int size : new int[]{0, 1, 2, 5}) {
            Object2ByteRBTreeMap<Key> original = new Object2ByteRBTreeMap<>();
            original.defaultReturnValue((byte) 30);
            for (int i = size - 1; i >= 0; i--) {
                original.put(Key.values()[i], (byte) (i + 10));
            }

            Object2ByteRBTreeMap<Key> copy = roundTrip(original);
            assertEquals(original.size(), copy.size());
            assertEquals((byte) 30, copy.getByte(Key.MISSING));
            for (int i = 0; i < size; i++) {
                assertTrue(copy.containsKey(Key.values()[i]));
                assertEquals((byte) (i + 10), copy.getByte(Key.values()[i]));
            }
            Object[] expectedKeys = new Object[size];
            for (int i = 0; i < size; i++) {
                expectedKeys[i] = Key.values()[i];
            }
            assertArrayEquals(expectedKeys, copy.keySet().toArray());

            copy.put(Key.EXTRA, (byte) 15);
            assertEquals((byte) 15, copy.getByte(Key.EXTRA));
            copy.removeByte(Key.EXTRA);
            assertFalse(copy.containsKey(Key.EXTRA));
            assertEquals(original.size(), copy.size());
        }
    }
}
