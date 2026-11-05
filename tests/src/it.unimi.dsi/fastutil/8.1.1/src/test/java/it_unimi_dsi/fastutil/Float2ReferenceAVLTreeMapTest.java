/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.floats.Float2ReferenceAVLTreeMap;
import it_unimi_dsi.fastutil.FastutilTestSupport.Value;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it_unimi_dsi.fastutil.FastutilTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Float2ReferenceAVLTreeMapTest {
    @Test
    void persistsEntriesAndRemainsMutable() throws IOException, ClassNotFoundException {
        for (int size : new int[]{0, 1, 2, 5}) {
            Float2ReferenceAVLTreeMap<Value> original = new Float2ReferenceAVLTreeMap<>();
            original.defaultReturnValue(Value.MISSING);
            for (int i = size - 1; i >= 0; i--) {
                original.put((float) i, (i == 1 ? null : Value.SHARED));
            }

            Float2ReferenceAVLTreeMap<Value> copy = roundTrip(original);
            assertEquals(original.size(), copy.size());
            assertSame(Value.MISSING, copy.get((float) 6));
            for (int i = 0; i < size; i++) {
                assertTrue(copy.containsKey((float) i));
                assertSame((i == 1 ? null : Value.SHARED), copy.get((float) i));
            }
            Object[] expectedKeys = new Object[size];
            for (int i = 0; i < size; i++) {
                expectedKeys[i] = (float) i;
            }
            assertArrayEquals(expectedKeys, copy.keySet().toArray());

            copy.put((float) 5, Value.SHARED);
            assertSame(Value.SHARED, copy.get((float) 5));
            copy.remove((float) 5);
            assertFalse(copy.containsKey((float) 5));
            assertEquals(original.size(), copy.size());
        }
    }
}
