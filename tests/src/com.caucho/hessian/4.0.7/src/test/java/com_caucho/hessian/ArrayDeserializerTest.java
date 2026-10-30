/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import org.junit.jupiter.api.Test;

import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ArrayDeserializerTest {
    @Test
    void restoresTypedArrayIncludingNullAndSharedElements() throws Exception {
        Item item = new Item("box");
        Item[] restored = HessianStreams.roundTrip(new Item[] {item, null, item});
        assertEquals(Item[].class, restored.getClass());
        assertEquals(3, restored.length);
        assertEquals("box", restored[0].name);
        assertNull(restored[1]);
        assertSame(restored[0], restored[2]);
    }

    public static class Item implements Serializable {
        private String name;

        public Item(String name) {
            this.name = name;
        }
    }
}
