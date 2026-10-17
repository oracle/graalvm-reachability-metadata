/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_observation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.common.annotation.ValueExpressionResolver;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.annotation.ObservationKeyValue;
import io.micrometer.observation.annotation.Observed;
import io.micrometer.observation.aop.Cardinality;
import io.micrometer.observation.aop.ObservationKeyValueAnnotationHandler;
import io.micrometer.observation.aop.ObservedAspect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Customizes the `@Observed` aspect: class-level `@Observed`, an aspect-wide observation
 * convention, a skip predicate, and key values resolved from expressions and method results.
 *
 * A shipping service is annotated once at class level, so every public method is observed.
 * The application builds `ObservedAspect` with its own `ObservationConvention`, a predicate
 * that leaves health checks unobserved, and an annotation handler whose expression resolver
 * reads fields of the order and shipment records the service exchanges.
 */
class ShippingAspectCustomizationTest {

    private RecordingHandler handler;

    private ShippingService service;

    @BeforeEach
    void setUp() {
        handler = new RecordingHandler();
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(handler);

        ObservedAspect aspect = new ObservedAspect(registry, new ShippingConvention(),
                joinPoint -> joinPoint.getSignature().getName().startsWith("health"));
        aspect.setObservationKeyValueAnnotationHandler(new ObservationKeyValueAnnotationHandler(
                resolverClass -> {
                    throw new IllegalStateException("no value resolver configured");
                },
                expressionResolverClass -> new RecordFieldExpressionResolver()));

        AspectJProxyFactory factory = new AspectJProxyFactory(new DefaultShippingService());
        factory.addInterface(ShippingService.class);
        factory.addAspect(aspect);
        service = factory.getProxy();
    }

    @Test
    void classLevelAnnotationObservesEveryPublicMethodThroughTheConvention() {
        Shipment shipment = service.ship(new Order("ord-17", 3));
        boolean cancelled = service.cancel("ord-18");

        assertThat(shipment).isEqualTo(new Shipment("trk-ord-17", "DISPATCHED"));
        assertThat(cancelled).isTrue();
        assertThat(handler.log).containsExactly(
                "start:shipping",
                "stop:shipping",
                "start:shipping",
                "stop:shipping");

        Observation.Context shipContext = handler.stopped.get(0);
        assertThat(shipContext).isInstanceOf(ObservedAspect.ObservedAspectContext.class);
        assertThat(shipContext.getContextualName()).isEqualTo("shipping ship");
        assertThat(shipContext.getLowCardinalityKeyValues()).contains(
                KeyValue.of("class", ShippingService.class.getName()),
                KeyValue.of("method", "ship"),
                KeyValue.of("carrier", "acme-freight"),
                KeyValue.of("component", "shipping"));
        assertThat(shipContext.getHighCardinalityKeyValue("target").getValue())
                .isEqualTo("DefaultShippingService");

        Observation.Context cancelContext = handler.stopped.get(1);
        assertThat(cancelContext.getContextualName()).isEqualTo("shipping cancel");
        assertThat(cancelContext.getLowCardinalityKeyValues()).contains(
                KeyValue.of("method", "cancel"),
                KeyValue.of("component", "shipping"));
    }

    @Test
    void parameterAndResultKeyValuesAreResolvedByExpression() {
        service.ship(new Order("ord-21", 5));

        Observation.Context context = handler.stopped.get(0);
        assertThat(context.getLowCardinalityKeyValue("order.lines").getValue()).isEqualTo("5");
        assertThat(context.getHighCardinalityKeyValue("order.id").getValue()).isEqualTo("ord-21");
        assertThat(context.getLowCardinalityKeyValue("shipment.status").getValue())
                .isEqualTo("DISPATCHED");
        assertThat(context.getHighCardinalityKeyValue("shipment.tracking").getValue())
                .isEqualTo("trk-ord-21");
    }

