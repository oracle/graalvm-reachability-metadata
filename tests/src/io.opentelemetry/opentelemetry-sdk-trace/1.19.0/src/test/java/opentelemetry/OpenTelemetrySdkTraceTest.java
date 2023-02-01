/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <
 *
 * http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package opentelemetry;

import io.opentelemetry.internal.shaded.jctools.queues.MpscArrayQueue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


public class OpenTelemetrySdkTraceTest {

    @Test
    public void sdkTracingTest() {
        Assertions.assertTrue(isFieldAccessible("io.opentelemetry.internal.shaded.jctools.queues.MpscArrayQueueConsumerIndexField", "consumerIndex"));
        Assertions.assertTrue(isFieldAccessible("io.opentelemetry.internal.shaded.jctools.queues.MpscArrayQueueProducerIndexField", "producerIndex"));
        Assertions.assertTrue(isFieldAccessible("io.opentelemetry.internal.shaded.jctools.queues.MpscArrayQueueProducerLimitField", "producerLimit"));

        MpscArrayQueue<String> q = new MpscArrayQueue<>(10);
        q.offer("test");
        Assertions.assertEquals(q.lvProducerIndex(), 1);
        Assertions.assertEquals(q.lvConsumerIndex(), 0);
        Assertions.assertEquals(q.relaxedPoll(), "test");
    }

    private boolean isFieldAccessible(String className, String fieldName) {
        try {
            Class<?> aClass = Class.forName(className);
            aClass.getDeclaredField(fieldName);
            return true;
        } catch (ClassNotFoundException | NoSuchFieldException ex) {
            return false;
        }
    }
}
