/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.io.netty.util.internal.shaded.org.jctools.queues.MpscArrayQueue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NettyJctoolsUnsafeAccessTest {
    @Test
    void exercisesPublicApi() throws Exception {
        MpscArrayQueue<String> queue = new MpscArrayQueue<>(4);
        assertTrue(queue.offer("first"));
        assertTrue(queue.offer("second"));
        assertEquals("first", queue.poll());
        assertEquals("second", queue.poll());
        assertNull(queue.poll());
        assertTrue(queue.isEmpty());
    }
}
