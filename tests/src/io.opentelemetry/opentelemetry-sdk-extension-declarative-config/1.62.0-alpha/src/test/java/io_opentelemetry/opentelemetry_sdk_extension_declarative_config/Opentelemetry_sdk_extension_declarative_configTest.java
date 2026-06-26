/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_sdk_extension_declarative_config;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.config.DeclarativeConfigException;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfigResult;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingDecision;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class Opentelemetry_sdk_extension_declarative_configTest {
    private static final String TRACE_ID = "00000000000000000000000000000001";
    private static final String SPAN_ID = "0000000000000001";

    @Test
    void yamlCanBeReadAsTypedDeclarativeConfigProperties() {
        String propertyName = "otel.test.declarative.service.name";
        String previousPropertyValue = System.getProperty(propertyName);
        System.setProperty(propertyName, "checkout-service");
        try {
            DeclarativeConfigProperties properties = DeclarativeConfiguration.toConfigProperties(yaml("""
                    file_format: "1.0"
                    disabled: false
                    resource:
                      attributes:
                        - name: service.name
                          value: ${sys:otel.test.declarative.service.name}
                        - name: service.namespace
                          value: ${env:OTEL_DECLARATIVE_CONFIG_MISSING:-fallback-namespace}
                        - name: feature.enabled
                          value: true
                          type: bool
                    custom_strings:
                      - first
                      - second
                    custom_numbers:
                      - 1
                      - 2
                      - 3
                    """));

            assertThat(properties.getPropertyKeys())
                    .containsExactlyInAnyOrder(
                            "file_format", "disabled", "resource", "custom_strings", "custom_numbers");
            assertThat(properties.getString("file_format")).isEqualTo("1.0");
            assertThat(properties.getBoolean("disabled")).isFalse();
            assertThat(properties.getScalarList("custom_strings", String.class))
                    .containsExactly("first", "second");
            assertThat(properties.getScalarList("custom_numbers", Long.class))
                    .containsExactly(1L, 2L, 3L);

            DeclarativeConfigProperties resource = properties.getStructured("resource");
            assertThat(resource).isNotNull();
            List<DeclarativeConfigProperties> attributes = resource.getStructuredList("attributes");
            assertThat(attributes).hasSize(3);
            assertThat(attributes.get(0).getString("name")).isEqualTo("service.name");
            assertThat(attributes.get(0).getString("value")).isEqualTo("checkout-service");
            assertThat(attributes.get(1).getString("value")).isEqualTo("fallback-namespace");
            assertThat(attributes.get(2).getBoolean("value")).isTrue();
        } finally {
            restoreSystemProperty(propertyName, previousPropertyValue);
        }
    }

    @Test
    void parseAndCreateBuildsSdkWithResourceAndSamplerConfiguration() {
        DeclarativeConfigResult result = DeclarativeConfiguration.parseAndCreate(yaml("""
                file_format: "1.0"
                resource:
                  schema_url: https://opentelemetry.io/schemas/1.37.0
                  attributes_list: deployment.environment.name=integration,service.namespace=metadata
                  attributes:
                    - name: service.name
                      value: declarative-config-test
                    - name: service.instance.id
                      value: 42
                      type: int
                propagator:
                  composite:
                    - tracecontext: {}
                    - baggage: {}
                tracer_provider:
                  sampler:
                    always_on: {}
                  processors: []
                meter_provider:
                  readers: []
                logger_provider:
                  processors: []
                """));
        try {
            Resource resource = result.getResource();

            assertThat(resource.getSchemaUrl()).isEqualTo("https://opentelemetry.io/schemas/1.37.0");
            assertThat(resource.getAttribute(AttributeKey.stringKey("service.name")))
                    .isEqualTo("declarative-config-test");
            assertThat(resource.getAttribute(AttributeKey.stringKey("deployment.environment.name")))
                    .isEqualTo("integration");
            assertThat(resource.getAttribute(AttributeKey.longKey("service.instance.id"))).isEqualTo(42L);
            assertThat(result.getSdk().getPropagators().getTextMapPropagator().fields())
                    .contains("traceparent", "baggage");
            assertThat(result.getSdk().getSdkTracerProvider().getSampler().getDescription())
                    .containsIgnoringCase("AlwaysOn");
        } finally {
            result.getSdk().close();
        }
    }

    @Test
    void createSamplerInterpretsNestedParentBasedSamplerConfiguration() {
        DeclarativeConfigProperties samplerProperties = DeclarativeConfiguration.toConfigProperties(yaml("""
                parent_based:
                  root:
                    always_off: {}
                  remote_parent_sampled:
                    always_on: {}
                  remote_parent_not_sampled:
                    always_off: {}
                  local_parent_sampled:
                    always_on: {}
                  local_parent_not_sampled:
                    always_off: {}
                """));

        Sampler sampler = DeclarativeConfiguration.createSampler(samplerProperties);

        assertThat(sampler.getDescription()).contains("ParentBased");
        assertThat(sampler.shouldSample(
                        Context.root(),
                        TRACE_ID,
                        SPAN_ID,
                        SpanKind.INTERNAL,
                        Attributes.empty(),
                        Collections.emptyList())
                .getDecision())
                .isEqualTo(SamplingDecision.DROP);
    }

    @Test
    void unsupportedFileFormatIsReportedAsDeclarativeConfigException() {
        assertThatExceptionOfType(DeclarativeConfigException.class)
                .isThrownBy(() -> DeclarativeConfiguration.parseAndCreate(yaml("""
                        file_format: "2.0"
                        disabled: true
                        """)))
                .withMessageContaining("Unsupported file format");
    }

    private static InputStream yaml(String yaml) {
        return new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    }

    private static void restoreSystemProperty(String name, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, previousValue);
        }
    }
}
