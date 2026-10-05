/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_reactive.mutiny_zero;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.IntStream;
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

public class MutinyZeroTest {
    @Test
    void createsPublishersFromValuesAndLazySources() throws Exception {
        assertThat(collect(ZeroPublisher.fromItems("alpha", "beta"))).containsExactly("alpha", "beta");
        assertThat(collect(ZeroPublisher.fromIterable(List.of(1, 2, 3)))).containsExactly(1, 2, 3);

        Flow.Publisher<Integer> generated = ZeroPublisher.fromGenerator(
                () -> 4, count -> IntStream.rangeClosed(1, count).iterator());
        assertThat(collect(generated)).containsExactly(1, 2, 3, 4);

        AtomicInteger streamSubscriptions = new AtomicInteger();
        Flow.Publisher<String> streamed = ZeroPublisher.fromStream(() -> {
            streamSubscriptions.incrementAndGet();
            return Stream.of("one", "two");
        });
        assertThat(collect(streamed)).containsExactly("one", "two");
        assertThat(collect(streamed)).containsExactly("one", "two");
        assertThat(streamSubscriptions).hasValue(2);
    }

    @Test
    void bridgesPublishersAndCompletionStages() throws Exception {
        Flow.Publisher<String> publisher = ZeroPublisher.fromCompletionStage(
                () -> CompletableFuture.completedFuture("ready"));
        assertThat(collect(publisher)).containsExactly("ready");

        Optional<String> first = await(ZeroPublisher.toCompletionStage(ZeroPublisher.fromItems("first", "second")));
        Optional<String> empty = await(ZeroPublisher.toCompletionStage(ZeroPublisher.empty()));
        assertThat(first).contains("first");
        assertThat(empty).isEmpty();

        IllegalStateException failure = new IllegalStateException("unavailable");
        assertThat(awaitFailure(PublisherHelpers.collectToList(ZeroPublisher.fromFailure(failure))))
                .isSameAs(failure);
    }

    @Test
    void composesTransformationSelectionSpreadingAndConcatenation() throws Exception {
        Flow.Publisher<Integer> transformed = new Transform<>(ZeroPublisher.fromItems(1, 2, 3), value -> value * 10);
        Flow.Publisher<Integer> selected = new Select<>(transformed, value -> value >= 20);
        Flow.Publisher<String> spread = new Spread<>(selected,
                value -> ZeroPublisher.fromItems(value + "a", value + "b"), 2, 2);
        Flow.Publisher<String> concatenated = new Concatenate<>(
                List.of(ZeroPublisher.fromItems("start"), spread, ZeroPublisher.empty()));

        assertThat(collect(concatenated)).containsExactly("start", "20a", "20b", "30a", "30b");
    }

    @Test
    void recoversFromFailuresAndRetriesSubscriptions() throws Exception {
        Flow.Publisher<String> recovered = new Recover<>(
                ZeroPublisher.fromFailure(new IllegalArgumentException("rejected")),
                failure -> "fallback:" + failure.getMessage());
        assertThat(collect(recovered)).containsExactly("fallback:rejected");

        AtomicInteger boundedAttempts = new AtomicInteger();
        Flow.Publisher<String> boundedRetry = new Retry<>(
                succeedsAfter(boundedAttempts, 2, "bounded"), Retry.atMost(2));
        assertThat(collect(boundedRetry)).containsExactly("bounded");
        assertThat(boundedAttempts).hasValue(3);

        AtomicInteger unlimitedAttempts = new AtomicInteger();
        Flow.Publisher<String> unlimitedRetry = new Retry<>(
                succeedsAfter(unlimitedAttempts, 1, "unlimited"), Retry.always());
        assertThat(collect(unlimitedRetry)).containsExactly("unlimited");
        assertThat(unlimitedAttempts).hasValue(2);
    }

    @Test
    void mapsAndComposesAsynchronousFailures() throws Exception {
        AtomicBoolean successMapperCalled = new AtomicBoolean();
        CompletionStage<String> unchanged = AsyncHelpers.applyExceptionally(
                CompletableFuture.completedFuture("ok"), failure -> {
                    successMapperCalled.set(true);
                    return failure;
                });
        assertThat(await(unchanged)).isEqualTo("ok");
        assertThat(successMapperCalled).isFalse();

        IllegalArgumentException original = new IllegalArgumentException("invalid");
        IllegalStateException mapped = new IllegalStateException("mapped");
        CompletionStage<String> mappedFailure = AsyncHelpers.applyExceptionally(
                CompletableFuture.failedFuture(original), failure -> mapped);
        assertThat(awaitFailure(mappedFailure)).isSameAs(mapped);

        CompletionStage<String> recovered = AsyncHelpers.composeExceptionally(
                CompletableFuture.failedFuture(original),
                failure -> CompletableFuture.completedFuture("recovered:" + failure.getMessage()));
        assertThat(await(recovered)).isEqualTo("recovered:invalid");
    }

