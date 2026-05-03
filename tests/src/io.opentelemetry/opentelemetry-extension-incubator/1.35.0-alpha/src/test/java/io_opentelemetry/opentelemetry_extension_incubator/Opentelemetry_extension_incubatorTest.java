/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_extension_incubator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.api.metrics.DoubleCounterBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleUpDownCounter;
import io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongGaugeBuilder;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.LongHistogramBuilder;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.ObservableDoubleCounter;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import io.opentelemetry.api.metrics.ObservableDoubleUpDownCounter;
import io.opentelemetry.api.metrics.ObservableLongCounter;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.extension.incubator.logs.AnyValue;
import io.opentelemetry.extension.incubator.logs.AnyValueType;
import io.opentelemetry.extension.incubator.logs.ExtendedLogRecordBuilder;
import io.opentelemetry.extension.incubator.logs.KeyAnyValue;
import io.opentelemetry.extension.incubator.metrics.DoubleGauge;
import io.opentelemetry.extension.incubator.metrics.ExtendedDoubleCounterBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedDoubleGaugeBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedDoubleHistogramBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedDoubleUpDownCounterBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedLongCounterBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedLongGaugeBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedLongHistogramBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedLongUpDownCounterBuilder;
import io.opentelemetry.extension.incubator.metrics.LongGauge;
import io.opentelemetry.extension.incubator.propagation.ExtendedContextPropagators;
import io.opentelemetry.extension.incubator.propagation.PassThroughPropagator;
import io.opentelemetry.extension.incubator.trace.ExtendedSpan;
import io.opentelemetry.extension.incubator.trace.ExtendedSpanBuilder;
import io.opentelemetry.extension.incubator.trace.ExtendedTracer;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

public class Opentelemetry_extension_incubatorTest {
    private static final ContextKey<String> REQUEST_ID = ContextKey.named("request-id");
    private static final String TRACE_ID = "00000000000000000000000000000001";
    private static final String SPAN_ID = "0000000000000002";

    @Test
    void anyValuePrimitivesAndBytesExposeTypesValuesAndRepresentations() {
        AnyValue<String> stringValue = AnyValue.of("alpha");
        assertThat(stringValue.getType()).isEqualTo(AnyValueType.STRING);
        assertThat(stringValue.getValue()).isEqualTo("alpha");
        assertThat(stringValue.asString()).isEqualTo("alpha");
        assertThat(stringValue.toString()).isEqualTo("AnyValueString{alpha}");
        assertThat(stringValue).isEqualTo(AnyValue.of("alpha")).hasSameHashCodeAs(AnyValue.of("alpha"));
        assertThat(stringValue).isNotEqualTo(AnyValue.of("beta"));

        AnyValue<Boolean> booleanValue = AnyValue.of(true);
        assertThat(booleanValue.getType()).isEqualTo(AnyValueType.BOOLEAN);
        assertThat(booleanValue.getValue()).isTrue();
        assertThat(booleanValue.asString()).isEqualTo("true");

        AnyValue<Long> longValue = AnyValue.of(42L);
        assertThat(longValue.getType()).isEqualTo(AnyValueType.LONG);
        assertThat(longValue.getValue()).isEqualTo(42L);
        assertThat(longValue.asString()).isEqualTo("42");

        AnyValue<Double> doubleValue = AnyValue.of(3.5D);
        assertThat(doubleValue.getType()).isEqualTo(AnyValueType.DOUBLE);
        assertThat(doubleValue.getValue()).isEqualTo(3.5D);
        assertThat(doubleValue.asString()).isEqualTo("3.5");

        byte[] rawBytes = new byte[] {1, 2, 3, 4};
        AnyValue<ByteBuffer> bytesValue = AnyValue.of(rawBytes);
        rawBytes[0] = 99;
        ByteBuffer buffer = bytesValue.getValue();
        byte[] copiedBytes = new byte[buffer.remaining()];
        buffer.duplicate().get(copiedBytes);
        assertThat(bytesValue.getType()).isEqualTo(AnyValueType.BYTES);
        assertThat(copiedBytes).containsExactly((byte) 1, (byte) 2, (byte) 3, (byte) 4);
        assertThat(buffer.isReadOnly()).isTrue();
        assertThat(bytesValue.asString()).isEqualTo("AQIDBA==");
        assertThat(bytesValue.toString()).isEqualTo("AnyValueBytes{AQIDBA==}");
        assertThat(bytesValue).isEqualTo(AnyValue.of(new byte[] {1, 2, 3, 4}));
        assertThatExceptionOfType(ReadOnlyBufferException.class).isThrownBy(() -> buffer.put((byte) 5));
    }

