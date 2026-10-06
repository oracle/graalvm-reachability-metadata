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
import io.micrometer.tracing.propagation.Propagator;
import io.micrometer.tracing.test.simple.SimpleSpan;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration;
import org.springframework.boot.micrometer.tracing.autoconfigure.NoopTracerAutoConfiguration;
import org.springframework.boot.micrometer.tracing.autoconfigure.TracingProperties;
import org.springframework.boot.micrometer.tracing.autoconfigure.TracingProperties.Exemplars.Include;
import org.springframework.boot.micrometer.tracing.autoconfigure.TracingProperties.Propagation.PropagationType;
import org.springframework.boot.micrometer.tracing.autoconfigure.otlp.OtlpExemplarsAutoConfiguration;
import org.springframework.boot.micrometer.tracing.autoconfigure.prometheus.PrometheusExemplarsAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

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
    void tracingPropertiesExposeSamplingBaggagePropagationAndExemplarSettings() {
        TracingProperties properties = new TracingProperties();

        assertThat(properties.getSampling().getProbability()).isEqualTo(0.10f);
        assertThat(properties.getBaggage().isEnabled()).isTrue();
        assertThat(properties.getBaggage().getCorrelation().isEnabled()).isTrue();
        assertThat(properties.getPropagation().getType()).isNull();
        assertThat(properties.getPropagation().getProduce()).containsExactly(PropagationType.W3C);
        assertThat(properties.getPropagation().getConsume()).containsExactly(PropagationType.values());
        assertThat(properties.getExemplars().getInclude()).isEqualTo(Include.SAMPLED_TRACES);

        properties.getSampling().setProbability(0.75f);
        properties.getBaggage().setEnabled(false);
        properties.getBaggage().getCorrelation().setEnabled(false);
        properties.getBaggage().setRemoteFields(List.of("tenant-id"));
        properties.getBaggage().setLocalFields(List.of("request-id"));
        properties.getBaggage().setTagFields(List.of("tenant-id"));
        properties.getBaggage().getCorrelation().setFields(List.of("tenant-id"));
        properties.getPropagation().setType(List.of(PropagationType.B3));
        properties.getPropagation().setProduce(List.of(PropagationType.B3));
        properties.getPropagation().setConsume(List.of(PropagationType.B3_MULTI));
        properties.getExemplars().setInclude(Include.ALL);

        assertThat(properties.getSampling().getProbability()).isEqualTo(0.75f);
        assertThat(properties.getBaggage().isEnabled()).isFalse();
        assertThat(properties.getBaggage().getCorrelation().isEnabled()).isFalse();
        assertThat(properties.getBaggage().getRemoteFields()).containsExactly("tenant-id");
        assertThat(properties.getBaggage().getLocalFields()).containsExactly("request-id");
        assertThat(properties.getBaggage().getTagFields()).containsExactly("tenant-id");
        assertThat(properties.getBaggage().getCorrelation().getFields()).containsExactly("tenant-id");
        assertThat(properties.getPropagation().getType()).containsExactly(PropagationType.B3);
        assertThat(properties.getPropagation().getProduce()).containsExactly(PropagationType.B3);
        assertThat(properties.getPropagation().getConsume()).containsExactly(PropagationType.B3_MULTI);
        assertThat(properties.getExemplars().getInclude()).isEqualTo(Include.ALL);
    }

    @Test
    void autoConfigurationCreatesTracingHandlersForConcreteTracer() {
        SimpleTracer tracer = new SimpleTracer();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(Tracer.class, () -> tracer);
            context.registerBean(Propagator.class, () -> Propagator.NOOP);
            context.register(MicrometerTracingAutoConfiguration.class);
            context.refresh();

            assertThat(context.getBean(Tracer.class)).isSameAs(tracer);
            assertThat(context.getBeansOfType(DefaultTracingObservationHandler.class)).hasSize(1);
            assertThat(context.getBeansOfType(PropagatingSenderTracingObservationHandler.class)).hasSize(1);
            assertThat(context.getBeansOfType(PropagatingReceiverTracingObservationHandler.class)).hasSize(1);

            ObservationRegistry registry = ObservationRegistry.create();
            registry.observationConfig().observationHandler(context.getBean(DefaultTracingObservationHandler.class));
            Observation observation = Observation.createNotStarted("orders.process", registry)
                .contextualName("process orders")
                .lowCardinalityKeyValue("operation", "process")
                .start();
            try (Observation.Scope ignored = observation.openScope()) {
                assertThat(tracer.currentSpan()).isNotNull();
                observation.event(Observation.Event.of("orders.loaded", "orders loaded"));
            }
            observation.stop();

            SimpleSpan span = tracer.onlySpan();
            assertThat(span.getName()).isEqualTo("process orders");
            assertThat(span.getTags()).containsEntry("operation", "process");
            assertThat(span.getEvents().stream().map(Map.Entry::getValue).toList()).contains("orders loaded");
            assertThat(span.getEndTimestamp().toEpochMilli()).isPositive();
            assertThat(tracer.currentSpan()).isNull();
        }
    }

}
