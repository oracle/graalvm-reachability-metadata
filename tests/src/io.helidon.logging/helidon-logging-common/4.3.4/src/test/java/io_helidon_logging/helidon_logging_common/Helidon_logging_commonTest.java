/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_logging.helidon_logging_common;

import io.helidon.common.context.spi.DataPropagationProvider;
import io.helidon.logging.common.HelidonMdc;
import io.helidon.logging.common.LogConfig;
import io.helidon.logging.common.MdcSupplierPropagator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class Helidon_logging_commonTest {
    private static final String REQUEST_ID = "requestId";
    private static final String TENANT = "tenant";

    @BeforeEach
    void clearMdcBeforeTest() {
        HelidonMdc.clear();
    }

    @AfterEach
    void clearMdcAfterTest() {
        HelidonMdc.clear();
    }

    @Test
    void deferredMdcSuppliersAreResolvedOnDemandAndCanBeRemoved() {
        AtomicInteger supplierCalls = new AtomicInteger();

        HelidonMdc.setDeferred(REQUEST_ID, () -> "request-" + supplierCalls.incrementAndGet());

        assertThat(supplierCalls).hasValue(0);
        assertThat(HelidonMdc.get(REQUEST_ID)).contains("request-1");
        assertThat(HelidonMdc.get(REQUEST_ID)).contains("request-2");

        HelidonMdc.remove(REQUEST_ID);

        assertThat(HelidonMdc.get(REQUEST_ID)).isEmpty();
    }

    @Test
    void supplierBasedMdcSetStoresAndReplacesCurrentThreadValue() {
        HelidonMdc.set(REQUEST_ID, () -> "initial");
        assertThat(HelidonMdc.get(REQUEST_ID)).contains("initial");

        HelidonMdc.set(REQUEST_ID, () -> "replacement");
        assertThat(HelidonMdc.get(REQUEST_ID)).contains("replacement");

        HelidonMdc.clear();
        assertThat(HelidonMdc.get(REQUEST_ID)).isEmpty();
    }

    @Test
    void stringBasedMdcOperationsAreSafeWithoutAConcreteLoggingBackend() {
        assertThatCode(() -> {
            HelidonMdc.set(REQUEST_ID, "request-123");
            Optional<String> value = HelidonMdc.get(REQUEST_ID);
            assertThat(value).isNotNull();
            HelidonMdc.remove(REQUEST_ID);
            HelidonMdc.clear();
        }).doesNotThrowAnyException();
    }

    @Test
    void mdcSupplierPropagatorCapturesAndRestoresSupplierSnapshots() {
        DataPropagationProvider<Map<String, Supplier<String>>> propagator = new MdcSupplierPropagator();
        HelidonMdc.setDeferred(REQUEST_ID, () -> "captured-request");
        Map<String, Supplier<String>> captured = propagator.data();

        HelidonMdc.setDeferred(REQUEST_ID, () -> "current-request");
        HelidonMdc.setDeferred(TENANT, () -> "current-tenant");

        assertThat(captured).containsOnlyKeys(REQUEST_ID);
        assertThat(captured.get(REQUEST_ID).get()).isEqualTo("captured-request");
        assertThat(HelidonMdc.get(REQUEST_ID)).contains("current-request");
        assertThat(HelidonMdc.get(TENANT)).contains("current-tenant");

        propagator.propagateData(captured);

        assertThat(HelidonMdc.get(REQUEST_ID)).contains("captured-request");
        assertThat(HelidonMdc.get(TENANT)).isEmpty();

        propagator.clearData(captured);

        assertThat(HelidonMdc.get(REQUEST_ID)).isEmpty();
    }

    @Test
    void serviceLoaderDiscoversMdcSupplierPropagatorForContextPropagation() {
        DataPropagationProvider<Map<String, Supplier<String>>> propagator = loadMdcSupplierPropagatorService();
        HelidonMdc.setDeferred(REQUEST_ID, () -> "request-from-service-provider");
        Map<String, Supplier<String>> captured = propagator.data();

        HelidonMdc.clear();
        assertThat(HelidonMdc.get(REQUEST_ID)).isEmpty();

        propagator.propagateData(captured);

        assertThat(HelidonMdc.get(REQUEST_ID)).contains("request-from-service-provider");
    }

    @Test
    void mdcSupplierPropagatorMovesCapturedContextBetweenThreads() throws Exception {
        DataPropagationProvider<Map<String, Supplier<String>>> propagator = new MdcSupplierPropagator();
        HelidonMdc.setDeferred(REQUEST_ID, () -> "request-from-parent");
        Map<String, Supplier<String>> parentContext = propagator.data();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> result = executor.submit(() -> {
                assertThat(HelidonMdc.get(REQUEST_ID)).isEmpty();
                propagator.propagateData(parentContext);
                try {
                    return HelidonMdc.get(REQUEST_ID).orElseThrow();
                } finally {
                    propagator.clearData(parentContext);
                    assertThat(HelidonMdc.get(REQUEST_ID)).isEmpty();
                }
            });

            assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("request-from-parent");
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private DataPropagationProvider<Map<String, Supplier<String>>> loadMdcSupplierPropagatorService() {
        ServiceLoader<?> serviceLoader = ServiceLoader.load(DataPropagationProvider.class);
        for (Object provider : serviceLoader) {
            if (provider instanceof MdcSupplierPropagator mdcSupplierPropagator) {
                return mdcSupplierPropagator;
            }
        }
        throw new AssertionError("MdcSupplierPropagator service provider was not discovered");
    }

    @Test
    void logConfigLifecycleMethodsAreIdempotent() {
        assertThatCode(() -> {
            LogConfig.initClass();
            LogConfig.configureRuntime();
            LogConfig.configureRuntime();
        }).doesNotThrowAnyException();
    }
}
