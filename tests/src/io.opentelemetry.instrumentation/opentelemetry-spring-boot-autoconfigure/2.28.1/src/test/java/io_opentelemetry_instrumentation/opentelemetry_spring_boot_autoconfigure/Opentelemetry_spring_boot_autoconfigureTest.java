/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_instrumentation.opentelemetry_spring_boot_autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.EarlyConfig;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.OtelMapConverter;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.OtelResourceProperties;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.OtelSpringProperties;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.resources.SpringResourceProvider;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.info.BuildProperties;
import org.springframework.mock.env.MockEnvironment;

public class Opentelemetry_spring_boot_autoconfigureTest {
    @Test
    void evaluatesClassicConfigurationProperties() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("otel.sdk.disabled", "true")
                .withProperty("otel.instrumentation.jdbc.enabled", "false");

        assertThat(EarlyConfig.isDeclarativeConfig(environment)).isFalse();
        assertThat(EarlyConfig.otelEnabled(environment)).isFalse();
        assertThat(EarlyConfig.isInstrumentationEnabled(environment, "jdbc", true)).isFalse();
        assertThat(EarlyConfig.isInstrumentationEnabled(environment, "r2dbc", true)).isTrue();
    }

    @Test
    void evaluatesDeclarativeInstrumentationLists() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("otel.file_format", "declarative")
                .withProperty("otel.disabled", "false")
                .withProperty(
                        "otel.distribution.spring-starter.instrumentation.disabled", "jdbc")
                .withProperty(
                        "otel.distribution.spring-starter.instrumentation.enabled", "r2dbc")
                .withProperty(
                        "otel.distribution.spring_starter.instrumentation.default_enabled",
                        "false");

        assertThat(EarlyConfig.isDeclarativeConfig(environment)).isTrue();
        assertThat(EarlyConfig.otelEnabled(environment)).isTrue();
        assertThat(EarlyConfig.isInstrumentationEnabled(environment, "jdbc", true)).isFalse();
        assertThat(EarlyConfig.isInstrumentationEnabled(environment, "r2dbc", false)).isTrue();
        assertThat(EarlyConfig.isInstrumentationEnabled(environment, "spring-web", true))
                .isFalse();
    }

    @Test
    void bindsSpringConfigurationProperties() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("otel.propagators", "tracecontext,baggage")
                .withProperty("otel.traces.exporter", "none,otlp")
                .withProperty("otel.resource.attributes[service.name]", "orders")
                .withProperty(
                        "otel.resource.attributes[deployment.environment]", "testing");
        Binder binder = Binder.get(environment);

        OtelSpringProperties springProperties =
                binder.bind("otel", Bindable.of(OtelSpringProperties.class)).get();
        OtelResourceProperties resourceProperties = binder.bind(
                        "otel.resource", Bindable.of(OtelResourceProperties.class))
                .get();

        assertThat(springProperties.getPropagators())
                .containsExactly("tracecontext", "baggage");
        assertThat(springProperties.getTracesExporter()).containsExactly("none", "otlp");
        assertThat(resourceProperties.getAttributes())
                .containsEntry("service.name", "orders")
                .containsEntry("deployment.environment", "testing");
    }

    @Test
    void convertsMapPropertiesAndBuildsSpringResource() {
        Map<String, String> converted = new OtelMapConverter()
                .convert("region=us-east-1,service.name=orders");

        Properties buildEntries = new Properties();
        buildEntries.setProperty("name", "orders-build");
        buildEntries.setProperty("version", "1.0-test");
        SpringResourceProvider provider = new SpringResourceProvider(
                Optional.of(new BuildProperties(buildEntries)));
        Resource resource = provider.createResource(DefaultConfigProperties.createFromMap(
                Map.of("spring.application.name", "orders-service")));

        assertThat(converted)
                .containsEntry("region", "us-east-1")
                .containsEntry("service.name", "orders");
        assertThat(resource.getAttribute(AttributeKey.stringKey("service.name")))
                .isEqualTo("orders-service");
        assertThat(resource.getAttribute(AttributeKey.stringKey("service.version")))
                .isEqualTo("1.0-test");
    }
}
