/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_observation;

import java.util.ArrayList;
import java.util.List;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Instruments an order-processing service with the core Observation API: a registry configured
 * with a handler, a global filter, a predicate, and a custom convention.
 *
 * The application wraps each order in an observation, nests a child observation for the payment
 * step, signals business events, and records failures. A handler acts as the tracing/logging
 * backend and records what it saw, which is what the test asserts on.
 */
class OrderProcessingObservationTest {

    private RecordingHandler handler;

    private OrderService service;

    @BeforeEach
    void setUp() {
        handler = new RecordingHandler();
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig()
                .observationHandler(handler)
                .observationPredicate((name, context) -> !name.startsWith("health"))
                .observationFilter(context -> context.addLowCardinalityKeyValue(KeyValue.of("region", "eu-west")));
        service = new OrderService(registry);
    }

    @Test
    void successfulOrderIsObservedWithConventionAndNestedPaymentStep() {
        String receipt = service.placeOrder(new Order("o-1", "premium", 3));

        assertThat(receipt).isEqualTo("receipt-o-1");
        assertThat(handler.log).containsExactly(
                "start:orders.placed",
                "scope-open:orders.placed",
                "start:orders.payment",
                "event:orders.payment:payment.authorized",
                "stop:orders.payment",
                "scope-close:orders.placed",
                "stop:orders.placed");

        Observation.Context order = handler.stopped.get(1);
        assertThat(order.getName()).isEqualTo("orders.placed");
        assertThat(order.getContextualName()).isEqualTo("place order premium");
        assertThat(order.getLowCardinalityKeyValues()).containsExactlyInAnyOrder(
                KeyValue.of("customer.tier", "premium"),
                KeyValue.of("outcome", "success"),
                KeyValue.of("region", "eu-west"));
        assertThat(order.getHighCardinalityKeyValues()).containsExactly(KeyValue.of("order.id", "o-1"));
        assertThat(order.getError()).isNull();

        Observation.Context payment = handler.stopped.get(0);
        assertThat(payment.getName()).isEqualTo("orders.payment");
        assertThat(payment.getParentObservation()).isNotNull();
        assertThat(payment.getParentObservation().getContextView().getName()).isEqualTo("orders.placed");
        assertThat(payment.getLowCardinalityKeyValue("region").getValue()).isEqualTo("eu-west");
    }

    @Test
    void rejectedOrderRecordsErrorAndOutcome() {
        assertThatThrownBy(() -> service.placeOrder(new Order("o-2", "basic", 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("quantity must be positive");

        assertThat(handler.log).containsExactly(
                "start:orders.placed",
                "scope-open:orders.placed",
                "scope-close:orders.placed",
                "error:orders.placed:quantity must be positive",
                "stop:orders.placed");
        Observation.Context order = handler.stopped.get(0);
        assertThat(order.getError()).isInstanceOf(IllegalArgumentException.class);
        assertThat(order.getLowCardinalityKeyValue("outcome").getValue()).isEqualTo("failure");
    }

    @Test
    void predicateTurnsHealthChecksIntoNoopObservations() {
        String status = service.healthCheck();

        assertThat(status).isEqualTo("UP:noop=true");
        assertThat(handler.log).isEmpty();
    }

    /** Domain object handed to the instrumented service. */
    record Order(String id, String tier, int quantity) {
    }

    /** Context carrying the order so the convention can derive key values from it. */
    static final class OrderContext extends Observation.Context {

        private final Order order;

        private boolean succeeded;

        OrderContext(Order order) {
            this.order = order;
        }

        Order getOrder() {
            return order;
        }

        void markSucceeded() {
            succeeded = true;
        }
    }

    /** Default naming and tagging the application ships for order observations. */
    static final class OrderConvention implements ObservationConvention<OrderContext> {

        @Override
        public String getName() {
            return "orders.placed";
        }

        @Override
        public String getContextualName(OrderContext context) {
            return "place order " + context.getOrder().tier();
        }

        @Override
        public KeyValues getLowCardinalityKeyValues(OrderContext context) {
            return KeyValues.of(
                    KeyValue.of("customer.tier", context.getOrder().tier()),
                    KeyValue.of("outcome", context.succeeded ? "success" : "failure"));
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(OrderContext context) {
            return KeyValues.of("order.id", context.getOrder().id());
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof OrderContext;
        }
    }

    /** Application service instrumented with observations. */
    static final class OrderService {

        private static final OrderConvention DEFAULT_CONVENTION = new OrderConvention();

        private final ObservationRegistry registry;

        OrderService(ObservationRegistry registry) {
            this.registry = registry;
        }

        String placeOrder(Order order) {
            OrderContext context = new OrderContext(order);
            Observation observation = Observation.createNotStarted(
                    null, DEFAULT_CONVENTION, () -> context, registry);
            return observation.observe(() -> {
                if (order.quantity() <= 0) {
                    throw new IllegalArgumentException("quantity must be positive");
                }
                authorizePayment(order);
                context.markSucceeded();
                return "receipt-" + order.id();
            });
        }

        private void authorizePayment(Order order) {
            Observation payment = Observation.createNotStarted("orders.payment", registry)
                    .highCardinalityKeyValue("order.id", order.id())
                    .start();
            try {
                payment.event(Observation.Event.of("payment.authorized"));
            } finally {
                payment.stop();
            }
        }

        String healthCheck() {
            Observation observation = Observation.createNotStarted("health.check", registry);
            return observation.observe(() -> "UP:noop=" + observation.isNoop());
        }
    }

    /** Backend handler that records the lifecycle callbacks it receives. */
    static final class RecordingHandler implements ObservationHandler<Observation.Context> {

        final List<String> log = new ArrayList<>();

        final List<Observation.Context> stopped = new ArrayList<>();

        @Override
        public void onStart(Observation.Context context) {
            log.add("start:" + context.getName());
        }

        @Override
        public void onError(Observation.Context context) {
            log.add("error:" + context.getName() + ":" + context.getError().getMessage());
        }

        @Override
        public void onEvent(Observation.Event event, Observation.Context context) {
            log.add("event:" + context.getName() + ":" + event.getName());
        }

        @Override
        public void onScopeOpened(Observation.Context context) {
            log.add("scope-open:" + context.getName());
        }

        @Override
        public void onScopeClosed(Observation.Context context) {
            log.add("scope-close:" + context.getName());
        }

        @Override
        public void onStop(Observation.Context context) {
            log.add("stop:" + context.getName());
            stopped.add(context);
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }
    }
}
