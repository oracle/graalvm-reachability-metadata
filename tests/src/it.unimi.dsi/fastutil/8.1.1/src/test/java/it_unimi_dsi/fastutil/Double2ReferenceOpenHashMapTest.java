/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.doubles.Double2ReferenceOpenHashMap;
import it_unimi_dsi.fastutil.FastutilTestSupport.Value;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it_unimi_dsi.fastutil.FastutilTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Double2ReferenceOpenHashMapTest {
    @Test
    void persistsEntriesAndRemainsMutable() throws IOException, ClassNotFoundException {
        int size = 5;
        Double2ReferenceOpenHashMap<Value> original = new Double2ReferenceOpenHashMap<>();
        original.defaultReturnValue(Value.MISSING);
        for (int i = size - 1; i >= 0; i--) {
            original.put((double) i, (i == 1 ? null : Value.SHARED));
        }

        Double2ReferenceOpenHashMap<Value> copy = roundTrip(original);
        assertEquals(original.size(), copy.size());
        assertSame(Value.MISSING, copy.get((double) 6));
        for (int i = 0; i < size; i++) {
            assertTrue(copy.containsKey((double) i));
            assertSame((i == 1 ? null : Value.SHARED), copy.get((double) i));
        }

        copy.put((double) 5, Value.SHARED);
        assertSame(Value.SHARED, copy.get((double) 5));
        copy.remove((double) 5);
        assertFalse(copy.containsKey((double) 5));
        assertEquals(original.size(), copy.size());
    }
}
