/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package opentelemetry;

import io.opentelemetry.internal.shaded.jctools.queues.atomic.MpscAtomicArrayQueue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


public class OpenTelemetrySdkTraceTest {

    @Test
    public void sdkTracingTest() {
        MpscAtomicArrayQueue<String> q = new MpscAtomicArrayQueue<>(10);
        q.offer("test");
        Assertions.assertEquals(q.lvProducerIndex(), 1);
        Assertions.assertEquals(q.lvConsumerIndex(), 0);
        Assertions.assertEquals(q.relaxedPoll(), "test");
    }
}
