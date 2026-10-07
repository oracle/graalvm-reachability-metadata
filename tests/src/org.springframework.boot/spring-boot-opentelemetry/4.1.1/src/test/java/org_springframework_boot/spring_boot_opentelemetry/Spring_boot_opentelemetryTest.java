/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_opentelemetry;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.LogLimits;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.resources.Resource;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.logging.DeferredLogs;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetryEnvironmentVariableEnvironmentPostProcessor;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetryProperties;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetryResourceAttributes;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import org.springframework.boot.opentelemetry.autoconfigure.logging.OpenTelemetryLoggingAutoConfiguration;
import org.springframework.boot.opentelemetry.autoconfigure.logging.otlp.OtlpGrpcLogRecordExporterBuilderCustomizer;
import org.springframework.boot.opentelemetry.autoconfigure.logging.otlp.OtlpHttpLogRecordExporterBuilderCustomizer;
import org.springframework.boot.opentelemetry.autoconfigure.logging.otlp.OtlpLoggingAutoConfiguration;
import org.springframework.boot.opentelemetry.autoconfigure.logging.otlp.OtlpLoggingConnectionDetails;
import org.springframework.boot.opentelemetry.autoconfigure.logging.otlp.OtlpLoggingProperties;
import org.springframework.boot.opentelemetry.autoconfigure.logging.otlp.Transport;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_opentelemetryTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OpenTelemetrySdkAutoConfiguration.class,
                    OpenTelemetryLoggingAutoConfiguration.class, OtlpLoggingAutoConfiguration.class));

    @Test
    void sdkAutoConfigurationUsesDefaultsAndBuildsLoggingProvider() {
        this.contextRunner
                .withPropertyValues("spring.application.name=orders", "spring.application.group=commerce",
                        "management.opentelemetry.resource-attributes.deployment.environment=test")
                .run((context) -> {
                    assertThat(context).hasSingleBean(OpenTelemetryProperties.class);
                    OpenTelemetryProperties properties = context.getBean(OpenTelemetryProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getResourceAttributes()).containsEntry("deployment.environment", "test");
                    assertThat(context).hasSingleBean(OtlpLoggingProperties.class);
                    OtlpLoggingProperties loggingProperties = context.getBean(OtlpLoggingProperties.class);
                    assertThat(loggingProperties.getEndpoint()).isNull();
                    assertThat(loggingProperties.getTimeout()).isEqualTo(Duration.ofSeconds(10));
                    assertThat(loggingProperties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(10));
                    assertThat(loggingProperties.getTransport()).isEqualTo(Transport.HTTP);
                    assertThat(loggingProperties.getCompression()).isEqualTo(OtlpLoggingProperties.Compression.NONE);
                    assertThat(loggingProperties.getHeaders()).isEmpty();
                    assertThat(context).hasSingleBean(Resource.class);
                    assertThat(context).hasSingleBean(SdkLoggerProvider.class);
                    assertThat(context).hasSingleBean(OpenTelemetrySdk.class);

                    Resource resource = context.getBean(Resource.class);
                    assertThat(resource.getAttribute(AttributeKey.stringKey("service.name"))).isEqualTo("orders");
                    assertThat(resource.getAttribute(AttributeKey.stringKey("service.namespace")))
                            .isEqualTo("commerce");
                    assertThat(resource.getAttribute(AttributeKey.stringKey("deployment.environment")))
                            .isEqualTo("test");
                    assertThat(context.getBean(OpenTelemetrySdk.class).getSdkLoggerProvider())
                            .isSameAs(context.getBean(SdkLoggerProvider.class));
                    assertThat(context.getBean(OpenTelemetrySdk.class).shutdown().join(10, TimeUnit.SECONDS)
                            .isSuccess()).isTrue();
                });
    }

    @Test
    void disabledOpenTelemetryCreatesSdkWithoutLoggingProvider() {
        this.contextRunner
                .withPropertyValues("management.opentelemetry.enabled=false")
                .run((context) -> {
                    assertThat(context).hasSingleBean(OpenTelemetryProperties.class);
                    assertThat(context.getBean(OpenTelemetryProperties.class).isEnabled()).isFalse();
                    assertThat(context).hasSingleBean(OpenTelemetrySdk.class);
                    assertThat(context).doesNotHaveBean(SdkLoggerProvider.class);
                    assertThat(context.getBean(OpenTelemetrySdk.class).shutdown().join(10, TimeUnit.SECONDS)
                            .isSuccess()).isTrue();
                });
    }

    @Test
    void loggingLimitsConfigureSdkLoggerProvider() {
        this.contextRunner
                .withPropertyValues("management.opentelemetry.enabled=true", "management.logging.export.enabled=true",
                        "management.opentelemetry.logging.limits.max-attributes=7",
                        "management.opentelemetry.logging.limits.max-attribute-value-length=64")
                .run((context) -> {
                    assertThat(context).hasSingleBean(LogLimits.class);
                    LogLimits limits = context.getBean(LogLimits.class);
                    assertThat(limits.getMaxNumberOfAttributes()).isEqualTo(7);
                    assertThat(limits.getMaxAttributeValueLength()).isEqualTo(64);
                    assertSdkShutdown(context);
                });
    }

    @Test
    void httpLoggingAutoConfigurationBindsPropertiesBuildsExporterAndAppliesCustomizer() {
        AtomicBoolean customizerInvoked = new AtomicBoolean();
        this.contextRunner
                .withPropertyValues("management.opentelemetry.enabled=true", "management.logging.export.enabled=true",
                        "management.logging.export.otlp.enabled=true",
                        "management.opentelemetry.logging.export.otlp.endpoint=http://collector.example.test/v1/logs",
                        "management.opentelemetry.logging.export.otlp.timeout=15s",
                        "management.opentelemetry.logging.export.otlp.connect-timeout=12s",
                        "management.opentelemetry.logging.export.otlp.compression=gzip",
                        "management.opentelemetry.logging.export.otlp.headers.authorization=Bearer test-token")
                .withBean(OtlpHttpLogRecordExporterBuilderCustomizer.class, () -> (builder) -> {
                    customizerInvoked.set(true);
                    builder.setEndpoint("http://custom-collector.example.test/v1/logs");
                })
                .run((context) -> {
                    assertThat(context).hasSingleBean(OtlpLoggingProperties.class);
                    OtlpLoggingProperties properties = context.getBean(OtlpLoggingProperties.class);
                    assertThat(properties.getEndpoint()).isEqualTo("http://collector.example.test/v1/logs");
                    assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(15));
                    assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(12));
                    assertThat(properties.getTransport()).isEqualTo(Transport.HTTP);
                    assertThat(properties.getCompression()).isEqualTo(OtlpLoggingProperties.Compression.GZIP);
                    assertThat(properties.getHeaders()).containsEntry("authorization", "Bearer test-token");

                    assertThat(context).hasSingleBean(OtlpLoggingConnectionDetails.class);
                    assertThat(context.getBean(OtlpLoggingConnectionDetails.class).getUrl(Transport.HTTP))
                            .isEqualTo("http://collector.example.test/v1/logs");
                    assertThat(context).hasSingleBean(OtlpHttpLogRecordExporter.class);
                    assertThat(context.getBean(OtlpHttpLogRecordExporter.class).toString())
                            .contains("endpoint=http://custom-collector.example.test/v1/logs",
                                    "compressorEncoding=gzip");
                    assertThat(customizerInvoked).isTrue();
                    assertSdkShutdown(context);
                });
    }

    @Test
    void grpcLoggingAutoConfigurationBuildsExporterAndAppliesCustomizer() {
        AtomicBoolean customizerInvoked = new AtomicBoolean();
        this.contextRunner
                .withPropertyValues("management.opentelemetry.enabled=true", "management.logging.export.enabled=true",
                        "management.logging.export.otlp.enabled=true",
                        "management.opentelemetry.logging.export.otlp.endpoint=http://collector.example.test:4317",
                        "management.opentelemetry.logging.export.otlp.transport=grpc",
                        "management.opentelemetry.logging.export.otlp.compression=gzip")
                .withBean(OtlpGrpcLogRecordExporterBuilderCustomizer.class, () -> (builder) -> {
                    customizerInvoked.set(true);
                    builder.setEndpoint("http://custom-collector.example.test:4317");
                })
                .run((context) -> {
                    assertThat(context).hasSingleBean(OtlpGrpcLogRecordExporter.class);
                    assertThat(context).doesNotHaveBean(OtlpHttpLogRecordExporter.class);
                    assertThat(context.getBean(OtlpLoggingProperties.class).getTransport()).isEqualTo(Transport.GRPC);
                    assertThat(context.getBean(OtlpLoggingConnectionDetails.class).getUrl(Transport.GRPC))
                            .isEqualTo("http://collector.example.test:4317");
                    assertThat(context.getBean(OtlpGrpcLogRecordExporter.class).toString())
                            .contains("endpoint=http://custom-collector.example.test:4317", "compressorEncoding=gzip");
                    assertThat(customizerInvoked).isTrue();
                    assertSdkShutdown(context);
                });
    }

    @Test
    void suppliedConnectionDetailsEnableHttpExportWithoutAnEndpointProperty() {
        this.contextRunner
                .withPropertyValues("management.opentelemetry.enabled=true", "management.logging.export.enabled=true",
                        "management.logging.export.otlp.enabled=true")
                .withBean(OtlpLoggingConnectionDetails.class,
                        () -> (transport) -> "http://connection-details.example.test/v1/logs")
                .run((context) -> {
                    assertThat(context.getBean(OtlpLoggingProperties.class).getEndpoint()).isNull();
                    assertThat(context).hasSingleBean(OtlpHttpLogRecordExporter.class);
                    assertThat(context.getBean(OtlpHttpLogRecordExporter.class).toString())
                            .contains("endpoint=http://connection-details.example.test/v1/logs");
                    assertSdkShutdown(context);
                });
    }

    @Test
    void disabledLoggingExportDoesNotCreateAnOtlpExporter() {
        this.contextRunner
                .withPropertyValues("management.logging.export.otlp.enabled=false",
                        "management.opentelemetry.logging.export.otlp.endpoint=http://collector.example.test/v1/logs")
                .run((context) -> {
                    assertThat(context).hasSingleBean(OtlpLoggingConnectionDetails.class);
                    assertThat(context).doesNotHaveBean(OtlpHttpLogRecordExporter.class);
                    assertThat(context).doesNotHaveBean(OtlpGrpcLogRecordExporter.class);
                    assertSdkShutdown(context);
                });
    }

    @Test
    void resourceAttributesCombineApplicationPropertiesAndConfiguredAttributes() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "billing")
                .withProperty("spring.application.group", "finance");
        Map<String, String> attributes = new LinkedHashMap<>();

        new OpenTelemetryResourceAttributes(environment, Map.of("deployment.environment", "test"))
                .applyTo(attributes::put);

        assertThat(attributes).containsEntry("service.name", "billing")
                .containsEntry("service.namespace", "finance")
                .containsEntry("deployment.environment", "test");
    }

    @Test
    void environmentVariableMappingCanBeDisabledThroughTheEnvironment() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("management.opentelemetry.map-environment-variables", "false")
                .withProperty("management.opentelemetry.enabled", "true");

        new OpenTelemetryEnvironmentVariableEnvironmentPostProcessor(new DeferredLogs())
                .postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("management.opentelemetry.enabled")).isEqualTo("true");
        assertThat(environment.getPropertySources().contains("openTelemetryEnvironmentVariables")).isFalse();
    }

    private static void assertSdkShutdown(ConfigurableApplicationContext context) {
        assertThat(context.getBean(OpenTelemetrySdk.class).shutdown().join(10, TimeUnit.SECONDS)
                .isSuccess()).isTrue();
    }

}
