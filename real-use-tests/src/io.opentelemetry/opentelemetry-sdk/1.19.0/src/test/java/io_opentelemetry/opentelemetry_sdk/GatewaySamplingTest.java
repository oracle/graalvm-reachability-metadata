/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_sdk;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanLimits;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises trace sampling and span limits: a custom route `Sampler` wrapped in a parent-based
 * sampler, `SpanLimits` that cap attributes and events, and a custom `SpanProcessor` composed with
 * a `BatchSpanProcessor` that only exports sampled spans when the provider is flushed.
 *
 * An API gateway traces every request it routes. Health checks are dropped, debug requests are
 * recorded for the gateway's own audit processor but never exported, and ordinary requests are
 * sampled and tagged with the rule that sampled them. Requests that arrive with a remote parent
 * follow the caller's sampling flag. The audit processor copies the tenant from the request
 * baggage onto each span as it starts, and the span limits keep a noisy request within budget.
 */
class GatewaySamplingTest {

    private static final AttributeKey<String> TARGET = AttributeKey.stringKey("http.target");

    private static final AttributeKey<String> RULE = AttributeKey.stringKey("sampler.rule");

    private static final AttributeKey<String> TENANT = AttributeKey.stringKey("tenant");

    private static final AttributeKey<String> USER_AGENT = AttributeKey.stringKey("http.user_agent");

    private static final AttributeKey<Long> STATUS = AttributeKey.longKey("http.status_code");

    private static final AttributeKey<String> REGION = AttributeKey.stringKey("gateway.region");

    private static final AttributeKey<String> UPSTREAM = AttributeKey.stringKey("gateway.upstream");

    private static final String REMOTE_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    private static final String REMOTE_SPAN_ID = "00f067aa0ba902b7";

    private InMemorySpanExporter exporter;

    private AuditProcessor audit;

    private SdkTracerProvider tracerProvider;

    @BeforeEach
    void setUp() {
        exporter = InMemorySpanExporter.create();
        audit = new AuditProcessor();
        BatchSpanProcessor batch = BatchSpanProcessor.builder(exporter)
                .setScheduleDelay(1, TimeUnit.HOURS)
                .setMaxExportBatchSize(512)
                .build();
        tracerProvider = SdkTracerProvider.builder()
                .setSampler(Sampler.parentBasedBuilder(new RouteSampler())
                        .setRemoteParentSampled(Sampler.alwaysOn())
                        .setRemoteParentNotSampled(Sampler.alwaysOff())
                        .build())
                .setSpanLimits(SpanLimits.builder()
                        .setMaxNumberOfAttributes(5)
                        .setMaxAttributeValueLength(12)
                        .setMaxNumberOfEvents(2)
                        .build())
                .addSpanProcessor(SpanProcessor.composite(audit, batch))
                .build();
    }

    @AfterEach
    void tearDown() {
        CompletableResultCode shutdown = tracerProvider.shutdown().join(10, TimeUnit.SECONDS);
        assertThat(shutdown.isSuccess()).isTrue();
    }

    @Test
    void samplingDecisionsFollowRoutesAndRemoteParents() {
        Gateway gateway = new Gateway(tracerProvider.get("api.gateway"));

        RoutedRequest orders = gateway.route(Context.root(), "/orders", "acme");
        RoutedRequest health = gateway.route(Context.root(), "/health", "acme");
        RoutedRequest debug = gateway.route(Context.root(), "/debug/routes", "globex");
        RoutedRequest remoteSampled = gateway.route(
                remoteParent(TraceFlags.getSampled()), "/health", "initech");
        RoutedRequest remoteUnsampled = gateway.route(
                remoteParent(TraceFlags.getDefault()), "/orders", "initech");

        assertThat(orders.recording()).isTrue();
        assertThat(orders.spanContext().isSampled()).isTrue();
        assertThat(health.recording()).isFalse();
        assertThat(health.spanContext().isSampled()).isFalse();
        assertThat(debug.recording()).isTrue();
        assertThat(debug.spanContext().isSampled()).isFalse();
        assertThat(remoteSampled.spanContext().isSampled()).isTrue();
        assertThat(remoteSampled.spanContext().getTraceId()).isEqualTo(REMOTE_TRACE_ID);
        assertThat(remoteUnsampled.recording()).isFalse();
        assertThat(remoteUnsampled.spanContext().getTraceId()).isEqualTo(REMOTE_TRACE_ID);
        assertThat(exporter.getFinishedSpanItems()).isEmpty();

        CompletableResultCode flush = tracerProvider.forceFlush().join(10, TimeUnit.SECONDS);
        assertThat(flush.isSuccess()).isTrue();

        List<SpanData> exported = exporter.getFinishedSpanItems();
        assertThat(exported).extracting(span -> span.getAttributes().get(TARGET))
                .containsExactly("/orders", "/health");
        Map<String, SpanData> byTenant = exported.stream()
                .collect(Collectors.toMap(span -> span.getAttributes().get(TENANT), Function.identity()));

        SpanData sampledOrder = byTenant.get("acme");
        assertThat(sampledOrder.getAttributes().get(RULE)).isEqualTo("default");
        assertThat(sampledOrder.getParentSpanContext().isValid()).isFalse();
        assertThat(sampledOrder.getKind()).isEqualTo(SpanKind.SERVER);

        SpanData continued = byTenant.get("initech");
        assertThat(continued.getTraceId()).isEqualTo(REMOTE_TRACE_ID);
        assertThat(continued.getParentSpanId()).isEqualTo(REMOTE_SPAN_ID);
        assertThat(continued.getParentSpanContext().isRemote()).isTrue();
        assertThat(continued.getAttributes().get(RULE)).isNull();

        assertThat(audit.ended()).containsExactly(
                "GET /orders sampled",
                "GET /debug/routes recorded",
                "GET /health sampled");
        assertThat(audit.started()).containsExactly("acme", "globex", "initech");
    }