    @Test
    void anyValueArraysAndKeyValueListsAreImmutableAndFormatted() {
        AnyValue<List<AnyValue<?>>> array = AnyValue.of(AnyValue.of("one"), AnyValue.of(2L), AnyValue.of(false));
        assertThat(array.getType()).isEqualTo(AnyValueType.ARRAY);
        assertThat(array.asString()).isEqualTo("[one, 2, false]");
        assertThat(array.toString()).isEqualTo("AnyValueArray{[one, 2, false]}");
        assertThat(array.getValue()).extracting(AnyValue::asString).containsExactly("one", "2", "false");
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> array.getValue().add(AnyValue.of("new")));
        assertThat(array).isEqualTo(AnyValue.of(AnyValue.of("one"), AnyValue.of(2L), AnyValue.of(false)));

        KeyAnyValue first = KeyAnyValue.of("first", AnyValue.of("one"));
        KeyAnyValue second = KeyAnyValue.of("second", AnyValue.of(2L));
        assertThat(first.getKey()).isEqualTo("first");
        assertThat(first.getAnyValue().asString()).isEqualTo("one");
        assertThat(first).isEqualTo(KeyAnyValue.of("first", AnyValue.of("one")));
        assertThat(first.toString()).contains("first", "AnyValueString{one}");

