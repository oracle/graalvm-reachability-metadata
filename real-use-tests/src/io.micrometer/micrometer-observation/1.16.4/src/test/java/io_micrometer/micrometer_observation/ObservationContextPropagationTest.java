/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_observation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.micrometer.context.ContextExecutorService;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Propagates the current observation across threads through the context-propagation integration:
 * the `ObservationThreadLocalAccessor` discovered by `ContextRegistry`, context snapshots, and a
 * context-aware executor service.
 *
 * The application handles a request on one thread and fans work out to a worker pool. Child
 * observations created on the worker must find the request observation as their parent, and the
 * worker thread must be left clean once the task finishes.
 */
class ObservationContextPropagationTest {

    private RecordingHandler handler;

    private ObservationRegistry registry;

    private ExecutorService workers;

    private ContextSnapshotFactory snapshots;

    @BeforeEach
    void setUp() {
        handler = new RecordingHandler();
        registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(handler);
        ObservationThreadLocalAccessor.getInstance().setObservationRegistry(registry);
        snapshots = ContextSnapshotFactory.builder().build();
        workers = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "inventory-worker"));
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        workers.shutdown();
        assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void observationAccessorIsRegisteredThroughServiceLoader() {
        assertThat(ContextRegistry.getInstance().getThreadLocalAccessors())
                .anySatisfy(accessor -> assertThat(accessor.key()).isEqualTo(ObservationThreadLocalAccessor.KEY));
        assertThat(ObservationThreadLocalAccessor.getInstance().getObservationRegistry()).isSameAs(registry);
    }

    @Test
    void contextExecutorServiceCarriesRequestObservationToWorker() throws Exception {
        InventoryService inventory = new InventoryService(registry, ContextExecutorService.wrap(workers, snapshots));

        Observation request = Observation.createNotStarted("http.request", registry)
                .contextualName("GET /orders/42");
        int available = request.observe(() -> inventory.lookup("sku-7"));

        assertThat(available).isEqualTo(7);
        assertThat(handler.stoppedNames).containsExactly("inventory.lookup", "http.request");
        Observation.Context lookup = handler.stopped.get("inventory.lookup");
        assertThat(handler.startThreads.get("inventory.lookup")).isEqualTo("inventory-worker");
        assertThat(handler.startThreads.get("http.request")).isNotEqualTo("inventory-worker");
        assertThat(lookup.getParentObservation()).isNotNull();
        assertThat(lookup.getParentObservation().getContextView().getName()).isEqualTo("http.request");
        assertThat(lookup.getParentObservation().getContextView().getContextualName()).isEqualTo("GET /orders/42");
        assertThat(lookup.getHighCardinalityKeyValue("sku").getValue()).isEqualTo("sku-7");

        String leftOnWorker = workers.submit(() -> describeCurrent(registry)).get(10, TimeUnit.SECONDS);
        assertThat(leftOnWorker).isEqualTo("none");
        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void plainExecutorDoesNotCarryObservation() throws Exception {
        InventoryService inventory = new InventoryService(registry, workers);

        Observation.createNotStarted("http.request", registry).observe(() -> inventory.lookup("sku-3"));

        Observation.Context lookup = handler.stopped.get("inventory.lookup");
        assertThat(lookup.getParentObservation()).isNull();
        assertThat(handler.stoppedNames).containsExactly("inventory.lookup", "http.request");
    }

    @Test
    void capturedSnapshotRestoresObservationInCallbacks() throws Exception {
        Observation request = Observation.start("batch.job", registry);
        ContextSnapshot snapshot;
        try (Observation.Scope scope = request.openScope()) {
            snapshot = snapshots.captureAll();
        }
        assertThat(registry.getCurrentObservation()).isNull();

        try (ContextSnapshot.Scope restored = snapshot.setThreadLocals()) {
            assertThat(registry.getCurrentObservation()).isSameAs(request);
        }
        assertThat(registry.getCurrentObservation()).isNull();

        Runnable wrapped = snapshot.wrap(() -> Observation.createNotStarted("batch.chunk", registry)
                .lowCardinalityKeyValue("chunk.thread", Thread.currentThread().getName())
                .observe(() -> { }));
        workers.submit(wrapped).get(10, TimeUnit.SECONDS);
        request.stop();

        Observation.Context chunk = handler.stopped.get("batch.chunk");
        assertThat(chunk.getParentObservation()).isSameAs(request);
        assertThat(chunk.getLowCardinalityKeyValue("chunk.thread").getValue()).isEqualTo("inventory-worker");
        assertThat(handler.stoppedNames).containsExactly("batch.chunk", "batch.job");
    }

    private static String describeCurrent(ObservationRegistry registry) {
        Observation current = registry.getCurrentObservation();
        return current == null ? "none" : current.getContext().getName();
    }

    /**
     * Application service that checks stock on a worker pool, observing each lookup.
     */
    private static final class InventoryService {

        private final ObservationRegistry registry;

        private final ExecutorService executor;

        InventoryService(ObservationRegistry registry, ExecutorService executor) {
            this.registry = registry;
            this.executor = executor;
        }

        int lookup(String sku) {
            try {
                return executor.submit(() -> Observation.createNotStarted("inventory.lookup", registry)
                        .highCardinalityKeyValue("sku", sku)
                        .observe(() -> Integer.parseInt(sku.substring(sku.indexOf('-') + 1))))
                        .get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException("Inventory lookup failed for " + sku, e);
            }
        }
    }

    /**
     * Handler standing in for a tracing backend: records stop order, contexts, and start threads.
     */
    private static final class RecordingHandler implements ObservationHandler<Observation.Context> {

        private final List<String> stoppedNames = new CopyOnWriteArrayList<>();

        private final Map<String, Observation.Context> stopped = new ConcurrentHashMap<>();

        private final Map<String, String> startThreads = new ConcurrentHashMap<>();

        @Override
        public void onStart(Observation.Context context) {
            startThreads.put(context.getName(), Thread.currentThread().getName());
        }

        @Override
        public void onStop(Observation.Context context) {
            stopped.put(context.getName(), context);
            stoppedNames.add(context.getName());
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }
    }
}
