/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenCustomHashMap;
import it_unimi_dsi.fastutil.FastutilTestSupport.Key;
import it_unimi_dsi.fastutil.FastutilTestSupport.KeyStrategy;
import it_unimi_dsi.fastutil.FastutilTestSupport.Value;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it_unimi_dsi.fastutil.FastutilTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Object2ReferenceOpenCustomHashMapTest {
    @Test
    void persistsEntriesAndRemainsMutable() throws IOException, ClassNotFoundException {
        int size = 5;
        Object2ReferenceOpenCustomHashMap<Key, Value> original = new Object2ReferenceOpenCustomHashMap<>(KeyStrategy.INSTANCE);
        original.defaultReturnValue(Value.MISSING);
        for (int i = size - 1; i >= 0; i--) {
            original.put(Key.values()[i], (i == 1 ? null : Value.SHARED));
        }
        original.put(null, Value.SHARED);

        Object2ReferenceOpenCustomHashMap<Key, Value> copy = roundTrip(original);
        assertEquals(original.size(), copy.size());
        assertSame(Value.MISSING, copy.get(Key.MISSING));
        for (int i = 0; i < size; i++) {
            assertTrue(copy.containsKey(Key.values()[i]));
            assertSame((i == 1 ? null : Value.SHARED), copy.get(Key.values()[i]));
        }
        assertTrue(copy.containsKey(null));
        assertSame(Value.SHARED, copy.get(null));
        assertSame(KeyStrategy.INSTANCE, copy.strategy());

        copy.put(Key.EXTRA, Value.SHARED);
        assertSame(Value.SHARED, copy.get(Key.EXTRA));
        copy.remove(Key.EXTRA);
        assertFalse(copy.containsKey(Key.EXTRA));
        assertEquals(original.size(), copy.size());
    }
}