        AnyValue<List<KeyAnyValue>> keyValueList = AnyValue.of(first, second);
        assertThat(keyValueList.getType()).isEqualTo(AnyValueType.KEY_VALUE_LIST);
        assertThat(keyValueList.asString()).isEqualTo("[first=one, second=2]");
        assertThat(keyValueList.toString()).isEqualTo("KeyAnyValueList{[first=one, second=2]}");
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> keyValueList.getValue().add(KeyAnyValue.of("third", AnyValue.of(true))));

        Map<String, AnyValue<?>> map = new LinkedHashMap<>();
        map.put("first", AnyValue.of("one"));
        map.put("second", AnyValue.of(2L));
        assertThat(AnyValue.of(map)).isEqualTo(keyValueList);
    }

    @Test
    void extendedLogRecordBuilderAcceptsStructuredAnyValueBody() {
        RecordingLogRecordBuilder logRecordBuilder = new RecordingLogRecordBuilder();
        Context context = Context.current().with(REQUEST_ID, "log-request");
        AnyValue<?> body = AnyValue.of(
                KeyAnyValue.of("event.name", AnyValue.of("checkout")),
                KeyAnyValue.of("attempt", AnyValue.of(2L)),
                KeyAnyValue.of("successful", AnyValue.of(true)));
        AttributeKey<String> sourceKey = AttributeKey.stringKey("log.source");

        assertThat(logRecordBuilder.setTimestamp(123L, TimeUnit.MILLISECONDS)).isSameAs(logRecordBuilder);
        assertThat(logRecordBuilder.setTimestamp(Instant.ofEpochSecond(4, 5))).isSameAs(logRecordBuilder);
        assertThat(logRecordBuilder.setObservedTimestamp(456L, TimeUnit.MILLISECONDS)).isSameAs(logRecordBuilder);
        assertThat(logRecordBuilder.setObservedTimestamp(Instant.ofEpochSecond(7, 8))).isSameAs(logRecordBuilder);
        assertThat(logRecordBuilder.setContext(context)).isSameAs(logRecordBuilder);
        assertThat(logRecordBuilder.setSeverity(Severity.INFO)).isSameAs(logRecordBuilder);
        assertThat(logRecordBuilder.setSeverityText("INFO")).isSameAs(logRecordBuilder);
        assertThat(logRecordBuilder.setBody(body)).isSameAs(logRecordBuilder);
        assertThat(logRecordBuilder.setAttribute(sourceKey, "test-source")).isSameAs(logRecordBuilder);

        logRecordBuilder.emit();

        assertThat(logRecordBuilder.context).isSameAs(context);
        assertThat(logRecordBuilder.severity).isEqualTo(Severity.INFO);
        assertThat(logRecordBuilder.severityText).isEqualTo("INFO");
        assertThat(logRecordBuilder.body).isSameAs(body);
        assertThat(logRecordBuilder.attributes).containsEntry(sourceKey, "test-source");
        assertThat(logRecordBuilder.emitted).isTrue();
    }

    @Test
    void publicFactoriesRejectNullInputs() {
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> AnyValue.of((String) null));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> AnyValue.of((byte[]) null));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> AnyValue.of((AnyValue<?>[]) null));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> AnyValue.of((KeyAnyValue[]) null));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> AnyValue.of((Map<String, AnyValue<?>>) null));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> KeyAnyValue.of(null, AnyValue.of("value")));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> KeyAnyValue.of("key", null));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> PassThroughPropagator.create((String[]) null));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> PassThroughPropagator.create("kept", null));
    }

    @Test
    void passThroughPropagatorExtractsOnlyConfiguredHeadersAndReinjectsThem() {
        TextMapPropagator propagator = PassThroughPropagator.create(
                Arrays.asList("x-request-id", "x-baggage", "missing"));
        Map<String, String> incoming = new HashMap<>();
        incoming.put("x-request-id", "request-123");
        incoming.put("x-baggage", "kept");
        incoming.put("ignored", "dropped");

        Context extracted = propagator.extract(Context.root(), incoming, new MapTextGetter());
        Map<String, String> outgoing = new LinkedHashMap<>();
        propagator.inject(extracted, outgoing, Map::put);

        assertThat(propagator.fields()).containsExactly("x-request-id", "x-baggage", "missing");
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> propagator.fields().add("other"));
        assertThat(outgoing).containsExactly(Map.entry("x-request-id", "request-123"), Map.entry("x-baggage", "kept"));
        assertThat(propagator.toString()).isEqualTo("PassThroughPropagator{fields=[x-request-id, x-baggage, missing]}");

        TextMapPropagator noopPropagator = PassThroughPropagator.create(List.of());
        assertThat(noopPropagator.fields()).isEmpty();
    }

    @Test
    void extendedContextPropagatorsInjectAndExtractTextMapContext() {
        ContextPropagators propagators = ContextPropagators.create(new RequestIdPropagator());

        try (Scope ignored = Context.current().with(REQUEST_ID, "current-request").makeCurrent()) {
            Map<String, String> injected = ExtendedContextPropagators.getTextMapPropagationContext(propagators);
            assertThat(injected).containsExactly(Map.entry("x-request-id", "current-request"));
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> injected.put("x-request-id", "changed"));
        }

        Map<String, String> carrier = Map.of("x-request-id", "incoming-request");
        Context extracted = ExtendedContextPropagators.extractTextMapPropagationContext(carrier, propagators);
        assertThat(extracted.get(REQUEST_ID)).isEqualTo("incoming-request");
        Context current = Context.current();
        assertThat(ExtendedContextPropagators.extractTextMapPropagationContext(null, propagators)).isSameAs(current);
    }

    @Test
    void extendedTracerDelegatesBuilderOperationsAndRunsCodeInStartedSpan() {
        RecordingTracer delegateTracer = new RecordingTracer();
        ExtendedTracer tracer = ExtendedTracer.create(delegateTracer);
        ExtendedSpanBuilder builder = tracer.spanBuilder("operation");
        SpanContext linkContext = SpanContext.create(
                TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
        Attributes attributes = Attributes.of(AttributeKey.stringKey("attribute-key"), "attribute-value");
        ContextPropagators propagators = ContextPropagators.create(new RequestIdPropagator());

        assertThat(builder.setParent(Context.root())).isSameAs(builder);
        assertThat(builder.setNoParent()).isSameAs(builder);
        assertThat(builder.addLink(linkContext)).isSameAs(builder);
        assertThat(builder.addLink(linkContext, attributes)).isSameAs(builder);
        assertThat(builder.setAttribute("string", "value")).isSameAs(builder);
        assertThat(builder.setAttribute("long", 7L)).isSameAs(builder);
        assertThat(builder.setAttribute("double", 2.5D)).isSameAs(builder);
        assertThat(builder.setAttribute("boolean", true)).isSameAs(builder);
        assertThat(builder.setAttribute(AttributeKey.stringKey("typed"), "typed-value")).isSameAs(builder);
        assertThat(builder.setAllAttributes(attributes)).isSameAs(builder);
        assertThat(builder.setSpanKind(SpanKind.CLIENT)).isSameAs(builder);
        assertThat(builder.setStartTimestamp(123L, TimeUnit.MILLISECONDS)).isSameAs(builder);
        assertThat(builder.setStartTimestamp(Instant.ofEpochSecond(4, 5))).isSameAs(builder);
        assertThat(builder.setParentFrom(propagators, Map.of("x-request-id", "parent-request"))).isSameAs(builder);

        String result = builder.startAndCall(() -> {
            assertThat(Span.current()).isSameAs(delegateTracer.builder.span);
            return "called";
        });

        assertThat(result).isEqualTo("called");
        assertThat(delegateTracer.spanNames).containsExactly("operation");
        assertThat(delegateTracer.builder.operations)
                .contains("setParent", "setNoParent", "addLink", "addLinkWithAttributes", "setStringAttribute",
                        "setLongAttribute", "setDoubleAttribute", "setBooleanAttribute", "setTypedAttribute",
                        "setAllAttributes", "setSpanKind", "setStartTimestamp", "setParent", "startSpan");
        assertThat(delegateTracer.builder.parent.get(REQUEST_ID)).isEqualTo("parent-request");
        assertThat(delegateTracer.builder.span.ended).isTrue();
        assertThat(delegateTracer.builder.span.statusCode).isNull();
    }

    @Test
    void extendedSpanAddLinkDefaultMethodsReturnTheCurrentSpan() {
        RecordingSpan span = new RecordingSpan();
        SpanContext linkContext = SpanContext.create(
                TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
        Attributes linkAttributes = Attributes.of(AttributeKey.stringKey("link-type"), "follow-up");

        assertThat(span.addLink(linkContext)).isSameAs(span);
        assertThat(span.addLink(linkContext, linkAttributes)).isSameAs(span);
        assertThat(span.ended).isFalse();
    }

    @Test
    void extendedSpanBuilderRecordsFailuresBeforeRethrowing() {
        RecordingTracer delegateTracer = new RecordingTracer();
        ExtendedSpanBuilder builder = ExtendedTracer.create(delegateTracer).spanBuilder("failing-operation");
        IllegalStateException failure = new IllegalStateException("boom");

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> builder.startAndRun(() -> {
            throw failure;
        })).withMessage("boom");

        assertThat(delegateTracer.builder.span.ended).isTrue();
        assertThat(delegateTracer.builder.span.statusCode).isEqualTo(StatusCode.ERROR);
        assertThat(delegateTracer.builder.span.recordedExceptions).containsExactly(failure);
    }

    @Test
    void extendedMetricBuildersReturnTheSameBuilderWhenAttributesAdviceIsSet() {
        List<AttributeKey<?>> advice = List.of(AttributeKey.stringKey("region"), AttributeKey.longKey("shard"));

        StubLongCounterBuilder longCounterBuilder = new StubLongCounterBuilder();
        assertThat(longCounterBuilder.setAttributesAdvice(advice)).isSameAs(longCounterBuilder);

        StubDoubleCounterBuilder doubleCounterBuilder = new StubDoubleCounterBuilder();
        assertThat(doubleCounterBuilder.setAttributesAdvice(advice)).isSameAs(doubleCounterBuilder);

        StubLongHistogramBuilder longHistogramBuilder = new StubLongHistogramBuilder();
        assertThat(longHistogramBuilder.setAttributesAdvice(advice)).isSameAs(longHistogramBuilder);

        StubDoubleHistogramBuilder doubleHistogramBuilder = new StubDoubleHistogramBuilder();
        assertThat(doubleHistogramBuilder.setAttributesAdvice(advice)).isSameAs(doubleHistogramBuilder);

        StubLongUpDownCounterBuilder longUpDownCounterBuilder = new StubLongUpDownCounterBuilder();
        assertThat(longUpDownCounterBuilder.setAttributesAdvice(advice)).isSameAs(longUpDownCounterBuilder);

        StubDoubleUpDownCounterBuilder doubleUpDownCounterBuilder = new StubDoubleUpDownCounterBuilder();
        assertThat(doubleUpDownCounterBuilder.setAttributesAdvice(advice)).isSameAs(doubleUpDownCounterBuilder);

        StubLongGaugeBuilder longGaugeBuilder = new StubLongGaugeBuilder();
        LongGauge longGauge = longGaugeBuilder.build();
        longGauge.set(10L);
        longGauge.set(11L, Attributes.empty());
        assertThat(longGaugeBuilder.setAttributesAdvice(advice)).isSameAs(longGaugeBuilder);
        assertThat(((RecordingLongGauge) longGauge).recordedValues).containsExactly(10L, 11L);

        StubDoubleGaugeBuilder doubleGaugeBuilder = new StubDoubleGaugeBuilder();
        DoubleGauge doubleGauge = doubleGaugeBuilder.build();
        doubleGauge.set(1.5D);
        doubleGauge.set(2.5D, Attributes.empty());
        assertThat(doubleGaugeBuilder.setAttributesAdvice(advice)).isSameAs(doubleGaugeBuilder);
        assertThat(((RecordingDoubleGauge) doubleGauge).recordedValues).containsExactly(1.5D, 2.5D);
    }

    private static final class RecordingLogRecordBuilder implements ExtendedLogRecordBuilder {
        private final Map<AttributeKey<?>, Object> attributes = new LinkedHashMap<>();
        private Context context;
        private Severity severity;
        private String severityText;
        private AnyValue<?> body;
        private boolean emitted;

        @Override
        public LogRecordBuilder setTimestamp(long timestamp, TimeUnit unit) {
            return this;
        }

        @Override
        public LogRecordBuilder setTimestamp(Instant timestamp) {
            return this;
        }

        @Override
        public LogRecordBuilder setObservedTimestamp(long timestamp, TimeUnit unit) {
            return this;
        }

        @Override
        public LogRecordBuilder setObservedTimestamp(Instant timestamp) {
            return this;
        }

        @Override
        public LogRecordBuilder setContext(Context context) {
            this.context = context;
            return this;
        }

        @Override
        public LogRecordBuilder setSeverity(Severity severity) {
            this.severity = severity;
            return this;
        }

        @Override
        public LogRecordBuilder setSeverityText(String severityText) {
            this.severityText = severityText;
            return this;
        }

        @Override
        public LogRecordBuilder setBody(String body) {
            return this;
        }

        @Override
        public LogRecordBuilder setBody(AnyValue<?> body) {
            this.body = body;
            return this;
        }

        @Override
        public <T> LogRecordBuilder setAttribute(AttributeKey<T> key, T value) {
            attributes.put(key, value);
            return this;
        }

        @Override
        public void emit() {
            emitted = true;
        }
    }

    private static final class RequestIdPropagator implements TextMapPropagator {
        @Override
        public Collection<String> fields() {
            return List.of("x-request-id");
        }

        @Override
        public <C> void inject(Context context, C carrier, TextMapSetter<C> setter) {
            String requestId = context.get(REQUEST_ID);
            if (requestId != null) {
                setter.set(carrier, "x-request-id", requestId);
            }
        }

        @Override
        public <C> Context extract(Context context, C carrier, TextMapGetter<C> getter) {
            String requestId = getter.get(carrier, "X-REQUEST-ID");
            return requestId == null ? context : context.with(REQUEST_ID, requestId);
        }
    }

    private static final class MapTextGetter implements TextMapGetter<Map<String, String>> {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier.get(key);
        }
    }

    private static final class RecordingTracer implements Tracer {
        private final List<String> spanNames = new ArrayList<>();
        private RecordingSpanBuilder builder;

        @Override
        public SpanBuilder spanBuilder(String spanName) {
            spanNames.add(spanName);
            builder = new RecordingSpanBuilder();
            return builder;
        }
    }

    private static final class RecordingSpanBuilder implements SpanBuilder {
        private final List<String> operations = new ArrayList<>();
        private final RecordingSpan span = new RecordingSpan();
        private Context parent;

        @Override
        public SpanBuilder setParent(Context context) {
            operations.add("setParent");
            parent = context;
            return this;
        }

        @Override
        public SpanBuilder setNoParent() {
            operations.add("setNoParent");
            parent = null;
            return this;
        }

        @Override
        public SpanBuilder addLink(SpanContext spanContext) {
            operations.add("addLink");
            return this;
        }

        @Override
        public SpanBuilder addLink(SpanContext spanContext, Attributes attributes) {
            operations.add("addLinkWithAttributes");
            return this;
        }

        @Override
        public SpanBuilder setAttribute(String key, String value) {
            operations.add("setStringAttribute");
            return this;
        }

        @Override
        public SpanBuilder setAttribute(String key, long value) {
            operations.add("setLongAttribute");
            return this;
        }

        @Override
        public SpanBuilder setAttribute(String key, double value) {
            operations.add("setDoubleAttribute");
            return this;
        }

        @Override
        public SpanBuilder setAttribute(String key, boolean value) {
            operations.add("setBooleanAttribute");
            return this;
        }

        @Override
        public <T> SpanBuilder setAttribute(AttributeKey<T> key, T value) {
            operations.add("setTypedAttribute");
            return this;
        }

        @Override
        public SpanBuilder setAllAttributes(Attributes attributes) {
            operations.add("setAllAttributes");
            return this;
        }

        @Override
        public SpanBuilder setSpanKind(SpanKind spanKind) {
            operations.add("setSpanKind");
            return this;
        }

        @Override
        public SpanBuilder setStartTimestamp(long startTimestamp, TimeUnit unit) {
            operations.add("setStartTimestamp");
            return this;
        }

        @Override
        public Span startSpan() {
            operations.add("startSpan");
            return span;
        }
    }

    private static final class RecordingSpan implements ExtendedSpan {
        private final List<Throwable> recordedExceptions = new ArrayList<>();
        private boolean ended;
        private StatusCode statusCode;

        @Override
        public <T> Span setAttribute(AttributeKey<T> key, T value) {
            return this;
        }

        @Override
        public Span addEvent(String name, Attributes attributes) {
            return this;
        }

        @Override
        public Span addEvent(String name, Attributes attributes, long timestamp, TimeUnit unit) {
            return this;
        }

        @Override
        public Span setStatus(StatusCode statusCode, String description) {
            this.statusCode = statusCode;
            return this;
        }

        @Override
        public Span recordException(Throwable exception, Attributes additionalAttributes) {
            recordedExceptions.add(exception);
            return this;
        }

        @Override
        public Span updateName(String name) {
            return this;
        }

        @Override
        public void end() {
            ended = true;
        }

        @Override
        public void end(long timestamp, TimeUnit unit) {
            ended = true;
        }

        @Override
        public SpanContext getSpanContext() {
            return SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
        }

        @Override
        public boolean isRecording() {
            return true;
        }
    }

    private static final class RecordingLongGauge implements LongGauge {
        private final List<Long> recordedValues = new ArrayList<>();

        @Override
        public void set(long value) {
            recordedValues.add(value);
        }

        @Override
        public void set(long value, Attributes attributes) {
            recordedValues.add(value);
        }
    }

    private static final class RecordingDoubleGauge implements DoubleGauge {
        private final List<Double> recordedValues = new ArrayList<>();

        @Override
        public void set(double value) {
            recordedValues.add(value);
        }

        @Override
        public void set(double value, Attributes attributes) {
            recordedValues.add(value);
        }
    }

    private static final class StubLongCounterBuilder implements ExtendedLongCounterBuilder {
        @Override
        public StubLongCounterBuilder setDescription(String description) {
            return this;
        }

        @Override
        public StubLongCounterBuilder setUnit(String unit) {
            return this;
        }

        @Override
        public DoubleCounterBuilder ofDoubles() {
            return null;
        }

        @Override
        public LongCounter build() {
            return null;
        }

        @Override
        public ObservableLongCounter buildWithCallback(Consumer<ObservableLongMeasurement> callback) {
            return null;
        }
    }

    private static final class StubDoubleCounterBuilder implements ExtendedDoubleCounterBuilder {
        @Override
        public StubDoubleCounterBuilder setDescription(String description) {
            return this;
        }

        @Override
        public StubDoubleCounterBuilder setUnit(String unit) {
            return this;
        }

        @Override
        public DoubleCounter build() {
            return null;
        }

        @Override
        public ObservableDoubleCounter buildWithCallback(Consumer<ObservableDoubleMeasurement> callback) {
            return null;
        }
    }

    private static final class StubLongHistogramBuilder implements ExtendedLongHistogramBuilder {
        @Override
        public StubLongHistogramBuilder setDescription(String description) {
            return this;
        }

        @Override
        public StubLongHistogramBuilder setUnit(String unit) {
            return this;
        }

        @Override
        public LongHistogram build() {
            return null;
        }
    }

    private static final class StubDoubleHistogramBuilder implements ExtendedDoubleHistogramBuilder {
        @Override
        public StubDoubleHistogramBuilder setDescription(String description) {
            return this;
        }

        @Override
        public StubDoubleHistogramBuilder setUnit(String unit) {
            return this;
        }

        @Override
        public LongHistogramBuilder ofLongs() {
            return null;
        }

        @Override
        public DoubleHistogram build() {
            return null;
        }
    }

    private static final class StubLongUpDownCounterBuilder implements ExtendedLongUpDownCounterBuilder {
        @Override
        public StubLongUpDownCounterBuilder setDescription(String description) {
            return this;
        }

        @Override
        public StubLongUpDownCounterBuilder setUnit(String unit) {
            return this;
        }

        @Override
        public DoubleUpDownCounterBuilder ofDoubles() {
            return null;
        }

        @Override
        public LongUpDownCounter build() {
            return null;
        }

        @Override
        public ObservableLongUpDownCounter buildWithCallback(Consumer<ObservableLongMeasurement> callback) {
            return null;
        }
    }

    private static final class StubDoubleUpDownCounterBuilder implements ExtendedDoubleUpDownCounterBuilder {
        @Override
        public StubDoubleUpDownCounterBuilder setDescription(String description) {
            return this;
        }

        @Override
        public StubDoubleUpDownCounterBuilder setUnit(String unit) {
            return this;
        }

        @Override
        public DoubleUpDownCounter build() {
            return null;
        }

        @Override
        public ObservableDoubleUpDownCounter buildWithCallback(Consumer<ObservableDoubleMeasurement> callback) {
            return null;
        }
    }

    private static final class StubLongGaugeBuilder implements ExtendedLongGaugeBuilder {
        private final RecordingLongGauge gauge = new RecordingLongGauge();

        @Override
        public StubLongGaugeBuilder setDescription(String description) {
            return this;
        }

        @Override
        public StubLongGaugeBuilder setUnit(String unit) {
            return this;
        }

        @Override
        public LongGauge build() {
            return gauge;
        }

        @Override
        public ObservableLongGauge buildWithCallback(Consumer<ObservableLongMeasurement> callback) {
            return null;
        }
    }

    private static final class StubDoubleGaugeBuilder implements ExtendedDoubleGaugeBuilder {
        private final RecordingDoubleGauge gauge = new RecordingDoubleGauge();

        @Override
        public StubDoubleGaugeBuilder setDescription(String description) {
            return this;
        }

        @Override
        public StubDoubleGaugeBuilder setUnit(String unit) {
            return this;
        }

        @Override
        public LongGaugeBuilder ofLongs() {
            return null;
        }

        @Override
        public DoubleGauge build() {
            return gauge;
        }

        @Override
        public ObservableDoubleGauge buildWithCallback(Consumer<ObservableDoubleMeasurement> callback) {
            return null;
        }
    }
}
