/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.objects.ObjectArrayPriorityQueue;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it_unimi_dsi.fastutil.FastutilTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ObjectArrayPriorityQueueTest {
    @Test
    void persistsPriorityOrdering() throws IOException, ClassNotFoundException {
        ObjectArrayPriorityQueue<String> original = new ObjectArrayPriorityQueue<>();
        original.enqueue("gamma");
        original.enqueue("alpha");
        original.enqueue("beta");

        ObjectArrayPriorityQueue<String> copy = roundTrip(original);
        assertEquals(3, copy.size());
        assertEquals("alpha", copy.first());
        copy.enqueue("aardvark");
        assertEquals("aardvark", copy.dequeue());
        assertEquals("alpha", copy.dequeue());
        assertEquals("beta", copy.dequeue());
        assertEquals("gamma", copy.dequeue());
        assertTrue(copy.isEmpty());
        assertEquals(3, original.size());
    }
}
