/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_reactive.mutiny_zero;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import mutiny.zero.AsyncHelpers;
import mutiny.zero.BackpressureStrategy;
import mutiny.zero.PublisherHelpers;
import mutiny.zero.Tube;
import mutiny.zero.TubeConfiguration;
import mutiny.zero.ZeroPublisher;
import mutiny.zero.operators.Concatenate;
import mutiny.zero.operators.Recover;
import mutiny.zero.operators.Retry;
import mutiny.zero.operators.Select;
import mutiny.zero.operators.Spread;
import mutiny.zero.operators.Transform;
import org.junit.jupiter.api.Test;

public class Mutiny_zeroTest {
    private static final long TIMEOUT_SECONDS = 10L;

    @Test
    public void createsPublishersFromSupportedDataSources() throws Exception {
        assertEquals(List.of("alpha", "beta"), collect(ZeroPublisher.fromItems("alpha", "beta")));
        assertEquals(List.of(1, 2, 3), collect(ZeroPublisher.fromIterable(List.of(1, 2, 3))));

        Flow.Publisher<String> streamPublisher = ZeroPublisher.fromStream(() -> Stream.of("fresh", "stream"));
        assertEquals(List.of("fresh", "stream"), collect(streamPublisher));
        assertEquals(List.of("fresh", "stream"), collect(streamPublisher));

        Flow.Publisher<Integer> generated = ZeroPublisher.fromGenerator(
                () -> List.of(4, 5, 6), values -> values.iterator());
        assertEquals(List.of(4, 5, 6), collect(generated));

        Flow.Publisher<String> staged =
                ZeroPublisher.fromCompletionStage(() -> CompletableFuture.completedFuture("completed"));
        assertEquals(List.of("completed"), collect(staged));
    }

    @Test
    public void deliversIterableItemsAccordingToDemandAndStopsAfterCancellation() {
        RecordingSubscriber<Integer> subscriber = new RecordingSubscriber<>();
        ZeroPublisher.fromIterable(List.of(1, 2, 3, 4)).subscribe(subscriber);

        subscriber.request(2);
        assertEquals(List.of(1, 2), subscriber.items());

        subscriber.request(1);
        assertEquals(List.of(1, 2, 3), subscriber.items());

        subscriber.cancel();
        subscriber.request(Long.MAX_VALUE);
        assertEquals(List.of(1, 2, 3), subscriber.items());
    }

    @Test
    public void rejectsNonPositiveDemand() throws Exception {
        RecordingSubscriber<String> subscriber = new RecordingSubscriber<>();
        ZeroPublisher.fromItems("unrequested").subscribe(subscriber);

        subscriber.request(0);

        assertTrue(subscriber.awaitFailure() instanceof IllegalArgumentException);
        assertTrue(subscriber.items().isEmpty());
    }

    @Test
    public void convertsPublishersToCompletionStages() throws Exception {
        Optional<String> first = ZeroPublisher.toCompletionStage(ZeroPublisher.fromItems("first", "second"))
                .toCompletableFuture()
                .get(TIMEOUT_SECONDS, SECONDS);
        Optional<String> empty = ZeroPublisher.<String>toCompletionStage(ZeroPublisher.empty())
                .toCompletableFuture()
                .get(TIMEOUT_SECONDS, SECONDS);

        assertEquals(Optional.of("first"), first);
        assertEquals(Optional.empty(), empty);
    }

    @Test
    public void completionStagePublisherIsLazyAndCancelsItsStage() {
        AtomicInteger subscriptions = new AtomicInteger();
        AtomicReference<CompletableFuture<String>> futureReference = new AtomicReference<>();
        Flow.Publisher<String> publisher = ZeroPublisher.fromCompletionStage(() -> {
            subscriptions.incrementAndGet();
            CompletableFuture<String> future = new CompletableFuture<>();
            futureReference.set(future);
            return future;
        });
        RecordingSubscriber<String> subscriber = new RecordingSubscriber<>();

        publisher.subscribe(subscriber);
        assertEquals(0, subscriptions.get());

        subscriber.request(1);
        assertEquals(1, subscriptions.get());

        subscriber.cancel();
        assertTrue(futureReference.get().isCancelled());
        assertTrue(subscriber.items().isEmpty());
    }

