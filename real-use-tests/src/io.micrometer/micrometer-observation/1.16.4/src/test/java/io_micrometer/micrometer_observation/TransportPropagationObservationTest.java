/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_observation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationHandler.FirstMatchingCompositeObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.Kind;
import io.micrometer.observation.transport.Propagator;
import io.micrometer.observation.transport.ReceiverContext;
import io.micrometer.observation.transport.RequestReplyReceiverContext;
import io.micrometer.observation.transport.RequestReplySenderContext;
import io.micrometer.observation.transport.SenderContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Propagates observations across a remote boundary with the transport contexts: sender and
 * receiver contexts, request-reply contexts, and the propagator setter and getter.
 *
 * A client calls a server over an in-memory channel whose messages carry string headers. A
 * propagating handler, selected by a first-matching composite handler, injects a trace header
 * into outgoing carriers and extracts it from incoming ones, the way a tracing bridge does. The
 * test asserts on the headers on the wire, on the request-reply responses, and on the trace
 * each side recorded.
 */
class TransportPropagationObservationTest {

    private static final String TRACE_HEADER = "x-trace-id";

    private TraceRecorder recorder;

    private ObservationRegistry registry;

    private InMemoryChannel channel;

    @BeforeEach
    void setUp() {
        recorder = new TraceRecorder();
        registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new FirstMatchingCompositeObservationHandler(
                new InjectingSenderHandler(recorder),
                new ExtractingReceiverHandler(recorder),
                new LocalHandler(recorder)));
        channel = new InMemoryChannel();
        new InventoryServer(registry, channel);
    }

    @Test
    void requestReplyCallCarriesTraceHeaderAndBothSidesSeeTheResponse() {
        InventoryClient client = new InventoryClient(registry, channel);

        String reply = client.lookup("sku-42");

        assertThat(reply).isEqualTo("sku-42:in-stock");
        Message request = channel.delivered.get(0);
        assertThat(request.headers).containsEntry("operation", "lookup").containsKey(TRACE_HEADER);
        String traceId = request.headers.get(TRACE_HEADER);

        Span clientSpan = recorder.find("inventory.client");
        Span serverSpan = recorder.find("inventory.server");
        assertThat(clientSpan.kind).isEqualTo(Kind.CLIENT);
        assertThat(clientSpan.traceId).isEqualTo(traceId);
        assertThat(clientSpan.remoteService).isEqualTo("inventory-service");
        assertThat(clientSpan.remoteAddress).isEqualTo("mem://inventory:7000");
        assertThat(clientSpan.response).isEqualTo("sku-42:in-stock");
        assertThat(clientSpan.tags).containsEntry("outcome", "SUCCESS");

        assertThat(serverSpan.kind).isEqualTo(Kind.SERVER);
        assertThat(serverSpan.traceId).isEqualTo(traceId);
        assertThat(serverSpan.remoteService).isEqualTo("storefront");
        assertThat(serverSpan.response).isEqualTo("sku-42:in-stock");
        assertThat(serverSpan.tags).containsEntry("operation", "lookup");

        assertThat(recorder.order).containsExactly(
                "start:inventory.client",
                "start:inventory.server",
                "start:inventory.stock-check",
                "stop:inventory.stock-check",
                "stop:inventory.server",
                "stop:inventory.client");
    }

    @Test
    void localWorkInsideTheServerJoinsTheIncomingTrace() {
        InventoryClient client = new InventoryClient(registry, channel);

        client.lookup("sku-7");

        Span serverSpan = recorder.find("inventory.server");
        Span stockCheck = recorder.find("inventory.stock-check");
        assertThat(stockCheck.kind).isNull();
        assertThat(stockCheck.traceId).isEqualTo(serverSpan.traceId);
        assertThat(stockCheck.parentName).isEqualTo("inventory.server");
    }

    @Test
    void separateCallsStartSeparateTraces() {
        InventoryClient client = new InventoryClient(registry, channel);

        client.lookup("sku-1");
        client.lookup("sku-2");

        assertThat(channel.delivered).hasSize(2);
        assertThat(channel.delivered.get(0).headers.get(TRACE_HEADER))
                .isNotEqualTo(channel.delivered.get(1).headers.get(TRACE_HEADER));
    }

    @Test
    void serverFailureIsRecordedOnBothSidesOfTheCall() {
        InventoryClient client = new InventoryClient(registry, channel);

        assertThatThrownBy(() -> client.lookup("unknown"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("remote failure: no such sku: unknown");

        Span clientSpan = recorder.find("inventory.client");
        Span serverSpan = recorder.find("inventory.server");
        assertThat(serverSpan.error).isInstanceOf(IllegalArgumentException.class);
        assertThat(serverSpan.response).isNull();
        assertThat(clientSpan.error).isInstanceOf(IllegalStateException.class);
        assertThat(clientSpan.tags).containsEntry("outcome", "FAILURE");
        assertThat(clientSpan.traceId).isEqualTo(serverSpan.traceId);
    }

    @Test
    void oneWayMessagesUseProducerAndConsumerKinds() {
        AuditPublisher publisher = new AuditPublisher(registry, channel);
        AuditConsumer consumer = new AuditConsumer(registry);

        publisher.publish("price-changed", "sku-42");
        Message message = channel.delivered.get(0);
        consumer.consume(message);

        assertThat(consumer.received).containsExactly("price-changed:sku-42");
        assertThat(message.headers).containsEntry("event-type", "price-changed");
        Span producer = recorder.find("audit.publish");
        Span consumerSpan = recorder.find("audit.consume");
        assertThat(producer.kind).isEqualTo(Kind.PRODUCER);
        assertThat(consumerSpan.kind).isEqualTo(Kind.CONSUMER);
        assertThat(consumerSpan.traceId).isEqualTo(producer.traceId);
        assertThat(consumerSpan.remoteService).isEqualTo("audit-topic");
    }

    @Test
    void consumerOfAnUntracedMessageStartsANewTrace() {
        AuditConsumer consumer = new AuditConsumer(registry);
        Message untraced = new Message(new LinkedHashMap<>(Map.of("event-type", "restock")), "sku-9");

        consumer.consume(untraced);

        Span consumerSpan = recorder.find("audit.consume");
        assertThat(consumerSpan.traceId).isNotBlank();
        assertThat(consumerSpan.extracted).isFalse();
        assertThat(consumer.received).containsExactly("restock:sku-9");
    }

    @Test
    void getterExposesMultiValuedHeaderLookups() {
        Propagator.Getter<Message> getter = Message.GETTER;
        Message message = new Message(new LinkedHashMap<>(Map.of("event-type", "restock")), "body");

        assertThat(getter.get(message, "event-type")).isEqualTo("restock");
        assertThat(getter.getAll(message, "event-type")).containsExactly("restock");
        assertThat(getter.get(message, "missing")).isNull();
        assertThat(getter.getAll(message, "missing")).isEmpty();
    }

    /** A message on the in-memory channel: string headers and a string body. */
    static final class Message {

        static final Propagator.Setter<Message> SETTER = (carrier, key, value) -> carrier.headers.put(key, value);

        static final Propagator.Getter<Message> GETTER = (carrier, key) -> carrier.headers.get(key);

        final Map<String, String> headers;

        final String body;

        Message(Map<String, String> headers, String body) {
            this.headers = headers;
            this.body = body;
        }
    }

    /** A synchronous in-memory transport with one server endpoint. */
    static final class InMemoryChannel {

        final List<Message> delivered = new ArrayList<>();

        private InventoryServer server;

        void bind(InventoryServer server) {
            this.server = server;
        }

        String call(Message request) {
            delivered.add(request);
            return server.handle(request);
        }

        void publish(Message message) {
            delivered.add(message);
        }
    }

    /** Client side of the inventory call: one request-reply sender observation per lookup. */
    static final class InventoryClient {

        private final ObservationRegistry registry;

        private final InMemoryChannel channel;

        InventoryClient(ObservationRegistry registry, InMemoryChannel channel) {
            this.registry = registry;
            this.channel = channel;
        }

        String lookup(String sku) {
            Message request = new Message(new LinkedHashMap<>(Map.of("operation", "lookup")), sku);
            RequestReplySenderContext<Message, String> context =
                    new RequestReplySenderContext<>(Message.SETTER, Kind.CLIENT);
            context.setCarrier(request);
            context.setRemoteServiceName("inventory-service");
            context.setRemoteServiceAddress("mem://inventory:7000");
            Observation observation = Observation.createNotStarted("inventory.client", () -> context, registry);
            observation.start();
            try {
                String reply = channel.call(request);
                if (reply.startsWith("ERROR ")) {
                    throw new IllegalStateException("remote failure: " + reply.substring(6));
                }
                context.setResponse(reply);
                observation.lowCardinalityKeyValue("outcome", "SUCCESS");
                return reply;
            } catch (RuntimeException e) {
                observation.lowCardinalityKeyValue("outcome", "FAILURE");
                observation.error(e);
                throw e;
            } finally {
                observation.stop();
            }
        }
    }

    /** Server side of the inventory call: one request-reply receiver observation per request. */
    static final class InventoryServer {

        private final ObservationRegistry registry;

        InventoryServer(ObservationRegistry registry, InMemoryChannel channel) {
            this.registry = registry;
            channel.bind(this);
        }

        String handle(Message request) {
            RequestReplyReceiverContext<Message, String> context =
                    new RequestReplyReceiverContext<>(Message.GETTER, Kind.SERVER);
            context.setCarrier(request);
            context.setRemoteServiceName("storefront");
            Observation observation = Observation.createNotStarted("inventory.server", () -> context, registry)
                    .lowCardinalityKeyValue("operation", Message.GETTER.get(request, "operation"))
                    .start();
            try (Observation.Scope scope = observation.openScope()) {
                String reply = answer(request.body);
                context.setResponse(reply);
                return reply;
            } catch (IllegalArgumentException e) {
                observation.error(e);
                return "ERROR " + e.getMessage();
            } finally {
                observation.stop();
            }
        }

        private String answer(String sku) {
            return Observation.createNotStarted("inventory.stock-check", registry).observe(() -> {
                if ("unknown".equals(sku)) {
                    throw new IllegalArgumentException("no such sku: " + sku);
                }
                return sku + ":in-stock";
            });
        }
    }

    /** Publishes one-way audit messages through a producer sender context. */
    static final class AuditPublisher {

        private final ObservationRegistry registry;

        private final InMemoryChannel channel;

        AuditPublisher(ObservationRegistry registry, InMemoryChannel channel) {
            this.registry = registry;
            this.channel = channel;
        }

        void publish(String eventType, String body) {
            Message message = new Message(new LinkedHashMap<>(Map.of("event-type", eventType)), body);
            SenderContext<Message> context = new SenderContext<>(Message.SETTER, Kind.PRODUCER);
            context.setCarrier(message);
            context.setRemoteServiceName("audit-topic");
            Observation.createNotStarted("audit.publish", () -> context, registry)
                    .observe(() -> channel.publish(message));
        }
    }

    /** Consumes audit messages through a consumer receiver context. */
    static final class AuditConsumer {

        final List<String> received = new ArrayList<>();

        private final ObservationRegistry registry;

        AuditConsumer(ObservationRegistry registry) {
            this.registry = registry;
        }

        void consume(Message message) {
            ReceiverContext<Message> context = new ReceiverContext<>(Message.GETTER, Kind.CONSUMER);
            context.setCarrier(message);
            context.setRemoteServiceName("audit-topic");
            Observation.createNotStarted("audit.consume", () -> context, registry)
                    .observe(() -> received.add(context.getGetter().get(message, "event-type") + ":" + message.body));
        }
    }

    /** What the tracing backend recorded for one observation. */
    static final class Span {

        final String name;

        String traceId;

        Kind kind;

        String remoteService;

        String remoteAddress;

        Object response;

        Throwable error;

        String parentName;

        boolean extracted;

        final Map<String, String> tags = new LinkedHashMap<>();

        Span(String name) {
            this.name = name;
        }
    }

    /** The tracing backend: spans keyed by context, in start order, plus the event order. */
    static final class TraceRecorder {

        private static final String SPAN_KEY = "recorder.span";

        final List<Span> spans = new ArrayList<>();

        final List<String> order = new ArrayList<>();

        private final AtomicInteger traceSequence = new AtomicInteger();

        Span start(Observation.Context context, String traceId, Kind kind) {
            Span span = new Span(context.getName());
            span.traceId = traceId;
            span.kind = kind;
            context.put(SPAN_KEY, span);
            spans.add(span);
            order.add("start:" + context.getName());
            return span;
        }

        String newTraceId() {
            return "trace-" + traceSequence.incrementAndGet();
        }

        Span spanOf(Observation.Context context) {
            return context.getRequired(SPAN_KEY);
        }

        void stop(Observation.Context context, Object response) {
            Span span = spanOf(context);
            span.response = response;
            span.error = context.getError();
            for (KeyValue keyValue : context.getAllKeyValues()) {
                span.tags.put(keyValue.getKey(), keyValue.getValue());
            }
            order.add("stop:" + context.getName());
        }

        Span find(String name) {
            return spans.stream().filter(span -> span.name.equals(name)).findFirst().orElseThrow();
        }
    }

    /** Starts a client or producer span and writes its trace id into the outgoing carrier. */
    static final class InjectingSenderHandler implements ObservationHandler<SenderContext<Message>> {

        private final TraceRecorder recorder;

        InjectingSenderHandler(TraceRecorder recorder) {
            this.recorder = recorder;
        }

        @Override
        public void onStart(SenderContext<Message> context) {
            Span span = recorder.start(context, recorder.newTraceId(), context.getKind());
            span.remoteService = context.getRemoteServiceName();
            span.remoteAddress = context.getRemoteServiceAddress();
            context.getSetter().set(context.getCarrier(), TRACE_HEADER, span.traceId);
        }

        @Override
        public void onStop(SenderContext<Message> context) {
            Object response = context instanceof RequestReplySenderContext<?, ?> reply ? reply.getResponse() : null;
            recorder.stop(context, response);
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof SenderContext;
        }
    }

    /** Starts a server or consumer span that continues the trace read from the incoming carrier. */
    static final class ExtractingReceiverHandler implements ObservationHandler<ReceiverContext<Message>> {

        private final TraceRecorder recorder;

        ExtractingReceiverHandler(TraceRecorder recorder) {
            this.recorder = recorder;
        }

        @Override
        public void onStart(ReceiverContext<Message> context) {
            String incoming = context.getGetter().get(context.getCarrier(), TRACE_HEADER);
            String traceId = incoming != null ? incoming : recorder.newTraceId();
            Span span = recorder.start(context, traceId, context.getKind());
            span.extracted = incoming != null;
            span.remoteService = context.getRemoteServiceName();
            span.remoteAddress = context.getRemoteServiceAddress();
        }

        @Override
        public void onStop(ReceiverContext<Message> context) {
            Object response = context instanceof RequestReplyReceiverContext<?, ?> reply ? reply.getResponse() : null;
            recorder.stop(context, response);
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof ReceiverContext;
        }
    }

    /** Records local observations as children of their parent observation's trace. */
    static final class LocalHandler implements ObservationHandler<Observation.Context> {

        private final TraceRecorder recorder;

        LocalHandler(TraceRecorder recorder) {
            this.recorder = recorder;
        }

        @Override
        public void onStart(Observation.Context context) {
            Observation.ContextView parent = context.getParentObservation() == null
                    ? null
                    : context.getParentObservation().getContextView();
            String traceId = parent == null ? recorder.newTraceId() : parent.<Span>getRequired(TraceRecorder.SPAN_KEY).traceId;
            Span span = recorder.start(context, traceId, null);
            span.parentName = parent == null ? null : parent.getName();
        }

        @Override
        public void onStop(Observation.Context context) {
            recorder.stop(context, null);
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }
    }
}
