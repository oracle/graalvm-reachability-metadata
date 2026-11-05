/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.bytes.Byte2ReferenceAVLTreeMap;
import it_unimi_dsi.fastutil.FastutilTestSupport.Value;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it_unimi_dsi.fastutil.FastutilTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Byte2ReferenceAVLTreeMapTest {
    @Test
    void persistsEntriesAndRemainsMutable() throws IOException, ClassNotFoundException {
        for (int size : new int[]{0, 1, 2, 5}) {
            Byte2ReferenceAVLTreeMap<Value> original = new Byte2ReferenceAVLTreeMap<>();
            original.defaultReturnValue(Value.MISSING);
            for (int i = size - 1; i >= 0; i--) {
                original.put((byte) i, (i == 1 ? null : Value.SHARED));
            }

            Byte2ReferenceAVLTreeMap<Value> copy = roundTrip(original);
            assertEquals(original.size(), copy.size());
            assertSame(Value.MISSING, copy.get((byte) 6));
            for (int i = 0; i < size; i++) {
                assertTrue(copy.containsKey((byte) i));
                assertSame((i == 1 ? null : Value.SHARED), copy.get((byte) i));
            }
            Object[] expectedKeys = new Object[size];
            for (int i = 0; i < size; i++) {
                expectedKeys[i] = (byte) i;
            }
            assertArrayEquals(expectedKeys, copy.keySet().toArray());

            copy.put((byte) 5, Value.SHARED);
            assertSame(Value.SHARED, copy.get((byte) 5));
            copy.remove((byte) 5);
            assertFalse(copy.containsKey((byte) 5));
            assertEquals(original.size(), copy.size());
        }
    }
}
