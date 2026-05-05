/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_api_incubator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.common.ExtendedAttributeKey;
import io.opentelemetry.api.incubator.common.ExtendedAttributeType;
import io.opentelemetry.api.incubator.common.ExtendedAttributes;
import io.opentelemetry.api.incubator.common.ExtendedAttributesBuilder;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigException;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.api.incubator.config.GlobalConfigProvider;
import io.opentelemetry.api.incubator.config.InstrumentationConfigUtil;
import io.opentelemetry.api.incubator.logs.ExtendedDefaultLoggerProvider;
import io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder;
import io.opentelemetry.api.incubator.logs.ExtendedLogger;
import io.opentelemetry.api.incubator.metrics.ExtendedDefaultMeterProvider;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleCounter;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleCounterBuilder;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleGauge;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleGaugeBuilder;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogram;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogramBuilder;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleUpDownCounter;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleUpDownCounterBuilder;
import io.opentelemetry.api.incubator.metrics.ExtendedLongCounter;
import io.opentelemetry.api.incubator.metrics.ExtendedLongCounterBuilder;
import io.opentelemetry.api.incubator.metrics.ExtendedLongGauge;
import io.opentelemetry.api.incubator.metrics.ExtendedLongGaugeBuilder;
import io.opentelemetry.api.incubator.metrics.ExtendedLongHistogram;
import io.opentelemetry.api.incubator.metrics.ExtendedLongHistogramBuilder;
import io.opentelemetry.api.incubator.metrics.ExtendedLongUpDownCounter;
import io.opentelemetry.api.incubator.metrics.ExtendedLongUpDownCounterBuilder;
import io.opentelemetry.api.incubator.propagation.ExtendedContextPropagators;
import io.opentelemetry.api.incubator.propagation.PassThroughPropagator;
import io.opentelemetry.api.incubator.trace.ExtendedDefaultTracerProvider;
import io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder;
import io.opentelemetry.api.incubator.trace.ExtendedTracer;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.api.metrics.DoubleGauge;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleUpDownCounter;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.metrics.ObservableDoubleCounter;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import io.opentelemetry.api.metrics.ObservableDoubleUpDownCounter;
import io.opentelemetry.api.metrics.ObservableLongCounter;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.common.ComponentLoader;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

public class Opentelemetry_api_incubatorTest {
    private static final TextMapSetter<Map<String, String>> MAP_SETTER =
                    (carrier, key, value) -> carrier.put(key, value);

