/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_prometheus.simpleclient_tracer_otel_agent;

import io.prometheus.client.exemplars.tracer.common.SpanContextSupplier;
import io.prometheus.client.exemplars.tracer.otel_agent.OpenTelemetryAgentSpanContextSupplier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Simpleclient_tracer_otel_agentTest {
    private static final String OTEL_EXEMPLARS_PROPERTY = "io.prometheus.otelExemplars";

    @Test
    void supplierImplementsThePrometheusSpanContextContract() {
        OpenTelemetryAgentSpanContextSupplier supplier = new OpenTelemetryAgentSpanContextSupplier();

        assertThat(supplier).isInstanceOf(SpanContextSupplier.class);
    }

    @Test
    void inactivePropertyDisablesAvailabilityCaseInsensitively() {
        for (String propertyValue : List.of("inactive", "INACTIVE", "InAcTiVe")) {
            assertThat(isAvailableWithOtelExemplarsProperty(propertyValue)).isFalse();
        }
    }

    @Test
    void inactivePropertyAvoidsTouchingAgentShadedOpenTelemetryApi() {
        assertThat(isAvailableWithOtelExemplarsProperty("inactive")).isFalse();
    }

    @Test
    void availabilityReturnsFalseWhenAgentShadedOpenTelemetryApiIsNotPresent() {
        assertThat(isAvailableWithOtelExemplarsProperty(null)).isFalse();
    }

    @Test
    void nonInactivePropertyStillRequiresAgentShadedOpenTelemetryApi() {
        assertThat(isAvailableWithOtelExemplarsProperty("active")).isFalse();
    }

    @Test
    void availabilityCheckDoesNotMutateConfiguredOtelExemplarsProperty() {
        Properties properties = System.getProperties();
        boolean hadPreviousValue = properties.containsKey(OTEL_EXEMPLARS_PROPERTY);
        String previousValue = properties.getProperty(OTEL_EXEMPLARS_PROPERTY);
        try {
            System.setProperty(OTEL_EXEMPLARS_PROPERTY, "custom");

            OpenTelemetryAgentSpanContextSupplier.isAvailable();

            assertThat(System.getProperty(OTEL_EXEMPLARS_PROPERTY)).isEqualTo("custom");
        } finally {
            if (hadPreviousValue) {
                System.setProperty(OTEL_EXEMPLARS_PROPERTY, previousValue);
            } else {
                System.clearProperty(OTEL_EXEMPLARS_PROPERTY);
            }
        }
    }

    @Test
    void spanContextMethodsRequireAgentShadedOpenTelemetryApiOnClasspath() {
        SpanContextSupplier supplier = new OpenTelemetryAgentSpanContextSupplier();

        assertThatThrownBy(supplier::getTraceId).isInstanceOf(LinkageError.class);
        assertThatThrownBy(supplier::getSpanId).isInstanceOf(LinkageError.class);
        assertThatThrownBy(supplier::isSampled).isInstanceOf(LinkageError.class);
    }

    @Test
    void inactivePropertyDoesNotMaskSpanContextLookups() {
        Properties properties = System.getProperties();
        boolean hadPreviousValue = properties.containsKey(OTEL_EXEMPLARS_PROPERTY);
        String previousValue = properties.getProperty(OTEL_EXEMPLARS_PROPERTY);
        try {
            System.setProperty(OTEL_EXEMPLARS_PROPERTY, "inactive");
            SpanContextSupplier supplier = new OpenTelemetryAgentSpanContextSupplier();

            assertThatThrownBy(supplier::getTraceId).isInstanceOf(LinkageError.class);
            assertThatThrownBy(supplier::getSpanId).isInstanceOf(LinkageError.class);
            assertThatThrownBy(supplier::isSampled).isInstanceOf(LinkageError.class);
        } finally {
            if (hadPreviousValue) {
                System.setProperty(OTEL_EXEMPLARS_PROPERTY, previousValue);
            } else {
                System.clearProperty(OTEL_EXEMPLARS_PROPERTY);
            }
        }
    }

    private static boolean isAvailableWithOtelExemplarsProperty(String propertyValue) {
        Properties properties = System.getProperties();
        boolean hadPreviousValue = properties.containsKey(OTEL_EXEMPLARS_PROPERTY);
        String previousValue = properties.getProperty(OTEL_EXEMPLARS_PROPERTY);
        try {
            if (propertyValue == null) {
                System.clearProperty(OTEL_EXEMPLARS_PROPERTY);
            } else {
                System.setProperty(OTEL_EXEMPLARS_PROPERTY, propertyValue);
            }
            return OpenTelemetryAgentSpanContextSupplier.isAvailable();
        } finally {
            if (hadPreviousValue) {
                System.setProperty(OTEL_EXEMPLARS_PROPERTY, previousValue);
            } else {
                System.clearProperty(OTEL_EXEMPLARS_PROPERTY);
            }
        }
    }
}
