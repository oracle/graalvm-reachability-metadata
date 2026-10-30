/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang_modules.scala_java8_compat_2_13;

import org.junit.jupiter.api.Test;
import scala.compat.java8.FutureConverters;
import scala.concurrent.Await;
import scala.concurrent.ExecutionContextExecutor;
import scala.concurrent.ExecutionContextExecutorService;
import scala.concurrent.Future;
import scala.concurrent.Promise;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FutureConvertersTest {
    @Test
    void propagatesJavaCompletionToScalaAndPreservesRoundTripIdentity() throws Exception {
        CompletableFuture<String> source = new CompletableFuture<>();
        Future<String> converted = FutureConverters.toScala(source);
        assertFalse(converted.isCompleted());
        assertSame(source, FutureConverters.toJava(converted));

        source.complete("ready");
        assertEquals("ready", Await.result(converted, new FiniteDuration(10, TimeUnit.SECONDS)));
    }

    @Test
    void propagatesScalaCompletionAndJavaTransformations() throws Exception {
        Promise<String> promise = FutureConverters.promise();
        CompletionStage<String> converted = FutureConverters.toJava(promise.future());
        assertSame(promise.future(), FutureConverters.toScala(converted));
        CompletionStage<String> transformed = converted.thenApply(String::toUpperCase)
                .thenCombine(CompletableFuture.completedFuture("!"), (value, suffix) -> value + suffix)
                .thenCompose(value -> CompletableFuture.completedFuture(value + " done"));

        promise.success("ready");
        assertEquals("READY! done", transformed.toCompletableFuture().get(10, TimeUnit.SECONDS));
    }

    @Test
    void propagatesJavaFailureToScala() {
        CompletableFuture<String> source = new CompletableFuture<>();
        Future<String> converted = FutureConverters.toScala(source);
        IllegalArgumentException failure = new IllegalArgumentException("invalid request");
        source.completeExceptionally(failure);

        IllegalArgumentException actual = assertThrows(IllegalArgumentException.class,
                () -> Await.result(converted, new FiniteDuration(10, TimeUnit.SECONDS)));
        assertSame(failure, actual);
    }

    @Test
    void propagatesScalaFailureAndSupportsJavaRecovery() throws Exception {
        Promise<String> promise = FutureConverters.promise();
        CompletionStage<String> converted = FutureConverters.toJava(promise.future());
        IllegalStateException failure = new IllegalStateException("unavailable");
        CompletionStage<String> recovered = converted.exceptionally(error -> "recovered: " + error.getMessage());
        promise.failure(failure);

        ExecutionException actual = assertThrows(ExecutionException.class,
                () -> converted.toCompletableFuture().get(10, TimeUnit.SECONDS));
        assertSame(failure, actual.getCause());
        assertEquals("recovered: unavailable", recovered.toCompletableFuture().get(10, TimeUnit.SECONDS));
    }

    @Test
    void createsCompletedPromises() throws Exception {
        Promise<String> success = FutureConverters.keptPromise("stored");
        assertEquals("stored", Await.result(success.future(), new FiniteDuration(10, TimeUnit.SECONDS)));

        IllegalArgumentException failure = new IllegalArgumentException("rejected");
        Promise<String> failed = FutureConverters.failedPromise(failure);
        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> Await.result(failed.future(), new FiniteDuration(10, TimeUnit.SECONDS))));
    }

    @Test
    void adaptsAnExecutorAndReportsErrors() {
        AtomicReference<Throwable> reported = new AtomicReference<>();
        ExecutionContextExecutor context = FutureConverters.fromExecutor(Runnable::run, reported::set);
        AtomicReference<String> result = new AtomicReference<>();
        context.execute(() -> result.set("executed"));
        assertEquals("executed", result.get());

        IllegalStateException failure = new IllegalStateException("task error");
        context.reportFailure(failure);
        assertSame(failure, reported.get());
    }

    @Test
    void adaptsAnExecutorService() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            ExecutionContextExecutorService context = FutureConverters.fromExecutorService(executor);
            CompletableFuture<String> result = new CompletableFuture<>();
            context.execute(() -> result.complete("executed"));
            assertEquals("executed", result.get(10, TimeUnit.SECONDS));
            context.shutdown();
            assertTrue(context.awaitTermination(10, TimeUnit.SECONDS));
            assertTrue(executor.isShutdown());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
