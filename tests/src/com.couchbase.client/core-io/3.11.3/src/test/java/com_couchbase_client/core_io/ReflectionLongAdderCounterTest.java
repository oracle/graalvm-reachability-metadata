/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.io.grpc.InternalChannelz.TransportStats;
import com.couchbase.client.core.deps.io.grpc.internal.TransportTracer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReflectionLongAdderCounterTest {
    @Test
    void recordsReceivedMessagesInTransportStatistics() {
        TransportTracer tracer = new TransportTracer();
        tracer.reportMessageReceived();
        tracer.reportMessageReceived();
        tracer.reportMessageSent(3);
        TransportStats stats = tracer.getStats();
        assertEquals(2, stats.messagesReceived);
        assertEquals(3, stats.messagesSent);
        assertTrue(stats.lastMessageReceivedTimeNanos > 0);
    }
}
