/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_observation;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CopyOnWriteArrayList;

import io.micrometer.observation.NullObservation;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.Observations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs instrumented code with observability switched off or deferred: the no-op registry, a
 * registry without handlers, the `Observations` global registry, and `NullObservation`.
 *
 * A catalog service is instrumented once and deployed in several ways: with tracing disabled,
 * before the application has wired a backend, through the global registry a library falls back
 * to, and under a silent grouping observation. The test asserts that business results and
 * failures are unchanged, that nothing reaches the backend while observability is off, that a
 * disabled observation still opens and restores scopes, and that child observations find the
 * right parent once observability is on.
 */
class DisabledObservabilityTest {

    @AfterEach
    void resetGlobalRegistry() {
        Observations.resetRegistry();
    }

    @Test
    void noopRegistryRunsInstrumentedCodeWithoutObserving() {
        CatalogService service = new CatalogService(ObservationRegistry.NOOP);

        assertThat(ObservationRegistry.NOOP.isNoop()).isTrue();
        assertThat(service.lookup("sku-1")).isEqualTo("Lamp:noop=true");
        assertThatThrownBy(() -> service.lookup("sku-404"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("unknown sku sku-404");

        Observation disabled = Observation.start("catalog.reindex", ObservationRegistry.NOOP)
                .lowCardinalityKeyValue("catalog", "main");
        assertThat(disabled.isNoop()).isTrue();
        assertThat(disabled.getContext().getLowCardinalityKeyValues()).isEmpty();
        disabled.stop();

        assertThat(Observation.createNotStarted("catalog.reindex", null).isNoop()).isTrue();
        assertThat(ObservationRegistry.NOOP.getCurrentObservation()).isNull();
    }

    @Test
    void registryWithoutHandlersStaysNoopUntilBackendIsWired() {
        ObservationRegistry registry = ObservationRegistry.create();
        CatalogService service = new CatalogService(registry);

        assertThat(registry.isNoop()).isTrue();
        assertThat(service.lookup("sku-2")).isEqualTo("Desk:noop=true");

        RecordingHandler backend = new RecordingHandler();
        registry.observationConfig().observationHandler(backend);

        assertThat(registry.isNoop()).isFalse();
        assertThat(service.lookup("sku-2")).isEqualTo("Desk:noop=false");
        assertThat(backend.log).containsExactly("start:catalog.lookup", "stop:catalog.lookup");
        assertThat(backend.stopped.get(0).getLowCardinalityKeyValue("found").getValue()).isEqualTo("true");
    }

    @Test
    void globalRegistryIsNoopUntilApplicationInstallsOne() {
        Observations.resetRegistry();
        ObservationRegistry global = Observations.getGlobalRegistry();
        CatalogService service = new CatalogService(global);

        assertThat(global.isNoop()).isTrue();
        assertThat(service.lookup("sku-1")).isEqualTo("Lamp:noop=true");

        RecordingHandler backend = new RecordingHandler();
        ObservationRegistry application = ObservationRegistry.create();
        application.observationConfig().observationHandler(backend);
        Observations.setRegistry(application);

        assertThat(Observations.getGlobalRegistry()).isSameAs(global);
        assertThat(global.isNoop()).isFalse();
        assertThat(service.lookup("sku-1")).isEqualTo("Lamp:noop=false");
        assertThatThrownBy(() -> service.lookup("sku-9"))
                .isInstanceOf(NoSuchElementException.class);
        assertThat(backend.log).containsExactly(
                "start:catalog.lookup",
                "stop:catalog.lookup",
                "start:catalog.lookup",
                "error:catalog.lookup:unknown sku sku-9",
                "stop:catalog.lookup");

        Observations.resetRegistry();
        assertThat(global.isNoop()).isTrue();
        assertThat(service.lookup("sku-2")).isEqualTo("Desk:noop=true");
        assertThat(backend.log).hasSize(5);
    }

    @Test
    void disabledObservationStillScopesAndRestoresEnclosingRequest() {
        RecordingHandler backend = new RecordingHandler();
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig()
                .observationHandler(backend)
                .observationPredicate((name, context) -> !name.startsWith("cache"));
        CatalogService service = new CatalogService(registry);

        Observation request = Observation.createNotStarted("http.request", registry);
        String result = request.observe(() -> {
            Observation cache = Observation.createNotStarted("cache.read", registry);
            String cached = cache.observe(() -> {
                assertThat(cache.isNoop()).isTrue();
                assertThat(registry.getCurrentObservation()).isSameAs(cache);
                return "miss";
            });
            assertThat(registry.getCurrentObservation()).isSameAs(request);
            return cached + "->" + service.lookup("sku-1");
        });

        assertThat(result).isEqualTo("miss->Lamp:noop=false");
        assertThat(backend.log).containsExactly(
                "start:http.request",
                "start:catalog.lookup",
                "stop:catalog.lookup",
                "stop:http.request");
        Observation.Context lookup = backend.stopped.get(0);
        assertThat(lookup.getParentObservation()).isSameAs(request);
        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void nullObservationGroupsChildrenWithoutReportingItself() {
        RecordingHandler backend = new RecordingHandler();
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(backend);
        CatalogService service = new CatalogService(registry);

        NullObservation batch = new NullObservation(registry);
        List<String> results = batch.observe(() -> {
            assertThat(registry.getCurrentObservation()).isSameAs(batch);
            return List.of(service.lookup("sku-1"), service.lookup("sku-2"));
        });

        assertThat(results).containsExactly("Lamp:noop=false", "Desk:noop=false");
        assertThat(backend.log).containsExactly(
                "start:catalog.lookup",
                "stop:catalog.lookup",
                "start:catalog.lookup",
                "stop:catalog.lookup");
        assertThat(backend.stopped)
                .allSatisfy(context -> assertThat(context.getParentObservation()).isSameAs(batch));
        assertThat(registry.getCurrentObservation()).isNull();
    }

    /** Application service instrumented once, whatever registry it is deployed with. */
    static final class CatalogService {

        private final ObservationRegistry registry;

        CatalogService(ObservationRegistry registry) {
            this.registry = registry;
        }

        String lookup(String sku) {
            Observation observation = Observation.createNotStarted("catalog.lookup", registry)
                    .highCardinalityKeyValue("sku", sku);
            return observation.observe(() -> {
                String product = switch (sku) {
                    case "sku-1" -> "Lamp";
                    case "sku-2" -> "Desk";
                    default -> throw new NoSuchElementException("unknown sku " + sku);
                };
                observation.lowCardinalityKeyValue("found", "true");
                return product + ":noop=" + observation.isNoop();
            });
        }
    }

    /** Backend that records the lifecycle callbacks it receives. */
    static final class RecordingHandler implements ObservationHandler<Observation.Context> {

        private final List<String> log = new CopyOnWriteArrayList<>();

        private final List<Observation.Context> stopped = new CopyOnWriteArrayList<>();

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
