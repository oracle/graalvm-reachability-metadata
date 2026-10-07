/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_micrometer_tracing;

import java.util.List;
import java.util.Map;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import io.micrometer.tracing.handler.TracingObservationHandler;
import io.micrometer.tracing.propagation.Propagator;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationHandlerGroup;
import org.springframework.boot.micrometer.tracing.autoconfigure.ConditionalOnEnabledTracingExport;
import org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration;
import org.springframework.boot.micrometer.tracing.autoconfigure.NoopTracerAutoConfiguration;
import org.springframework.boot.micrometer.tracing.autoconfigure.TracingProperties;
import org.springframework.boot.micrometer.tracing.autoconfigure.TracingProperties.Exemplars.Include;
import org.springframework.boot.micrometer.tracing.autoconfigure.TracingProperties.Propagation.PropagationType;
import org.springframework.boot.micrometer.tracing.autoconfigure.otlp.OtlpExemplarsAutoConfiguration;
import org.springframework.boot.micrometer.tracing.autoconfigure.prometheus.PrometheusExemplarsAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_micrometer_tracingTest {

    @Test
    void autoConfigurationIsAdvertisedForSpringBootDiscovery() {
        ImportCandidates candidates = ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader());

        assertThat(candidates.getCandidates()).contains(MicrometerTracingAutoConfiguration.class.getName(),
                NoopTracerAutoConfiguration.class.getName(), OtlpExemplarsAutoConfiguration.class.getName(),
                PrometheusExemplarsAutoConfiguration.class.getName());
    }

    @Test
    void autoConfigurationProvidesNoopTracerWhenNoTracerIsConfigured() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(NoopTracerAutoConfiguration.class);
            context.refresh();

            assertThat(context.getBean(Tracer.class)).isSameAs(Tracer.NOOP);
        }
    }

    @Test
    void tracingPropertiesBindAllNestedConsumerSettings() {
        TracingProperties defaults = new TracingProperties();
        assertThat(defaults.getSampling().getProbability()).isEqualTo(0.10f);
        assertThat(defaults.getBaggage().isEnabled()).isTrue();
        assertThat(defaults.getBaggage().getCorrelation().isEnabled()).isTrue();
        assertThat(defaults.getPropagation().getType()).isNull();
        assertThat(defaults.getPropagation().getProduce()).containsExactly(PropagationType.W3C);
        assertThat(defaults.getPropagation().getConsume()).containsExactly(PropagationType.values());
        assertThat(defaults.getExemplars().getInclude()).isEqualTo(Include.SAMPLED_TRACES);

        MapConfigurationPropertySource source = new MapConfigurationPropertySource();
        source.put("management.tracing.sampling.probability", "0.75");
        source.put("management.tracing.baggage.enabled", "false");
        source.put("management.tracing.baggage.correlation.enabled", "false");
        source.put("management.tracing.baggage.remote-fields", "tenant-id,locale");
        source.put("management.tracing.baggage.local-fields", "request-id");
        source.put("management.tracing.baggage.tag-fields", "tenant-id");
        source.put("management.tracing.baggage.correlation.fields", "tenant-id,request-id");
        source.put("management.tracing.propagation.type", "b3");
        source.put("management.tracing.propagation.produce", "b3,b3-multi");
        source.put("management.tracing.propagation.consume", "w3c,b3");
        source.put("management.tracing.exemplars.include", "all");

        TracingProperties properties = new Binder(source)
                .bind("management.tracing", Bindable.of(TracingProperties.class))
                .orElseThrow(() -> new AssertionError("Tracing properties were not bound"));

        assertThat(properties.getSampling().getProbability()).isEqualTo(0.75f);
        assertThat(properties.getBaggage().isEnabled()).isFalse();
        assertThat(properties.getBaggage().getCorrelation().isEnabled()).isFalse();
        assertThat(properties.getBaggage().getRemoteFields()).containsExactly("tenant-id", "locale");
        assertThat(properties.getBaggage().getLocalFields()).containsExactly("request-id");
        assertThat(properties.getBaggage().getTagFields()).containsExactly("tenant-id");
        assertThat(properties.getBaggage().getCorrelation().getFields()).containsExactly("tenant-id", "request-id");
        assertThat(properties.getPropagation().getType()).containsExactly(PropagationType.B3);
        assertThat(properties.getPropagation().getProduce()).containsExactly(PropagationType.B3,
                PropagationType.B3_MULTI);
        assertThat(properties.getPropagation().getConsume()).containsExactly(PropagationType.W3C, PropagationType.B3);
        assertThat(properties.getExemplars().getInclude()).isEqualTo(Include.ALL);
    }

    @Test
    void micrometerAutoConfigurationWiresAndOrdersTracingHandlers() {
        SimpleTracer tracer = new SimpleTracer();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(Tracer.class, () -> tracer);
            context.registerBean(Propagator.class, () -> Propagator.NOOP);
            context.register(MicrometerTracingAutoConfiguration.class);
            context.refresh();

            DefaultTracingObservationHandler defaultHandler = context.getBean(DefaultTracingObservationHandler.class);
            PropagatingReceiverTracingObservationHandler<?> receiverHandler =
                    context.getBean(PropagatingReceiverTracingObservationHandler.class);
            PropagatingSenderTracingObservationHandler<?> senderHandler =
                    context.getBean(PropagatingSenderTracingObservationHandler.class);
            ObservationHandlerGroup group = context.getBean(ObservationHandlerGroup.class);

            assertThat(group.isMember(defaultHandler)).isTrue();
            List<?> orderedHandlers = context.getBeanProvider(TracingObservationHandler.class)
                    .orderedStream()
                    .toList();
            assertThat(orderedHandlers).hasSize(3);
            assertThat(orderedHandlers.get(0)).isSameAs(receiverHandler);
            assertThat(orderedHandlers.get(1)).isSameAs(senderHandler);
            assertThat(orderedHandlers.get(2)).isSameAs(defaultHandler);

            ObservationRegistry registry = ObservationRegistry.create();
            registry.observationConfig().observationHandler(defaultHandler);
            Observation.createNotStarted("checkout", registry)
                    .observe(() -> assertThat(tracer.currentSpan()).isNotNull());

            assertThat(tracer.onlySpan().getName()).isEqualTo("checkout");
        }
    }

    @Test
    void exporterSpecificSettingTakesPrecedenceOverGlobalSetting() {
        try (AnnotationConfigApplicationContext defaults = loadExporterContext(Map.of());
                AnnotationConfigApplicationContext globallyDisabled =
                        loadExporterContext(Map.of("management.tracing.export.enabled", false));
                AnnotationConfigApplicationContext exporterEnabled = loadExporterContext(Map.of(
                        "management.tracing.export.enabled", false,
                        "management.tracing.export.test.enabled", true));
                AnnotationConfigApplicationContext exporterDisabled = loadExporterContext(Map.of(
                        "management.tracing.export.enabled", true,
                        "management.tracing.export.test.enabled", false))) {
            assertThat(defaults.containsBean("testTracingExporter")).isTrue();
            assertThat(globallyDisabled.containsBean("testTracingExporter")).isFalse();
            assertThat(exporterEnabled.getBean("testTracingExporter", String.class)).isEqualTo("enabled");
            assertThat(exporterDisabled.containsBean("testTracingExporter")).isFalse();
        }
    }

    private static AnnotationConfigApplicationContext loadExporterContext(Map<String, Object> properties) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        context.register(TestTracingExporterConfiguration.class);
        context.refresh();
        return context;
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnEnabledTracingExport("test")
    public static class TestTracingExporterConfiguration {

        @Bean
        String testTracingExporter() {
            return "enabled";
        }

    }

}