    @Test
    public void propagatesPublisherFailures() throws Exception {
        IllegalStateException failure = new IllegalStateException("publisher failed");
        Flow.Publisher<String> publisher = ZeroPublisher.fromFailure(failure);
        RecordingSubscriber<String> subscriber = new RecordingSubscriber<>();

        publisher.subscribe(subscriber);
        subscriber.request(Long.MAX_VALUE);

        assertSame(failure, subscriber.awaitFailure());
        assertTrue(subscriber.items().isEmpty());
    }

    @Test
    public void composesTransformationSelectionConcatenationAndSpread() throws Exception {
        Flow.Publisher<Integer> transformed =
                new Transform<>(ZeroPublisher.fromItems(1, 2, 3, 4), value -> value * 10);
        Flow.Publisher<Integer> selected = new Select<>(transformed, value -> value >= 30);
        Flow.Publisher<Integer> concatenated =
                new Concatenate<>(List.of(selected, ZeroPublisher.fromItems(5)));
        Flow.Publisher<Integer> spread =
                new Spread<>(concatenated, value -> ZeroPublisher.fromItems(value, -value), 1, 2);

        assertEquals(List.of(30, -30, 40, -40, 5, -5), collect(spread));
    }

    @Test
    public void preservesDemandWhileConcatenatingPublishers() throws Exception {
        Flow.Publisher<Integer> concatenated = new Concatenate<>(List.of(
                ZeroPublisher.fromItems(1, 2),
                ZeroPublisher.empty(),
                ZeroPublisher.fromItems(3, 4)));
        RecordingSubscriber<Integer> subscriber = new RecordingSubscriber<>();
        concatenated.subscribe(subscriber);

        subscriber.request(3);
        assertEquals(List.of(1, 2, 3), subscriber.items());

        subscriber.request(2);
        subscriber.awaitCompletion();
        assertEquals(List.of(1, 2, 3, 4), subscriber.items());
    }

    @Test
    public void recoversAndRetriesFailedPublishers() throws Exception {
        Flow.Publisher<String> recovered =
                new Recover<>(ZeroPublisher.fromFailure(new IllegalArgumentException("recoverable")),
                        failure -> "fallback");
        assertEquals(List.of("fallback"), collect(recovered));

        Flow.Publisher<String> completed =
                new Recover<>(ZeroPublisher.fromFailure(new IllegalArgumentException("ignored")), failure -> null);
        assertEquals(List.of(), collect(completed));

        IllegalStateException mappingFailure = new IllegalStateException("recovery failed");
        RecordingSubscriber<String> failedRecoverySubscriber = new RecordingSubscriber<>();
        new Recover<>(ZeroPublisher.<String>fromFailure(new IllegalArgumentException("recoverable")), failure -> {
            throw mappingFailure;
        }).subscribe(failedRecoverySubscriber);
        failedRecoverySubscriber.request(1);
        assertSame(mappingFailure, failedRecoverySubscriber.awaitFailure());

        AtomicInteger attempts = new AtomicInteger();
        Flow.Publisher<String> transientFailure = ZeroPublisher.fromCompletionStage(() -> {
            if (attempts.incrementAndGet() < 3) {
                return CompletableFuture.failedFuture(new IllegalStateException("try again"));
            }
            return CompletableFuture.completedFuture("eventual success");
        });
        Flow.Publisher<String> retried = new Retry<>(transientFailure, Retry.atMost(2));

        assertEquals(List.of("eventual success"), collect(retried));
        assertEquals(3, attempts.get());
        assertTrue(Retry.always().test(new IllegalStateException("retryable")));
    }

    @Test
    public void mapsAndComposesCompletionStageFailures() throws Exception {
        IllegalStateException original = new IllegalStateException("original");
        IllegalArgumentException mapped = new IllegalArgumentException("mapped", original);
        CompletionStage<String> failed = CompletableFuture.failedFuture(original);

        CompletionStage<String> mappedStage = AsyncHelpers.applyExceptionally(failed, failure -> mapped);
        RecordingSubscriber<String> mappedSubscriber = new RecordingSubscriber<>();
        ZeroPublisher.fromCompletionStage(() -> mappedStage).subscribe(mappedSubscriber);
        mappedSubscriber.request(1);
        assertSame(mapped, mappedSubscriber.awaitFailure());

        CompletionStage<String> recovered =
                AsyncHelpers.composeExceptionally(failed, failure -> CompletableFuture.completedFuture("recovered"));
        assertEquals("recovered", recovered.toCompletableFuture().get(TIMEOUT_SECONDS, SECONDS));

        AtomicBoolean mapperCalled = new AtomicBoolean();
        CompletionStage<String> successful = AsyncHelpers.applyExceptionally(
                CompletableFuture.completedFuture("unchanged"), failure -> {
                    mapperCalled.set(true);
                    return failure;
                });
        assertEquals("unchanged", successful.toCompletableFuture().get(TIMEOUT_SECONDS, SECONDS));
        assertFalse(mapperCalled.get());
    }

