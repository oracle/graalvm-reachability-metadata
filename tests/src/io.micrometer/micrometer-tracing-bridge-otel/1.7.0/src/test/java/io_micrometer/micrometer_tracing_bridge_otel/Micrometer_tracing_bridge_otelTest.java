/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_tracing_bridge_otel;

import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.Link;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.exporter.FinishedSpan;
import io.micrometer.tracing.exporter.SpanReporter;
import io.micrometer.tracing.otel.bridge.BaggageTaggingSpanProcessor;
import io.micrometer.tracing.otel.bridge.CompositeSpanExporter;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.micrometer.tracing.otel.propagation.BaggageTextMapPropagator;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class Micrometer_tracing_bridge_otelTest {

    @Test
    void tracerBuildsRichOpenTelemetrySpansThroughMicrometerApi() {
        RecordingSpanExporter exporter = new RecordingSpanExporter();
        TracingFixture fixture = TracingFixture.create(exporter, List.of(), List.of());
        try {
            Span parent = fixture.tracer.nextSpan().name("parent-span");
            Map<String, Object> linkTags = new LinkedHashMap<>();
            linkTags.put("link.kind", "parent");
            linkTags.put("sample.count", 3L);
            linkTags.put("sample.enabled", true);
            RuntimeException failure = new RuntimeException("failure-message");

            Span child = fixture.tracer.spanBuilder()
                    .setParent(parent.context())
                    .name("client-span")
                    .kind(Span.Kind.CLIENT)
                    .startTimestamp(1_234L, TimeUnit.MILLISECONDS)
                    .tag("http.method", "GET")
                    .tag("retry.count", 2L)
                    .tag("load.factor", 0.75D)
                    .tag("cache.hit", true)
                    .tagOfStrings("zones", List.of("a", "b"))
                    .tagOfLongs("attempts", List.of(1L, 2L))
                    .tagOfDoubles("ratios", List.of(0.25D, 0.5D))
                    .tagOfBooleans("decisions", List.of(true, false))
                    .remoteServiceName("inventory")
                    .remoteIpAndPort("10.0.0.8", 8080)
                    .event("builder-event")
                    .addLink(new Link(parent.context(), linkTags))
                    .error(failure)
                    .start();

            child.event("runtime-event").tag("late.tag", "yes").end();
            parent.end();

            SpanData childData = onlySpanNamed(exporter.exported, "client-span");
            assertThat(childData.getKind()).isEqualTo(SpanKind.CLIENT);
            assertThat(childData.getTraceId()).isEqualTo(parent.context().traceId());
            assertThat(childData.getParentSpanId()).isEqualTo(parent.context().spanId());
            assertThat(childData.getStartEpochNanos()).isEqualTo(TimeUnit.MILLISECONDS.toNanos(1_234L));
            assertThat(childData.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
            assertThat(childData.getStatus().getDescription()).isEqualTo("failure-message");

            Attributes attributes = childData.getAttributes();
            assertThat(attributes.get(AttributeKey.stringKey("http.method"))).isEqualTo("GET");
            assertThat(attributes.get(AttributeKey.longKey("retry.count"))).isEqualTo(2L);
            assertThat(attributes.get(AttributeKey.doubleKey("load.factor"))).isEqualTo(0.75D);
            assertThat(attributes.get(AttributeKey.booleanKey("cache.hit"))).isTrue();
            assertThat(attributes.get(AttributeKey.stringArrayKey("zones"))).containsExactly("a", "b");
            assertThat(attributes.get(AttributeKey.longArrayKey("attempts"))).containsExactly(1L, 2L);
            assertThat(attributes.get(AttributeKey.doubleArrayKey("ratios"))).containsExactly(0.25D, 0.5D);
            assertThat(attributes.get(AttributeKey.booleanArrayKey("decisions"))).containsExactly(true, false);
            assertThat(attributes.get(AttributeKey.stringKey("peer.service"))).isEqualTo("inventory");
            assertThat(attributes.get(AttributeKey.stringKey("network.peer.address"))).isEqualTo("10.0.0.8");
            assertThat(attributes.get(AttributeKey.longKey("network.peer.port"))).isEqualTo(8080L);
            assertThat(attributes.get(AttributeKey.stringKey("late.tag"))).isEqualTo("yes");

            assertThat(eventNames(childData)).contains("builder-event", "runtime-event", "exception");
            LinkData exportedLink = childData.getLinks().get(0);
            assertThat(exportedLink.getSpanContext().getSpanId()).isEqualTo(parent.context().spanId());
            assertThat(exportedLink.getAttributes().get(AttributeKey.stringKey("link.kind"))).isEqualTo("parent");
            assertThat(exportedLink.getAttributes().get(AttributeKey.longKey("sample.count"))).isEqualTo(3L);
            assertThat(exportedLink.getAttributes().get(AttributeKey.booleanKey("sample.enabled"))).isTrue();
        }
        finally {
            fixture.close();
        }
    }

    @Test
    void currentTraceContextScopesAndWrapsWorkWithMicrometerTracer() throws Exception {
        RecordingSpanExporter exporter = new RecordingSpanExporter();
        TracingFixture fixture = TracingFixture.create(exporter, List.of(), List.of());
        try {
            Span scoped = fixture.tracer.nextSpan().name("scoped");
            Callable<String> wrapped;
            try (Tracer.SpanInScope ignored = fixture.tracer.withSpan(scoped)) {
                assertThat(fixture.tracer.currentSpan().context().spanId()).isEqualTo(scoped.context().spanId());
                wrapped = fixture.currentTraceContext.wrap(() -> fixture.tracer.currentSpan().context().spanId());
            }

            assertThat(fixture.tracer.currentSpan()).isNull();
            assertThat(wrapped.call()).isEqualTo(scoped.context().spanId());

            try (Tracer.SpanInScope ignored = fixture.tracer.withSpan(scoped)) {
                try (CurrentTraceContext.Scope clearingScope = fixture.currentTraceContext.maybeScope(null)) {
                    assertThat(fixture.tracer.currentSpan()).isNull();
                }
                assertThat(fixture.tracer.currentSpan().context().spanId()).isEqualTo(scoped.context().spanId());
            }
            scoped.end();
            assertThat(onlySpanNamed(exporter.exported, "scoped").getSpanId()).isEqualTo(scoped.context().spanId());
        }
        finally {
            fixture.close();
        }
    }

    @Test
    void propagatorInjectsAndExtractsTraceContextAndConfiguredBaggage() {
        RecordingSpanExporter exporter = new RecordingSpanExporter();
        TracingFixture fixture = TracingFixture.create(exporter, List.of("request-id", "tenant-id"),
                List.of("tenant-id"));
        try {
            BaggageTextMapPropagator baggagePropagator = new BaggageTextMapPropagator(
                    List.of("request-id", "tenant-id"), fixture.baggageManager);
            OtelPropagator propagator = new OtelPropagator(ContextPropagators.create(TextMapPropagator.composite(
                    W3CTraceContextPropagator.getInstance(), baggagePropagator)), fixture.otelTracer);
            Span producer = fixture.tracer.nextSpan().name("producer");
            Map<String, String> carrier = new LinkedHashMap<>();

            try (Tracer.SpanInScope ignored = fixture.tracer.withSpan(producer);
                    BaggageInScope requestId = fixture.tracer.createBaggageInScope("request-id", "req-123");
                    BaggageInScope tenantId = fixture.tracer.createBaggageInScope("tenant-id", "tenant-a");
                    BaggageInScope localOnly = fixture.tracer.createBaggageInScope("local-only", "secret")) {
                assertThat(fixture.tracer.getAllBaggage()).containsEntry("request-id", "req-123")
                        .containsEntry("tenant-id", "tenant-a").containsEntry("local-only", "secret");
                propagator.inject(producer.context(), carrier, (map, key, value) -> map.put(key, value));
            }

            assertThat(propagator.fields()).contains("traceparent", "request-id", "tenant-id");
            assertThat(carrier).containsEntry("request-id", "req-123").containsEntry("tenant-id", "tenant-a")
                    .containsKey("traceparent").doesNotContainKey("local-only");

            Span consumer = propagator.extract(carrier, Map::get).name("consumer").start();
            try (Tracer.SpanInScope ignored = fixture.tracer.withSpan(consumer)) {
                assertThat(fixture.tracer.currentSpan().context().traceId()).isEqualTo(producer.context().traceId());
                assertThat(fixture.tracer.getBaggage("request-id").get()).isEqualTo("req-123");
                assertThat(fixture.tracer.getBaggage("tenant-id").get()).isEqualTo("tenant-a");
            }

            consumer.end();
            producer.end();
            SpanData consumerData = onlySpanNamed(exporter.exported, "consumer");
            assertThat(consumerData.getTraceId()).isEqualTo(producer.context().traceId());
            assertThat(consumerData.getParentSpanId()).isEqualTo(producer.context().spanId());
            assertThat(consumerData.getAttributes().get(AttributeKey.stringKey("tenant-id"))).isEqualTo("tenant-a");
        }
        finally {
            fixture.close();
        }
    }

    @Test
    void compositeExporterFiltersMutatesReportsAndDelegatesFinishedSpans() {
        RecordingSpanExporter delegateExporter = new RecordingSpanExporter();
        RecordingSpanReporter reporter = new RecordingSpanReporter();
        CompositeSpanExporter compositeExporter = new CompositeSpanExporter(List.of(delegateExporter),
                List.of(span -> !"drop".equals(span.getName())), List.of(reporter), List.of(span -> span
                        .setName("filtered-" + span.getName())
                        .setTags(Map.of("filtered", "true"))
                        .setRemoteServiceName("payments")
                        .setLocalServiceName("checkout")));
        TracingFixture fixture = TracingFixture.create(compositeExporter, List.of(), List.of());
        try {
            fixture.tracer.nextSpan().name("keep").end();
            fixture.tracer.nextSpan().name("drop").end();
        }
        finally {
            fixture.close();
        }

        assertThat(reporter.closed).isTrue();
        assertThat(delegateExporter.shutdown).isTrue();
        assertThat(delegateExporter.exported).hasSize(1);
        SpanData exported = delegateExporter.exported.get(0);
        assertThat(exported.getName()).isEqualTo("filtered-keep");
        assertThat(exported.getAttributes().get(AttributeKey.stringKey("filtered"))).isEqualTo("true");
        assertThat(exported.getAttributes().get(AttributeKey.stringKey("peer.service"))).isEqualTo("payments");
        assertThat(exported.getResource().getAttribute(AttributeKey.stringKey("service.name"))).isEqualTo("checkout");
        assertThat(reporter.reported).extracting(FinishedSpan::getName).containsExactly("filtered-keep");
        assertThat(reporter.reported.get(0).getRemoteServiceName()).isEqualTo("payments");
        assertThat(reporter.reported.get(0).getLocalServiceName()).isEqualTo("checkout");
    }

    private static SpanData onlySpanNamed(List<SpanData> spans, String name) {
        List<SpanData> matching = spans.stream()
                .filter(span -> name.equals(span.getName()))
                .collect(Collectors.toList());
        assertThat(matching).hasSize(1);
        return matching.get(0);
    }

    private static List<String> eventNames(SpanData spanData) {
        return spanData.getEvents().stream().map(EventData::getName).collect(Collectors.toList());
    }

    private static final class TracingFixture {
        final RecordingSpanExporter ownedExporter;
        final SdkTracerProvider tracerProvider;
        final OtelCurrentTraceContext currentTraceContext;
        final OtelBaggageManager baggageManager;
        final io.opentelemetry.api.trace.Tracer otelTracer;
        final Tracer tracer;

        private TracingFixture(RecordingSpanExporter ownedExporter, SdkTracerProvider tracerProvider,
                OtelCurrentTraceContext currentTraceContext, OtelBaggageManager baggageManager,
                io.opentelemetry.api.trace.Tracer otelTracer, Tracer tracer) {
            this.ownedExporter = ownedExporter;
            this.tracerProvider = tracerProvider;
            this.currentTraceContext = currentTraceContext;
            this.baggageManager = baggageManager;
            this.otelTracer = otelTracer;
            this.tracer = tracer;
        }

        static TracingFixture create(SpanExporter exporter, List<String> remoteFields, List<String> tagFields) {
            OtelCurrentTraceContext currentTraceContext = new OtelCurrentTraceContext();
            OtelBaggageManager baggageManager = new OtelBaggageManager(currentTraceContext, remoteFields, tagFields);
            SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                    .setSampler(Sampler.alwaysOn())
                    .setResource(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), "orders")))
                    .addSpanProcessor(new BaggageTaggingSpanProcessor(tagFields))
                    .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                    .build();
            io.opentelemetry.api.trace.Tracer otelTracer = tracerProvider.get("micrometer-bridge-test");
            OtelTracer tracer = new OtelTracer(otelTracer, currentTraceContext, event -> { }, baggageManager);
            RecordingSpanExporter ownedExporter = exporter instanceof RecordingSpanExporter
                    ? (RecordingSpanExporter) exporter : null;
            return new TracingFixture(ownedExporter, tracerProvider, currentTraceContext, baggageManager, otelTracer,
                    tracer);
        }

        void close() {
            this.tracerProvider.shutdown().join(10, TimeUnit.SECONDS);
            if (this.ownedExporter != null && !this.ownedExporter.shutdown) {
                this.ownedExporter.shutdown().join(10, TimeUnit.SECONDS);
            }
        }
    }

    private static final class RecordingSpanExporter implements SpanExporter {
        final List<SpanData> exported = new ArrayList<>();
        boolean flush;
        boolean shutdown;

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            this.exported.addAll(spans);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            this.flush = true;
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            this.shutdown = true;
            return CompletableResultCode.ofSuccess();
        }
    }

    private static final class RecordingSpanReporter implements SpanReporter {
        final List<FinishedSpan> reported = new ArrayList<>();
        boolean closed;

        @Override
        public void report(FinishedSpan span) {
            this.reported.add(span);
        }

        @Override
        public void close() {
            this.closed = true;
        }
    }

}
