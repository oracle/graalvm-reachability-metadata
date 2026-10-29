/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.util.internal.PlatformDependent;
import java.util.Queue;
import org.junit.jupiter.api.Test;

public class UnsafeAccessTest {
    @Test
    void growsTheTransportProducerQueueAndPreservesOrder() {
        Queue<Integer> queue = PlatformDependent.newMpscQueue(8, 1024);
        for (int index = 0; index < 100; index++) {
            assertTrue(queue.offer(index));
        }
        for (int index = 0; index < 100; index++) {
            assertEquals(index, queue.poll());
        }
        assertNull(queue.poll());
    }
}
