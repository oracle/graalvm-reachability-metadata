/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_contrib.opentelemetry_baggage_processor;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.common.ComponentLoader;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.contrib.baggage.processor.BaggageLogRecordComponentProvider;
import io.opentelemetry.contrib.baggage.processor.BaggageLogRecordProcessor;
import io.opentelemetry.contrib.baggage.processor.BaggageProcessorCustomizer;
import io.opentelemetry.contrib.baggage.processor.BaggageSpanComponentProvider;
import io.opentelemetry.contrib.baggage.processor.BaggageSpanProcessor;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.SdkLoggerProviderBuilder;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

public class Opentelemetry_baggage_processorTest {
    private static final AttributeKey<String> USER_ID = AttributeKey.stringKey("user.id");
    private static final AttributeKey<String> USER_SECRET = AttributeKey.stringKey("user.secret");
    private static final AttributeKey<String> DEPLOYMENT = AttributeKey.stringKey("deployment");
    private static final AttributeKey<String> IGNORED = AttributeKey.stringKey("ignored");
    private static final AttributeKey<String> TENANT_ID = AttributeKey.stringKey("tenant.id");
    private static final AttributeKey<String> REQUEST_ID = AttributeKey.stringKey("request.id");

    @Test
    void spanProcessorCopiesAllBaggageIntoStartedSpan() {
        RecordingSpanExporter exporter = new RecordingSpanExporter();
        try (SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BaggageSpanProcessor.allowAllBaggageKeys())
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build()) {
            Context context = baggageContext(Map.of("user.id", "alice", "deployment", "prod"));

            Span span = tracerProvider.get("baggage-test").spanBuilder("checkout").setParent(context).startSpan();
            span.end();
            assertSuccessful(tracerProvider.forceFlush());

            assertThat(exporter.getFinishedSpans()).hasSize(1);
            SpanData spanData = exporter.getFinishedSpans().get(0);
            assertThat(spanData.getAttributes().get(USER_ID)).isEqualTo("alice");
            assertThat(spanData.getAttributes().get(DEPLOYMENT)).isEqualTo("prod");
        }
    }

    @Test
    void spanProcessorAppliesIncludeAndExcludePatterns() {
        RecordingSpanExporter exporter = new RecordingSpanExporter();
        SpanProcessor baggageProcessor = new BaggageSpanProcessor(
                List.of("user.*", "deployment"),
                List.of("user.secret"));
        try (SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(baggageProcessor)
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build()) {
            Context context = baggageContext(Map.of(
                    "user.id", "alice",
                    "user.secret", "redacted",
                    "deployment", "prod",
                    "ignored", "not-copied"));

            Span span = tracerProvider.get("baggage-test").spanBuilder("filtered-span").setParent(context).startSpan();
            span.end();
            assertSuccessful(tracerProvider.forceFlush());

            assertThat(exporter.getFinishedSpans()).hasSize(1);
            SpanData spanData = exporter.getFinishedSpans().get(0);
            assertThat(spanData.getAttributes().get(USER_ID)).isEqualTo("alice");
            assertThat(spanData.getAttributes().get(DEPLOYMENT)).isEqualTo("prod");
            assertThat(spanData.getAttributes().get(USER_SECRET)).isNull();
            assertThat(spanData.getAttributes().get(IGNORED)).isNull();
            assertThat(baggageProcessor.isStartRequired()).isTrue();
            assertThat(baggageProcessor.isEndRequired()).isFalse();
            assertThat(baggageProcessor.toString())
                    .contains("BaggageSpanProcessor")
                    .contains("baggageKeyPredicate");
        }
    }

    @Test
    void logRecordProcessorCopiesFilteredBaggageBeforeExport() {
        RecordingLogRecordExporter exporter = new RecordingLogRecordExporter();
        try (SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .addLogRecordProcessor(new BaggageLogRecordProcessor(
                        List.of("user.*", "deployment"),
                        List.of("user.secret")))
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
                .build()) {
            Context context = baggageContext(Map.of(
                    "user.id", "alice",
                    "user.secret", "redacted",
                    "deployment", "prod",
                    "ignored", "not-copied"));
            Logger logger = loggerProvider.get("baggage-test");

            logger.logRecordBuilder().setContext(context).setBody("order accepted").emit();
            assertSuccessful(loggerProvider.forceFlush());

            assertThat(exporter.getLogRecords()).hasSize(1);
            LogRecordData logRecord = exporter.getLogRecords().get(0);
            assertThat(logRecord.getAttributes().get(USER_ID)).isEqualTo("alice");
            assertThat(logRecord.getAttributes().get(DEPLOYMENT)).isEqualTo("prod");
            assertThat(logRecord.getAttributes().get(USER_SECRET)).isNull();
            assertThat(logRecord.getAttributes().get(IGNORED)).isNull();
        }
    }

    @Test
    void logRecordProcessorAllowAllBaggageKeysCopiesUnfilteredBaggage() {
        RecordingLogRecordExporter exporter = new RecordingLogRecordExporter();
        BaggageLogRecordProcessor baggageProcessor = BaggageLogRecordProcessor.allowAllBaggageKeys();
        try (SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .addLogRecordProcessor(baggageProcessor)
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
                .build()) {
            Context context = baggageContext(Map.of(
                    "user.id", "alice",
                    "user.secret", "redacted",
                    "deployment", "prod"));

            loggerProvider.get("baggage-test").logRecordBuilder().setContext(context).setBody("all baggage").emit();
            assertSuccessful(loggerProvider.forceFlush());

            assertThat(exporter.getLogRecords()).hasSize(1);
            LogRecordData logRecord = exporter.getLogRecords().get(0);
            assertThat(logRecord.getAttributes().get(USER_ID)).isEqualTo("alice");
            assertThat(logRecord.getAttributes().get(USER_SECRET)).isEqualTo("redacted");
            assertThat(logRecord.getAttributes().get(DEPLOYMENT)).isEqualTo("prod");
            assertThat(baggageProcessor.toString())
                    .contains("BaggageLogRecordProcessor")
                    .contains("baggageKeyPredicate");
        }
    }

    @Test
    void predicateConstructorsUseCallerSuppliedBaggageKeyPredicate() {
        RecordingSpanExporter spanExporter = new RecordingSpanExporter();
        RecordingLogRecordExporter logExporter = new RecordingLogRecordExporter();
        try (SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(new BaggageSpanProcessor(key -> key.endsWith(".id")))
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
                SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                        .addLogRecordProcessor(new BaggageLogRecordProcessor(key -> key.endsWith(".id")))
                        .addLogRecordProcessor(SimpleLogRecordProcessor.create(logExporter))
                        .build()) {
            Context context = baggageContext(Map.of(
                    "tenant.id", "acme",
                    "request.id", "req-1",
                    "deployment", "prod",
                    "user.secret", "redacted"));

            Span span = tracerProvider.get("baggage-test").spanBuilder("predicate-span")
                    .setParent(context)
                    .startSpan();
            span.end();
            loggerProvider.get("baggage-test").logRecordBuilder().setContext(context).setBody("predicate log")
                    .emit();
            assertSuccessful(tracerProvider.forceFlush());
            assertSuccessful(loggerProvider.forceFlush());

            assertThat(spanExporter.getFinishedSpans()).hasSize(1);
            SpanData spanData = spanExporter.getFinishedSpans().get(0);
            assertThat(spanData.getAttributes().get(TENANT_ID)).isEqualTo("acme");
            assertThat(spanData.getAttributes().get(REQUEST_ID)).isEqualTo("req-1");
            assertThat(spanData.getAttributes().get(DEPLOYMENT)).isNull();
            assertThat(spanData.getAttributes().get(USER_SECRET)).isNull();

            assertThat(logExporter.getLogRecords()).hasSize(1);
            LogRecordData logRecord = logExporter.getLogRecords().get(0);
            assertThat(logRecord.getAttributes().get(TENANT_ID)).isEqualTo("acme");
            assertThat(logRecord.getAttributes().get(REQUEST_ID)).isEqualTo("req-1");
            assertThat(logRecord.getAttributes().get(DEPLOYMENT)).isNull();
            assertThat(logRecord.getAttributes().get(USER_SECRET)).isNull();
        }
    }

    @Test
    void componentProvidersCreateTypedBaggageProcessorsFromDeclarativeConfiguration() {
        BaggageSpanComponentProvider spanProvider = new BaggageSpanComponentProvider();
        BaggageLogRecordComponentProvider logProvider = new BaggageLogRecordComponentProvider();
        DeclarativeConfigProperties config = new MapDeclarativeConfigProperties(Map.of(
                "included", List.of("tenant.*", "request.id"),
                "excluded", List.of("tenant.secret")));

        assertThat(spanProvider.getName()).isEqualTo("baggage");
        assertThat(spanProvider.getType()).isEqualTo(SpanProcessor.class);
        assertThat(spanProvider.create(config)).isInstanceOf(BaggageSpanProcessor.class);
        assertThat(logProvider.getName()).isEqualTo("baggage");
        assertThat(logProvider.getType()).isEqualTo(LogRecordProcessor.class);
        assertThat(logProvider.create(config)).isInstanceOf(BaggageLogRecordProcessor.class);

        RecordingSpanExporter spanExporter = new RecordingSpanExporter();
        RecordingLogRecordExporter logExporter = new RecordingLogRecordExporter();
        try (SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(spanProvider.create(config))
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
                SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                        .addLogRecordProcessor(logProvider.create(config))
                        .addLogRecordProcessor(SimpleLogRecordProcessor.create(logExporter))
                        .build()) {
            Context context = baggageContext(Map.of(
                    "tenant.id", "acme",
                    "tenant.secret", "redacted",
                    "request.id", "req-1",
                    "ignored", "not-copied"));

            Span span = tracerProvider.get("baggage-test").spanBuilder("component-provider-span")
                    .setParent(context)
                    .startSpan();
            span.end();
            loggerProvider.get("baggage-test").logRecordBuilder().setContext(context).setBody("component log").emit();
            assertSuccessful(tracerProvider.forceFlush());
            assertSuccessful(loggerProvider.forceFlush());

            assertThat(spanExporter.getFinishedSpans()).hasSize(1);
            assertThat(spanExporter.getFinishedSpans().get(0).getAttributes().get(TENANT_ID)).isEqualTo("acme");
            assertThat(spanExporter.getFinishedSpans().get(0).getAttributes().get(REQUEST_ID)).isEqualTo("req-1");
            assertThat(spanExporter.getFinishedSpans().get(0).getAttributes()
                    .get(AttributeKey.stringKey("tenant.secret"))).isNull();
            assertThat(spanExporter.getFinishedSpans().get(0).getAttributes().get(IGNORED)).isNull();

            assertThat(logExporter.getLogRecords()).hasSize(1);
            assertThat(logExporter.getLogRecords().get(0).getAttributes().get(TENANT_ID)).isEqualTo("acme");
            assertThat(logExporter.getLogRecords().get(0).getAttributes().get(REQUEST_ID)).isEqualTo("req-1");
            assertThat(logExporter.getLogRecords().get(0).getAttributes()
                    .get(AttributeKey.stringKey("tenant.secret"))).isNull();
            assertThat(logExporter.getLogRecords().get(0).getAttributes().get(IGNORED)).isNull();
        }
    }

    @Test
    void autoconfigurationCustomizerAddsProcessorsWhenIncludePropertiesAreConfigured() {
        BaggageProcessorCustomizer baggageCustomizer = new BaggageProcessorCustomizer();
        RecordingAutoConfigurationCustomizer autoConfigurationCustomizer = new RecordingAutoConfigurationCustomizer();
        baggageCustomizer.customize(autoConfigurationCustomizer);
        MapConfigProperties properties = new MapConfigProperties(Map.of(
                "otel.java.experimental.span-attributes.copy-from-baggage.include", List.of("tenant.*"),
                "otel.java.experimental.log-attributes.copy-from-baggage.include", List.of("request.id")));
        RecordingSpanExporter spanExporter = new RecordingSpanExporter();
        RecordingLogRecordExporter logExporter = new RecordingLogRecordExporter();

        SdkTracerProviderBuilder tracerBuilder = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter));
        SdkLoggerProviderBuilder loggerBuilder = SdkLoggerProvider.builder()
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(logExporter));
        autoConfigurationCustomizer.applyTracerProviderCustomizer(tracerBuilder, properties);
        autoConfigurationCustomizer.applyLoggerProviderCustomizer(loggerBuilder, properties);

        try (SdkTracerProvider tracerProvider = tracerBuilder.build();
                SdkLoggerProvider loggerProvider = loggerBuilder.build()) {
            Context context = baggageContext(Map.of(
                    "tenant.id", "acme",
                    "request.id", "req-1",
                    "ignored", "not-copied"));

            Span span = tracerProvider.get("baggage-test").spanBuilder("autoconfigured-span")
                    .setParent(context)
                    .startSpan();
            span.end();
            loggerProvider.get("baggage-test").logRecordBuilder().setContext(context).setBody("autoconfigured log")
                    .emit();
            assertSuccessful(tracerProvider.forceFlush());
            assertSuccessful(loggerProvider.forceFlush());

            assertThat(spanExporter.getFinishedSpans()).hasSize(1);
            assertThat(spanExporter.getFinishedSpans().get(0).getAttributes().get(TENANT_ID)).isEqualTo("acme");
            assertThat(spanExporter.getFinishedSpans().get(0).getAttributes().get(REQUEST_ID)).isNull();
            assertThat(logExporter.getLogRecords()).hasSize(1);
            assertThat(logExporter.getLogRecords().get(0).getAttributes().get(REQUEST_ID)).isEqualTo("req-1");
            assertThat(logExporter.getLogRecords().get(0).getAttributes().get(TENANT_ID)).isNull();
        }
    }

    private static Context baggageContext(Map<String, String> entries) {
        BaggageBuilder builder = Baggage.builder();
        entries.forEach(builder::put);
        return builder.build().storeInContext(Context.root());
    }

    private static void assertSuccessful(CompletableResultCode resultCode) {
        assertThat(resultCode.join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    private static final class RecordingSpanExporter implements SpanExporter {
        private final List<SpanData> finishedSpans = new ArrayList<>();

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            finishedSpans.addAll(spans);
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

        List<SpanData> getFinishedSpans() {
            return List.copyOf(finishedSpans);
        }
    }

    private static final class RecordingLogRecordExporter implements LogRecordExporter {
        private final List<LogRecordData> logRecords = new ArrayList<>();

        @Override
        public CompletableResultCode export(Collection<LogRecordData> logs) {
            logRecords.addAll(logs);
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

        List<LogRecordData> getLogRecords() {
            return List.copyOf(logRecords);
        }
    }

    private static final class MapDeclarativeConfigProperties implements DeclarativeConfigProperties {
        private final Map<String, List<String>> scalarLists;

        MapDeclarativeConfigProperties(Map<String, List<String>> scalarLists) {
            this.scalarLists = Map.copyOf(scalarLists);
        }

        @Override
        public String getString(String name) {
            return null;
        }

        @Override
        public Boolean getBoolean(String name) {
            return null;
        }

        @Override
        public Integer getInt(String name) {
            return null;
        }

        @Override
        public Long getLong(String name) {
            return null;
        }

        @Override
        public Double getDouble(String name) {
            return null;
        }

        @Override
        public <T> List<T> getScalarList(String name, Class<T> scalarType) {
            assertThat(scalarType).isEqualTo(String.class);
            List<String> values = scalarLists.getOrDefault(name, List.of());
            return values.stream().map(scalarType::cast).toList();
        }

        @Override
        public DeclarativeConfigProperties getStructured(String name) {
            return null;
        }

        @Override
        public List<DeclarativeConfigProperties> getStructuredList(String name) {
            return List.of();
        }

        @Override
        public Set<String> getPropertyKeys() {
            return scalarLists.keySet();
        }

        @Override
        public ComponentLoader getComponentLoader() {
            return new ComponentLoader() {
                @Override
                public <T> Iterable<T> load(Class<T> serviceClass) {
                    return List.of();
                }
            };
        }
    }

    private static final class MapConfigProperties implements ConfigProperties {
        private final Map<String, List<String>> lists;

        MapConfigProperties(Map<String, List<String>> lists) {
            this.lists = Map.copyOf(lists);
        }

        @Override
        public String getString(String name) {
            return null;
        }

        @Override
        public Boolean getBoolean(String name) {
            return null;
        }

        @Override
        public Integer getInt(String name) {
            return null;
        }

        @Override
        public Long getLong(String name) {
            return null;
        }

        @Override
        public Double getDouble(String name) {
            return null;
        }

        @Override
        public Duration getDuration(String name) {
            return null;
        }

        @Override
        public List<String> getList(String name) {
            return lists.getOrDefault(name, List.of());
        }

        @Override
        public Map<String, String> getMap(String name) {
            return Map.of();
        }
    }

    private static final class RecordingAutoConfigurationCustomizer implements AutoConfigurationCustomizer {
        private BiFunction<SdkTracerProviderBuilder, ConfigProperties, SdkTracerProviderBuilder>
                tracerProviderCustomizer;
        private BiFunction<SdkLoggerProviderBuilder, ConfigProperties, SdkLoggerProviderBuilder>
                loggerProviderCustomizer;

        @Override
        public AutoConfigurationCustomizer addPropagatorCustomizer(
                BiFunction<? super TextMapPropagator, ConfigProperties, ? extends TextMapPropagator> customizer) {
            return this;
        }

        @Override
        public AutoConfigurationCustomizer addResourceCustomizer(
                BiFunction<? super Resource, ConfigProperties, ? extends Resource> customizer) {
            return this;
        }

        @Override
        public AutoConfigurationCustomizer addSamplerCustomizer(
                BiFunction<? super Sampler, ConfigProperties, ? extends Sampler> customizer) {
            return this;
        }

        @Override
        public AutoConfigurationCustomizer addSpanExporterCustomizer(
                BiFunction<? super SpanExporter, ConfigProperties, ? extends SpanExporter> customizer) {
            return this;
        }

        @Override
        public AutoConfigurationCustomizer addPropertiesSupplier(Supplier<Map<String, String>> supplier) {
            return this;
        }

        @Override
        public AutoConfigurationCustomizer addPropertiesCustomizer(
                Function<ConfigProperties, Map<String, String>> customizer) {
            return this;
        }

        @Override
        public AutoConfigurationCustomizer addTracerProviderCustomizer(
                BiFunction<SdkTracerProviderBuilder, ConfigProperties, SdkTracerProviderBuilder> customizer) {
            this.tracerProviderCustomizer = customizer;
            return this;
        }

        @Override
        public AutoConfigurationCustomizer addMeterProviderCustomizer(
                BiFunction<SdkMeterProviderBuilder, ConfigProperties, SdkMeterProviderBuilder> customizer) {
            return this;
        }

        @Override
        public AutoConfigurationCustomizer addMetricExporterCustomizer(
                BiFunction<? super MetricExporter, ConfigProperties, ? extends MetricExporter> customizer) {
            return this;
        }

        @Override
        public AutoConfigurationCustomizer addMetricReaderCustomizer(
                BiFunction<? super MetricReader, ConfigProperties, ? extends MetricReader> customizer) {
            return this;
        }

        @Override
        public AutoConfigurationCustomizer addLoggerProviderCustomizer(
                BiFunction<SdkLoggerProviderBuilder, ConfigProperties, SdkLoggerProviderBuilder> customizer) {
            this.loggerProviderCustomizer = customizer;
            return this;
        }

        @Override
        public AutoConfigurationCustomizer addLogRecordExporterCustomizer(
                BiFunction<? super LogRecordExporter, ConfigProperties, ? extends LogRecordExporter> customizer) {
            return this;
        }

        void applyTracerProviderCustomizer(SdkTracerProviderBuilder builder, ConfigProperties properties) {
            assertThat(tracerProviderCustomizer).isNotNull();
            tracerProviderCustomizer.apply(builder, properties);
        }

        void applyLoggerProviderCustomizer(SdkLoggerProviderBuilder builder, ConfigProperties properties) {
            assertThat(loggerProviderCustomizer).isNotNull();
            loggerProviderCustomizer.apply(builder, properties);
        }
    }
}
