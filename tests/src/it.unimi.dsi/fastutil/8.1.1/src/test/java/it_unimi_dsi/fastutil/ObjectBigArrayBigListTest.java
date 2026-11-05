/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.objects.ObjectBigArrayBigList;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it_unimi_dsi.fastutil.FastutilTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ObjectBigArrayBigListTest {
    @Test
    void persistsOrderNullsAndSharedElements() throws IOException, ClassNotFoundException {
        ObjectBigArrayBigList<String> original = new ObjectBigArrayBigList<>();
        String shared = "shared";
        original.add(shared);
        original.add(null);
        original.add(shared);
        original.add("last");

        ObjectBigArrayBigList<String> copy = roundTrip(original);
        assertEquals(4, copy.size64());
        assertEquals("shared", copy.get(0L));
        assertNull(copy.get(1L));
        assertSame(copy.get(0L), copy.get(2L));
        assertEquals("last", copy.get(3L));

        copy.set(1L, "updated");
        copy.add("tail");
        assertEquals("shared", copy.remove(0L));
        assertEquals(4, copy.size64());
        assertEquals("updated", copy.get(0L));
        assertEquals("tail", copy.get(3L));
        assertNull(original.get(1L));
    }
}