    @Test
    void spanLimitsKeepNoisyRequestWithinBudget() {
        Tracer tracer = tracerProvider.get("api.gateway");
        Span span = tracer.spanBuilder("GET /search")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute(TARGET, "/search")
                .startSpan();
        span.setAttribute(USER_AGENT, "Mozilla/5.0 (X11; Linux x86_64)");
        span.setAttribute(STATUS, 200L);
        span.setAttribute(REGION, "eu-west-1");
        span.setAttribute(UPSTREAM, "search-backend-7");
        span.addEvent("cache miss");
        span.addEvent("backend called");
        span.addEvent("response encoded");
        span.end();

        CompletableResultCode flush = tracerProvider.forceFlush().join(10, TimeUnit.SECONDS);
        assertThat(flush.isSuccess()).isTrue();

        assertThat(exporter.getFinishedSpanItems()).hasSize(1);
        SpanData search = exporter.getFinishedSpanItems().get(0);
        assertThat(search.getAttributes().size()).isEqualTo(5);
        assertThat(search.getTotalAttributeCount()).isEqualTo(6);
        assertThat(search.getAttributes().get(TARGET)).isEqualTo("/search");
        assertThat(search.getAttributes().get(RULE)).isEqualTo("default");
        assertThat(search.getAttributes().get(USER_AGENT)).isEqualTo("Mozilla/5.0 ");
        assertThat(search.getAttributes().get(STATUS)).isEqualTo(200L);
        assertThat(search.getAttributes().get(REGION)).isEqualTo("eu-west-1");
        assertThat(search.getAttributes().get(UPSTREAM)).isNull();
        assertThat(search.getEvents()).extracting(EventData::getName)
                .containsExactly("cache miss", "backend called");
        assertThat(search.getTotalRecordedEvents()).isEqualTo(3);
    }

    private static Context remoteParent(TraceFlags flags) {
        SpanContext parent = SpanContext.createFromRemoteParent(
                REMOTE_TRACE_ID, REMOTE_SPAN_ID, flags, TraceState.getDefault());
        return Context.root().with(Span.wrap(parent));
    }

    /** Root sampler that applies the gateway's per-route sampling rules. */
    static final class RouteSampler implements Sampler {

        private final Sampler fallback = Sampler.traceIdRatioBased(1.0);

        @Override
        public SamplingResult shouldSample(Context parentContext, String traceId, String name,
                SpanKind spanKind, Attributes attributes, List<LinkData> parentLinks) {
            String target = attributes.get(TARGET);
            if (target != null && target.startsWith("/health")) {
                return SamplingResult.drop();
            }
            if (target != null && target.startsWith("/debug")) {
                return SamplingResult.recordOnly();
            }
            SamplingResult result = fallback.shouldSample(
                    parentContext, traceId, name, spanKind, attributes, parentLinks);
            return SamplingResult.create(result.getDecision(), Attributes.of(RULE, "default"));
        }

        @Override
        public String getDescription() {
            return "RouteSampler{fallback=" + fallback.getDescription() + "}";
        }
    }

    /** Processor that tags spans with the request tenant and audits every recorded span. */
    static final class AuditProcessor implements SpanProcessor {

        private final List<String> started = Collections.synchronizedList(new ArrayList<>());

        private final List<String> ended = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void onStart(Context parentContext, ReadWriteSpan span) {
            String tenant = Baggage.fromContext(parentContext).getEntryValue("tenant");
            if (tenant != null) {
                span.setAttribute(TENANT, tenant);
                started.add(tenant);
            }
        }

        @Override
        public boolean isStartRequired() {
            return true;
        }

        @Override
        public void onEnd(ReadableSpan span) {
            String outcome = span.getSpanContext().isSampled() ? "sampled" : "recorded";
            ended.add(span.getName() + " " + outcome);
        }

        @Override
        public boolean isEndRequired() {
            return true;
        }

        List<String> started() {
            return List.copyOf(started);
        }

        List<String> ended() {
            return List.copyOf(ended);
        }
    }

    /** What the gateway observed about the span of a request while it was still open. */
    record RoutedRequest(SpanContext spanContext, boolean recording) {
    }

    /** Gateway that opens a server span for every request it routes. */
    static final class Gateway {

        private final Tracer tracer;

        Gateway(Tracer tracer) {
            this.tracer = tracer;
        }

        RoutedRequest route(Context incoming, String target, String tenant) {
            Context context = incoming.with(Baggage.builder().put("tenant", tenant).build());
            try (Scope ignored = context.makeCurrent()) {
                Span span = tracer.spanBuilder("GET " + target)
                        .setSpanKind(SpanKind.SERVER)
                        .setAttribute(TARGET, target)
                        .startSpan();
                RoutedRequest routed = new RoutedRequest(span.getSpanContext(), span.isRecording());
                span.end();
                return routed;
            }
        }
    }
}
