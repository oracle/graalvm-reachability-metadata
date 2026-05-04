/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_util_promise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.FailedPromisesException;
import org.osgi.util.promise.Promise;
import org.osgi.util.promise.PromiseFactory;
import org.osgi.util.promise.Promises;
import org.osgi.util.promise.TimeoutException;

public class Org_osgi_util_promiseTest {
    @Test
    void resolvedAndFailedPromisesExposeValuesAndFailures() throws Exception {
        Promise<String> resolved = Promises.resolved("ready");

        assertThat(resolved.isDone()).isTrue();
        assertThat(resolved.getValue()).isEqualTo("ready");
        assertThat(resolved.getFailure()).isNull();

        IllegalArgumentException failure = new IllegalArgumentException("boom");
        Promise<String> failed = Promises.failed(failure);

        assertThat(failed.isDone()).isTrue();
        assertThat(failed.getFailure()).isSameAs(failure);
        assertFailureCause(failed, failure);
    }

    @Test
    void deferredResolvesCallbacksAndRejectsSecondResolution() throws Exception {
        PromiseFactory factory = new PromiseFactory(PromiseFactory.inlineExecutor());
        Deferred<String> deferred = factory.deferred();
        Promise<String> promise = deferred.getPromise();
        AtomicInteger resolvedCallbacks = new AtomicInteger();
        AtomicReference<String> successValue = new AtomicReference<>();
        AtomicInteger failureCallbacks = new AtomicInteger();

        promise.onResolve(resolvedCallbacks::incrementAndGet)
                .onSuccess(successValue::set)
                .onFailure(failure -> failureCallbacks.incrementAndGet());

        assertThat(promise.isDone()).isFalse();
        deferred.resolve("done");

        assertThat(promise.isDone()).isTrue();
        assertThat(promise.getValue()).isEqualTo("done");
        assertThat(resolvedCallbacks).hasValue(1);
        assertThat(successValue).hasValue("done");
        assertThat(failureCallbacks).hasValue(0);

        promise.onResolve(resolvedCallbacks::incrementAndGet);
        assertThat(resolvedCallbacks).hasValue(2);
        assertThatThrownBy(() -> deferred.resolve("again")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> deferred.fail(new IllegalStateException("again")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void promiseChainsTransformFilterAndObserveSuccessfulValues() throws Exception {
        PromiseFactory factory = new PromiseFactory(PromiseFactory.inlineExecutor());
        AtomicReference<String> observedValue = new AtomicReference<>();

        Promise<String> transformed = factory.resolved(4)
                .filter(value -> value > 3)
                .map(value -> value * 2)
                .flatMap(value -> factory.resolved("value-" + value))
                .thenAccept(observedValue::set);

        assertThat(transformed.getValue()).isEqualTo("value-8");
        assertThat(observedValue).hasValue("value-8");

        Promise<Integer> chained = transformed.then(
                success -> factory.resolved(success.getValue().length()),
                failure -> {
                    throw new AssertionError("failure callback should not run");
                });
        assertThat(chained.getValue()).isEqualTo(7);

        Promise<Integer> nullSuccess = factory.resolved("ignored").then(null);
        assertThat(nullSuccess.getValue()).isNull();
    }

    @Test
    void thenFailureCallbackObservesAndControlsChainedFailure() throws Exception {
        PromiseFactory factory = new PromiseFactory(PromiseFactory.inlineExecutor());
        IllegalArgumentException originalFailure = new IllegalArgumentException("original failure");
        Promise<String> failed = factory.failed(originalFailure);
        AtomicReference<Promise<?>> observedPromise = new AtomicReference<>();
        AtomicReference<Throwable> observedFailure = new AtomicReference<>();

        Promise<Integer> chained = failed.then(
                success -> {
                    throw new AssertionError("success callback should not run");
                },
                resolved -> {
                    observedPromise.set(resolved);
                    observedFailure.set(resolved.getFailure());
                });

        assertThat(observedPromise).hasValue(failed);
        assertThat(observedFailure).hasValue(originalFailure);
        assertFailureCause(chained, originalFailure);

        IllegalStateException replacementFailure = new IllegalStateException("replacement failure");
        Promise<Integer> replacement = failed.then(
                success -> {
                    throw new AssertionError("success callback should not run");
                },
                resolved -> {
                    throw replacementFailure;
                });
        assertFailureCause(replacement, replacementFailure);
    }

    @Test
    void promiseChainsPropagateFilteringMappingAndFlatMappingFailures() throws Exception {
        PromiseFactory factory = new PromiseFactory(PromiseFactory.inlineExecutor());

        Promise<Integer> filtered = factory.resolved(2).filter(value -> value > 3);
        assertThat(failureCause(filtered)).isInstanceOf(NoSuchElementException.class);

        IllegalStateException mapperFailure = new IllegalStateException("mapper failed");
        Promise<Integer> mapped = factory.resolved(2).map(value -> {
            throw mapperFailure;
        });
        assertFailureCause(mapped, mapperFailure);

        IllegalArgumentException flatMapperFailure = new IllegalArgumentException("flat mapper failed");
        Promise<Integer> flatMapped = factory.resolved(2).flatMap(value -> {
            throw flatMapperFailure;
        });
        assertFailureCause(flatMapped, flatMapperFailure);
    }

    @Test
    void failedPromisesCanRecoverOrFallback() throws Exception {
        PromiseFactory factory = new PromiseFactory(PromiseFactory.inlineExecutor());
        IllegalStateException originalFailure = new IllegalStateException("original");
        AtomicReference<Throwable> observedFailure = new AtomicReference<>();
        Promise<String> failed = factory.<String>failed(originalFailure).onFailure(observedFailure::set);

        assertThat(failed.recover(promise -> "recovered").getValue()).isEqualTo("recovered");
        assertThat(failed.recoverWith(promise -> factory.resolved(null)).getValue()).isNull();
        assertThat(failed.fallbackTo(factory.resolved("fallback")).getValue()).isEqualTo("fallback");
        assertThat(observedFailure).hasValue(originalFailure);

        Promise<String> nullRecovery = failed.recover(promise -> null);
        assertFailureCause(nullRecovery, originalFailure);

        Promise<String> failedFallback = failed.fallbackTo(factory.failed(new IllegalArgumentException("fallback")));
        assertFailureCause(failedFallback, originalFailure);
    }

    @Test
    void allPromisesAndCollectorPreserveOrderingAndReportAllFailures() throws Exception {
        PromiseFactory factory = new PromiseFactory(PromiseFactory.inlineExecutor());

        Promise<List<String>> all = Promises.all(
                factory.resolved("first"),
                factory.resolved("second"),
                factory.resolved("third"));
        assertThat(all.getValue()).containsExactly("first", "second", "third");

        Promise<List<String>> collected = Stream.of(factory.resolved("a"), factory.resolved("b"))
                .collect(factory.<String, String>toPromise());
        assertThat(collected.getValue()).containsExactly("a", "b");
        assertThat(factory.<String, String>all(Collections.emptyList()).getValue()).isEmpty();

        Promise<String> failedOne = factory.failed(new IllegalArgumentException("one"));
        Promise<String> failedTwo = factory.failed(new IllegalStateException("two"));
        Promise<List<String>> failedAll = factory.all(Arrays.asList(factory.resolved("ok"), failedOne, failedTwo));

        Throwable failure = failureCause(failedAll);
        assertThat(failure).isInstanceOf(FailedPromisesException.class);
        assertThat(((FailedPromisesException) failure).getFailedPromises()).containsExactly(failedOne, failedTwo);
        assertThat(failure.getCause()).isSameAs(failedOne.getFailure());
    }

    @Test
    void factorySubmitsTasksAndBridgesCompletionStagesAndPromises() throws Exception {
        ExecutorService callbackExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
        try {
            PromiseFactory factory = new PromiseFactory(callbackExecutor, scheduledExecutor);

            Promise<Integer> submitted = factory.submit(() -> 21 * 2);
            assertThat(submitted.getValue()).isEqualTo(42);

            CompletableFuture<String> stage = new CompletableFuture<>();
            Promise<String> fromStage = factory.resolvedWith(stage);
            stage.complete("from stage");
            assertThat(fromStage.getValue()).isEqualTo("from stage");

            Deferred<Integer> deferred = factory.deferred();
            Promise<Integer> mirrored = factory.resolvedWith(deferred.getPromise());
            deferred.resolve(5);
            assertThat(mirrored.getValue()).isEqualTo(5);

            assertThat(factory.resolved("completion stage")
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(1, TimeUnit.SECONDS)).isEqualTo("completion stage");
        } finally {
            callbackExecutor.shutdownNow();
            scheduledExecutor.shutdownNow();
        }
    }

    @Test
    void completionStageBridgesExceptionalCompletionInBothDirections() throws Exception {
        PromiseFactory factory = new PromiseFactory(PromiseFactory.inlineExecutor());
        IllegalStateException stageFailure = new IllegalStateException("stage failed");
        CompletableFuture<String> stage = new CompletableFuture<>();

        Promise<String> fromStage = factory.resolvedWith(stage);
        stage.completeExceptionally(stageFailure);
        assertFailureCause(fromStage, stageFailure);

        IllegalArgumentException promiseFailure = new IllegalArgumentException("promise failed");
        CompletableFuture<String> fromPromise = factory.<String>failed(promiseFailure)
                .toCompletionStage()
                .toCompletableFuture();

        assertThatThrownBy(() -> fromPromise.get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCause(promiseFailure);
    }

    @Test
    void deferredCanResolveFromPromisesAndCompletionStages() throws Exception {
        PromiseFactory factory = new PromiseFactory(PromiseFactory.inlineExecutor());

        Deferred<String> fromPromise = factory.deferred();
        Promise<Void> promiseResolution = fromPromise.resolveWith(factory.resolved("mirrored"));
        assertThat(promiseResolution.getValue()).isNull();
        assertThat(fromPromise.getPromise().getValue()).isEqualTo("mirrored");

        Deferred<Integer> fromStage = factory.deferred();
        CompletableFuture<Integer> stage = new CompletableFuture<>();
        Promise<Void> stageResolution = fromStage.resolveWith(stage);
        stage.complete(9);
        assertThat(stageResolution.getValue()).isNull();
        assertThat(fromStage.getPromise().getValue()).isEqualTo(9);

        Deferred<String> alreadyResolved = factory.deferred();
        alreadyResolved.resolve("first");
        Promise<Void> failedResolution = alreadyResolved.resolveWith(factory.resolved("second"));
        assertThat(failureCause(failedResolution)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void timeoutAndDelayUseScheduledExecutor() throws Exception {
        ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
        try {
            PromiseFactory factory = new PromiseFactory(PromiseFactory.inlineExecutor(), scheduledExecutor);

            Promise<String> timedOut = factory.<String>deferred().getPromise().timeout(25);
            assertThat(failureCause(timedOut)).isInstanceOf(TimeoutException.class);

            Promise<String> delayed = factory.resolved("later").delay(10);
            assertThat(delayed.getValue()).isEqualTo("later");
        } finally {
            scheduledExecutor.shutdownNow();
        }
    }

    @Test
    void callbacksExecutorThreadOptionDispatchesCallbacksThroughExecutor() throws Exception {
        ExecutorService callbackExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "promise-callback-test");
            thread.setDaemon(true);
            return thread;
        });
        ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
        try {
            PromiseFactory factory = new PromiseFactory(
                    callbackExecutor,
                    scheduledExecutor,
                    PromiseFactory.Option.CALLBACKS_EXECUTOR_THREAD);
            CountDownLatch callbackCalled = new CountDownLatch(1);
            AtomicReference<String> callbackThreadName = new AtomicReference<>();

            factory.resolved("value").onSuccess(value -> {
                callbackThreadName.set(Thread.currentThread().getName());
                callbackCalled.countDown();
            });

            assertThat(callbackCalled.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(callbackThreadName).hasValue("promise-callback-test");
        } finally {
            callbackExecutor.shutdownNow();
            scheduledExecutor.shutdownNow();
        }
    }

    private static Throwable failureCause(Promise<?> promise) throws Exception {
        try {
            promise.getValue();
        } catch (InvocationTargetException e) {
            return e.getCause();
        }
        throw new AssertionError("Expected promise to fail");
    }

    private static void assertFailureCause(Promise<?> promise, Throwable expectedFailure) throws Exception {
        assertThat(promise.getFailure()).isSameAs(expectedFailure);
        assertThat(failureCause(promise)).isSameAs(expectedFailure);
    }
}
