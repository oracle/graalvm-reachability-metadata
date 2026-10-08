/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.concurrent.atomic.AtomicReference;
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
