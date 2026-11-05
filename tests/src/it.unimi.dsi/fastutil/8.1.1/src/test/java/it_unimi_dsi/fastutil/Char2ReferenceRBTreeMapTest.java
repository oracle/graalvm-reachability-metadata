/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.chars.Char2ReferenceRBTreeMap;
import it_unimi_dsi.fastutil.FastutilTestSupport.Value;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it_unimi_dsi.fastutil.FastutilTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Char2ReferenceRBTreeMapTest {
    @Test
    void persistsEntriesAndRemainsMutable() throws IOException, ClassNotFoundException {
        for (int size : new int[]{0, 1, 2, 5}) {
            Char2ReferenceRBTreeMap<Value> original = new Char2ReferenceRBTreeMap<>();
            original.defaultReturnValue(Value.MISSING);
            for (int i = size - 1; i >= 0; i--) {
                original.put((char) i, (i == 1 ? null : Value.SHARED));
            }

            Char2ReferenceRBTreeMap<Value> copy = roundTrip(original);
            assertEquals(original.size(), copy.size());
            assertSame(Value.MISSING, copy.get((char) 6));
            for (int i = 0; i < size; i++) {
                assertTrue(copy.containsKey((char) i));
                assertSame((i == 1 ? null : Value.SHARED), copy.get((char) i));
            }
            Object[] expectedKeys = new Object[size];
            for (int i = 0; i < size; i++) {
                expectedKeys[i] = (char) i;
            }
            assertArrayEquals(expectedKeys, copy.keySet().toArray());

            copy.put((char) 5, Value.SHARED);
            assertSame(Value.SHARED, copy.get((char) 5));
            copy.remove((char) 5);
            assertFalse(copy.containsKey((char) 5));
            assertEquals(original.size(), copy.size());
        }
    }
}
