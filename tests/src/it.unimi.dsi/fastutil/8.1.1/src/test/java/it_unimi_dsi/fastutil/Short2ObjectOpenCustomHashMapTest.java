/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenCustomHashMap;
import it_unimi_dsi.fastutil.PrimitiveHashStrategies.ShortStrategy;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it_unimi_dsi.fastutil.FastutilTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Short2ObjectOpenCustomHashMapTest {
    @Test
    void persistsEntriesAndRemainsMutable() throws IOException, ClassNotFoundException {
        int size = 5;
        Short2ObjectOpenCustomHashMap<String> original = new Short2ObjectOpenCustomHashMap<>(ShortStrategy.INSTANCE);
        original.defaultReturnValue("missing");
        for (int i = size - 1; i >= 0; i--) {
            original.put((short) i, (i == 1 ? null : "item-" + i));
        }

        Short2ObjectOpenCustomHashMap<String> copy = roundTrip(original);
        assertEquals(original.size(), copy.size());
        assertEquals("missing", copy.get((short) 6));
        for (int i = 0; i < size; i++) {
            assertTrue(copy.containsKey((short) i));
            assertEquals((i == 1 ? null : "item-" + i), copy.get((short) i));
        }
        assertSame(ShortStrategy.INSTANCE, copy.strategy());

        copy.put((short) 5, "item-5");
        assertEquals("item-5", copy.get((short) 5));
        copy.remove((short) 5);
        assertFalse(copy.containsKey((short) 5));
        assertEquals(original.size(), copy.size());
    }
}
