/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_micrometer_tracing_opentelemetry;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.OpenTelemetryTracingProperties;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.SpanExporters;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.SpanProcessors;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.SdkTracerProviderBuilderCustomizer;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpGrpcSpanExporterBuilderCustomizer;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpHttpSpanExporterBuilderCustomizer;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingAutoConfiguration;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingConnectionDetails;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingProperties;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.Transport;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.testcontainers.service.connection.ServiceConnectionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Spring_boot_micrometer_tracing_opentelemetryTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OtlpTracingAutoConfiguration.class));

    @Test
    void openTelemetryTracingPropertiesExposeDefaultsAndNestedConfiguration() {
        OpenTelemetryTracingProperties properties = new OpenTelemetryTracingProperties();

        assertThat(properties.getSampler())
                .isEqualTo(OpenTelemetryTracingProperties.Sampler.PARENT_BASED_TRACE_ID_RATIO);
        assertThat(properties.getExport().isIncludeUnsampled()).isFalse();
        assertThat(properties.getExport().getTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.getExport().getMaxBatchSize()).isEqualTo(512);
        assertThat(properties.getExport().getMaxQueueSize()).isEqualTo(2048);
        assertThat(properties.getExport().getScheduleDelay()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.getLimits().getMaxAttributeValueLength()).isEqualTo(Integer.MAX_VALUE);
        assertThat(properties.getLimits().getMaxAttributes()).isEqualTo(128);
        assertThat(properties.getLimits().getMaxEvents()).isEqualTo(128);
        assertThat(properties.getLimits().getMaxLinks()).isEqualTo(128);
        assertThat(properties.getLimits().getMaxAttributesPerEvent()).isEqualTo(128);
        assertThat(properties.getLimits().getMaxAttributesPerLink()).isEqualTo(128);

        MapConfigurationPropertySource source = new MapConfigurationPropertySource();
        source.put("management.opentelemetry.tracing.sampler", "always-on");
        source.put("management.opentelemetry.tracing.export.include-unsampled", "true");
        source.put("management.opentelemetry.tracing.export.timeout", "11s");
        source.put("management.opentelemetry.tracing.export.max-batch-size", "64");
        source.put("management.opentelemetry.tracing.export.max-queue-size", "256");
        source.put("management.opentelemetry.tracing.export.schedule-delay", "2s");
        source.put("management.opentelemetry.tracing.limits.max-attribute-value-length", "80");
        source.put("management.opentelemetry.tracing.limits.max-attributes", "32");
        source.put("management.opentelemetry.tracing.limits.max-events", "16");
        source.put("management.opentelemetry.tracing.limits.max-links", "8");
        source.put("management.opentelemetry.tracing.limits.max-attributes-per-event", "6");
        source.put("management.opentelemetry.tracing.limits.max-attributes-per-link", "4");

        OpenTelemetryTracingProperties bound = new Binder(source)
                .bind("management.opentelemetry.tracing", Bindable.ofInstance(properties))
                .orElseThrow(() -> new AssertionError("OpenTelemetry tracing properties were not bound"));

        assertThat(bound).isSameAs(properties);
        assertThat(properties.getSampler()).isEqualTo(OpenTelemetryTracingProperties.Sampler.ALWAYS_ON);
        assertThat(properties.getExport().isIncludeUnsampled()).isTrue();
        assertThat(properties.getExport().getTimeout()).isEqualTo(Duration.ofSeconds(11));
        assertThat(properties.getExport().getMaxBatchSize()).isEqualTo(64);
        assertThat(properties.getExport().getMaxQueueSize()).isEqualTo(256);
        assertThat(properties.getExport().getScheduleDelay()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.getLimits().getMaxAttributeValueLength()).isEqualTo(80);
        assertThat(properties.getLimits().getMaxAttributes()).isEqualTo(32);
        assertThat(properties.getLimits().getMaxEvents()).isEqualTo(16);
        assertThat(properties.getLimits().getMaxLinks()).isEqualTo(8);
        assertThat(properties.getLimits().getMaxAttributesPerEvent()).isEqualTo(6);
        assertThat(properties.getLimits().getMaxAttributesPerLink()).isEqualTo(4);
    }

    @Test
    void otlpTracingPropertiesBindTransportExportAndSslSettings() {
        OtlpTracingProperties properties = new OtlpTracingProperties();
        assertThat(properties.getEndpoint()).isNull();
        assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.getTransport()).isEqualTo(Transport.HTTP);
        assertThat(properties.getCompression()).isEqualTo(OtlpTracingProperties.Compression.NONE);
        assertThat(properties.getHeaders()).isEmpty();
        assertThat(properties.getSsl().getBundle()).isNull();

        MapConfigurationPropertySource source = new MapConfigurationPropertySource();
        source.put("management.opentelemetry.tracing.export.otlp.endpoint", "https://collector.example.test/v1/traces");
        source.put("management.opentelemetry.tracing.export.otlp.timeout", "15s");
        source.put("management.opentelemetry.tracing.export.otlp.connect-timeout", "12s");
        source.put("management.opentelemetry.tracing.export.otlp.transport", "grpc");
        source.put("management.opentelemetry.tracing.export.otlp.compression", "gzip");
        source.put("management.opentelemetry.tracing.export.otlp.headers.authorization", "Bearer test-token");
        source.put("management.opentelemetry.tracing.export.otlp.headers.tenant", "native-tests");
        source.put("management.opentelemetry.tracing.export.otlp.ssl.bundle", "collector");

        OtlpTracingProperties bound = new Binder(source)
                .bind("management.opentelemetry.tracing.export.otlp", Bindable.ofInstance(properties))
                .orElseThrow(() -> new AssertionError("OTLP tracing properties were not bound"));

        assertThat(bound).isSameAs(properties);
        assertThat(properties.getEndpoint()).isEqualTo("https://collector.example.test/v1/traces");
        assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(15));
        assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(12));
        assertThat(properties.getTransport()).isEqualTo(Transport.GRPC);
        assertThat(properties.getCompression()).isEqualTo(OtlpTracingProperties.Compression.GZIP);
        assertThat(properties.getHeaders()).containsEntry("authorization", "Bearer test-token")
                .containsEntry("tenant", "native-tests");
        assertThat(properties.getSsl().getBundle()).isEqualTo("collector");
    }

    @Test
    void otlpAutoConfigurationCreatesHttpExporterFromTracingProperties() {
        this.contextRunner
                .withPropertyValues("management.tracing.export.otlp.enabled=true",
                        "management.opentelemetry.tracing.export.otlp.endpoint=http://collector.example.test/v1/traces",
                        "management.opentelemetry.tracing.export.otlp.compression=gzip")
                .run((context) -> {
                    assertThat(context).hasSingleBean(OtlpTracingConnectionDetails.class);
                    assertThat(context).hasSingleBean(OtlpHttpSpanExporter.class);
                    assertThat(context.getBean(OtlpTracingConnectionDetails.class).getUrl(Transport.HTTP))
                            .isEqualTo("http://collector.example.test/v1/traces");
                    assertThat(context.getBean(OtlpHttpSpanExporter.class).toString())
                            .contains("endpoint=http://collector.example.test/v1/traces", "compressorEncoding=gzip");
                    assertThat(context.getBean(OtlpHttpSpanExporter.class).shutdown().join(10, TimeUnit.SECONDS)
                            .isSuccess()).isTrue();
                });
    }

    @Test
    void otlpAutoConfigurationCreatesGrpcExporterFromTracingProperties() {
        this.contextRunner
                .withPropertyValues("management.tracing.export.otlp.enabled=true",
                        "management.opentelemetry.tracing.export.otlp.endpoint=http://collector.example.test:4317",
                        "management.opentelemetry.tracing.export.otlp.transport=grpc",
                        "management.opentelemetry.tracing.export.otlp.compression=gzip")
                .run((context) -> {
                    assertThat(context).hasSingleBean(OtlpTracingConnectionDetails.class);
                    assertThat(context).hasSingleBean(OtlpGrpcSpanExporter.class);
                    assertThat(context).doesNotHaveBean(OtlpHttpSpanExporter.class);
                    assertThat(context.getBean(OtlpTracingConnectionDetails.class).getUrl(Transport.GRPC))
                            .isEqualTo("http://collector.example.test:4317");
                    assertThat(context.getBean(OtlpGrpcSpanExporter.class).toString())
                            .contains("endpoint=http://collector.example.test:4317", "compressorEncoding=gzip");
                    assertThat(context.getBean(OtlpGrpcSpanExporter.class).shutdown().join(10, TimeUnit.SECONDS)
                            .isSuccess()).isTrue();
                });
    }

    @Test
    void testcontainersCollectorProvidesOtlpConnectionDetails() {
        this.contextRunner
                .withConfiguration(AutoConfigurations.of(ServiceConnectionAutoConfiguration.class))
                .withUserConfiguration(OtlpContainerConfiguration.class)
                .withPropertyValues("management.tracing.export.otlp.enabled=true")
                .run((context) -> {
                    assertThat(context).hasSingleBean(OtlpTracingConnectionDetails.class);
                    OtlpTracingConnectionDetails connectionDetails = context
                            .getBean(OtlpTracingConnectionDetails.class);
                    assertThat(connectionDetails.getUrl(Transport.HTTP))
                            .isEqualTo("http://collector.example.test:14318/v1/traces");
                    assertThat(connectionDetails.getUrl(Transport.GRPC))
                            .isEqualTo("http://collector.example.test:14317/v1/traces");
                });
    }

    @Test
    void spanExportersKeepAnImmutableConsumerFacingCollection() {
        RecordingSpanExporter first = new RecordingSpanExporter();
        RecordingSpanExporter second = new RecordingSpanExporter();

        SpanExporters exporters = SpanExporters.of(first, second);

        assertThat(exporters.list()).containsExactly(first, second);
        assertThat(exporters).containsExactly(first, second);
        assertThat(exporters.spliterator().getExactSizeIfKnown()).isEqualTo(2);
        assertThatThrownBy(() -> exporters.list().add(first))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void spanProcessorsWrapRealSdkProcessorsAndRemainIterable() {
        RecordingSpanExporter exporter = new RecordingSpanExporter();
        SpanProcessor processor = SimpleSpanProcessor.create(exporter);
        SpanProcessors processors = SpanProcessors.of(processor);

        assertThat(processors.list()).containsExactly(processor);
        assertThat(processors.iterator().next()).isSameAs(processor);
        assertThat(processors.spliterator().getExactSizeIfKnown()).isEqualTo(1);
        assertThatThrownBy(() -> processors.list().add(processor))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(processor.shutdown().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    @Test
    void tracerProviderBuilderCustomizerChangesTheSdkProvider() {
        SdkTracerProviderBuilderCustomizer customizer = (builder) -> builder.setSampler(Sampler.alwaysOn());
        SdkTracerProviderBuilder builder = SdkTracerProvider.builder();
        customizer.customize(builder);

        try (SdkTracerProvider provider = builder.build()) {
            assertThat(provider.getSampler().getDescription()).isEqualTo("AlwaysOnSampler");
        }
    }

    @Test
    void otlpExporterBuilderCustomizersConfigureHttpAndGrpcExporters() {
        OtlpHttpSpanExporterBuilderCustomizer httpCustomizer = (builder) -> builder
                .setEndpoint("http://collector.example.test/v1/traces")
                .setCompression("gzip");
        OtlpHttpSpanExporter httpExporter = buildHttpExporter(httpCustomizer);

        OtlpGrpcSpanExporterBuilderCustomizer grpcCustomizer = (builder) -> builder
                .setEndpoint("http://collector.example.test:4317")
                .setCompression("gzip");
        OtlpGrpcSpanExporter grpcExporter = buildGrpcExporter(grpcCustomizer);

        try {
            assertThat(httpExporter.toString())
                    .contains("endpoint=http://collector.example.test/v1/traces", "compressorEncoding=gzip");
            assertThat(grpcExporter.toString())
                    .contains("endpoint=http://collector.example.test:4317", "compressorEncoding=gzip");
        } finally {
            httpExporter.shutdown().join(10, TimeUnit.SECONDS);
            grpcExporter.shutdown().join(10, TimeUnit.SECONDS);
        }
    }

    private static OtlpHttpSpanExporter buildHttpExporter(OtlpHttpSpanExporterBuilderCustomizer customizer) {
        OtlpHttpSpanExporterBuilder builder = OtlpHttpSpanExporter.builder();
        customizer.customize(builder);
        return builder.build();
    }

    private static OtlpGrpcSpanExporter buildGrpcExporter(OtlpGrpcSpanExporterBuilderCustomizer customizer) {
        OtlpGrpcSpanExporterBuilder builder = OtlpGrpcSpanExporter.builder();
        customizer.customize(builder);
        return builder.build();
    }

    @Configuration(proxyBeanMethods = false)
    static class OtlpContainerConfiguration {

        @Bean(destroyMethod = "")
        @ServiceConnection(name = "otel/opentelemetry-collector-contrib")
        RecordingOtlpContainer otlpContainer() {
            return new RecordingOtlpContainer();
        }

    }

    static final class RecordingOtlpContainer extends GenericContainer<RecordingOtlpContainer> {

        RecordingOtlpContainer() {
            super("otel/opentelemetry-collector-contrib");
        }

        @Override
        public String getHost() {
            return "collector.example.test";
        }

        @Override
        public Integer getMappedPort(int originalPort) {
            return switch (originalPort) {
                case 4317 -> 14317;
                case 4318 -> 14318;
                default -> throw new IllegalArgumentException("Unexpected OTLP port: " + originalPort);
            };
        }

    }

    private static final class RecordingSpanExporter implements SpanExporter {

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }

    }

}
