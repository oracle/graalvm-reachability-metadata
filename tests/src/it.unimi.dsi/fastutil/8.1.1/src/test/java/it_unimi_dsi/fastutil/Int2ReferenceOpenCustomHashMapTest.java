/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenCustomHashMap;
import it_unimi_dsi.fastutil.FastutilTestSupport.Value;
import it_unimi_dsi.fastutil.PrimitiveHashStrategies.IntStrategy;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it_unimi_dsi.fastutil.FastutilTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Int2ReferenceOpenCustomHashMapTest {
    @Test
    void persistsEntriesAndRemainsMutable() throws IOException, ClassNotFoundException {
        int size = 5;
        Int2ReferenceOpenCustomHashMap<Value> original = new Int2ReferenceOpenCustomHashMap<>(IntStrategy.INSTANCE);
        original.defaultReturnValue(Value.MISSING);
        for (int i = size - 1; i >= 0; i--) {
            original.put(i, (i == 1 ? null : Value.SHARED));
        }

        Int2ReferenceOpenCustomHashMap<Value> copy = roundTrip(original);
        assertEquals(original.size(), copy.size());
        assertSame(Value.MISSING, copy.get(6));
        for (int i = 0; i < size; i++) {
            assertTrue(copy.containsKey(i));
            assertSame((i == 1 ? null : Value.SHARED), copy.get(i));
        }
        assertSame(IntStrategy.INSTANCE, copy.strategy());

        copy.put(5, Value.SHARED);
        assertSame(Value.SHARED, copy.get(5));
        copy.remove(5);
        assertFalse(copy.containsKey(5));
        assertEquals(original.size(), copy.size());
    }
}
