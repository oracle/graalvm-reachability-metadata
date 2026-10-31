/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import java.util.Queue;

import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class UnsafeAccessTest {
    @Test
    void linkedQueueGrowsAcrossChunksAndPreservesOrder() {
        Queue<Integer> queue = PlatformDependent.newMpscQueue(8, 2048);
        for (int i = 0; i < 1024; i++) {
            Assertions.assertTrue(queue.offer(i));
        }
        for (int i = 0; i < 1024; i++) {
            Assertions.assertEquals(Integer.valueOf(i), queue.poll());
        }
        Assertions.assertNull(queue.poll());
        Assertions.assertTrue(queue.isEmpty());
    }

    @Test
    void singleProducerQueueTransfersLinkedNodes() {
        Queue<String> queue = PlatformDependent.newSpscQueue();
        Assertions.assertTrue(queue.offer("first"));
        Assertions.assertTrue(queue.offer("second"));
        Assertions.assertEquals("first", queue.poll());
        Assertions.assertEquals("second", queue.poll());
        Assertions.assertNull(queue.poll());
    }
}
