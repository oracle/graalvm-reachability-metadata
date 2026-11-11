/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.EventListener;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ApplicationListenerMethodAdapterTest {
    @Test
    void deliversPayloadAndPublishesListenerResult() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(Listener.class);
            context.refresh();
            context.publishEvent("shipment");
            Listener listener = context.getBean(Listener.class);
            assertEquals(List.of("shipment"), listener.received);
            assertEquals(List.of(8), listener.lengths);
        }
    }

    public static class Listener {
        private final List<String> received = new ArrayList<>();
        private final List<Integer> lengths = new ArrayList<>();

        @EventListener
        public Integer record(String payload) {
            received.add(payload);
            return payload.length();
        }

        @EventListener
        public void recordLength(Integer length) {
            lengths.add(length);
        }
    }
}
