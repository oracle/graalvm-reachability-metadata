/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it_unimi_dsi.fastutil.FastutilTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ObjectArrayFIFOQueueTest {
    @Test
    void persistsWrappedQueueAndSupportsBothEnds() throws IOException, ClassNotFoundException {
        ObjectArrayFIFOQueue<String> original = new ObjectArrayFIFOQueue<>(4);
        original.enqueue("discard");
        original.enqueue("first");
        original.enqueue("second");
        assertEquals("discard", original.dequeue());
        original.enqueue("third");

        ObjectArrayFIFOQueue<String> copy = roundTrip(original);
        assertEquals(3, copy.size());
        assertEquals("first", copy.first());
        assertEquals("first", copy.dequeue());
        assertEquals("third", copy.dequeueLast());
        copy.enqueue("tail");
        copy.enqueueFirst("head");
        assertEquals("head", copy.dequeue());
        assertEquals("second", copy.dequeue());
        assertEquals("tail", copy.dequeue());
        assertTrue(copy.isEmpty());
        assertEquals(3, original.size());
    }
}