    @Test
    void buffersTubeItemsUntilDemandAndRunsLifecycleCallbacks() throws Exception {
        TubeConfiguration configuration = new TubeConfiguration()
                .withBackpressureStrategy(BackpressureStrategy.BUFFER)
                .withBufferSize(2);
        assertThat(configuration.getBackpressureStrategy()).isEqualTo(BackpressureStrategy.BUFFER);
        assertThat(configuration.getBufferSize()).isEqualTo(2);

        AtomicLong requested = new AtomicLong();
        AtomicBoolean terminated = new AtomicBoolean();
        AtomicReference<Tube<String>> tubeReference = new AtomicReference<>();
        ProbeSubscriber<String> subscriber = subscribe(configuration, tube -> {
            tubeReference.set(tube);
            tube.whenRequested(requested::addAndGet)
                    .whenTerminates(() -> terminated.set(true))
                    .send("first")
                    .send("second")
                    .complete();
        });

        assertThat(subscriber.items()).isEmpty();
        subscriber.request(2);
        subscriber.awaitDone();
        assertThat(subscriber.items()).containsExactly("first", "second");
        assertThat(requested).hasValue(2);
        assertThat(terminated).isTrue();
        assertThat(tubeReference.get().cancelled()).isTrue();
        assertThat(tubeReference.get().outstandingRequests()).isZero();
    }

    @Test
    void appliesEachTubeOverflowStrategy() throws Exception {
        ProbeSubscriber<String> unbounded = subscribe(
                configuration(BackpressureStrategy.UNBOUNDED_BUFFER, -1),
                tube -> tube.send("one").send("two").complete());
        unbounded.request(2);
        unbounded.awaitDone();
        assertThat(unbounded.items()).containsExactly("one", "two");

        ProbeSubscriber<String> latest = subscribe(
                configuration(BackpressureStrategy.LATEST, 1),
                tube -> tube.send("old").send("new").complete());
        latest.request(1);
        latest.awaitDone();
        assertThat(latest.items()).containsExactly("new");

        ProbeSubscriber<String> dropped = subscribe(new TubeConfiguration(),
                tube -> tube.send("dropped").complete());
        dropped.awaitDone();
        assertThat(dropped.items()).isEmpty();

        ProbeSubscriber<String> ignored = subscribe(
                configuration(BackpressureStrategy.IGNORE, -1),
                tube -> tube.send("delivered").complete());
        ignored.awaitDone();
        assertThat(ignored.items()).containsExactly("delivered");

        ProbeSubscriber<String> errored = subscribe(
                configuration(BackpressureStrategy.ERROR, -1),
                tube -> tube.send("without-demand"));
        assertThat(errored.awaitFailure())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("without-demand");
    }

    @Test
    void propagatesTubeFailures() throws Exception {
        IllegalStateException failure = new IllegalStateException("tube failed");
        ProbeSubscriber<String> subscriber = subscribe(new TubeConfiguration(), tube -> tube.fail(failure));

        assertThat(subscriber.awaitFailure()).isSameAs(failure);
    }

    @Test
    void notifiesTubeCancellationCallbacks() {
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicBoolean terminated = new AtomicBoolean();
        ProbeSubscriber<String> subscriber = subscribe(new TubeConfiguration(), tube -> tube
                .whenCancelled(() -> cancelled.set(true))
                .whenTerminates(() -> terminated.set(true)));

        subscriber.cancel();
        assertThat(cancelled).isTrue();
        assertThat(terminated).isTrue();
    }

    private static TubeConfiguration configuration(BackpressureStrategy strategy, int bufferSize) {
        return new TubeConfiguration().withBackpressureStrategy(strategy).withBufferSize(bufferSize);
    }

    private static <T> ProbeSubscriber<T> subscribe(
            TubeConfiguration configuration, Consumer<Tube<T>> tubeConsumer) {
        ProbeSubscriber<T> subscriber = new ProbeSubscriber<>();
        ZeroPublisher.create(configuration, tubeConsumer).subscribe(subscriber);
        return subscriber;
    }

    private static Flow.Publisher<String> succeedsAfter(
            AtomicInteger attempts, int failuresBeforeSuccess, String result) {
        return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
            private boolean finished;

            @Override
            public void request(long count) {
                if (finished) {
                    return;
                }
                finished = true;
                if (attempts.incrementAndGet() <= failuresBeforeSuccess) {
                    subscriber.onError(new IllegalStateException("try again"));
                } else {
                    subscriber.onNext(result);
                    subscriber.onComplete();
                }
            }

            @Override
            public void cancel() {
                finished = true;
            }
        });
    }

    private static <T> List<T> collect(Flow.Publisher<T> publisher) throws Exception {
        return await(PublisherHelpers.collectToList(publisher));
    }

    private static <T> T await(CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(10, SECONDS);
    }

    private static Throwable awaitFailure(CompletionStage<?> stage) throws Exception {
        try {
            stage.toCompletableFuture().get(10, SECONDS);
            throw new AssertionError("Expected the completion stage to fail");
        } catch (ExecutionException exception) {
            return exception.getCause();
        }
    }

    private static final class ProbeSubscriber<T> implements Flow.Subscriber<T> {
        private final List<T> items = new ArrayList<>();
        private final CompletableFuture<Void> done = new CompletableFuture<>();
        private final CompletableFuture<Throwable> failure = new CompletableFuture<>();
        private Flow.Subscription subscription;

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
            failure.complete(throwable);
            done.complete(null);
        }

        @Override
        public void onComplete() {
            done.complete(null);
        }

        private void request(long count) {
            subscription.request(count);
        }

        private void cancel() {
            subscription.cancel();
        }

        private List<T> items() {
            return List.copyOf(items);
        }

        private void awaitDone() throws Exception {
            done.get(10, SECONDS);
        }

        private Throwable awaitFailure() throws Exception {
            return failure.get(10, SECONDS);
        }
    }
}
