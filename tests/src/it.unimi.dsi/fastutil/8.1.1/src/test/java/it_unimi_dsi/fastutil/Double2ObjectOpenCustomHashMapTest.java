/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.doubles.Double2ObjectOpenCustomHashMap;
import it_unimi_dsi.fastutil.PrimitiveHashStrategies.DoubleStrategy;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it_unimi_dsi.fastutil.FastutilTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Double2ObjectOpenCustomHashMapTest {
    @Test
    void persistsEntriesAndRemainsMutable() throws IOException, ClassNotFoundException {
        int size = 5;
        Double2ObjectOpenCustomHashMap<String> original = new Double2ObjectOpenCustomHashMap<>(DoubleStrategy.INSTANCE);
        original.defaultReturnValue("missing");
        for (int i = size - 1; i >= 0; i--) {
            original.put((double) i, (i == 1 ? null : "item-" + i));
        }

        Double2ObjectOpenCustomHashMap<String> copy = roundTrip(original);
        assertEquals(original.size(), copy.size());
        assertEquals("missing", copy.get((double) 6));
        for (int i = 0; i < size; i++) {
            assertTrue(copy.containsKey((double) i));
            assertEquals((i == 1 ? null : "item-" + i), copy.get((double) i));
        }
        assertSame(DoubleStrategy.INSTANCE, copy.strategy());

        copy.put((double) 5, "item-5");
        assertEquals("item-5", copy.get((double) 5));
        copy.remove((double) 5);
        assertFalse(copy.containsKey((double) 5));
        assertEquals(original.size(), copy.size());
    }
}
