/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.shorts.Short2ReferenceArrayMap;
import it_unimi_dsi.fastutil.FastutilTestSupport.Value;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it_unimi_dsi.fastutil.FastutilTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Short2ReferenceArrayMapTest {
    @Test
    void persistsEntriesAndRemainsMutable() throws IOException, ClassNotFoundException {
        int size = 5;
        Short2ReferenceArrayMap<Value> original = new Short2ReferenceArrayMap<>();
        original.defaultReturnValue(Value.MISSING);
        for (int i = size - 1; i >= 0; i--) {
            original.put((short) i, (i == 1 ? null : Value.SHARED));
        }

        Short2ReferenceArrayMap<Value> copy = roundTrip(original);
        assertEquals(original.size(), copy.size());
        assertSame(Value.MISSING, copy.get((short) 6));
        for (int i = 0; i < size; i++) {
            assertTrue(copy.containsKey((short) i));
            assertSame((i == 1 ? null : Value.SHARED), copy.get((short) i));
        }

        copy.put((short) 5, Value.SHARED);
        assertSame(Value.SHARED, copy.get((short) 5));
        copy.remove((short) 5);
        assertFalse(copy.containsKey((short) 5));
        assertEquals(original.size(), copy.size());
    }
}
