/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it_unimi_dsi.fastutil.FastutilTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ReferenceArrayListTest {
    @Test
    void persistsOrderNullsAndSharedElements() throws IOException, ClassNotFoundException {
        ReferenceArrayList<String> original = new ReferenceArrayList<>();
        String shared = "shared";
        original.add(shared);
        original.add(null);
        original.add(shared);
        original.add("last");

        ReferenceArrayList<String> copy = roundTrip(original);
        assertEquals(4, copy.size());
        assertEquals("shared", copy.get(0));
        assertNull(copy.get(1));
        assertSame(copy.get(0), copy.get(2));
        assertEquals("last", copy.get(3));

        copy.set(1, "updated");
        copy.add("tail");
        assertEquals("shared", copy.remove(0));
        assertEquals(4, copy.size());
        assertEquals("updated", copy.get(0));
        assertEquals("tail", copy.get(3));
        assertNull(original.get(1));
    }
}