    @Test
    public void buffersItemsUntilDemandAndRunsTubeCallbacks() throws Exception {
        TubeConfiguration configuration = new TubeConfiguration()
                .withBackpressureStrategy(BackpressureStrategy.BUFFER)
                .withBufferSize(2);
        AtomicReference<Tube<String>> tubeReference = new AtomicReference<>();
        AtomicLong requested = new AtomicLong();
        AtomicBoolean terminated = new AtomicBoolean();
        RecordingSubscriber<String> subscriber = subscribe(configuration, tubeReference);
        Tube<String> tube = tubeReference.get();
        tube.whenRequested(requested::addAndGet).whenTerminates(() -> terminated.set(true));

        tube.send("one").send("two");
        tube.complete();
        assertTrue(subscriber.items().isEmpty());

        subscriber.request(2);
        subscriber.awaitCompletion();

        assertEquals(List.of("one", "two"), subscriber.items());
        assertEquals(2L, requested.get());
        assertEquals(0L, tube.outstandingRequests());
        assertTrue(terminated.get());
        assertEquals(BackpressureStrategy.BUFFER, configuration.getBackpressureStrategy());
        assertEquals(2, configuration.getBufferSize());
    }

    @Test
    public void appliesDropLatestAndUnboundedBackpressureStrategies() throws Exception {
        AtomicReference<Tube<String>> dropTubeReference = new AtomicReference<>();
        RecordingSubscriber<String> dropSubscriber = subscribe(
                new TubeConfiguration().withBackpressureStrategy(BackpressureStrategy.DROP), dropTubeReference);
        dropTubeReference.get().send("dropped");
        dropSubscriber.request(1);
        dropTubeReference.get().send("kept").complete();
        dropSubscriber.awaitCompletion();
        assertEquals(List.of("kept"), dropSubscriber.items());

        AtomicReference<Tube<Integer>> latestTubeReference = new AtomicReference<>();
        RecordingSubscriber<Integer> latestSubscriber = subscribe(
                new TubeConfiguration().withBackpressureStrategy(BackpressureStrategy.LATEST).withBufferSize(2),
                latestTubeReference);
        latestTubeReference.get().send(1).send(2).send(3).complete();
        latestSubscriber.request(2);
        latestSubscriber.awaitCompletion();
        assertEquals(List.of(2, 3), latestSubscriber.items());

        AtomicReference<Tube<Integer>> unboundedTubeReference = new AtomicReference<>();
        RecordingSubscriber<Integer> unboundedSubscriber = subscribe(
                new TubeConfiguration().withBackpressureStrategy(BackpressureStrategy.UNBOUNDED_BUFFER),
                unboundedTubeReference);
        unboundedTubeReference.get().send(7).send(8).send(9).complete();
        unboundedSubscriber.request(3);
        unboundedSubscriber.awaitCompletion();
        assertEquals(List.of(7, 8, 9), unboundedSubscriber.items());
    }

    @Test
    public void appliesErrorAndIgnoreBackpressureStrategies() throws Exception {
        AtomicReference<Tube<String>> errorTubeReference = new AtomicReference<>();
        RecordingSubscriber<String> errorSubscriber = subscribe(
                new TubeConfiguration().withBackpressureStrategy(BackpressureStrategy.ERROR), errorTubeReference);
        errorTubeReference.get().send("without demand");
        assertTrue(errorSubscriber.awaitFailure() instanceof IllegalStateException);

        AtomicReference<Tube<String>> ignoreTubeReference = new AtomicReference<>();
        RecordingSubscriber<String> ignoreSubscriber = subscribe(
                new TubeConfiguration().withBackpressureStrategy(BackpressureStrategy.IGNORE), ignoreTubeReference);
        ignoreTubeReference.get().send("one").send("two").complete();
        ignoreSubscriber.awaitCompletion();
        assertEquals(List.of("one", "two"), ignoreSubscriber.items());
    }

