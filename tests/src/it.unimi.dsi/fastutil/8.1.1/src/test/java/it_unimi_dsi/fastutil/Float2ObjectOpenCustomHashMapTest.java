/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.floats.Float2ObjectOpenCustomHashMap;
import it_unimi_dsi.fastutil.PrimitiveHashStrategies.FloatStrategy;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it_unimi_dsi.fastutil.FastutilTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Float2ObjectOpenCustomHashMapTest {
    @Test
    void persistsEntriesAndRemainsMutable() throws IOException, ClassNotFoundException {
        int size = 5;
        Float2ObjectOpenCustomHashMap<String> original = new Float2ObjectOpenCustomHashMap<>(FloatStrategy.INSTANCE);
        original.defaultReturnValue("missing");
        for (int i = size - 1; i >= 0; i--) {
            original.put((float) i, (i == 1 ? null : "item-" + i));
        }

        Float2ObjectOpenCustomHashMap<String> copy = roundTrip(original);
        assertEquals(original.size(), copy.size());
        assertEquals("missing", copy.get((float) 6));
        for (int i = 0; i < size; i++) {
            assertTrue(copy.containsKey((float) i));
            assertEquals((i == 1 ? null : "item-" + i), copy.get((float) i));
        }
        assertSame(FloatStrategy.INSTANCE, copy.strategy());

        copy.put((float) 5, "item-5");
        assertEquals("item-5", copy.get((float) 5));
        copy.remove((float) 5);
        assertFalse(copy.containsKey((float) 5));
        assertEquals(original.size(), copy.size());
    }
}