    private static final TextMapGetter<Map<String, String>> MAP_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier.get(key);
        }
    };

    @Test
    void extendedAttributeKeysConvertToAndFromStandardAttributeKeys() {
        AttributeKey<String> serviceNameKey = AttributeKey.stringKey("service.name");
        ExtendedAttributeKey<String> extendedServiceNameKey = ExtendedAttributeKey.fromAttributeKey(serviceNameKey);

        assertThat(extendedServiceNameKey.getKey()).isEqualTo("service.name");
        assertThat(extendedServiceNameKey.getType()).isEqualTo(ExtendedAttributeType.STRING);
        assertThat(extendedServiceNameKey.asAttributeKey()).isEqualTo(serviceNameKey);
        assertThat(extendedServiceNameKey).isEqualTo(ExtendedAttributeKey.stringKey("service.name"));
        assertThat(extendedServiceNameKey.hashCode())
                        .isEqualTo(ExtendedAttributeKey.stringKey("service.name").hashCode());
        assertThat(extendedServiceNameKey).hasToString("service.name");

        assertThat(ExtendedAttributeKey.fromAttributeKey(AttributeKey.booleanKey("enabled")).getType())
                        .isEqualTo(ExtendedAttributeType.BOOLEAN);
        assertThat(ExtendedAttributeKey.fromAttributeKey(AttributeKey.longKey("attempt")).getType())
                        .isEqualTo(ExtendedAttributeType.LONG);
        assertThat(ExtendedAttributeKey.fromAttributeKey(AttributeKey.doubleKey("ratio")).getType())
                        .isEqualTo(ExtendedAttributeType.DOUBLE);
        assertThat(ExtendedAttributeKey.fromAttributeKey(AttributeKey.stringArrayKey("tags")).getType())
                        .isEqualTo(ExtendedAttributeType.STRING_ARRAY);
        assertThat(ExtendedAttributeKey.fromAttributeKey(AttributeKey.booleanArrayKey("flags")).getType())
                        .isEqualTo(ExtendedAttributeType.BOOLEAN_ARRAY);
        assertThat(ExtendedAttributeKey.fromAttributeKey(AttributeKey.longArrayKey("counts")).getType())
                        .isEqualTo(ExtendedAttributeType.LONG_ARRAY);
        assertThat(ExtendedAttributeKey.fromAttributeKey(AttributeKey.doubleArrayKey("scores")).getType())
                        .isEqualTo(ExtendedAttributeType.DOUBLE_ARRAY);

        ExtendedAttributeKey<ExtendedAttributes> nestedKey = ExtendedAttributeKey.extendedAttributesKey("resource");
        assertThat(nestedKey.getType()).isEqualTo(ExtendedAttributeType.EXTENDED_ATTRIBUTES);
        assertThat(nestedKey.asAttributeKey()).isNull();
    }

    @Test
    void extendedAttributesStoreScalarsArraysNestedValuesAndStandardViews() {
        ExtendedAttributeKey<String> stringKey = ExtendedAttributeKey.stringKey("string.value");
        ExtendedAttributeKey<Boolean> booleanKey = ExtendedAttributeKey.booleanKey("boolean.value");
        ExtendedAttributeKey<Long> longKey = ExtendedAttributeKey.longKey("long.value");
        ExtendedAttributeKey<Double> doubleKey = ExtendedAttributeKey.doubleKey("double.value");
        ExtendedAttributeKey<List<String>> stringArrayKey = ExtendedAttributeKey.stringArrayKey("string.array");
        ExtendedAttributeKey<List<Boolean>> booleanArrayKey = ExtendedAttributeKey.booleanArrayKey("boolean.array");
        ExtendedAttributeKey<List<Long>> longArrayKey = ExtendedAttributeKey.longArrayKey("long.array");
        ExtendedAttributeKey<List<Double>> doubleArrayKey = ExtendedAttributeKey.doubleArrayKey("double.array");
        ExtendedAttributeKey<ExtendedAttributes> nestedKey = ExtendedAttributeKey.extendedAttributesKey("nested.value");
        ExtendedAttributes nested = ExtendedAttributes.builder().put("nested.string", "inside").build();

        ExtendedAttributes attributes = ExtendedAttributes.builder()
                        .put(stringKey, "alpha")
                        .put(booleanKey, true)
                        .put(longKey, 42L)
                        .put(doubleKey, 3.5D)
                        .put(stringArrayKey, List.of("a", "b"))
                        .put(booleanArrayKey, List.of(true, false))
                        .put(longArrayKey, List.of(1L, 2L, 3L))
                        .put(doubleArrayKey, List.of(1.25D, 2.5D))
                        .put(nestedKey, nested)
                        .build();

        assertThat(attributes.isEmpty()).isFalse();
        assertThat(attributes.size()).isEqualTo(9);
        assertThat(attributes.get(stringKey)).isEqualTo("alpha");
        assertThat(attributes.get(booleanKey)).isTrue();
        assertThat(attributes.get(longKey)).isEqualTo(42L);
        assertThat(attributes.get(doubleKey)).isEqualTo(3.5D);
        assertThat(attributes.get(stringArrayKey)).containsExactly("a", "b");
        assertThat(attributes.get(booleanArrayKey)).containsExactly(true, false);
        assertThat(attributes.get(longArrayKey)).containsExactly(1L, 2L, 3L);
        assertThat(attributes.get(doubleArrayKey)).containsExactly(1.25D, 2.5D);
        assertThat(attributes.get(nestedKey)).isSameAs(nested);

        Map<ExtendedAttributeKey<?>, Object> seen = new LinkedHashMap<>();
        attributes.forEach(seen::put);
        assertThat(seen).containsEntry(stringKey, "alpha").containsEntry(nestedKey, nested);
        assertThat(attributes.asMap()).containsEntry(booleanKey, true).containsEntry(longKey, 42L);

        Attributes standardAttributes = attributes.asAttributes();
        assertThat(standardAttributes.get(AttributeKey.stringKey("string.value"))).isEqualTo("alpha");
        assertThat(standardAttributes.get(AttributeKey.booleanKey("boolean.value"))).isTrue();
        assertThat(standardAttributes.get(AttributeKey.longKey("long.value"))).isEqualTo(42L);
        assertThat(standardAttributes.get(AttributeKey.doubleKey("double.value"))).isEqualTo(3.5D);
        assertThat(standardAttributes.get(AttributeKey.stringArrayKey("string.array"))).containsExactly("a", "b");
        assertThat(standardAttributes.size()).isEqualTo(8);
        assertThat(attributes.asAttributes()).isSameAs(standardAttributes);
    }

    @Test
    void extendedAttributesBuilderMergesStandardAttributesAndRemovesEntries() {
        AttributeKey<String> standardStringKey = AttributeKey.stringKey("standard.string");
        ExtendedAttributeKey<Long> longKey = ExtendedAttributeKey.longKey("long.value");
        ExtendedAttributeKey<Double> doubleKey = ExtendedAttributeKey.doubleKey("double.value");
        Attributes standardAttributes = Attributes.of(standardStringKey, "standard");
        ExtendedAttributes extraAttributes = ExtendedAttributes.builder()
                        .put(longKey, 10L)
                        .put(doubleKey, 11.5D)
                        .build();

        ExtendedAttributes attributes = ExtendedAttributes.builder()
                        .putAll(standardAttributes)
                        .putAll(extraAttributes)
                        .remove(standardStringKey)
                        .removeIf(key -> key.getType() == ExtendedAttributeType.DOUBLE)
                        .put("added", "value")
                        .build();

        assertThat(attributes.get(standardStringKey)).isNull();
        assertThat(attributes.get(longKey)).isEqualTo(10L);
        assertThat(attributes.get(doubleKey)).isNull();
        assertThat(attributes.get(ExtendedAttributeKey.stringKey("added"))).isEqualTo("value");

        ExtendedAttributes rebuilt = attributes.toBuilder()
                        .remove(longKey)
                        .put("primitive.long", 7L)
                        .put("primitive.double", 2.25D)
                        .put("primitive.boolean", true)
                        .put("string.array", "x", "y")
                        .put("long.array", 3L, 4L)
                        .put("double.array", 4.5D, 5.5D)
                        .put("boolean.array", true, false)
                        .build();

        assertThat(rebuilt.get(longKey)).isNull();
        assertThat(rebuilt.get(ExtendedAttributeKey.longKey("primitive.long"))).isEqualTo(7L);
        assertThat(rebuilt.get(ExtendedAttributeKey.doubleKey("primitive.double"))).isEqualTo(2.25D);
        assertThat(rebuilt.get(ExtendedAttributeKey.booleanKey("primitive.boolean"))).isTrue();
        assertThat(rebuilt.get(ExtendedAttributeKey.stringArrayKey("string.array"))).containsExactly("x", "y");
        assertThat(rebuilt.get(ExtendedAttributeKey.longArrayKey("long.array"))).containsExactly(3L, 4L);
        assertThat(rebuilt.get(ExtendedAttributeKey.doubleArrayKey("double.array"))).containsExactly(4.5D, 5.5D);
        assertThat(rebuilt.get(ExtendedAttributeKey.booleanArrayKey("boolean.array"))).containsExactly(true, false);
    }

    @Test
    void emptyAttributesAndBuilderIgnoreInvalidKeysAndNullValues() {
        assertThat(ExtendedAttributes.empty().isEmpty()).isTrue();
        assertThat(ExtendedAttributes.empty().size()).isZero();
        assertThat(ExtendedAttributes.empty().asMap()).isEmpty();
        assertThat(ExtendedAttributes.empty().asAttributes().isEmpty()).isTrue();

        ExtendedAttributesBuilder builder = ExtendedAttributes.builder();
        builder.put("", "ignored");
        builder.put(ExtendedAttributeKey.stringKey("null.value"), null);
        builder.put((ExtendedAttributeKey<String>) null, "ignored");
        builder.removeIf(null);

        assertThat(builder.build().isEmpty()).isTrue();
    }

    @Test
    void noopLoggerProviderExposesExtendedLoggerAndFluentLogRecordBuilder() {
        LoggerProvider loggerProvider = ExtendedDefaultLoggerProvider.getNoop();
        Logger logger = loggerProvider.loggerBuilder("integration.logger")
                        .setSchemaUrl("https://example.com/schema")
                        .setInstrumentationVersion("1.0.0")
                        .build();

        assertThat(logger).isInstanceOf(ExtendedLogger.class);
        ExtendedLogger extendedLogger = (ExtendedLogger) logger;
        assertThat(extendedLogger.isEnabled()).isFalse();
        assertThat(extendedLogger.isEnabled(Severity.INFO)).isFalse();
        assertThat(extendedLogger.isEnabled(Severity.ERROR, Context.root())).isFalse();

        ExtendedAttributes extendedAttributes = ExtendedAttributes.builder()
                        .put("log.extended", "attribute")
                        .put("log.nested", ExtendedAttributes.builder().put("inner", "value").build())
                        .build();
        ExtendedLogRecordBuilder builder = extendedLogger.logRecordBuilder();

        assertThat(builder.setTimestamp(123, TimeUnit.MILLISECONDS)).isSameAs(builder);
        assertThat(builder.setTimestamp(Instant.EPOCH)).isSameAs(builder);
        assertThat(builder.setObservedTimestamp(456, TimeUnit.MILLISECONDS)).isSameAs(builder);
        assertThat(builder.setObservedTimestamp(Instant.EPOCH.plusSeconds(1))).isSameAs(builder);
        assertThat(builder.setContext(Context.root())).isSameAs(builder);
        assertThat(builder.setSeverity(Severity.WARN)).isSameAs(builder);
        assertThat(builder.setSeverityText("WARN")).isSameAs(builder);
        assertThat(builder.setBody("body")).isSameAs(builder);
        assertThat(builder.setBody(Value.of("structured body"))).isSameAs(builder);
        assertThat(builder.setEventName("event.name")).isSameAs(builder);
        assertThat(builder.setAllAttributes(Attributes.of(AttributeKey.stringKey("standard"), "value")))
                        .isSameAs(builder);
        assertThat(builder.setAllAttributes(extendedAttributes)).isSameAs(builder);
        assertThat(builder.setAttribute(AttributeKey.longKey("standard.long"), 1L)).isSameAs(builder);
        assertThat(builder.setAttribute(ExtendedAttributeKey.stringKey("extended.string"), "value")).isSameAs(builder);
        assertThat(builder.setException(new IllegalArgumentException("boom"))).isSameAs(builder);
        builder.emit();
    }

    @Test
    void noopMeterProviderCreatesExtendedSynchronousInstruments() {
        Meter meter = noopExtendedMeter();
        Attributes attributes = Attributes.of(AttributeKey.stringKey("route"), "/orders");
        Context context = Context.root();

        ExtendedLongCounterBuilder longCounterBuilder =
                        (ExtendedLongCounterBuilder) meter.counterBuilder("long.counter");
        LongCounter longCounter = longCounterBuilder
                        .setAttributesAdvice(List.of(AttributeKey.stringKey("route")))
                        .setDescription("long counter")
                        .setUnit("requests")
                        .build();
        assertThat(longCounter).isInstanceOf(ExtendedLongCounter.class);
        assertThat(((ExtendedLongCounter) longCounter).isEnabled()).isFalse();
        longCounter.add(1L);
        longCounter.add(2L, attributes);
        longCounter.add(3L, attributes, context);

        ExtendedDoubleCounterBuilder doubleCounterBuilder =
                        (ExtendedDoubleCounterBuilder) meter.counterBuilder("double.counter").ofDoubles();
        DoubleCounter doubleCounter = doubleCounterBuilder
                        .setAttributesAdvice(List.of(AttributeKey.stringKey("route")))
                        .setDescription("double counter")
                        .setUnit("seconds")
                        .build();
        assertThat(doubleCounter).isInstanceOf(ExtendedDoubleCounter.class);
        assertThat(((ExtendedDoubleCounter) doubleCounter).isEnabled()).isFalse();
        doubleCounter.add(1.5D);
        doubleCounter.add(2.5D, attributes);
        doubleCounter.add(3.5D, attributes, context);

        LongUpDownCounter longUpDownCounter = meter.upDownCounterBuilder("long.updown")
                        .setDescription("long up down counter")
                        .setUnit("items")
                        .build();
        assertThat(longUpDownCounter).isInstanceOf(ExtendedLongUpDownCounter.class);
        assertThat(((ExtendedLongUpDownCounter) longUpDownCounter).isEnabled()).isFalse();
        longUpDownCounter.add(-1L);
        longUpDownCounter.add(2L, attributes);
        longUpDownCounter.add(-3L, attributes, context);

        DoubleUpDownCounter doubleUpDownCounter = meter.upDownCounterBuilder("double.updown")
                        .ofDoubles()
                        .setDescription("double up down counter")
                        .setUnit("items")
                        .build();
        assertThat(doubleUpDownCounter).isInstanceOf(ExtendedDoubleUpDownCounter.class);
        assertThat(((ExtendedDoubleUpDownCounter) doubleUpDownCounter).isEnabled()).isFalse();
        doubleUpDownCounter.add(-1.5D);
        doubleUpDownCounter.add(2.5D, attributes);
        doubleUpDownCounter.add(-3.5D, attributes, context);
    }

    @Test
    void noopMeterProviderCreatesExtendedGaugeAndHistogramInstruments() {
        Meter meter = noopExtendedMeter();
        Attributes attributes = Attributes.of(AttributeKey.stringKey("route"), "/orders");
        Context context = Context.root();

        LongHistogram longHistogram = meter.histogramBuilder("long.histogram")
                        .setDescription("long histogram")
                        .setUnit("milliseconds")
                        .ofLongs()
                        .build();
        assertThat(longHistogram).isInstanceOf(ExtendedLongHistogram.class);
        assertThat(((ExtendedLongHistogram) longHistogram).isEnabled()).isFalse();
        longHistogram.record(10L);
        longHistogram.record(11L, attributes);
        longHistogram.record(12L, attributes, context);

        DoubleHistogram doubleHistogram = meter.histogramBuilder("double.histogram")
                        .setDescription("double histogram")
                        .setUnit("milliseconds")
                        .build();
        assertThat(doubleHistogram).isInstanceOf(ExtendedDoubleHistogram.class);
        assertThat(((ExtendedDoubleHistogram) doubleHistogram).isEnabled()).isFalse();
        doubleHistogram.record(10.5D);
        doubleHistogram.record(11.5D, attributes);
        doubleHistogram.record(12.5D, attributes, context);

        ExtendedLongGaugeBuilder longGaugeBuilder =
                        (ExtendedLongGaugeBuilder) meter.gaugeBuilder("long.gauge").ofLongs();
        LongGauge longGauge = longGaugeBuilder
                        .setAttributesAdvice(List.of(AttributeKey.stringKey("route")))
                        .setDescription("long gauge")
                        .setUnit("bytes")
                        .build();
        assertThat(longGauge).isInstanceOf(ExtendedLongGauge.class);
        assertThat(((ExtendedLongGauge) longGauge).isEnabled()).isFalse();
        longGauge.set(10L);
        longGauge.set(11L, attributes);
        longGauge.set(12L, attributes, context);

        DoubleGauge doubleGauge = meter.gaugeBuilder("double.gauge")
                        .setDescription("double gauge")
                        .setUnit("bytes")
                        .build();
        assertThat(doubleGauge).isInstanceOf(ExtendedDoubleGauge.class);
        assertThat(((ExtendedDoubleGauge) doubleGauge).isEnabled()).isFalse();
        doubleGauge.set(10.5D);
        doubleGauge.set(11.5D, attributes);
        doubleGauge.set(12.5D, attributes, context);
    }

    @Test
    void noopMeterProviderAcceptsAttributesAdviceOnAdditionalInstrumentBuilders() {
        Meter meter = noopExtendedMeter();
        List<AttributeKey<?>> advisedKeys = List.of(
                        AttributeKey.stringKey("route"),
                        AttributeKey.longKey("status.code"));

        ExtendedLongUpDownCounterBuilder longUpDownCounterBuilder =
                        (ExtendedLongUpDownCounterBuilder) meter.upDownCounterBuilder("advised.long.updown");
        assertThat(longUpDownCounterBuilder.setAttributesAdvice(advisedKeys)).isSameAs(longUpDownCounterBuilder);
        assertThat(longUpDownCounterBuilder.build()).isInstanceOf(ExtendedLongUpDownCounter.class);

        ExtendedDoubleUpDownCounterBuilder doubleUpDownCounterBuilder =
                        (ExtendedDoubleUpDownCounterBuilder) meter.upDownCounterBuilder("advised.double.updown")
                                        .ofDoubles();
        assertThat(doubleUpDownCounterBuilder.setAttributesAdvice(advisedKeys)).isSameAs(doubleUpDownCounterBuilder);
        assertThat(doubleUpDownCounterBuilder.build()).isInstanceOf(ExtendedDoubleUpDownCounter.class);

        ExtendedLongHistogramBuilder longHistogramBuilder =
                        (ExtendedLongHistogramBuilder) meter.histogramBuilder("advised.long.histogram")
                                        .ofLongs();
        assertThat(longHistogramBuilder.setAttributesAdvice(advisedKeys)).isSameAs(longHistogramBuilder);
        assertThat(longHistogramBuilder.build()).isInstanceOf(ExtendedLongHistogram.class);

        ExtendedDoubleHistogramBuilder doubleHistogramBuilder =
                        (ExtendedDoubleHistogramBuilder) meter.histogramBuilder("advised.double.histogram");
        assertThat(doubleHistogramBuilder.setAttributesAdvice(advisedKeys)).isSameAs(doubleHistogramBuilder);
        assertThat(doubleHistogramBuilder.build()).isInstanceOf(ExtendedDoubleHistogram.class);

        ExtendedDoubleGaugeBuilder doubleGaugeBuilder =
                        (ExtendedDoubleGaugeBuilder) meter.gaugeBuilder("advised.double.gauge");
        assertThat(doubleGaugeBuilder.setAttributesAdvice(advisedKeys)).isSameAs(doubleGaugeBuilder);
        assertThat(doubleGaugeBuilder.build()).isInstanceOf(ExtendedDoubleGauge.class);
    }

    @Test
    void noopMeterProviderCreatesObservableInstrumentsAndBatchCallbacks() {
        Meter meter = noopExtendedMeter();
        AtomicBoolean callbackInvoked = new AtomicBoolean(false);

        ObservableLongCounter longCounter = meter.counterBuilder("observable.long.counter")
                        .buildWithCallback(measurement -> recordLongMeasurement(measurement, callbackInvoked));
        ObservableDoubleCounter doubleCounter = meter.counterBuilder("observable.double.counter")
                        .ofDoubles()
                        .buildWithCallback(measurement -> recordDoubleMeasurement(measurement, callbackInvoked));
        ObservableLongUpDownCounter longUpDownCounter = meter.upDownCounterBuilder("observable.long.updown")
                        .buildWithCallback(measurement -> recordLongMeasurement(measurement, callbackInvoked));
        ObservableDoubleUpDownCounter doubleUpDownCounter = meter.upDownCounterBuilder("observable.double.updown")
                        .ofDoubles()
                        .buildWithCallback(measurement -> recordDoubleMeasurement(measurement, callbackInvoked));
        ObservableLongGauge longGauge = meter.gaugeBuilder("observable.long.gauge")
                        .ofLongs()
                        .buildWithCallback(measurement -> recordLongMeasurement(measurement, callbackInvoked));
        ObservableDoubleGauge doubleGauge = meter.gaugeBuilder("observable.double.gauge")
                        .buildWithCallback(measurement -> recordDoubleMeasurement(measurement, callbackInvoked));
        BatchCallback batchCallback = meter.batchCallback(() -> callbackInvoked.set(true),
                        meter.counterBuilder("batch.counter").buildObserver());

        longCounter.close();
        doubleCounter.close();
        longUpDownCounter.close();
        doubleUpDownCounter.close();
        longGauge.close();
        doubleGauge.close();
        batchCallback.close();
        assertThat(callbackInvoked).isFalse();
    }

    @Test
    void noopTracerProviderExposesExtendedTracerAndSpanBuilderHelpers() throws Exception {
        TracerProvider tracerProvider = ExtendedDefaultTracerProvider.getNoop();
        Tracer tracer = tracerProvider.tracerBuilder("integration.tracer")
                        .setSchemaUrl("https://example.com/schema")
                        .setInstrumentationVersion("1.0.0")
                        .build();

        assertThat(tracerProvider.get("named")).isInstanceOf(ExtendedTracer.class);
        assertThat(tracerProvider.get("named", "1.0.0")).isInstanceOf(ExtendedTracer.class);
        assertThat(tracer).isInstanceOf(ExtendedTracer.class);
        ExtendedTracer extendedTracer = (ExtendedTracer) tracer;
        assertThat(extendedTracer.isEnabled()).isFalse();

        SpanContext invalidContext = SpanContext.getInvalid();
        ExtendedSpanBuilder builder = extendedTracer.spanBuilder("client span")
                        .setParent(Context.root())
                        .setParentFrom(ContextPropagators.noop(), Map.of("traceparent", "ignored"))
                        .setNoParent()
                        .addLink(invalidContext)
                        .addLink(invalidContext, Attributes.of(AttributeKey.stringKey("link"), "value"))
                        .setAttribute("string", "value")
                        .setAttribute("long", 1L)
                        .setAttribute("double", 2.5D)
                        .setAttribute("boolean", true)
                        .setAttribute(AttributeKey.stringKey("typed"), "attribute")
                        .setAllAttributes(Attributes.of(AttributeKey.longKey("all"), 3L))
                        .setSpanKind(SpanKind.CLIENT)
                        .setStartTimestamp(Instant.EPOCH);

        Span span = builder.startSpan();
        assertThat(span.getSpanContext().isValid()).isFalse();
        span.end();
        span.end(1, TimeUnit.MILLISECONDS);
        span.end(Instant.EPOCH.plusMillis(1));

        assertThat(builder.startAndCall(() -> "called")).isEqualTo("called");
        assertThat(builder.startAndCall(() -> "called without callback", (finishedSpan, error) -> {
            throw new AssertionError("Noop span builder should not invoke the completion callback");
        })).isEqualTo("called without callback");

        AtomicBoolean runnableInvoked = new AtomicBoolean(false);
        builder.startAndRun(() -> runnableInvoked.set(true));
        assertThat(runnableInvoked).isTrue();
        builder.startAndRun(() -> runnableInvoked.set(false), (finishedSpan, error) -> {
            throw new AssertionError("Noop span builder should not invoke the completion callback");
        });
        assertThat(runnableInvoked).isFalse();
    }

    @Test
    void passThroughPropagatorRoundTripsConfiguredFieldsCaseInsensitively() {
        TextMapPropagator propagator = PassThroughPropagator.create("x-request-id", "x-tenant-id", "missing");
        Map<String, String> carrier = Map.of("x-request-id", "request-123", "x-tenant-id", "tenant-a");

        Context extracted = propagator.extract(Context.root(), carrier, MAP_GETTER);
        Map<String, String> injected = new LinkedHashMap<>();
        propagator.inject(extracted, injected, MAP_SETTER);

        assertThat(propagator.fields()).containsExactly("x-request-id", "x-tenant-id", "missing");
        assertThat(propagator).hasToString("PassThroughPropagator{fields=[x-request-id, x-tenant-id, missing]}");
        assertThat(injected).containsEntry("x-request-id", "request-123").containsEntry("x-tenant-id", "tenant-a");
        assertThat(injected).doesNotContainKey("missing");

        TextMapPropagator emptyPropagator = PassThroughPropagator.create(List.of());
        assertThat(emptyPropagator.fields()).isEmpty();
        assertThatThrownBy(() -> PassThroughPropagator.create("valid", null))
                        .isInstanceOf(NullPointerException.class)
                        .hasMessageContaining("field");
    }

    @Test
    void extendedContextPropagatorsExtractAndInjectCurrentTextMapContext() {
        ContextPropagators propagators = ContextPropagators.create(PassThroughPropagator.create("x-request-id"));
        Context extracted = ExtendedContextPropagators.extractTextMapPropagationContext(
                        Map.of("X-Request-ID", "request-456"), propagators);

        try (Scope ignored = extracted.makeCurrent()) {
            Map<String, String> currentPropagationContext =
                            ExtendedContextPropagators.getTextMapPropagationContext(propagators);
            assertThat(currentPropagationContext).containsExactly(Map.entry("x-request-id", "request-456"));
            assertThatThrownBy(() -> currentPropagationContext.put("other", "value"))
                            .isInstanceOf(UnsupportedOperationException.class);
        }

        Context unchanged = ExtendedContextPropagators.extractTextMapPropagationContext(null, propagators);
        assertThat(unchanged).isSameAs(Context.current());
    }

    @Test
    void declarativeConfigPropertiesExposeDefaultsNestedValuesAndMaps() {
        DeclarativeConfigProperties nested = new MapBackedDeclarativeConfigProperties(Map.of("inner", "value"));
        DeclarativeConfigProperties properties = new MapBackedDeclarativeConfigProperties(Map.of(
                        "string", "text",
                        "boolean", true,
                        "int", 3,
                        "long", 4L,
                        "double", 5.5D,
                        "string.list", List.of("a", "b"),
                        "structured", nested,
                        "structured.list", List.of(nested)));

        assertThat(properties.getString("string")).isEqualTo("text");
        assertThat(properties.getString("missing", "fallback")).isEqualTo("fallback");
        assertThat(properties.getBoolean("boolean")).isTrue();
        assertThat(properties.getBoolean("missing", true)).isTrue();
        assertThat(properties.getInt("int")).isEqualTo(3);
        assertThat(properties.getInt("missing", 7)).isEqualTo(7);
        assertThat(properties.getLong("long")).isEqualTo(4L);
        assertThat(properties.getLong("missing", 8L)).isEqualTo(8L);
        assertThat(properties.getDouble("double")).isEqualTo(5.5D);
        assertThat(properties.getDouble("missing", 9.5D)).isEqualTo(9.5D);
        assertThat(properties.getScalarList("string.list", String.class)).containsExactly("a", "b");
        assertThat(properties.getScalarList("missing", String.class, List.of("fallback"))).containsExactly("fallback");
        assertThat(properties.getStructured("structured")).isSameAs(nested);
        assertThat(properties.getStructured("missing", DeclarativeConfigProperties.empty())).isSameAs(
                        DeclarativeConfigProperties.empty());
        assertThat(properties.getStructuredList("structured.list")).containsExactly(nested);
        assertThat(properties.getStructuredList("missing", List.of(nested))).containsExactly(nested);
        assertThat(properties.getPropertyKeys()).contains("string", "structured", "structured.list");
        assertThat(properties.getComponentLoader()).isNotNull();

        Map<String, Object> propertyMap = DeclarativeConfigProperties.toMap(properties);
        assertThat(propertyMap).containsEntry("string", "text")
                        .containsEntry("boolean", true)
                        .containsEntry("long", 4L)
                        .containsEntry("double", 5.5D)
                        .containsEntry("string.list", List.of("a", "b"));
        assertThat(propertyMap.get("structured")).isEqualTo(Map.of("inner", "value"));
        assertThat(propertyMap.get("structured.list")).isEqualTo(List.of(Map.of("inner", "value")));
    }

    @Test
    void instrumentationConfigUtilReadsKnownInstrumentationSettings() {
        DeclarativeConfigProperties clientHttp = new MapBackedDeclarativeConfigProperties(Map.of(
                        "request_captured_headers", List.of("x-client-request"),
                        "response_captured_headers", List.of("x-client-response")));
        DeclarativeConfigProperties serverHttp = new MapBackedDeclarativeConfigProperties(Map.of(
                        "request_captured_headers", List.of("x-server-request"),
                        "response_captured_headers", List.of("x-server-response")));
        DeclarativeConfigProperties peer = new MapBackedDeclarativeConfigProperties(Map.of("service_mapping", List.of(
                        new MapBackedDeclarativeConfigProperties(Map.of("peer", "db.example", "service", "database")),
                        new MapBackedDeclarativeConfigProperties(Map.of("peer", "cache.example")),
                        new MapBackedDeclarativeConfigProperties(
                                        Map.of("peer", "queue.example", "service", "queue")))));
        DeclarativeConfigProperties general = new MapBackedDeclarativeConfigProperties(Map.of(
                        "peer", peer,
                        "http", new MapBackedDeclarativeConfigProperties(Map.of(
                                        "client", clientHttp,
                                        "server", serverHttp))));
        DeclarativeConfigProperties java = new MapBackedDeclarativeConfigProperties(Map.of(
                        "http-client", new MapBackedDeclarativeConfigProperties(Map.of("enabled", true))));
        ConfigProvider provider = () -> new MapBackedDeclarativeConfigProperties(Map.of(
                        "general", general,
                        "java", java));

        assertThat(InstrumentationConfigUtil.peerServiceMapping(provider))
                        .containsExactly(Map.entry("db.example", "database"), Map.entry("queue.example", "queue"));
        assertThat(InstrumentationConfigUtil.httpClientRequestCapturedHeaders(provider))
                        .containsExactly("x-client-request");
        assertThat(InstrumentationConfigUtil.httpClientResponseCapturedHeaders(provider))
                        .containsExactly("x-client-response");
        assertThat(InstrumentationConfigUtil.httpServerRequestCapturedHeaders(provider))
                        .containsExactly("x-server-request");
        assertThat(InstrumentationConfigUtil.httpServerResponseCapturedHeaders(provider))
                        .containsExactly("x-server-response");
        assertThat(InstrumentationConfigUtil.javaInstrumentationConfig(provider, "http-client").getBoolean("enabled"))
                        .isTrue();
        String missingSetting = InstrumentationConfigUtil.getOrNull(provider,
                        properties -> properties.getString("missing"), "general", "peer");
        assertThat(missingSetting).isNull();
        assertThat(InstrumentationConfigUtil.peerServiceMapping(ConfigProvider.noop())).isNull();
    }

    @Test
    void configProviderGlobalStateAndExceptionsUsePublicApi() {
        ConfigProvider noop = ConfigProvider.noop();
        assertThat(noop.getInstrumentationConfig()).isNull();
        assertThat(DeclarativeConfigProperties.empty().getPropertyKeys()).isEmpty();
        assertThat(DeclarativeConfigProperties.empty().getComponentLoader()).isNotNull();

        DeclarativeConfigProperties config = new MapBackedDeclarativeConfigProperties(Map.of("enabled", true));
        ConfigProvider provider = () -> config;
        GlobalConfigProvider.resetForTest();
        try {
            GlobalConfigProvider.set(provider);
            assertThat(GlobalConfigProvider.get().getInstrumentationConfig().getBoolean("enabled")).isTrue();
            assertThatThrownBy(() -> GlobalConfigProvider.set(() -> DeclarativeConfigProperties.empty()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("GlobalConfigProvider.set has already been called");
        } finally {
            GlobalConfigProvider.resetForTest();
        }

        RuntimeException cause = new RuntimeException("cause");
        DeclarativeConfigException exception = new DeclarativeConfigException("invalid config", cause);
        assertThat(exception).hasMessage("invalid config").hasCause(cause);
    }

    @Test
    void extendedOpenTelemetryDefaultConfigProviderIsNoop() {
        ExtendedOpenTelemetry openTelemetry = new MinimalExtendedOpenTelemetry();

        assertThat(openTelemetry.getConfigProvider().getInstrumentationConfig()).isNull();
        assertThat(openTelemetry.getTracerProvider()).isInstanceOf(TracerProvider.class);
        assertThat(openTelemetry.getPropagators()).isNotNull();
        assertThat(openTelemetry.getMeterProvider()).isNotNull();
        assertThat(openTelemetry.getLogsBridge()).isNotNull();
    }

    private static Meter noopExtendedMeter() {
        MeterProvider meterProvider = ExtendedDefaultMeterProvider.getNoop();
        return meterProvider.meterBuilder("integration.meter")
                        .setSchemaUrl("https://example.com/schema")
                        .setInstrumentationVersion("1.0.0")
                        .build();
    }

    private static void recordLongMeasurement(ObservableLongMeasurement measurement, AtomicBoolean invoked) {
        invoked.set(true);
        measurement.record(1L);
        measurement.record(2L, Attributes.of(AttributeKey.stringKey("measurement"), "long"));
    }

    private static void recordDoubleMeasurement(ObservableDoubleMeasurement measurement, AtomicBoolean invoked) {
        invoked.set(true);
        measurement.record(1.5D);
        measurement.record(2.5D, Attributes.of(AttributeKey.stringKey("measurement"), "double"));
    }

    private static final class MinimalExtendedOpenTelemetry implements ExtendedOpenTelemetry {
        @Override
        public TracerProvider getTracerProvider() {
            return ExtendedDefaultTracerProvider.getNoop();
        }

        @Override
        public ContextPropagators getPropagators() {
            return ContextPropagators.noop();
        }
    }

    private static final class MapBackedDeclarativeConfigProperties implements DeclarativeConfigProperties {
        private final Map<String, Object> values;

        private MapBackedDeclarativeConfigProperties(Map<String, Object> values) {
            this.values = values;
        }

        @Override
        public String getString(String name) {
            Object value = values.get(name);
            return value instanceof String ? (String) value : null;
        }

        @Override
        public Boolean getBoolean(String name) {
            Object value = values.get(name);
            return value instanceof Boolean ? (Boolean) value : null;
        }

        @Override
        public Integer getInt(String name) {
            Object value = values.get(name);
            return value instanceof Integer ? (Integer) value : null;
        }

        @Override
        public Long getLong(String name) {
            Object value = values.get(name);
            return value instanceof Long ? (Long) value : null;
        }

        @Override
        public Double getDouble(String name) {
            Object value = values.get(name);
            return value instanceof Double ? (Double) value : null;
        }

        @Override
        public <T> List<T> getScalarList(String name, Class<T> scalarType) {
            Object value = values.get(name);
            if (!(value instanceof List<?>)) {
                return null;
            }
            List<?> source = (List<?>) value;
            List<T> converted = new ArrayList<>(source.size());
            for (Object item : source) {
                if (!scalarType.isInstance(item)) {
                    throw new DeclarativeConfigException("List item is not a " + scalarType.getSimpleName());
                }
                converted.add(scalarType.cast(item));
            }
            return converted;
        }

        @Override
        public DeclarativeConfigProperties getStructured(String name) {
            Object value = values.get(name);
            return value instanceof DeclarativeConfigProperties ? (DeclarativeConfigProperties) value : null;
        }

        @Override
        public List<DeclarativeConfigProperties> getStructuredList(String name) {
            Object value = values.get(name);
            if (!(value instanceof List<?>)) {
                return null;
            }
            List<?> source = (List<?>) value;
            List<DeclarativeConfigProperties> converted = new ArrayList<>(source.size());
            for (Object item : source) {
                if (!(item instanceof DeclarativeConfigProperties)) {
                    throw new DeclarativeConfigException("List item is not structured config");
                }
                converted.add((DeclarativeConfigProperties) item);
            }
            return converted;
        }

        @Override
        public Set<String> getPropertyKeys() {
            return values.keySet();
        }

        @Override
        public ComponentLoader getComponentLoader() {
            return DeclarativeConfigProperties.empty().getComponentLoader();
        }
    }
}
