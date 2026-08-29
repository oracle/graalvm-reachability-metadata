/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_observation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.GlobalObservationConvention;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.ObservationTextPublisher;
import io.micrometer.observation.Observations;
import io.micrometer.observation.docs.ObservationDocumentation;
import io.micrometer.observation.transport.Kind;
import io.micrometer.observation.transport.ReceiverContext;
import io.micrometer.observation.transport.RequestReplyReceiverContext;
import io.micrometer.observation.transport.RequestReplySenderContext;
import io.micrometer.observation.transport.SenderContext;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Micrometer_observationTest {
    @Test
    void observationLifecycleNotifiesHandlersMaintainsScopeAndAppliesFilters() {
        ObservationRegistry registry = ObservationRegistry.create();
        RecordingHandler handler = new RecordingHandler("handler", context -> true);
        AtomicReference<Observation.Context> stoppedContext = new AtomicReference<>();
        registry.observationConfig()
                .observationFilter(context -> context.addLowCardinalityKeyValue(KeyValue.of("filtered", "true")))
                .observationHandler(handler)
                .observationHandler(new ObservationHandler<Observation.Context>() {
                    @Override
                    public void onStop(Observation.Context context) {
                        stoppedContext.set(context);
                    }

                    @Override
                    public boolean supportsContext(Observation.Context context) {
                        return true;
                    }
                });

        Observation parent = Observation.start("parent.operation", registry);
        try (Observation.Scope parentScope = parent.openScope()) {
            assertThat(registry.getCurrentObservation()).isSameAs(parent);

            Observation child = Observation.createNotStarted("child.operation", registry)
                    .contextualName("child contextual name")
                    .lowCardinalityKeyValue("phase", "initial")
                    .lowCardinalityKeyValues(KeyValues.of("component", "orders"))
                    .highCardinalityKeyValue("request.id", "abc-123");

            child.observe(() -> {
                assertThat(registry.getCurrentObservation()).isSameAs(child);
                assertThat(child.getContext().getParentObservation()).isSameAs(parent);
                child.event(Observation.Event.of("checkpoint", "checkpoint %s").format("42"));
            });

            assertThat(registry.getCurrentObservation()).isSameAs(parent);
            assertThat(child.getContext().getName()).isEqualTo("child.operation");
            assertThat(child.getContext().getContextualName()).isEqualTo("child contextual name");
            assertThat(child.getContext().getLowCardinalityKeyValue("phase").getValue()).isEqualTo("initial");
            assertThat(child.getContext().getLowCardinalityKeyValue("component").getValue()).isEqualTo("orders");
            assertThat(child.getContext().getHighCardinalityKeyValue("request.id").getValue()).isEqualTo("abc-123");
        }

        assertThat(registry.getCurrentObservation()).isNull();
        parent.stop();
        assertThat(stoppedContext.get().getLowCardinalityKeyValue("filtered").getValue()).isEqualTo("true");
        assertThat(handler.events).containsSubsequence(
                "handler:start:parent.operation",
                "handler:scope-opened:parent.operation",
                "handler:start:child.operation",
                "handler:scope-opened:child.operation",
                "handler:event:checkpoint:checkpoint 42",
                "handler:scope-closed:child.operation",
                "handler:stop:child.operation",
                "handler:scope-closed:parent.operation");
    }

    @Test
    void manualScopesCanBeResetMadeCurrentAndNested() {
        ObservationRegistry registry = ObservationRegistry.create();
        RecordingHandler handler = new RecordingHandler("manual", context -> true);
        registry.observationConfig().observationHandler(handler);

        Observation parent = Observation.start("manual.parent", registry);
        Observation.Scope parentScope = parent.openScope();
        Observation child = Observation.createNotStarted("manual.child", registry).start();
        Observation.Scope childScope = child.openScope();

        assertThat(child.getContext().getParentObservation()).isSameAs(parent);
        assertThat(childScope.getPreviousObservationScope()).isSameAs(parentScope);
        assertThat(child.getEnclosingScope()).isSameAs(childScope);
        assertThat(registry.getCurrentObservation()).isSameAs(child);

        childScope.reset();
        assertThat(registry.getCurrentObservation()).isNull();
        assertThat(handler.events).contains("manual:scope-reset:manual.child");

        childScope.makeCurrent();
        assertThat(registry.getCurrentObservation()).isSameAs(child);
        childScope.close();
        assertThat(registry.getCurrentObservation()).isSameAs(parent);
        parentScope.close();
        assertThat(registry.getCurrentObservation()).isNull();
        child.stop();
        parent.stop();
    }

    @Test
    void observeAndWrapHelpersReturnValuesPropagateCheckedExceptionsAndRecordErrors() throws IOException {
        ObservationRegistry registry = ObservationRegistry.create();
        RecordingHandler handler = new RecordingHandler("helper", context -> true);
        registry.observationConfig().observationHandler(handler);

        Observation supplierObservation = Observation.createNotStarted("supplier", registry);
        String observed = supplierObservation.observe(() -> "observed-value");
        assertThat(observed).isEqualTo("observed-value");

        Observation callableObservation = Observation.createNotStarted("checked", registry);
        Observation.CheckedCallable<String, IOException> callable =
                callableObservation.wrapChecked(() -> "checked-value");
        assertThat(callable.call()).isEqualTo("checked-value");

        Observation failingObservation = Observation.createNotStarted("failing", registry);
        IOException failure = new IOException("boom");
        assertThatExceptionOfType(IOException.class)
                .isThrownBy(() -> failingObservation.observeChecked(() -> {
                    throw failure;
                }))
                .isSameAs(failure);

        assertThat(failingObservation.getContext().getError()).isSameAs(failure);
        assertThat(handler.events).containsSubsequence(
                "helper:start:failing",
                "helper:scope-opened:failing",
                "helper:scope-closed:failing",
                "helper:error:failing",
                "helper:stop:failing");

        Observation scopedObservation = Observation.createNotStarted("scoped", registry).start();
        assertThat(scopedObservation.scoped(() -> registry.getCurrentObservation().getContextView().getName()))
                .isEqualTo("scoped");
        Observation.tryScoped(scopedObservation,
                () -> assertThat(registry.getCurrentObservation()).isSameAs(scopedObservation));
        assertThat(Observation.tryScoped(null, () -> "plain")).isEqualTo("plain");
        assertThat(Observation.tryScopedChecked(null, () -> "checked-plain")).isEqualTo("checked-plain");
        scopedObservation.stop();
    }

    @Test
    void explicitParentObservationCanBeAssignedIndependentlyOfCurrentScope() {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new RecordingHandler("explicit-parent", context -> true));
        Observation explicitParent = Observation.start("explicit.parent", registry);
        Observation currentParent = Observation.start("current.parent", registry);

        try (Observation.Scope currentScope = currentParent.openScope()) {
            Observation child = Observation.createNotStarted("explicit.child", registry)
                    .parentObservation(explicitParent)
                    .start();

            assertThat(child.getContext().getParentObservation()).isSameAs(explicitParent);
            assertThat(registry.getCurrentObservation()).isSameAs(currentParent);
            child.stop();
        }

        currentParent.stop();
        explicitParent.stop();
    }

    @Test
    void predicatesNoopRegistriesAndNoopConventionsAvoidUnnecessaryWork() {
        AtomicInteger contextCreations = new AtomicInteger();
        Observation nullRegistryObservation = Observation.createNotStarted("ignored", () -> {
            contextCreations.incrementAndGet();
            return new Observation.Context();
        }, null);
        Observation noopRegistryObservation = Observation.createNotStarted("ignored", () -> {
            contextCreations.incrementAndGet();
            return new Observation.Context();
        }, ObservationRegistry.NOOP);

        assertThat(nullRegistryObservation.isNoop()).isTrue();
        assertThat(noopRegistryObservation.isNoop()).isTrue();
        assertThat(contextCreations).hasValue(0);

        ObservationRegistry registry = ObservationRegistry.create();
        RecordingHandler handler = new RecordingHandler("predicate", context -> true);
        registry.observationConfig()
                .observationPredicate((name, context) -> !name.startsWith("skip"))
                .observationHandler(handler);

        Observation skipped = Observation.createNotStarted("skip.this", () -> {
            contextCreations.incrementAndGet();
            return new Observation.Context();
        }, registry);
        skipped.observe(() -> assertThat(registry.getCurrentObservation().isNoop()).isTrue());

        assertThat(skipped.isNoop()).isTrue();
        assertThat(contextCreations).hasValue(1);
        assertThat(handler.events).isEmpty();
    }

    @Test
    void conventionsSelectCustomGlobalOrDefaultNamesAndKeyValues() {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig()
                .observationHandler(new RecordingHandler("convention", context -> true))
                .observationConvention(new GlobalRequestConvention());

        RequestContext globalContext = new RequestContext("/orders/{id}", "tenant-a");
        Observation globalObservation = Observation.createNotStarted((ObservationConvention<RequestContext>) null,
                new DefaultRequestConvention(), () -> globalContext, registry).start();
        globalObservation.stop();

        assertThat(globalContext.getName()).isEqualTo("request.global");
        assertThat(globalContext.getContextualName()).isEqualTo("GET /orders/{id} globally");
        assertThat(globalContext.getLowCardinalityKeyValue("route").getValue()).isEqualTo("/orders/{id}");
        assertThat(globalContext.getLowCardinalityKeyValue("source").getValue()).isEqualTo("global");
        assertThat(globalContext.getHighCardinalityKeyValue("tenant").getValue()).isEqualTo("tenant-a");

        RequestContext customContext = new RequestContext("/payments/{id}", "tenant-b");
        Observation customObservation = Observation.start(new CustomRequestConvention(), () -> customContext, registry);
        customObservation.stop();

        assertThat(customContext.getName()).isEqualTo("request.custom");
        assertThat(customContext.getContextualName()).isEqualTo("POST /payments/{id}");
        assertThat(customContext.getLowCardinalityKeyValue("source").getValue()).isEqualTo("custom");

        Observation documented = DocumentedObservation.ORDER_CREATED.start(registry);
        documented.stop();

        assertThat(documented.getContext().getName()).isEqualTo("documented.order.created");
        assertThat(documented.getContext().getContextualName()).isEqualTo("order created");
        assertThat(DocumentedObservation.ORDER_CREATED.getLowCardinalityKeyNames())
                .containsExactly(DocumentedLowCardinalityKeyName.OUTCOME);
        assertThat(DocumentedObservation.ORDER_CREATED.getHighCardinalityKeyNames())
                .containsExactly(DocumentedHighCardinalityKeyName.ORDER_ID);
        assertThat(DocumentedObservation.ORDER_CREATED.getEvents()[0].format("42").getContextualName())
                .isEqualTo("order 42 created");
        assertThat(DocumentedObservation.ORDER_CREATED.getPrefix()).isEqualTo("order.");
    }

    @Test
    void compositeHandlersDispatchToFirstOrAllMatchingHandlers() {
        RecordingHandler rejecting = new RecordingHandler("rejecting", context -> false);
        RecordingHandler first = new RecordingHandler("first", context -> context.containsKey("supported"));
        RecordingHandler second = new RecordingHandler("second", context -> context.containsKey("supported"));
        Observation.Context context = new Observation.Context().put("supported", true);

        ObservationHandler.CompositeObservationHandler firstMatching =
                new ObservationHandler.FirstMatchingCompositeObservationHandler(rejecting, first, second);
        firstMatching.onStart(context);
        firstMatching.onEvent(Observation.Event.of("event"), context);
        firstMatching.onStop(context);

        assertThat(firstMatching.supportsContext(context)).isTrue();
        assertThat(first.events).containsExactly("first:start:null", "first:event:event:event", "first:stop:null");
        assertThat(second.events).isEmpty();

        ObservationHandler.CompositeObservationHandler allMatching =
                new ObservationHandler.AllMatchingCompositeObservationHandler(rejecting, first, second);
        allMatching.onScopeOpened(context);
        allMatching.onScopeClosed(context);

        assertThat(allMatching.getHandlers()).hasSize(3);
        assertThat(first.events).contains("first:scope-opened:null", "first:scope-closed:null");
        assertThat(second.events).containsExactly("second:scope-opened:null", "second:scope-closed:null");
    }

    @Test
    void contextViewsAndEventsExposeTypedStateAndKeyValueCollections() {
        Observation.Context context = new Observation.Context();
        context.setName("context.name");
        context.setContextualName("contextual");
        context.put("string", "value")
                .put(RequestContext.class, new RequestContext("/route", "tenant"))
                .addLowCardinalityKeyValues(KeyValues.of("method", "GET", "status", "200"))
                .addHighCardinalityKeyValue(KeyValue.of("url", "/orders/42"));

        RequestContext computed = context.computeIfAbsent("computed",
                key -> new RequestContext("/computed", "tenant-c"));
        RequestContext existing = context.computeIfAbsent("computed", key -> new RequestContext("/other", "tenant-d"));
        Object removed = context.remove("string");

        assertThat(removed).isEqualTo("value");
        assertThat(context.containsKey("string")).isFalse();
        assertThat(context.getOrDefault("missing", "fallback")).isEqualTo("fallback");
        assertThat(context.getOrDefault("missing-supplier", () -> "supplied")).isEqualTo("supplied");
        RequestContext requiredContext = context.getRequired(RequestContext.class);
        assertThat(requiredContext.route).isEqualTo("/route");
        assertThat(existing).isSameAs(computed);
        assertThat(context.getLowCardinalityKeyValues().stream().map(KeyValue::getKey))
                .containsExactly("method", "status");
        assertThat(context.getAllKeyValues().stream().map(KeyValue::getKey)).contains("method", "status", "url");

        context.removeLowCardinalityKeyValues("method", "status");
        context.removeHighCardinalityKeyValues("url");
        assertThat(context.getLowCardinalityKeyValues()).isEmpty();
        assertThat(context.getHighCardinalityKeyValues()).isEmpty();
        assertThat(context.toString()).contains("context.name", "contextual", "computed");

        assertThatThrownBy(() -> context.getRequired("absent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absent");

        Observation.Event event = Observation.Event.of("published", "item %s published", 123456L);
        assertThat(event.getName()).isEqualTo("published");
        assertThat(event.getWallTime()).isEqualTo(123456L);
        assertThat(event.format("alpha").getContextualName()).isEqualTo("item alpha published");
        assertThat(event.toString()).contains("published", "123456");
    }

    @Test
    void textPublisherPublishesConvertedLifecycleMessagesOnlyForSupportedContexts() {
        ObservationRegistry registry = ObservationRegistry.create();
        List<String> messages = new ArrayList<>();
        registry.observationConfig().observationHandler(new ObservationTextPublisher(messages::add,
                context -> context.getName().startsWith("publish"),
                context -> context.getName() + "|" + context.getLowCardinalityKeyValue("kind").getValue()));

        Observation ignored = Observation.start("ignored", registry);
        ignored.stop();

        Observation published = Observation.createNotStarted("publish.lifecycle", registry)
                .lowCardinalityKeyValue("kind", "text");
        published.observe(() -> published.event(Observation.Event.of("midpoint")));

        assertThat(messages).hasSize(5);
        assertThat(messages).contains(
                "START - publish.lifecycle|text",
                " OPEN - publish.lifecycle|text",
                "CLOSE - publish.lifecycle|text",
                " STOP - publish.lifecycle|text");
        assertThat(messages.get(2))
                .contains("EVENT - event.name='midpoint', event.contextualName='midpoint'")
                .contains("publish.lifecycle|text");
    }

    @Test
    void transportContextsUsePropagatorCallbacksCarrierKindsAndResponses() {
        Map<String, String> carrier = new HashMap<>();
        SenderContext<Map<String, String>> sender = new SenderContext<>((map, key, value) -> map.put(key, value));
        sender.setCarrier(carrier);
        sender.setRemoteServiceName("orders-service");
        sender.setRemoteServiceAddress("orders.internal:443");
        sender.getSetter().set(sender.getCarrier(), "traceparent", "00-abc-def-01");

        assertThat(sender.getKind()).isEqualTo(Kind.PRODUCER);
        assertThat(sender.getRemoteServiceName()).isEqualTo("orders-service");
        assertThat(sender.getRemoteServiceAddress()).isEqualTo("orders.internal:443");
        assertThat(carrier).containsEntry("traceparent", "00-abc-def-01");

        ReceiverContext<Map<String, String>> receiver = new ReceiverContext<>(Map::get, Kind.CONSUMER);
        receiver.setCarrier(carrier);
        receiver.setRemoteServiceName("producer-service");
        receiver.setRemoteServiceAddress("producer.internal:9092");

        assertThat(receiver.getGetter().get(receiver.getCarrier(), "traceparent")).isEqualTo("00-abc-def-01");
        assertThat(receiver.getKind()).isEqualTo(Kind.CONSUMER);
        assertThat(receiver.getRemoteServiceName()).isEqualTo("producer-service");
        assertThat(receiver.getRemoteServiceAddress()).isEqualTo("producer.internal:9092");

        RequestReplySenderContext<Map<String, String>, Integer> requestSender =
                new RequestReplySenderContext<>((map, key, value) -> map.put(key, value));
        requestSender.setResponse(201);
        RequestReplyReceiverContext<Map<String, String>, String> requestReceiver =
                new RequestReplyReceiverContext<>(Map::get);
        requestReceiver.setResponse("accepted");

        assertThat(requestSender.getKind()).isEqualTo(Kind.CLIENT);
        assertThat(requestSender.getResponse()).isEqualTo(201);
        assertThat(requestReceiver.getKind()).isEqualTo(Kind.SERVER);
        assertThat(requestReceiver.getResponse()).isEqualTo("accepted");
    }

    @Test
    void lateBoundObservationConventionAppliesNameContextualNameAndKeyValuesWhenStarted() {
        ObservationRegistry registry = ObservationRegistry.create();
        RecordingHandler handler = new RecordingHandler("late-convention", context -> true);
        registry.observationConfig().observationHandler(handler);

        RequestContext context = new RequestContext("/late/{id}", "tenant-late");
        Observation observation = Observation.createNotStarted("placeholder.name", () -> context, registry)
                .observationConvention(new CustomRequestConvention())
                .lowCardinalityKeyValue("phase", "prepared");

        observation.start();
        observation.stop();

        assertThat(context.getName()).isEqualTo("request.custom");
        assertThat(context.getContextualName()).isEqualTo("POST /late/{id}");
        assertThat(context.getLowCardinalityKeyValue("route").getValue()).isEqualTo("/late/{id}");
        assertThat(context.getLowCardinalityKeyValue("source").getValue()).isEqualTo("custom");
        assertThat(context.getLowCardinalityKeyValue("phase").getValue()).isEqualTo("prepared");
        assertThat(handler.events).containsSubsequence(
                "late-convention:start:request.custom",
                "late-convention:stop:request.custom");
    }

    @Test
    void globalObservationsDelegateToConfiguredRegistryAndCanReset() {
        ObservationRegistry registry = ObservationRegistry.create();
        RecordingHandler handler = new RecordingHandler("global", context -> true);
        registry.observationConfig().observationHandler(handler);

        try {
            Observations.setRegistry(registry);
            Observation observation = Observation.start("global.operation", Observations.getGlobalRegistry());
            try (Observation.Scope scope = observation.openScope()) {
                assertThat(scope.getCurrentObservation()).isSameAs(observation);
                assertThat(Observations.getGlobalRegistry().getCurrentObservation()).isSameAs(observation);
                assertThat(registry.getCurrentObservation()).isSameAs(observation);
            }
            observation.stop();
        } finally {
            Observations.resetRegistry();
        }

        assertThat(handler.events).contains("global:start:global.operation", "global:stop:global.operation");
    }

    private static final class RecordingHandler implements ObservationHandler<Observation.Context> {
        private final String name;

        private final Predicate<Observation.Context> supportsContext;

        private final List<String> events = new ArrayList<>();

        private RecordingHandler(String name, Predicate<Observation.Context> supportsContext) {
            this.name = name;
            this.supportsContext = supportsContext;
        }

        @Override
        public void onStart(Observation.Context context) {
            record("start", context);
        }

        @Override
        public void onError(Observation.Context context) {
            record("error", context);
        }

        @Override
        public void onEvent(Observation.Event event, Observation.Context context) {
            this.events.add(this.name + ":event:" + event.getName() + ":" + event.getContextualName());
        }

        @Override
        public void onScopeOpened(Observation.Context context) {
            record("scope-opened", context);
        }

        @Override
        public void onScopeClosed(Observation.Context context) {
            record("scope-closed", context);
        }

        @Override
        public void onScopeReset(Observation.Context context) {
            record("scope-reset", context);
        }

        @Override
        public void onStop(Observation.Context context) {
            record("stop", context);
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return this.supportsContext.test(context);
        }

        private void record(String event, Observation.Context context) {
            this.events.add(this.name + ":" + event + ":" + context.getName());
        }
    }

    private static final class RequestContext extends Observation.Context {
        private final String route;

        private final String tenant;

        private RequestContext(String route, String tenant) {
            this.route = route;
            this.tenant = tenant;
        }
    }

    private static final class DefaultRequestConvention implements ObservationConvention<RequestContext> {
        @Override
        public KeyValues getLowCardinalityKeyValues(RequestContext context) {
            return KeyValues.of("route", context.route, "source", "default");
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(RequestContext context) {
            return KeyValues.of("tenant", context.tenant);
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof RequestContext;
        }

        @Override
        public String getName() {
            return "request.default";
        }

        @Override
        public String getContextualName(RequestContext context) {
            return "GET " + context.route;
        }
    }

    private static final class GlobalRequestConvention implements GlobalObservationConvention<RequestContext> {
        @Override
        public KeyValues getLowCardinalityKeyValues(RequestContext context) {
            return KeyValues.of("route", context.route, "source", "global");
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(RequestContext context) {
            return KeyValues.of("tenant", context.tenant);
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof RequestContext;
        }

        @Override
        public String getName() {
            return "request.global";
        }

        @Override
        public String getContextualName(RequestContext context) {
            return "GET " + context.route + " globally";
        }
    }

    private static final class CustomRequestConvention implements ObservationConvention<RequestContext> {
        @Override
        public KeyValues getLowCardinalityKeyValues(RequestContext context) {
            return KeyValues.of("route", context.route, "source", "custom");
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof RequestContext;
        }

        @Override
        public String getName() {
            return "request.custom";
        }

        @Override
        public String getContextualName(RequestContext context) {
            return "POST " + context.route;
        }
    }

    private enum DocumentedObservation implements ObservationDocumentation {
        ORDER_CREATED;

        @Override
        public String getName() {
            return "documented.order.created";
        }

        @Override
        public String getContextualName() {
            return "order created";
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return new KeyName[] {DocumentedLowCardinalityKeyName.OUTCOME};
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return new KeyName[] {DocumentedHighCardinalityKeyName.ORDER_ID};
        }

        @Override
        public Observation.Event[] getEvents() {
            return new Observation.Event[] {Observation.Event.of("created", "order %s created")};
        }

        @Override
        public String getPrefix() {
            return "order.";
        }
    }

    private enum DocumentedLowCardinalityKeyName implements KeyName {
        OUTCOME {
            @Override
            public String asString() {
                return "order.outcome";
            }
        }
    }

    private enum DocumentedHighCardinalityKeyName implements KeyName {
        ORDER_ID {
            @Override
            public String asString() {
                return "order.id";
            }
        }
    }
}
