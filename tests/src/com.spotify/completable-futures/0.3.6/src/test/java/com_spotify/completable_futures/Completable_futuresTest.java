/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_spotify.completable_futures;

import com.spotify.futures.CompletableFutures;
import com.spotify.futures.ConcurrencyReducer;
import com.spotify.futures.Function3;
import com.spotify.futures.Function4;
import com.spotify.futures.Function5;
import com.spotify.futures.Function6;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Completable_futuresTest {
    private static final long TIMEOUT_SECONDS = 2L;

    @Test
    void allAsListPreservesInputOrderAndPropagatesFailures() throws Exception {
        CompletableFuture<String> first = new CompletableFuture<>();
        CompletableFuture<String> second = new CompletableFuture<>();

        CompletableFuture<List<String>> ordered = CompletableFutures.allAsList(Arrays.asList(first, second));
        second.complete("second");
        assertFalse(ordered.isDone());

        first.complete("first");
        assertEquals(Arrays.asList("first", "second"), await(ordered));

        IllegalArgumentException failure = new IllegalArgumentException("boom");
        CompletableFuture<List<String>> failed = CompletableFutures.allAsList(Arrays.asList(
                CompletableFuture.completedFuture("ok"),
                CompletableFutures.exceptionallyCompletedFuture(failure)));

        assertSame(failure, rootCause(assertThrows(CompletionException.class, failed::join)));
    }

    @Test
    void allAsMapAndJoinCollectorsResolveValuesByKey() throws Exception {
        Map<String, CompletionStage<Integer>> futuresByName = new HashMap<>();
        futuresByName.put("one", CompletableFuture.completedFuture(1));
        futuresByName.put("two", CompletableFuture.completedFuture(2));

        Map<String, Integer> valuesByName = await(CompletableFutures.allAsMap(futuresByName));
        assertEquals(2, valuesByName.size());
        assertEquals(1, valuesByName.get("one"));
        assertEquals(2, valuesByName.get("two"));

        CompletableFuture<List<String>> collectedList = Arrays.asList(
                        CompletableFuture.completedFuture("a"), CompletableFuture.completedFuture("b"))
                .stream()
                .collect(CompletableFutures.joinList());
        assertEquals(Arrays.asList("a", "b"), await(collectedList));

        CompletableFuture<Map<Integer, String>> collectedMap = Arrays.asList("x", "yy")
                .stream()
                .collect(CompletableFutures.joinMap(String::length, value -> completed(value.toUpperCase())));
        Map<Integer, String> expected = new HashMap<>();
        expected.put(1, "X");
        expected.put(2, "YY");
        assertEquals(expected, await(collectedMap));
    }

    @Test
    void successfulAsListReplacesFailuresWithFallbackValues() throws Exception {
        IllegalStateException failure = new IllegalStateException("missing");

        CompletableFuture<List<String>> result = CompletableFutures.successfulAsList(
                Arrays.asList(completed("good"), CompletableFutures.exceptionallyCompletedFuture(failure)),
                throwable -> "fallback:" + throwable.getClass().getSimpleName());

        assertEquals(Arrays.asList("good", "fallback:IllegalStateException"), await(result));
    }

    @Test
    void completedFutureUtilitiesExposeValuesAndExceptions() {
        CompletableFuture<String> completed = CompletableFuture.completedFuture("done");
        CompletableFuture<String> incomplete = new CompletableFuture<>();
        IllegalStateException failure = new IllegalStateException("failed");
        CompletableFuture<String> failed = CompletableFutures.exceptionallyCompletedFuture(failure);

        CompletableFutures.checkCompleted(completed);
        assertEquals("done", CompletableFutures.getCompleted(completed));
        assertThrows(IllegalStateException.class, () -> CompletableFutures.checkCompleted(incomplete));
        assertSame(failure, CompletableFutures.getException(failed));
        assertThrows(IllegalStateException.class, () -> CompletableFutures.getException(completed));
    }

    @Test
    void compositionHelpersFlattenSuccessAndFailureStages() throws Exception {
        CompletionStage<String> handledSuccess = CompletableFutures.handleCompose(
                completed("value"),
                (value, error) -> completed(value + ":handled"));
        assertEquals("value:handled", await(handledSuccess));

        IllegalArgumentException failure = new IllegalArgumentException("bad");
        CompletionStage<String> handledFailure = CompletableFutures.handleCompose(
                CompletableFutures.exceptionallyCompletedFuture(failure),
                (value, error) -> completed("recovered:" + error.getClass().getSimpleName()));
        assertEquals("recovered:IllegalArgumentException", await(handledFailure));

        CompletionStage<String> exceptionalRecovery = CompletableFutures.exceptionallyCompose(
                CompletableFutures.exceptionallyCompletedFuture(new RuntimeException("temporary")),
                error -> completed("recovered"));
        assertEquals("recovered", await(exceptionalRecovery));

        CompletionStage<String> supplied = CompletableFutures.supplyAsyncCompose(
                () -> completed("direct"),
                Runnable::run);
        assertEquals("direct", await(supplied));

        CompletionStage<String> dereferenced = CompletableFutures.dereference(completed(completed("nested")));
        assertEquals("nested", await(dereferenced));
    }

    @Test
    void supplyAsyncComposeWithoutExecutorRunsSupplierAndWaitsForReturnedStage() throws Exception {
        CompletableFuture<String> source = new CompletableFuture<>();
        CountDownLatch supplierInvoked = new CountDownLatch(1);

        CompletionStage<String> composed = CompletableFutures.supplyAsyncCompose(() -> {
            supplierInvoked.countDown();
            return source.thenApply(value -> value + ":mapped");
        });

        assertTrue(supplierInvoked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertFalse(composed.toCompletableFuture().isDone());

        source.complete("async");
        assertEquals("async:mapped", await(composed));
    }

    @Test
    void combineSupportsFixedAritiesAndCombinedFuturesAccess() throws Exception {
        CompletionStage<Integer> two = CompletableFutures.combine(completed(1), completed(2), Integer::sum);
        CompletionStage<Integer> three = CompletableFutures.combine(completed(1), completed(2), completed(3),
                (a, b, c) -> a + b + c);
        CompletionStage<Integer> four = CompletableFutures.combine(
                completed(1), completed(2), completed(3), completed(4),
                (a, b, c, d) -> a + b + c + d);
        CompletionStage<Integer> five = CompletableFutures.combine(
                completed(1), completed(2), completed(3), completed(4), completed(5),
                (a, b, c, d, e) -> a + b + c + d + e);
        CompletionStage<Integer> six = CompletableFutures.combine(
                completed(1), completed(2), completed(3), completed(4), completed(5), completed(6),
                (a, b, c, d, e, f) -> a + b + c + d + e + f);

        assertEquals(3, await(two));
        assertEquals(6, await(three));
        assertEquals(10, await(four));
        assertEquals(15, await(five));
        assertEquals(21, await(six));

        CompletableFuture<String> left = CompletableFuture.completedFuture("left");
        CompletableFuture<String> nullValue = CompletableFuture.completedFuture(null);
        CompletionStage<String> combined = CompletableFutures.combine(
                futures -> futures.get(left) + ":" + futures.<String>get(nullValue),
                Arrays.asList(left, nullValue));
        assertEquals("left:null", await(combined));

        CompletableFuture<String> unrelated = CompletableFuture.completedFuture("unrelated");
        CompletionStage<String> invalidLookup = CompletableFutures.combine(
                futures -> futures.get(unrelated),
                Collections.singletonList(left));
        assertInstanceOf(IllegalArgumentException.class,
                rootCause(assertThrows(CompletionException.class, invalidLookup.toCompletableFuture()::join)));
    }

    @Test
    void combineFuturesFlattensFutureReturningCombiners() throws Exception {
        CompletionStage<Integer> two = CompletableFutures.combineFutures(completed(1), completed(2),
                (a, b) -> completed(a + b));
        CompletionStage<Integer> three = CompletableFutures.combineFutures(completed(1), completed(2), completed(3),
                (a, b, c) -> completed(a + b + c));
        CompletionStage<Integer> four = CompletableFutures.combineFutures(completed(1), completed(2), completed(3),
                completed(4), (a, b, c, d) -> completed(a + b + c + d));
        CompletionStage<Integer> five = CompletableFutures.combineFutures(completed(1), completed(2), completed(3),
                completed(4), completed(5), (a, b, c, d, e) -> completed(a + b + c + d + e));
        CompletionStage<Integer> six = CompletableFutures.combineFutures(completed(1), completed(2), completed(3),
                completed(4), completed(5), completed(6), (a, b, c, d, e, f) -> completed(a + b + c + d + e + f));

        assertEquals(3, await(two));
        assertEquals(6, await(three));
        assertEquals(10, await(four));
        assertEquals(15, await(five));
        assertEquals(21, await(six));
    }

    @Test
    void customFunctionInterfacesSupportAndThenComposition() {
        Function3<Integer, Integer, Integer, Integer> sum3 = (a, b, c) -> a + b + c;
        Function4<Integer, Integer, Integer, Integer, Integer> sum4 = (a, b, c, d) -> a + b + c + d;
        Function5<Integer, Integer, Integer, Integer, Integer, Integer> sum5 = (a, b, c, d, e) -> a + b + c + d + e;
        Function6<Integer, Integer, Integer, Integer, Integer, Integer, Integer> sum6 =
                (a, b, c, d, e, f) -> a + b + c + d + e + f;

        assertEquals("6", sum3.andThen(String::valueOf).apply(1, 2, 3));
        assertEquals("10", sum4.andThen(String::valueOf).apply(1, 2, 3, 4));
        assertEquals("15", sum5.andThen(String::valueOf).apply(1, 2, 3, 4, 5));
        assertEquals("21", sum6.andThen(String::valueOf).apply(1, 2, 3, 4, 5, 6));
    }

    @Test
    void pollCompletesWhenSupplierEventuallyReturnsValueAndCancelsOnFailure() throws Exception {
        ScheduledExecutorService successExecutor = Executors.newSingleThreadScheduledExecutor();
        try {
            AtomicInteger attempts = new AtomicInteger();
            CompletableFuture<String> polled = CompletableFutures.poll(
                    () -> attempts.incrementAndGet() >= 3 ? Optional.of("ready") : Optional.empty(),
                    Duration.ofMillis(5),
                    successExecutor);

            assertEquals("ready", await(polled));
            assertTrue(attempts.get() >= 3);
        } finally {
            successExecutor.shutdownNow();
            assertTrue(successExecutor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }

        ScheduledExecutorService failureExecutor = Executors.newSingleThreadScheduledExecutor();
        try {
            IllegalStateException failure = new IllegalStateException("poll failed");
            CompletableFuture<String> failed = CompletableFutures.poll(
                    () -> {
                        throw failure;
                    },
                    Duration.ofMillis(5),
                    failureExecutor);

            assertSame(failure, rootCause(assertThrows(ExecutionException.class, () -> failed.get(
                    TIMEOUT_SECONDS, TimeUnit.SECONDS))));
        } finally {
            failureExecutor.shutdownNow();
            assertTrue(failureExecutor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }
    }

    @Test
    void concurrencyReducerLimitsActiveJobsAndStartsQueuedJobsAfterCompletion() throws Exception {
        ConcurrencyReducer<String> reducer = ConcurrencyReducer.create(2, 2);
        CopyOnWriteArrayList<String> started = new CopyOnWriteArrayList<>();
        CompletableFuture<String> firstSource = new CompletableFuture<>();
        CompletableFuture<String> secondSource = new CompletableFuture<>();
        CompletableFuture<String> thirdSource = new CompletableFuture<>();

        CompletableFuture<String> first = reducer.add(() -> {
            started.add("first");
            return firstSource;
        });
        CompletableFuture<String> second = reducer.add(() -> {
            started.add("second");
            return secondSource;
        });
        CompletableFuture<String> third = reducer.add(() -> {
            started.add("third");
            return thirdSource;
        });

        assertEquals(Arrays.asList("first", "second"), started);
        assertEquals(2, reducer.numActive());
        assertEquals(1, reducer.numQueued());
        assertEquals(0, reducer.remainingActiveCapacity());
        assertEquals(1, reducer.remainingQueueCapacity());

        firstSource.complete("one");
        assertEquals("one", await(first));
        assertEquals(Arrays.asList("first", "second", "third"), started);
        assertEquals(2, reducer.numActive());
        assertEquals(0, reducer.numQueued());

        secondSource.complete("two");
        thirdSource.complete("three");
        assertEquals("two", await(second));
        assertEquals("three", await(third));
        assertEquals(0, reducer.numActive());
        assertEquals(2, reducer.remainingActiveCapacity());
    }

    @Test
    void concurrencyReducerSkipsCancelledQueuedJobsAndContinuesPump() throws Exception {
        ConcurrencyReducer<String> reducer = ConcurrencyReducer.create(1, 3);
        CopyOnWriteArrayList<String> started = new CopyOnWriteArrayList<>();
        CompletableFuture<String> blocker = new CompletableFuture<>();
        CompletableFuture<String> nextSource = new CompletableFuture<>();

        CompletableFuture<String> active = reducer.add(() -> {
            started.add("active");
            return blocker;
        });
        CompletableFuture<String> cancelled = reducer.add(() -> {
            started.add("cancelled");
            return CompletableFuture.completedFuture("cancelled");
        });
        CompletableFuture<String> next = reducer.add(() -> {
            started.add("next");
            return nextSource;
        });

        assertEquals(Collections.singletonList("active"), started);
        assertEquals(1, reducer.numActive());
        assertEquals(2, reducer.numQueued());
        assertTrue(cancelled.cancel(true));

        blocker.complete("done");
        assertEquals("done", await(active));
        assertEquals(Arrays.asList("active", "next"), started);
        assertTrue(cancelled.isCancelled());
        assertEquals(1, reducer.numActive());
        assertEquals(0, reducer.numQueued());

        nextSource.complete("next");
        assertEquals("next", await(next));
        assertEquals(0, reducer.numActive());
    }

    @Test
    void concurrencyReducerReportsCapacityAndJobFailures() throws Exception {
        ConcurrencyReducer<String> reducer = ConcurrencyReducer.create(1, 1);
        CompletableFuture<String> blocker = new CompletableFuture<>();
        CompletableFuture<String> queuedSource = new CompletableFuture<>();

        CompletableFuture<String> active = reducer.add(() -> blocker);
        CompletableFuture<String> queued = reducer.add(() -> queuedSource);
        CompletableFuture<String> rejected = reducer.add(() -> CompletableFuture.completedFuture("rejected"));

        assertInstanceOf(ConcurrencyReducer.CapacityReachedException.class,
                rootCause(assertThrows(CompletionException.class, rejected::join)));

        blocker.complete("active");
        queuedSource.complete("queued");
        assertEquals("active", await(active));
        assertEquals("queued", await(queued));

        IllegalArgumentException thrown = new IllegalArgumentException("call failed");
        CompletableFuture<String> callableFailure = reducer.add(() -> {
            throw thrown;
        });
        assertSame(thrown, rootCause(assertThrows(CompletionException.class, callableFailure::join)));

        CompletableFuture<String> nullStage = reducer.add(() -> null);
        assertInstanceOf(NullPointerException.class,
                rootCause(assertThrows(CompletionException.class, nullStage::join)));

        assertThrows(IllegalArgumentException.class, () -> ConcurrencyReducer.create(0, 1));
        assertThrows(IllegalArgumentException.class, () -> ConcurrencyReducer.create(1, 0));
    }

    private static <T> CompletableFuture<T> completed(T value) {
        return CompletableFuture.completedFuture(value);
    }

    private static <T> T await(CompletionStage<T> stage)
            throws InterruptedException, ExecutionException, TimeoutException {
        return stage.toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException || current instanceof ExecutionException) {
            current = current.getCause();
        }
        assertTrue(current != null);
        return current;
    }
}
