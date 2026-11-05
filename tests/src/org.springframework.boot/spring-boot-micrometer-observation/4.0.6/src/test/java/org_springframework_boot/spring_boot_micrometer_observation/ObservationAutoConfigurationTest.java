/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_micrometer_observation;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationPredicate;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationProperties;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationRegistryCustomizer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ObservationAutoConfigurationTest {
    @Test
    void bindsPropertiesAndAddsCommonKeyValuesToCompletedObservations() {
        RecordingHandler handler = new RecordingHandler();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("observations", Map.of(
                    "management.observations.key-values.region", "west",
                    "management.observations.key-values.application", "checkout",
                    "management.observations.http.client.requests.name", "outbound.calls",
                    "management.observations.http.server.requests.name", "inbound.calls")));
            context.registerBean(RecordingHandler.class, () -> handler);
            context.register(ObservationAutoConfiguration.class);
            context.refresh();

            ObservationRegistry registry = context.getBean(ObservationRegistry.class);
            ObservationProperties properties = context.getBean(ObservationProperties.class);
            Observation.createNotStarted("checkout.submit", registry)
                    .lowCardinalityKeyValue("payment", "card").observe(() -> { });

            assertEquals(1, handler.completed.size());
            Observation.Context completed = handler.completed.getFirst();
            assertEquals("checkout.submit", completed.getName());
            assertEquals(KeyValue.of("region", "west"), completed.getLowCardinalityKeyValue("region"));
            assertEquals(KeyValue.of("application", "checkout"), completed.getLowCardinalityKeyValue("application"));
            assertEquals(KeyValue.of("payment", "card"), completed.getLowCardinalityKeyValue("payment"));
            assertEquals("outbound.calls", properties.getHttp().getClient().getRequests().getName());
            assertEquals("inbound.calls", properties.getHttp().getServer().getRequests().getName());
        }
    }

    @Test
    void longestNamePrefixOverridesGlobalObservationSetting() {
        RecordingHandler handler = new RecordingHandler();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("observations", Map.of(
                    "management.observations.enable.all", "false",
                    "management.observations.enable.checkout", "true",
                    "management.observations.enable.checkout.internal", "false",
                    "management.observations.enable.checkout.internal.audit", "true")));
            context.registerBean(RecordingHandler.class, () -> handler);
            context.register(ObservationAutoConfiguration.class);
            context.refresh();

            ObservationRegistry registry = context.getBean(ObservationRegistry.class);
            for (String name : List.of("checkout.submit", "checkout.internal.cache",
                    "checkout.internal.audit.write", "inventory.read", "checkoutOther")) {
                Observation.createNotStarted(name, registry).observe(() -> { });
            }

            assertEquals(List.of("checkout.submit", "checkout.internal.audit.write"),
                    handler.completed.stream().map(Observation.Context::getName).toList());
        }
    }

    @Test
    void customizesAndPostProcessesAnApplicationProvidedRegistry() {
        RecordingHandler handler = new RecordingHandler();
        ObservationRegistry suppliedRegistry = ObservationRegistry.create();
        RegionCustomizer customizer = new RegionCustomizer();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ObservationRegistry.class, () -> suppliedRegistry);
            context.registerBean(RecordingHandler.class, () -> handler);
            context.registerBean(RegionCustomizer.class, () -> customizer);
            context.registerBean(ObservationPredicate.class, () -> (name, observationContext) -> !name.startsWith("ignored"));
            context.register(ObservationAutoConfiguration.class);
            context.refresh();

            assertEquals(1, context.getBeansOfType(ObservationRegistry.class).size());
            assertSame(suppliedRegistry, context.getBean(ObservationRegistry.class));
            assertSame(suppliedRegistry, customizer.customizedRegistry);
            Observation.createNotStarted("checkout.submit", suppliedRegistry).observe(() -> { });
            Observation.createNotStarted("ignored.health", suppliedRegistry).observe(() -> { });

            assertEquals(1, handler.completed.size());
            assertEquals(KeyValue.of("customized", "yes"),
                    handler.completed.getFirst().getLowCardinalityKeyValue("customized"));
        }
    }

    @Test
    void defaultPropertiesEnableObservationsAndUseStandardHttpNames() {
        RecordingHandler handler = new RecordingHandler();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(RecordingHandler.class, () -> handler);
            context.register(ObservationAutoConfiguration.class);
            context.refresh();

            ObservationProperties properties = context.getBean(ObservationProperties.class);
            assertTrue(properties.getEnable().isEmpty());
            assertTrue(properties.getKeyValues().isEmpty());
            assertEquals("http.client.requests", properties.getHttp().getClient().getRequests().getName());
            assertEquals("http.server.requests", properties.getHttp().getServer().getRequests().getName());
            ObservationRegistry registry = context.getBean(ObservationRegistry.class);
            assertFalse(registry.isNoop());
            Observation.createNotStarted("inventory.read", registry).observe(() -> { });
            assertEquals(List.of("inventory.read"), handler.completed.stream().map(Observation.Context::getName).toList());
        }
    }

    public static class RecordingHandler implements ObservationHandler<Observation.Context> {
        private final List<Observation.Context> completed = new ArrayList<>();

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

        @Override
        public void onStop(Observation.Context context) {
            completed.add(context);
        }
    }

    public static class RegionCustomizer implements ObservationRegistryCustomizer<ObservationRegistry> {
        private ObservationRegistry customizedRegistry;

        @Override
        public void customize(ObservationRegistry registry) {
            customizedRegistry = registry;
            registry.observationConfig().observationFilter(context ->
                    context.addLowCardinalityKeyValue(KeyValue.of("customized", "yes")));
        }
    }
}
