/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_core_reactive;

import io.micronaut.core.async.publisher.Publishers;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompletableFuturePublisherTest {
    @Test
    void obtainsFutureOnlyWhenSubscribedAndDeliversItsResult() throws Exception {
        AtomicInteger supplied = new AtomicInteger();
        CompletableFuture<String> source = new CompletableFuture<>();
        Publisher<String> publisher = Publishers.fromCompletableFuture(() -> {
            supplied.incrementAndGet();
            return source;
        });
        assertEquals(0, supplied.get());

        CompletableFuture<List<String>> result = Flux.from(publisher).collectList().toFuture();
        assertEquals(1, supplied.get());
        assertFalse(result.isDone());
        source.complete("delivered");
        assertEquals(List.of("delivered"), result.get(10, TimeUnit.SECONDS));
    }

    @Test
    void completesWithoutAnItemForNullFutureResult() {
        Publisher<String> publisher = Publishers.fromCompletableFuture(CompletableFuture.completedFuture(null));
        assertEquals(List.of(), Flux.from(publisher).collectList().block(Duration.ofSeconds(10)));
    }

    @Test
    void forwardsExceptionalCompletion() {
        IllegalStateException failure = new IllegalStateException("operation rejected");
        CompletableFuture<List<String>> result = Flux.from(Publishers.<String>fromCompletableFuture(
                CompletableFuture.failedFuture(failure))).collectList().toFuture();

        ExecutionException thrown = assertThrows(ExecutionException.class, () -> result.get(10, TimeUnit.SECONDS));
        assertSame(failure, thrown.getCause());
    }

    @Test
    void cancelsTheFutureWhenTheConsumerCancels() {
        CompletableFuture<String> source = new CompletableFuture<>();
        CompletableFuture<String> result = Mono.from(Publishers.fromCompletableFuture(source)).toFuture();

        assertFalse(source.isDone());
        assertTrue(result.cancel(false));
        assertTrue(source.isCancelled());
    }
}