    @Test
    void plainParameterKeyValueUsesTheArgumentText() {
        service.cancel("ord-30");

        Observation.Context context = handler.stopped.get(0);
        assertThat(context.getHighCardinalityKeyValue("order.id").getValue()).isEqualTo("ord-30");
    }

    @Test
    void failedShipmentRecordsErrorWithoutResultKeyValues() {
        assertThatThrownBy(() -> service.ship(new Order("ord-40", 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("order ord-40 has no lines");

        assertThat(handler.log).containsExactly(
                "start:shipping",
                "error:shipping:order ord-40 has no lines",
                "stop:shipping");
        Observation.Context context = handler.stopped.get(0);
        assertThat(context.getLowCardinalityKeyValue("order.lines").getValue()).isEqualTo("0");
        assertThat(context.getLowCardinalityKeyValue("shipment.status")).isNull();
    }

    @Test
    void skipPredicateLeavesHealthCheckUnobserved() {
        String health = service.healthCheck();

        assertThat(health).isEqualTo("UP");
        assertThat(handler.log).isEmpty();
    }

    /** Order placed by a customer; the aspect reads its fields by expression. */
    record Order(String id, int lines) {
    }

    /** Shipment created for an order; the aspect reads it from the method result. */
    record Shipment(String trackingNumber, String status) {
    }

    /** Resolves field-name expressions against the records the service exchanges. */
    static final class RecordFieldExpressionResolver implements ValueExpressionResolver {

        private static final Map<String, Function<Object, Object>> FIELDS = Map.of(
                "id", value -> ((Order) value).id(),
                "lines", value -> ((Order) value).lines(),
                "trackingNumber", value -> ((Shipment) value).trackingNumber(),
                "status", value -> ((Shipment) value).status());

        @Override
        public String resolve(String expression, Object parameter) {
            Function<Object, Object> field = FIELDS.get(expression);
            if (field == null) {
                throw new IllegalArgumentException("unknown expression " + expression);
            }
            return String.valueOf(field.apply(parameter));
        }
    }

    /** Convention the application applies to every aspect-created observation. */
    static final class ShippingConvention implements ObservationConvention<ObservedAspect.ObservedAspectContext> {

        @Override
        public KeyValues getLowCardinalityKeyValues(ObservedAspect.ObservedAspectContext context) {
            return KeyValues.of("component", "shipping");
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(ObservedAspect.ObservedAspectContext context) {
            return KeyValues.of("target", context.getProceedingJoinPoint().getTarget().getClass().getSimpleName());
        }

        @Override
        public String getContextualName(ObservedAspect.ObservedAspectContext context) {
            return "shipping " + context.getProceedingJoinPoint().getSignature().getName();
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof ObservedAspect.ObservedAspectContext;
        }
    }

    /** Business interface the application calls; the proxy implements it. */
    interface ShippingService {

        Shipment ship(Order order);

        boolean cancel(String orderId);

        String healthCheck();
    }

    /** Implementation observed as a whole by its class-level annotation. */
    @Observed(name = "shipping", lowCardinalityKeyValues = {"carrier", "acme-freight"})
    static class DefaultShippingService implements ShippingService {

        @Override
        @ObservationKeyValue(key = "shipment.status", expression = "status", cardinality = Cardinality.LOW)
        @ObservationKeyValue(key = "shipment.tracking", expression = "trackingNumber")
        public Shipment ship(
                @ObservationKeyValue(key = "order.lines", expression = "lines", cardinality = Cardinality.LOW)
                @ObservationKeyValue(key = "order.id", expression = "id") Order order) {
            if (order.lines() == 0) {
                throw new IllegalArgumentException("order " + order.id() + " has no lines");
            }
            return new Shipment("trk-" + order.id(), "DISPATCHED");
        }

        @Override
        public boolean cancel(@ObservationKeyValue("order.id") String orderId) {
            return orderId.startsWith("ord-");
        }

        @Override
        public String healthCheck() {
            return "UP";
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
