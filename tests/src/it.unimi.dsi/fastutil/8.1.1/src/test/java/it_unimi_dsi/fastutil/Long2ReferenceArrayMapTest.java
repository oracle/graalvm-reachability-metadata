/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.longs.Long2ReferenceArrayMap;
import it_unimi_dsi.fastutil.FastutilTestSupport.Value;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it_unimi_dsi.fastutil.FastutilTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Long2ReferenceArrayMapTest {
    @Test
    void persistsEntriesAndRemainsMutable() throws IOException, ClassNotFoundException {
        int size = 5;
        Long2ReferenceArrayMap<Value> original = new Long2ReferenceArrayMap<>();
        original.defaultReturnValue(Value.MISSING);
        for (int i = size - 1; i >= 0; i--) {
            original.put((long) i, (i == 1 ? null : Value.SHARED));
        }

        Long2ReferenceArrayMap<Value> copy = roundTrip(original);
        assertEquals(original.size(), copy.size());
        assertSame(Value.MISSING, copy.get((long) 6));
        for (int i = 0; i < size; i++) {
            assertTrue(copy.containsKey((long) i));
            assertSame((i == 1 ? null : Value.SHARED), copy.get((long) i));
        }

        copy.put((long) 5, Value.SHARED);
        assertSame(Value.SHARED, copy.get((long) 5));
        copy.remove((long) 5);
        assertFalse(copy.containsKey((long) 5));
        assertEquals(original.size(), copy.size());
    }
}
