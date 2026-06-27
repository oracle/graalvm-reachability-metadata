/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_tracing;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.Kind;
import io.micrometer.observation.transport.ReceiverContext;
import io.micrometer.observation.transport.SenderContext;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.Link;
import io.micrometer.tracing.ScopedSpan;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.SpanAndScope;
import io.micrometer.tracing.SpanCustomizer;
import io.micrometer.tracing.ThreadLocalSpan;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.contextpropagation.BaggageToPropagate;
import io.micrometer.tracing.contextpropagation.ObservationAwareBaggageThreadLocalAccessor;
import io.micrometer.tracing.contextpropagation.ObservationAwareSpanThreadLocalAccessor;
import io.micrometer.tracing.exporter.FinishedSpan;
import io.micrometer.tracing.exporter.SpanIgnoringSpanExportingPredicate;
import io.micrometer.tracing.exporter.TestSpanReporter;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import io.micrometer.tracing.handler.TracingObservationHandler.TracingContext;
import io.micrometer.tracing.propagation.Propagator;
import io.micrometer.tracing.test.simple.SimpleSpan;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class Micrometer_tracingTest {

    @Test
    void spanBuilderCreatesRichChildSpanWithTagsEventsErrorAndLinks() {
        SimpleTracer tracer = new SimpleTracer();
        TraceContext parentContext = tracer.traceContextBuilder()
            .traceId("463ac35c9f6413ad48485a3953bb6124")
            .spanId("a2fb4a1d1a96d312")
            .sampled(true)
            .build();
        Map<String, Object> linkTags = Map.of("link.type", "batch");
        Link link = new Link(parentContext, linkTags);
        IllegalStateException error = new IllegalStateException("boom");

        Span span = tracer.spanBuilder()
            .setParent(parentContext)
            .name("outbound call")
            .kind(Span.Kind.CLIENT)
            .tag("component", "client")
            .tag("attempt", 2L)
            .tag("sample.rate", 0.5D)
            .tag("cached", false)
            .tagOfStrings("zones", List.of("eu", "us"))
            .event("queued")
            .error(error)
            .remoteServiceName("inventory")
            .remoteIpAndPort("192.0.2.10", 8443)
            .startTimestamp(123, TimeUnit.MILLISECONDS)
            .addLink(link)
            .start();
        span.event("sent", 456, TimeUnit.MILLISECONDS).end(789, TimeUnit.MILLISECONDS);

        SimpleSpan simpleSpan = tracer.onlySpan();
        assertThat(simpleSpan).isSameAs(span);
        assertThat(simpleSpan.getName()).isEqualTo("outbound call");
        assertThat(simpleSpan.getKind()).isEqualTo(Span.Kind.CLIENT);
        assertThat(simpleSpan.getTraceId()).isEqualTo(parentContext.traceId());
        assertThat(simpleSpan.getParentId()).isEqualTo(parentContext.spanId());
        assertThat(simpleSpan.context().sampled()).isTrue();
        assertThat(simpleSpan.getTags()).containsEntry("component", "client")
            .containsEntry("attempt", "2")
            .containsEntry("sample.rate", "0.5")
            .containsEntry("cached", "false")
            .containsEntry("zones", "eu,us");
        assertThat(eventNames(simpleSpan)).contains("queued", "sent");
        assertThat(simpleSpan.getError()).isSameAs(error);
        assertThat(simpleSpan.getRemoteServiceName()).isEqualTo("inventory");
        assertThat(simpleSpan.getRemoteIp()).isEqualTo("192.0.2.10");
        assertThat(simpleSpan.getRemotePort()).isEqualTo(8443);
        assertThat(simpleSpan.getStartTimestamp().toEpochMilli()).isEqualTo(123L);
        assertThat(simpleSpan.getEndTimestamp().toEpochMilli()).isPositive();
        assertThat(simpleSpan.getLinks()).containsExactly(new Link(parentContext, linkTags));
    }

    @Test
    void spanScopeAndBaggageAreBoundToCurrentTraceContextAndCopiedToChildren() {
        SimpleTracer tracer = new SimpleTracer();
        Span parent = tracer.nextSpan().name("parent").start();

        assertThat(tracer.currentSpan()).isNull();
        try (Tracer.SpanInScope parentScope = tracer.withSpan(parent);
                BaggageInScope tenant = tracer.createBaggageInScope("tenant", "acme")) {
            assertThat(tracer.currentSpan()).isSameAs(parent);
            assertThat(tracer.currentTraceContext().context()).isSameAs(parent.context());
            assertThat(tenant.name()).isEqualTo("tenant");
            assertThat(tenant.get()).isEqualTo("acme");
            assertThat(tracer.getAllBaggage()).containsEntry("tenant", "acme");

            Span child = tracer.nextSpan().name("child").start();
            child.end();

            assertThat(child.context().traceId()).isEqualTo(parent.context().traceId());
            assertThat(child.context().parentId()).isEqualTo(parent.context().spanId());
            assertThat(tracer.getAllBaggage(child.context())).containsEntry("tenant", "acme");
        }
        parent.end();

        assertThat(tracer.currentSpan()).isNull();
        assertThat(tracer.currentTraceContext().context()).isNull();
        assertThat(tracer.getAllBaggage()).doesNotContainKey("tenant");
        assertThat(tracer.getSpans()).hasSize(2);
    }

    @Test
    void defaultObservationHandlerCreatesScopedSpanAndRecordsObservationData() {
        SimpleTracer tracer = new SimpleTracer();
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new DefaultTracingObservationHandler(tracer));
        RuntimeException failure = new RuntimeException("database unavailable");

        Observation observation = Observation.createNotStarted("database.query", registry)
            .contextualName("select user")
            .lowCardinalityKeyValue("db.system", "h2")
            .highCardinalityKeyValue("db.statement", "select 1")
            .start();
        try (Observation.Scope scope = observation.openScope()) {
            assertThat(scope.getCurrentObservation()).isSameAs(observation);
            assertThat(tracer.currentSpan()).isNotNull();
            observation.event(Observation.Event.of("row.fetched", "row fetched"));
            observation.error(failure);
        }
        observation.stop();

        SimpleSpan span = tracer.onlySpan();
        assertThat(span.getName()).isEqualTo("select user");
        assertThat(span.getTags()).containsEntry("db.system", "h2")
            .containsEntry("db.statement", "select 1");
        assertThat(eventNames(span)).contains("row fetched");
        assertThat(span.getError()).isSameAs(failure);
        assertThat(span.getEndTimestamp().toEpochMilli()).isPositive();
        assertThat(tracer.currentSpan()).isNull();
    }

    @Test
    void propagatingHandlersInjectAndExtractTraceContextThroughCarrier() {
        SimpleTracer senderTracer = new SimpleTracer();
        HeaderPropagator senderPropagator = new HeaderPropagator(senderTracer);
        Map<String, String> carrier = new LinkedHashMap<>();
        Span clientParent = senderTracer.nextSpan().name("client parent").start();
        TracingContext tracingContext = new TracingContext();
        tracingContext.setSpan(clientParent);
        SenderContext<Map<String, String>> senderContext = new SenderContext<>(Map::put, Kind.CLIENT);
        senderContext.setCarrier(carrier);
        senderContext.put(TracingContext.class, tracingContext);
        senderContext.setName("http client");
        senderContext.setRemoteServiceName("orders");
        senderContext.setRemoteServiceAddress("http://orders.example.test:8080");
        senderContext.addLowCardinalityKeyValue(KeyValue.of("method", "GET"));
        PropagatingSenderTracingObservationHandler<SenderContext<Map<String, String>>> senderHandler =
                new PropagatingSenderTracingObservationHandler<>(senderTracer, senderPropagator);

        senderHandler.onStart(senderContext);
        senderHandler.onStop(senderContext);

        SimpleSpan senderSpan = senderTracer.lastSpan();
        assertThat(senderSpan.getTraceId()).isEqualTo(clientParent.context().traceId());
        assertThat(senderSpan.getParentId()).isEqualTo(clientParent.context().spanId());
        assertThat(senderSpan.getSpanId()).isNotBlank();
        assertThat(carrier).containsEntry(HeaderPropagator.TRACE_ID, senderSpan.getTraceId())
            .containsEntry(HeaderPropagator.SPAN_ID, senderSpan.getSpanId())
            .containsEntry(HeaderPropagator.SAMPLED, String.valueOf(senderSpan.context().sampled()));
        assertThat(senderSpan.getName()).isEqualTo("http client");
        assertThat(senderSpan.getKind()).isEqualTo(Span.Kind.CLIENT);
        assertThat(senderSpan.getRemoteServiceName()).isEqualTo("orders");
        assertThat(senderSpan.getRemoteIp()).isEqualTo("orders.example.test");
        assertThat(senderSpan.getRemotePort()).isEqualTo(8080);
        assertThat(senderSpan.getTags()).containsEntry("method", "GET");
        clientParent.end();

        SimpleTracer receiverTracer = new SimpleTracer();
        ReceiverContext<Map<String, String>> receiverContext = new ReceiverContext<>(Map::get, Kind.SERVER);
        receiverContext.setCarrier(carrier);
        receiverContext.setName("http server");
        receiverContext.setRemoteServiceName("gateway");
        receiverContext.setRemoteServiceAddress("http://gateway.example.test:9090");
        receiverContext.addHighCardinalityKeyValue(KeyValue.of("route", "/orders/{id}"));
        HeaderPropagator receiverPropagator = new HeaderPropagator(receiverTracer);
        PropagatingReceiverTracingObservationHandler<ReceiverContext<Map<String, String>>> receiverHandler =
                new PropagatingReceiverTracingObservationHandler<>(receiverTracer, receiverPropagator);

        receiverHandler.onStart(receiverContext);
        receiverHandler.onStop(receiverContext);

        SimpleSpan receiverSpan = receiverTracer.onlySpan();
        assertThat(receiverSpan.getName()).isEqualTo("http server");
        assertThat(receiverSpan.getKind()).isEqualTo(Span.Kind.SERVER);
        assertThat(receiverSpan.getTraceId()).isEqualTo(senderSpan.getTraceId());
        assertThat(receiverSpan.getParentId()).isEqualTo(senderSpan.getSpanId());
        assertThat(receiverSpan.getRemoteServiceName()).isEqualTo("gateway");
        assertThat(receiverSpan.getRemoteIp()).isEqualTo("gateway.example.test");
        assertThat(receiverSpan.getRemotePort()).isEqualTo(9090);
        assertThat(receiverSpan.getTags()).containsEntry("route", "/orders/{id}");
    }

    @Test
    void contextPropagationAccessorsCaptureRestoreSpanAndBaggage() {
        SimpleTracer tracer = new SimpleTracer();
        ObservationRegistry registry = ObservationRegistry.create();
        ObservationAwareSpanThreadLocalAccessor spanAccessor =
                new ObservationAwareSpanThreadLocalAccessor(registry, tracer);
        ObservationAwareBaggageThreadLocalAccessor baggageAccessor =
                new ObservationAwareBaggageThreadLocalAccessor(registry, tracer);
        Span original = tracer.nextSpan().name("original").start();
        Span propagated = tracer.nextSpan().name("propagated").start();

        assertThat(spanAccessor.key()).isEqualTo(ObservationAwareSpanThreadLocalAccessor.KEY);
        assertThat(baggageAccessor.key()).isEqualTo(ObservationAwareBaggageThreadLocalAccessor.KEY);
        assertThat(spanAccessor.getValue()).isNull();
        assertThat(baggageAccessor.getValue()).isNull();

        try (Tracer.SpanInScope originalScope = tracer.withSpan(original);
                BaggageInScope tenant = tracer.createBaggageInScope(original.context(), "tenant", "acme")) {
            assertThat(spanAccessor.getValue()).isSameAs(original);
            assertThat(baggageAccessor.getValue()).isEqualTo(new BaggageToPropagate("tenant", "acme"));

            spanAccessor.setValue(propagated);
            assertThat(tracer.currentSpan()).isSameAs(propagated);
            baggageAccessor.setValue(new BaggageToPropagate(Map.of("request-id", "r-123")));

            assertThat(baggageAccessor.getValue()).isEqualTo(new BaggageToPropagate("request-id", "r-123"));
            assertThat(tracer.getAllBaggage(propagated.context())).containsEntry("request-id", "r-123");

            baggageAccessor.restore();
            assertThat(tracer.getAllBaggage(propagated.context())).doesNotContainKey("request-id");
            spanAccessor.restore(original);
            assertThat(tracer.currentSpan()).isSameAs(original);
        }
        propagated.end();
        original.end();
    }

    @Test
    void scopedSpanFacadeStartsFinishesAndRecordsSpanData() {
        SimpleTracer tracer = new SimpleTracer();
        IllegalArgumentException failure = new IllegalArgumentException("invalid batch");

        ScopedSpan scopedSpan = tracer.startScopedSpan("batch processing");
        assertThat(scopedSpan.isNoop()).isFalse();
        scopedSpan.name("process orders")
            .tag("batch.id", "b-123")
            .event("batch claimed")
            .error(failure)
            .end();

        SimpleSpan span = tracer.onlySpan();
        assertThat(span.getName()).isEqualTo("process orders");
        assertThat(span.getTags()).containsEntry("batch.id", "b-123");
        assertThat(eventNames(span)).contains("batch claimed");
        assertThat(span.getError()).isSameAs(failure);
        assertThat(span.getEndTimestamp().toEpochMilli()).isPositive();
    }

    @Test
    void currentSpanCustomizerMutatesTheSpanBoundToTheCurrentScope() {
        SimpleTracer tracer = new SimpleTracer();
        Span span = tracer.nextSpan().name("original").start();

        try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
            SpanCustomizer customizer = tracer.currentSpanCustomizer();
            assertThat(customizer.name("server request")).isSameAs(customizer);
            customizer.tag("http.method", "GET")
                .tag("http.status_code", 200L)
                .tag("cache.hit", true)
                .tag("sample.ratio", 0.75D)
                .event("response committed");
        }
        span.end();

        SimpleSpan simpleSpan = tracer.onlySpan();
        assertThat(simpleSpan).isSameAs(span);
        assertThat(simpleSpan.getName()).isEqualTo("server request");
        assertThat(simpleSpan.getTags()).containsEntry("http.method", "GET")
            .containsEntry("http.status_code", "200")
            .containsEntry("cache.hit", "true")
            .containsEntry("sample.ratio", "0.75");
        assertThat(eventNames(simpleSpan)).contains("response committed");
        assertThat(tracer.currentSpan()).isNull();
    }

    @Test
    void threadLocalSpanStacksNestedSpansAndRestoresPreviousScope() {
        SimpleTracer tracer = new SimpleTracer();
        ThreadLocalSpan threadLocalSpan = new ThreadLocalSpan(tracer);
        Span first = tracer.nextSpan().name("first").start();
        Span second = tracer.nextSpan().name("second").start();

        assertThat(threadLocalSpan.get()).isNull();
        threadLocalSpan.set(first);
        assertThat(threadLocalSpan.get().getSpan()).isSameAs(first);
        assertThat(tracer.currentSpan()).isSameAs(first);

        threadLocalSpan.set(second);
        assertThat(threadLocalSpan.get().getSpan()).isSameAs(second);
        assertThat(tracer.currentSpan()).isSameAs(second);

        SpanAndScope removedSecond = threadLocalSpan.remove();
        assertThat(removedSecond.getSpan()).isSameAs(second);
        assertThat(tracer.currentSpan()).isSameAs(first);

        SpanAndScope removedFirst = threadLocalSpan.remove();
        assertThat(removedFirst.getSpan()).isSameAs(first);
        assertThat(tracer.currentSpan()).isNull();
        assertThat(threadLocalSpan.remove()).isNull();

        second.end();
        first.end();
    }

    @Test
    void spanReporterPredicateAndNoopImplementationsAreUsableThroughFacadeApis() throws Exception {
        SimpleTracer tracer = new SimpleTracer();
        SimpleSpan healthSpan = tracer.nextSpan().name("health-check").start();
        healthSpan.end();
        SimpleSpan businessSpan = tracer.nextSpan().name("checkout").start();
        businessSpan.end();

        SpanIgnoringSpanExportingPredicate predicate = new SpanIgnoringSpanExportingPredicate(
                List.of("health.*"), List.of("internal.*"));
        assertThat(predicate.isExportable(healthSpan)).isFalse();
        assertThat(predicate.isExportable(businessSpan)).isTrue();

        TestSpanReporter reporter = new TestSpanReporter();
        reporter.report(businessSpan);
        assertThat(reporter.spans()).containsExactly(businessSpan);
        FinishedSpan reported = reporter.poll();
        assertThat(reported).isSameAs(businessSpan);
        reporter.close();
        assertThat(reporter.spans()).isEmpty();

        Span noopSpan = Tracer.NOOP.nextSpan()
            .name("ignored")
            .tag("key", "value")
            .event("ignored")
            .remoteServiceName("none")
            .remoteIpAndPort("127.0.0.1", 1)
            .start();
        noopSpan.error(new RuntimeException("ignored")).end();
        try (Tracer.SpanInScope ignored = Tracer.NOOP.withSpan(noopSpan);
                BaggageInScope baggage = Tracer.NOOP.createBaggageInScope("noop", "value");
                CurrentTraceContext.Scope scope = CurrentTraceContext.NOOP.maybeScope(TraceContext.NOOP)) {
            assertThat(noopSpan.isNoop()).isTrue();
            assertThat(baggage.get()).isNull();
            assertThat(Tracer.NOOP.currentSpan()).isSameAs(Span.NOOP);
            assertThat(scope).isNotNull();
        }
    }

    private static Collection<String> eventNames(SimpleSpan span) {
        return span.getEvents().stream().map(Map.Entry::getValue).toList();
    }

    private static final class HeaderPropagator implements Propagator {

        private static final String TRACE_ID = "x-trace-id";

        private static final String SPAN_ID = "x-span-id";

        private static final String SAMPLED = "x-sampled";

        private final Tracer tracer;

        private HeaderPropagator(Tracer tracer) {
            this.tracer = tracer;
        }

        @Override
        public List<String> fields() {
            return List.of(TRACE_ID, SPAN_ID, SAMPLED);
        }

        @Override
        public <C> void inject(TraceContext context, C carrier, Propagator.Setter<C> setter) {
            setter.set(carrier, TRACE_ID, context.traceId());
            setter.set(carrier, SPAN_ID, context.spanId());
            setter.set(carrier, SAMPLED, String.valueOf(context.sampled()));
        }

        @Override
        public <C> Span.Builder extract(C carrier, Propagator.Getter<C> getter) {
            String traceId = getter.get(carrier, TRACE_ID);
            String spanId = getter.get(carrier, SPAN_ID);
            String sampled = getter.get(carrier, SAMPLED);
            if (traceId == null || spanId == null) {
                return this.tracer.spanBuilder().setNoParent();
            }
            TraceContext parentContext = this.tracer.traceContextBuilder()
                .traceId(traceId)
                .spanId(spanId)
                .sampled(Boolean.valueOf(sampled))
                .build();
            return this.tracer.spanBuilder().setParent(parentContext);
        }

    }

}
