/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_observation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.micrometer.common.KeyValue;
import io.micrometer.common.annotation.ValueResolver;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.annotation.Observed;
import io.micrometer.observation.annotation.ObservationKeyValue;
import io.micrometer.observation.aop.Cardinality;
import io.micrometer.observation.aop.ObservationKeyValueAnnotationHandler;
import io.micrometer.observation.aop.ObservedAspect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Observes an inventory service declaratively with the `@Observed` annotation aspect, applied
 * through a Spring AOP proxy.
 *
 * The application annotates its service methods with `@Observed` and its parameters with
 * `@ObservationKeyValue`, registers `ObservedAspect` with an annotation handler, and calls the
 * service only through the proxy. A recording handler stands in for the tracing backend.
 */
class ObservedAnnotationAspectTest {

    private RecordingHandler handler;

    private InventoryService service;

    @BeforeEach
    void setUp() {
        handler = new RecordingHandler();
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(handler);

        SkuResolver skuResolver = new SkuResolver();
        ObservedAspect aspect = new ObservedAspect(registry);
        aspect.setObservationKeyValueAnnotationHandler(new ObservationKeyValueAnnotationHandler(
                resolverClass -> skuResolver,
                expressionResolverClass -> {
                    throw new IllegalStateException("no expression resolver configured");
                }));

        AspectJProxyFactory factory = new AspectJProxyFactory(new DefaultInventoryService());
        factory.addInterface(InventoryService.class);
        factory.addAspect(aspect);
        service = factory.getProxy();
    }

    @Test
    void annotatedMethodIsObservedWithDeclaredNameAndKeyValues() {
        int remaining = service.reserve(new Sku("ABC-123"), "warehouse-7");

        assertThat(remaining).isEqualTo(41);
        assertThat(handler.log).containsExactly(
                "start:inventory.reserve",
                "scope-open:inventory.reserve",
                "stop:inventory.reserve",
                "scope-close:inventory.reserve");

        Observation.Context context = handler.stopped.get(0);
        assertThat(context).isInstanceOf(ObservedAspect.ObservedAspectContext.class);
        assertThat(context.getContextualName()).isEqualTo("reserve stock");
        assertThat(context.getLowCardinalityKeyValues()).contains(
                KeyValue.of("class", InventoryService.class.getName()),
                KeyValue.of("method", "reserve"),
                KeyValue.of("channel", "web"),
                KeyValue.of("warehouse", "warehouse-7"));
        assertThat(context.getHighCardinalityKeyValue("sku").getValue()).isEqualTo("sku:ABC-123");
        assertThat(context.getError()).isNull();
    }

    @Test
    void unnamedObservationFallsBackToAspectDefaults() {
        String report = service.stockReport();

        assertThat(report).isEqualTo("42 units");
        Observation.Context context = handler.stopped.get(0);
        assertThat(context.getName()).isEqualTo("method.observed");
        assertThat(context.getContextualName()).isEqualTo("InventoryService#stockReport");
        assertThat(context.getLowCardinalityKeyValues()).contains(
                KeyValue.of("class", InventoryService.class.getName()),
                KeyValue.of("method", "stockReport"));
    }

    @Test
    void failingMethodRecordsErrorOnObservation() {
        assertThatThrownBy(() -> service.reserve(new Sku("ABC-123"), "closed"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("warehouse closed is not accepting reservations");

        assertThat(handler.log).containsExactly(
                "start:inventory.reserve",
                "scope-open:inventory.reserve",
                "scope-close:inventory.reserve",
                "error:inventory.reserve:warehouse closed is not accepting reservations",
                "stop:inventory.reserve");
        Observation.Context context = handler.stopped.get(0);
        assertThat(context.getError()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void asynchronousMethodStopsObservationWhenStageCompletes() {
        CompletableFuture<String> pending = new CompletableFuture<>();
        DefaultInventoryService.nextRestock = pending;

        CompletionStage<String> stage = service.restock(5);

        assertThat(handler.log).containsExactly(
                "start:inventory.restock",
                "scope-open:inventory.restock",
                "scope-close:inventory.restock");

        pending.complete("restocked 5");

        assertThat(stage.toCompletableFuture().join()).isEqualTo("restocked 5");
        assertThat(handler.log).last().isEqualTo("stop:inventory.restock");
    }

    /** Stock-keeping unit passed to the service; its key value is rendered by a resolver. */
    record Sku(String code) {
    }

    /** Renders a SKU parameter for the `sku` high-cardinality key value. */
    static final class SkuResolver implements ValueResolver {

        @Override
        public String resolve(Object parameter) {
            return "sku:" + ((Sku) parameter).code();
        }
    }

    /** Business interface the application calls; the proxy implements it. */
    interface InventoryService {

        int reserve(Sku sku, String warehouse);

        String stockReport();

        CompletionStage<String> restock(int units);
    }

    /** Implementation carrying the observation annotations. */
    static class DefaultInventoryService implements InventoryService {

        static CompletableFuture<String> nextRestock;

        @Override
        @Observed(name = "inventory.reserve", contextualName = "reserve stock",
                lowCardinalityKeyValues = {"channel", "web"})
        public int reserve(
                @ObservationKeyValue(key = "sku", resolver = SkuResolver.class) Sku sku,
                @ObservationKeyValue(key = "warehouse", cardinality = Cardinality.LOW) String warehouse) {
            if ("closed".equals(warehouse)) {
                throw new IllegalStateException("warehouse " + warehouse + " is not accepting reservations");
            }
            return 41;
        }

        @Override
        @Observed
        public String stockReport() {
            return "42 units";
        }

        @Override
        @Observed(name = "inventory.restock")
        public CompletionStage<String> restock(int units) {
            return nextRestock;
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
