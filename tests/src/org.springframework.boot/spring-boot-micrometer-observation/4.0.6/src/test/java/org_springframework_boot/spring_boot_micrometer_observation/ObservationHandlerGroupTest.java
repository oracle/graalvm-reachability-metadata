/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_micrometer_observation;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationHandlerGroup;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.Ordered;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ObservationHandlerGroupTest {
    @Test
    void identifiesMembersByTheirHandlerType() {
        ObservationHandlerGroup group = ObservationHandlerGroup.of(GroupedHandler.class);
        assertSame(GroupedHandler.class, group.handlerType());
        assertTrue(group.isMember(new GroupedHandler("primary", 0, new ArrayList<>())));
        assertFalse(group.isMember(new IndependentHandler(new ArrayList<>())));
        assertEquals(0, group.compareTo(ObservationHandlerGroup.of(IndependentHandler.class)));
    }

    @Test
    void registersOnlyTheFirstHandlerSupportingEachObservationContext() {
        List<String> events = new ArrayList<>();
        ObservationRegistry registry = ObservationRegistry.create();
        ObservationHandlerGroup group = ObservationHandlerGroup.of(GroupedHandler.class);
        group.registerMembers(registry.observationConfig(), List.of(
                new GroupedHandler("primary", 0, events),
                new GroupedHandler("fallback", 1, events)));

        Observation.createNotStarted("checkout.submit", registry).observe(() -> { });

        assertEquals(List.of("primary:checkout.submit"), events);
    }

    @Test
    void autoConfigurationGroupsOrderedHandlersAndKeepsIndependentHandlers() {
        List<String> events = new ArrayList<>();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("fallback", GroupedHandler.class, () -> new GroupedHandler("fallback", 10, events));
            context.registerBean("primary", GroupedHandler.class, () -> new GroupedHandler("primary", 0, events));
            context.registerBean(IndependentHandler.class, () -> new IndependentHandler(events));
            context.registerBean(ObservationHandlerGroup.class, () -> ObservationHandlerGroup.of(GroupedHandler.class));
            context.register(ObservationAutoConfiguration.class);
            context.refresh();

            Observation.createNotStarted("inventory.read", context.getBean(ObservationRegistry.class)).observe(() -> { });

            assertEquals(2, events.size());
            assertTrue(events.contains("primary:inventory.read"));
            assertTrue(events.contains("independent:inventory.read"));
        }
    }

    public static class GroupedHandler implements ObservationHandler<Observation.Context>, Ordered {
        private final String name;
        private final int order;
        private final List<String> events;

        public GroupedHandler(String name, int order, List<String> events) {
            this.name = name;
            this.order = order;
            this.events = events;
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

        @Override
        public void onStop(Observation.Context context) {
            events.add(name + ":" + context.getName());
        }

        @Override
        public int getOrder() {
            return order;
        }
    }

    public static class IndependentHandler implements ObservationHandler<Observation.Context> {
        private final List<String> events;

        public IndependentHandler(List<String> events) {
            this.events = events;
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

        @Override
        public void onStop(Observation.Context context) {
            events.add("independent:" + context.getName());
        }
    }
}
