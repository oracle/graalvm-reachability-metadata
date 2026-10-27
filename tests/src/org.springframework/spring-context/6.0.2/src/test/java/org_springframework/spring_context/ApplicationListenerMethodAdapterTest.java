/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.EventListener;

public class ApplicationListenerMethodAdapterTest {

    @Test
    void processEventInvokesListenerMethodOnTargetBean() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(RecordingListener.class);
            context.refresh();
            RecordingListener listener = context.getBean(RecordingListener.class);
            RecordingEvent event = new RecordingEvent("test-source");

            context.publishEvent(event);

            assertSame(event, listener.lastEvent());
            assertEquals(1, listener.invocationCount());
        }
    }

    public static class RecordingListener {

        private final AtomicReference<RecordingEvent> lastEvent = new AtomicReference<>();

        private final AtomicInteger invocationCount = new AtomicInteger();

        @EventListener
        public void record(RecordingEvent event) {
            this.lastEvent.set(event);
            this.invocationCount.incrementAndGet();
        }

        RecordingEvent lastEvent() {
            return this.lastEvent.get();
        }

        int invocationCount() {
            return this.invocationCount.get();
        }
    }

    public static class RecordingEvent extends ApplicationEvent {

        public RecordingEvent(Object source) {
            super(source);
        }
    }
}
