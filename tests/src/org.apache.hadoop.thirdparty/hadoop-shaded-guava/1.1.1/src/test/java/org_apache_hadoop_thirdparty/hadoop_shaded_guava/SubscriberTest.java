/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_guava;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicReference;
import org.apache.hadoop.thirdparty.com.google.common.eventbus.EventBus;
import org.apache.hadoop.thirdparty.com.google.common.eventbus.Subscribe;
import org.junit.jupiter.api.Test;

public class SubscriberTest {
    @Test
    void postingEventInvokesRegisteredSubscriberMethod() {
        EventBus eventBus = new EventBus("subscriber-test");
        RecordingListener listener = new RecordingListener();
        eventBus.register(listener);

        eventBus.post("delivered");

        assertEquals("delivered", listener.receivedEvent());
    }

    public static final class RecordingListener {
        private final AtomicReference<String> receivedEvent = new AtomicReference<>();

        @Subscribe
        public void record(String event) {
            receivedEvent.set(event);
        }

        String receivedEvent() {
            return receivedEvent.get();
        }
    }
}
