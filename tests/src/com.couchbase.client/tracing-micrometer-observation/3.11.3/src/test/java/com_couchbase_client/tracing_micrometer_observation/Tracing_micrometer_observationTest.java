/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.tracing_micrometer_observation;

import com.couchbase.client.core.cnc.RequestSpan;
import com.couchbase.client.tracing.observation.CouchbaseSenderContext;
import com.couchbase.client.tracing.observation.ObservationRequestSpan;
import com.couchbase.client.tracing.observation.ObservationRequestTracer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import io.micrometer.observation.transport.Kind;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import io.micrometer.tracing.propagation.Propagator;
import io.micrometer.tracing.test.simple.SimpleSpan;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class Tracing_micrometer_observationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Test
    void recordsClientObservationNameAttributesExceptionAndCompletion() {
        SimpleTracer simpleTracer = new SimpleTracer();
        TestObservationRegistry registry = tracingRegistry(simpleTracer);
        ObservationRequestTracer tracer = ObservationRequestTracer.wrap(registry);
        IllegalStateException failure = new IllegalStateException("query failed");

        assertThat(tracer.observationRegistry()).isSameAs(registry);

        ObservationRequestSpan requestSpan =
                (ObservationRequestSpan) tracer.requestSpan("cb.query", null);
        CouchbaseSenderContext context = (CouchbaseSenderContext) requestSpan.observation().getContext();

        assertThat(context.getName()).isEqualTo("db.couchbase.operations");
        assertThat(context.getContextualName()).isEqualTo("cb.query");
        assertThat(context.getOperationName()).isEqualTo("cb.query");
        assertThat(context.getKind()).isEqualTo(Kind.CLIENT);
        assertThat(context.getParentObservation()).isSameAs(Observation.NOOP);
        TestObservationRegistryAssert.assertThat(registry)
                .hasSingleObservationThat()
                .hasBeenStarted()
                .isNotStopped();

        requestSpan.attribute("document.id", "customer-42");
        requestSpan.attribute("retry", true);
        requestSpan.attribute("attempt", 3L);
        requestSpan.attribute("ignored.null", (String) null);
        requestSpan.lowCardinalityAttribute("service", "query");
        requestSpan.lowCardinalityAttribute("readonly", false);
        requestSpan.lowCardinalityAttribute("replicas", 2L);
        requestSpan.event("dispatched", Instant.EPOCH);
        requestSpan.status(RequestSpan.StatusCode.ERROR);
        requestSpan.requestContext(null);
        requestSpan.recordException(failure);
        requestSpan.end();

        TestObservationRegistryAssert.assertThat(registry)
                .hasSingleObservationThat()
                .hasBeenStarted()
                .hasBeenStopped()
                .hasNameEqualTo("db.couchbase.operations")
                .hasContextualNameEqualTo("cb.query")
                .hasHighCardinalityKeyValue("document.id", "customer-42")
                .hasHighCardinalityKeyValue("retry", "true")
                .hasHighCardinalityKeyValue("attempt", "3")
                .doesNotHaveHighCardinalityKeyValueWithKey("ignored.null")
                .hasLowCardinalityKeyValue("service", "query")
                .hasLowCardinalityKeyValue("readonly", "false")
                .hasLowCardinalityKeyValue("replicas", "2")
                .hasError(failure);

        SimpleSpan finishedSpan = simpleTracer.onlySpan();
        assertThat(finishedSpan.getName()).isEqualTo("cb.query");
        assertThat(finishedSpan.getKind()).isEqualTo(Span.Kind.CLIENT);
        assertThat(finishedSpan.getParentId()).isEmpty();
        assertThat(finishedSpan.getTags())
                .containsEntry("document.id", "customer-42")
                .containsEntry("retry", "true")
                .containsEntry("attempt", "3")
                .containsEntry("service", "query")
                .containsEntry("readonly", "false")
                .containsEntry("replicas", "2")
                .doesNotContainKey("ignored.null");
        assertThat(finishedSpan.getError()).isSameAs(failure);
        assertThat(finishedSpan.getEvents()).isEmpty();
        assertThat(finishedSpan.getEndTimestamp()).isAfter(finishedSpan.getStartTimestamp().minusMillis(1));
    }

    @Test
    void explicitParentOverridesAnIntermediateCurrentSpanAndRestoresItsScope() {
        SimpleTracer simpleTracer = new SimpleTracer();
        TestObservationRegistry registry = tracingRegistry(simpleTracer);
        ObservationRequestTracer tracer = ObservationRequestTracer.wrap(registry);

        ObservationRequestSpan parent = (ObservationRequestSpan) tracer.requestSpan("cb.parent", null);
        SimpleSpan intermediate = simpleTracer.nextSpan().name("application.intermediate").start();
        ObservationRequestSpan child;

        try (Tracer.SpanInScope ignored = simpleTracer.withSpan(intermediate)) {
            child = (ObservationRequestSpan) tracer.requestSpan("cb.child", parent);
            assertThat(simpleTracer.currentSpan()).isSameAs(intermediate);
            child.end();
        }
        intermediate.end();
        parent.end();

        List<SimpleSpan> spans = List.copyOf(simpleTracer.getSpans());
        assertThat(spans).hasSize(3);

        SimpleSpan parentSpan = spans.get(0);
        SimpleSpan childSpan = spans.get(2);
        CouchbaseSenderContext parentContext = (CouchbaseSenderContext) parent.observation().getContext();
        CouchbaseSenderContext childContext = (CouchbaseSenderContext) child.observation().getContext();

        assertThat(parentContext.getParentObservation()).isSameAs(Observation.NOOP);
        assertThat(childContext.getParentObservation()).isSameAs(parent.observation());
        assertThat(parentSpan.getName()).isEqualTo("cb.parent");
        assertThat(parentSpan.getParentId()).isEmpty();
        assertThat(childSpan.getName()).isEqualTo("cb.child");
        assertThat(childSpan.getParentId()).isEqualTo(parentSpan.getSpanId());
        assertThat(childSpan.getParentId()).isNotEqualTo(intermediate.getSpanId());
        assertThat(childSpan.getTraceId()).isEqualTo(parentSpan.getTraceId());
        assertThat(parentSpan.getKind()).isEqualTo(Span.Kind.CLIENT);
        assertThat(childSpan.getKind()).isEqualTo(Span.Kind.CLIENT);
        assertThat(simpleTracer.currentSpan()).isNull();
        TestObservationRegistryAssert.assertThat(registry)
                .hasNumberOfObservationsEqualTo(2)
                .hasNumberOfObservationsWithNameEqualTo("db.couchbase.operations", 2)
                .hasObservationWithNameEqualTo("db.couchbase.operations")
                .that()
                .hasBeenStarted()
                .hasBeenStopped();
    }

    @Test
    void observationPredicateCanDisableSelectedCouchbaseOperations() {
        SimpleTracer simpleTracer = new SimpleTracer();
        TestObservationRegistry registry = tracingRegistry(simpleTracer);
        registry.observationConfig().observationPredicate((name, context) ->
                !(context instanceof CouchbaseSenderContext
                        && ((CouchbaseSenderContext) context)
                                .getOperationName()
                                .equals("cb.suppressed")));
        ObservationRequestTracer tracer = ObservationRequestTracer.wrap(registry);

        ObservationRequestSpan requestSpan =
                (ObservationRequestSpan) tracer.requestSpan("cb.suppressed", null);

        assertThat(requestSpan.observation().isNoop()).isTrue();
        requestSpan.end();
        TestObservationRegistryAssert.assertThat(registry).doesNotHaveAnyObservation();
        assertThat(simpleTracer.getSpans()).isEmpty();
    }

    @Test
    void tracerLifecycleCompletesWithoutEndingAnActiveRequestSpan() {
        SimpleTracer simpleTracer = new SimpleTracer();
        TestObservationRegistry registry = tracingRegistry(simpleTracer);
        ObservationRequestTracer tracer = ObservationRequestTracer.wrap(registry);

        assertThat(tracer.start().block(TIMEOUT)).isNull();
        ObservationRequestSpan requestSpan =
                (ObservationRequestSpan) tracer.requestSpan("cb.lifecycle", null);
        SimpleSpan activeSpan = simpleTracer.lastSpan();

        assertThat(tracer.stop(TIMEOUT).block(TIMEOUT)).isNull();
        assertThat(activeSpan.getEndTimestamp()).isEqualTo(Instant.EPOCH);
        TestObservationRegistryAssert.assertThat(registry)
                .hasSingleObservationThat()
                .hasBeenStarted()
                .isNotStopped();

        requestSpan.end();

        assertThat(activeSpan.getEndTimestamp()).isAfter(Instant.EPOCH);
        TestObservationRegistryAssert.assertThat(registry)
                .hasSingleObservationThat()
                .hasBeenStopped();
    }

    private static TestObservationRegistry tracingRegistry(SimpleTracer tracer) {
        TestObservationRegistry registry = TestObservationRegistry.create();
        registry.observationConfig().observationHandler(
                new PropagatingSenderTracingObservationHandler<>(tracer, Propagator.NOOP));
        return registry;
    }
}
