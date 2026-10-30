/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_core_reactive;

import io.micronaut.core.async.publisher.AsyncSingleResultPublisher;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AsyncSingleResultPublisherTest {
    @Test
    void executesSupplierOnDemand() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            AtomicInteger calls = new AtomicInteger();
            AsyncSingleResultPublisher<String> publisher = new AsyncSingleResultPublisher<>(executor, () -> {
                calls.incrementAndGet();
                return "computed";
            });
            assertEquals(0, calls.get());
            assertEquals(List.of("computed"), Flux.from(publisher).collectList().block(Duration.ofSeconds(10)));
            assertEquals(1, calls.get());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void completesWithoutItemWhenSupplierReturnsNull() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            AsyncSingleResultPublisher<String> publisher = new AsyncSingleResultPublisher<>(executor, () -> null);
            assertEquals(List.of(), Flux.from(publisher).collectList().block(Duration.ofSeconds(10)));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void forwardsSupplierFailure() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            IllegalArgumentException failure = new IllegalArgumentException("invalid input");
            AsyncSingleResultPublisher<String> publisher = new AsyncSingleResultPublisher<>(executor, () -> {
                throw failure;
            });
            CompletableFuture<List<String>> result = Flux.from(publisher).collectList().toFuture();
            ExecutionException thrown = assertThrows(ExecutionException.class,
                    () -> result.get(10, TimeUnit.SECONDS));
            assertSame(failure, thrown.getCause());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