    @Test
    public void safelyPublishesItemsSentByConcurrentThreads() throws Exception {
        int producerCount = 4;
        int itemsPerProducer = 25;
        AtomicReference<Tube<Integer>> tubeReference = new AtomicReference<>();
        RecordingSubscriber<Integer> subscriber = subscribe(new TubeConfiguration(), tubeReference);
        subscriber.request(Long.MAX_VALUE);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(producerCount);

        try {
            List<Future<?>> producers = new ArrayList<>();
            for (int producer = 0; producer < producerCount; producer++) {
                int firstItem = producer * itemsPerProducer;
                producers.add(executor.submit(() -> {
                    if (!start.await(TIMEOUT_SECONDS, SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to send items");
                    }
                    for (int offset = 0; offset < itemsPerProducer; offset++) {
                        tubeReference.get().send(firstItem + offset);
                    }
                    return null;
                }));
            }

            start.countDown();
            for (Future<?> producer : producers) {
                producer.get(TIMEOUT_SECONDS, SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        tubeReference.get().complete();
        subscriber.awaitCompletion();

        Set<Integer> expected = new HashSet<>();
        for (int item = 0; item < producerCount * itemsPerProducer; item++) {
            expected.add(item);
        }
        assertEquals(producerCount * itemsPerProducer, subscriber.items().size());
        assertEquals(expected, new HashSet<>(subscriber.items()));
    }

    @Test
    public void signalsTubeFailureAndCancellationCallbacks() throws Exception {
        AtomicReference<Tube<String>> failedTubeReference = new AtomicReference<>();
        RecordingSubscriber<String> failedSubscriber = subscribe(new TubeConfiguration(), failedTubeReference);
        IllegalArgumentException failure = new IllegalArgumentException("tube failed");
        failedTubeReference.get().fail(failure);
        assertSame(failure, failedSubscriber.awaitFailure());

        AtomicReference<Tube<String>> cancelledTubeReference = new AtomicReference<>();
        RecordingSubscriber<String> cancelledSubscriber = subscribe(new TubeConfiguration(), cancelledTubeReference);
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicBoolean terminated = new AtomicBoolean();
        Tube<String> cancelledTube = cancelledTubeReference.get()
                .whenCancelled(() -> cancelled.set(true))
                .whenTerminates(() -> terminated.set(true));

        cancelledSubscriber.cancel();

        assertTrue(cancelledTube.cancelled());
        assertTrue(cancelled.get());
        assertTrue(terminated.get());
    }

    private static <T> List<T> collect(Flow.Publisher<T> publisher) throws Exception {
        return PublisherHelpers.collectToList(publisher)
                .toCompletableFuture()
                .get(TIMEOUT_SECONDS, SECONDS);
    }

    private static <T> RecordingSubscriber<T> subscribe(
            TubeConfiguration configuration, AtomicReference<Tube<T>> tubeReference) {
        Flow.Publisher<T> publisher = ZeroPublisher.create(configuration, tubeReference::set);
        RecordingSubscriber<T> subscriber = new RecordingSubscriber<>();
        publisher.subscribe(subscriber);
        assertNotNull(tubeReference.get());
        return subscriber;
    }

    private static final class RecordingSubscriber<T> implements Flow.Subscriber<T> {
        private final List<T> items = Collections.synchronizedList(new ArrayList<>());
        private final CompletableFuture<Void> terminated = new CompletableFuture<>();
        private volatile Flow.Subscription subscription;
        private volatile Throwable failure;

        @Override
        public void onSubscribe(Flow.Subscription newSubscription) {
            subscription = newSubscription;
        }

        @Override
        public void onNext(T item) {
            items.add(item);
        }

        @Override
        public void onError(Throwable throwable) {
            failure = throwable;
            terminated.complete(null);
        }

        @Override
        public void onComplete() {
            terminated.complete(null);
        }

        private void request(long count) {
            assertNotNull(subscription);
            subscription.request(count);
        }

        private void cancel() {
            assertNotNull(subscription);
            subscription.cancel();
        }

        private List<T> items() {
            synchronized (items) {
                return List.copyOf(items);
            }
        }

        private void awaitCompletion() throws Exception {
            terminated.get(TIMEOUT_SECONDS, SECONDS);
            assertNull(failure, "Expected completion but received " + failure);
        }

        private Throwable awaitFailure() throws Exception {
            terminated.get(TIMEOUT_SECONDS, SECONDS);
            assertNotNull(failure);
            return failure;
        }
    }
}
