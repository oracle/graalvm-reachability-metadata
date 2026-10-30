/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_core_reactive;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.async.subscriber.CompletionAwareSubscriber;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompletionAwareSubscriberTest {
    @Test
    void collectsRequestedItemsAndTracksCompletion() throws Exception {
        ResultSubscriber subscriber = new ResultSubscriber();
        assertFalse(subscriber.isComplete());
        Flux.just("first", "second").subscribe(subscriber);

        assertEquals(List.of("first", "second"), subscriber.result.get(10, TimeUnit.SECONDS));
        assertTrue(subscriber.isComplete());
    }

    @Test
    void tracksErrorTermination() {
        ResultSubscriber subscriber = new ResultSubscriber();
        IllegalArgumentException failure = new IllegalArgumentException("invalid item");
        Publishers.<String>just(failure).subscribe(subscriber);

        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> subscriber.result.get(10, TimeUnit.SECONDS));
        assertSame(failure, thrown.getCause());
        assertTrue(subscriber.isComplete());
        assertTrue(subscriber.values.isEmpty());
    }

    private static final class ResultSubscriber extends CompletionAwareSubscriber<String> {
        private final List<String> values = new ArrayList<>();
        private final CompletableFuture<List<String>> result = new CompletableFuture<>();

        @Override
        protected void doOnSubscribe(Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        protected void doOnNext(String message) {
            values.add(message);
        }

        @Override
        protected void doOnError(Throwable failure) {
            result.completeExceptionally(failure);
        }

        @Override
        protected void doOnComplete() {
            result.complete(List.copyOf(values));
        }
    }
}
