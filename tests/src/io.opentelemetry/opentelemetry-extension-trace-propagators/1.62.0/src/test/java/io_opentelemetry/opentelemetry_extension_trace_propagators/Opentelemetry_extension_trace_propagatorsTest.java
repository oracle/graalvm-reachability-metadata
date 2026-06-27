/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_extension_trace_propagators;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.extension.trace.propagation.JaegerPropagator;
import io.opentelemetry.extension.trace.propagation.OtTracePropagator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class Opentelemetry_extension_trace_propagatorsTest {
    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String OTHER_TRACE_ID = "0000000000000000aaaaaaaaaaaaaaaa";
    private static final String SPAN_ID = "00f067aa0ba902b7";
    private static final String OTHER_SPAN_ID = "00000000000000ab";
    private static final TextMapSetter<Map<String, String>> MAP_SETTER = Map::put;
    private static final TextMapGetter<Map<String, String>> MAP_GETTER = new MapGetter();

    @Test
    void b3SingleHeaderInjectsAndExtractsSampledSpanContext() {
        TextMapPropagator propagator = B3Propagator.injectingSingleHeader();
        Map<String, String> carrier = new LinkedHashMap<>();

        propagator.inject(sampledContext(), carrier, MAP_SETTER);

        assertThat(propagator.fields()).containsExactly("b3");
        assertThat(carrier).containsEntry("b3", TRACE_ID + "-" + SPAN_ID + "-1");

        Context extracted = propagator.extract(Context.root(), carrier, MAP_GETTER);

        assertExtractedSpan(extracted, TRACE_ID, SPAN_ID, true);
    }

    @Test
    void b3MultiHeaderInjectsHeadersAndExtractsDebugAsSampledContext() {
        TextMapPropagator multiHeaderPropagator = B3Propagator.injectingMultiHeaders();
        Map<String, String> carrier = new LinkedHashMap<>();

        multiHeaderPropagator.inject(sampledContext(), carrier, MAP_SETTER);

        assertThat(multiHeaderPropagator.fields())
                .containsExactly("X-B3-TraceId", "X-B3-SpanId", "X-B3-Sampled");
        assertThat(carrier)
                .containsEntry("X-B3-TraceId", TRACE_ID)
                .containsEntry("X-B3-SpanId", SPAN_ID)
                .containsEntry("X-B3-Sampled", "1")
                .doesNotContainKey("X-B3-Flags");

        Map<String, String> debugCarrier = new LinkedHashMap<>();
        debugCarrier.put("X-B3-TraceId", OTHER_TRACE_ID);
        debugCarrier.put("X-B3-SpanId", OTHER_SPAN_ID);
        debugCarrier.put("X-B3-Sampled", "0");
        debugCarrier.put("X-B3-Flags", "1");

        Context extracted = multiHeaderPropagator.extract(Context.root(), debugCarrier, MAP_GETTER);
        Map<String, String> reinjected = new LinkedHashMap<>();
        B3Propagator.injectingSingleHeader().inject(extracted, reinjected, MAP_SETTER);

        assertExtractedSpan(extracted, OTHER_TRACE_ID, OTHER_SPAN_ID, true);
        assertThat(reinjected).containsEntry("b3", OTHER_TRACE_ID + "-" + OTHER_SPAN_ID + "-d");
    }

    @Test
    void b3ExtractionPrefersSingleHeaderWhenBothFormatsArePresent() {
        Map<String, String> carrier = new LinkedHashMap<>();
        carrier.put("b3", TRACE_ID + "-" + SPAN_ID + "-0");
        carrier.put("X-B3-TraceId", OTHER_TRACE_ID);
        carrier.put("X-B3-SpanId", OTHER_SPAN_ID);
        carrier.put("X-B3-Sampled", "1");

        Context extracted =
                B3Propagator.injectingMultiHeaders().extract(Context.root(), carrier, MAP_GETTER);

        assertExtractedSpan(extracted, TRACE_ID, SPAN_ID, false);
    }

    @Test
    @SuppressWarnings("deprecation")
    void jaegerInjectsTraceHeaderAndBaggageThenExtractsThem() {
        TextMapPropagator propagator = JaegerPropagator.getInstance();
        Map<String, String> carrier = new LinkedHashMap<>();
        Context context =
                Baggage.builder()
                        .put("tenant", "payments")
                        .build()
                        .storeInContext(sampledContext());

        propagator.inject(context, carrier, MAP_SETTER);

        assertThat(propagator.fields()).containsExactly("uber-trace-id");
        assertThat(carrier)
                .containsEntry("uber-trace-id", TRACE_ID + ":" + SPAN_ID + ":0:1")
                .containsEntry("uberctx-tenant", "payments");

        carrier.put("uberctx-user", "alice");
        carrier.put("jaeger-baggage", "region=eu, environment = test");
        Context extracted = propagator.extract(Context.root(), carrier, MAP_GETTER);

        assertExtractedSpan(extracted, TRACE_ID, SPAN_ID, true);
        assertThat(Baggage.fromContext(extracted).getEntryValue("tenant")).isEqualTo("payments");
        assertThat(Baggage.fromContext(extracted).getEntryValue("user")).isEqualTo("alice");
        assertThat(Baggage.fromContext(extracted).getEntryValue("region")).isEqualTo("eu");
        assertThat(Baggage.fromContext(extracted).getEntryValue("environment")).isEqualTo("test");
    }

    @Test
    @SuppressWarnings("deprecation")
    void jaegerExtractsUrlEncodedHeaderAndPadsShortIdentifiers() {
        Map<String, String> carrier = new LinkedHashMap<>();
        carrier.put("uber-trace-id", "1%3A2%3A0%3A1");

        Context extracted =
                JaegerPropagator.getInstance().extract(Context.root(), carrier, MAP_GETTER);

        assertExtractedSpan(
                extracted, "00000000000000000000000000000001", "0000000000000002", true);
    }

    @Test
    @SuppressWarnings("deprecation")
    void otTraceTruncatesTraceIdOnInjectionAndPadsItOnExtraction() {
        TextMapPropagator propagator = OtTracePropagator.getInstance();
        Map<String, String> carrier = new LinkedHashMap<>();
        Context context =
                Baggage.builder()
                        .put("request", "checkout")
                        .build()
                        .storeInContext(
                                contextWithSpan(
                                        OTHER_TRACE_ID, OTHER_SPAN_ID, TraceFlags.getDefault()));

        propagator.inject(context, carrier, MAP_SETTER);

        assertThat(propagator.fields())
                .containsExactly("ot-tracer-traceid", "ot-tracer-spanid", "ot-tracer-sampled");
        assertThat(carrier)
                .containsEntry("ot-tracer-traceid", "aaaaaaaaaaaaaaaa")
                .containsEntry("ot-tracer-spanid", OTHER_SPAN_ID)
                .containsEntry("ot-tracer-sampled", "false")
                .containsEntry("ot-baggage-request", "checkout");

        Context extracted = propagator.extract(Context.root(), carrier, MAP_GETTER);

        assertExtractedSpan(extracted, OTHER_TRACE_ID, OTHER_SPAN_ID, false);
        assertThat(Baggage.fromContext(extracted).getEntryValue("request")).isEqualTo("checkout");
    }

    @Test
    @SuppressWarnings("deprecation")
    void invalidIncomingHeadersDoNotReplaceTheCurrentSpan() {
        Context current = sampledContext();
        Map<String, String> carrier = new LinkedHashMap<>();
        carrier.put("b3", "not-a-valid-b3-header");
        carrier.put("uber-trace-id", "not-a-valid-jaeger-header");
        carrier.put("ot-tracer-traceid", "not-a-trace-id");
        carrier.put("ot-tracer-spanid", SPAN_ID);
        carrier.put("ot-tracer-sampled", "true");

        Context afterB3 =
                B3Propagator.injectingSingleHeader().extract(current, carrier, MAP_GETTER);
        Context afterJaeger =
                JaegerPropagator.getInstance().extract(current, carrier, MAP_GETTER);
        Context afterOtTrace =
                OtTracePropagator.getInstance().extract(current, carrier, MAP_GETTER);

        assertExtractedSpan(afterB3, TRACE_ID, SPAN_ID, true);
        assertExtractedSpan(afterJaeger, TRACE_ID, SPAN_ID, true);
        assertExtractedSpan(afterOtTrace, TRACE_ID, SPAN_ID, true);
    }

    @Test
    void b3SingleHeaderExtractsDebugContextForDebugReinjection() {
        Map<String, String> carrier = new LinkedHashMap<>();
        carrier.put("b3", OTHER_TRACE_ID + "-" + OTHER_SPAN_ID + "-d");

        Context extracted =
                B3Propagator.injectingSingleHeader().extract(Context.root(), carrier, MAP_GETTER);
        Map<String, String> reinjected = new LinkedHashMap<>();

        B3Propagator.injectingSingleHeader().inject(extracted, reinjected, MAP_SETTER);

        assertExtractedSpan(extracted, OTHER_TRACE_ID, OTHER_SPAN_ID, true);
        assertThat(reinjected).containsEntry("b3", OTHER_TRACE_ID + "-" + OTHER_SPAN_ID + "-d");
    }

    private static Context sampledContext() {
        return contextWithSpan(TRACE_ID, SPAN_ID, TraceFlags.getSampled());
    }

    private static Context contextWithSpan(String traceId, String spanId, TraceFlags traceFlags) {
        SpanContext spanContext =
                SpanContext.create(traceId, spanId, traceFlags, TraceState.getDefault());
        return Context.root().with(Span.wrap(spanContext));
    }

    private static void assertExtractedSpan(
            Context context,
            String expectedTraceId,
            String expectedSpanId,
            boolean expectedSampled) {
        SpanContext spanContext = Span.fromContext(context).getSpanContext();
        assertThat(spanContext.isValid()).isTrue();
        assertThat(spanContext.getTraceId()).isEqualTo(expectedTraceId);
        assertThat(spanContext.getSpanId()).isEqualTo(expectedSpanId);
        assertThat(spanContext.isSampled()).isEqualTo(expectedSampled);
    }

    private static final class MapGetter implements TextMapGetter<Map<String, String>> {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            if (carrier == null) {
                return Collections.emptyList();
            }
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            if (carrier == null || key == null) {
                return null;
            }
            String value = carrier.get(key);
            if (value != null) {
                return value;
            }
            for (Map.Entry<String, String> entry : carrier.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(key)) {
                    return entry.getValue();
                }
            }
            return null;
        }
    }
}
