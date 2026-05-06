/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_jdk8

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.stream.consumeAsFlow
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.stream.Stream

@OptIn(ExperimentalCoroutinesApi::class)
public class KotlinxCoroutinesJdk8Test {
    @Test
    fun futureBuilderRunsCoroutineAndCompletesFuture(): Unit {
        val dispatcher = Executors.newSingleThreadExecutor { runnable: Runnable ->
            Thread(runnable, "coroutines-jdk8-future-test")
        }.asCoroutineDispatcher()

        try {
            val observedThreadName: AtomicReference<String> = AtomicReference()
            val future: CompletableFuture<String> = CoroutineScope(dispatcher + CoroutineName("jdk8-future"))
                .future {
                    observedThreadName.set(Thread.currentThread().name)
                    delay(1)
                    "completed from coroutine"
                }

            assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("completed from coroutine")
            assertThat(future.isDone).isTrue()
            assertThat(observedThreadName.get()).startsWith("coroutines-jdk8-future-test")
        } finally {
            dispatcher.close()
        }
    }

    @Test
    fun futureBuilderRejectsLazyStart(): Unit {
        assertThatThrownBy {
            CoroutineScope(Job()).future(start = CoroutineStart.LAZY) {
                "never started"
            }
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("LAZY")
    }

    @Test
    fun cancellingFutureReturnedByBuilderCancelsCoroutine(): Unit {
        val dispatcher = Executors.newSingleThreadExecutor { runnable: Runnable ->
            Thread(runnable, "coroutines-jdk8-cancel-test")
        }.asCoroutineDispatcher()

        try {
            val coroutineStarted: CompletableFuture<Unit> = CompletableFuture()
            val cancellationObserved: CompletableFuture<Boolean> = CompletableFuture()
            val future: CompletableFuture<String> = CoroutineScope(dispatcher).future {
                coroutineStarted.complete(Unit)
                try {
                    delay(TimeUnit.SECONDS.toMillis(30))
                    "unreachable"
                } finally {
                    cancellationObserved.complete(true)
                }
            }

            coroutineStarted.get(5, TimeUnit.SECONDS)
            assertThat(future.cancel(true)).isTrue()

            assertThatThrownBy { future.join() }
                .isInstanceOf(CancellationException::class.java)
            assertThat(cancellationObserved.get(5, TimeUnit.SECONDS)).isTrue()
        } finally {
            dispatcher.close()
        }
    }

    @Test
    fun awaitReadsSuccessfulCompletionStageResult(): Unit = runBlocking {
        val future: CompletableFuture<String> = CompletableFuture.completedFuture("stage result")

        assertThat(future.await()).isEqualTo("stage result")
    }

    @Test
    fun awaitUnwrapsExceptionalCompletionStageResult(): Unit {
        val cause: IllegalStateException = IllegalStateException("stage failed")
        val future: CompletableFuture<String> = CompletableFuture()
        future.completeExceptionally(cause)

        val thrown: IllegalStateException = assertThrows<IllegalStateException> {
            runBlocking {
                future.await()
            }
        }
        assertThat(thrown).isSameAs(cause)
    }

    @Test
    fun cancellingCoroutineAwaitingFutureCancelsUnderlyingFuture(): Unit = runBlocking {
        val future: CompletableFuture<String> = CompletableFuture()
        val awaiting: Deferred<String> = async(start = CoroutineStart.UNDISPATCHED) {
            future.await()
        }

        awaiting.cancelAndJoin()

        assertThat(awaiting.isCancelled).isTrue()
        assertThat(future.isCancelled).isTrue()
    }

    @Test
    fun completionStageAsDeferredMirrorsSuccessAndFailure(): Unit {
        val successfulStage: CompletableFuture<Int> = CompletableFuture()
        val successfulDeferred: Deferred<Int> = successfulStage.asDeferred()
        successfulStage.complete(42)

        assertThat(runBlocking { successfulDeferred.await() }).isEqualTo(42)

        val cause: IllegalArgumentException = IllegalArgumentException("deferred stage failed")
        val failedStage: CompletableFuture<String> = CompletableFuture()
        val failedDeferred: Deferred<String> = failedStage.asDeferred()
        failedStage.completeExceptionally(cause)

        val thrown: IllegalArgumentException = assertThrows<IllegalArgumentException> {
            runBlocking {
                failedDeferred.await()
            }
        }
        assertThat(thrown).hasMessage(cause.message)
    }

    @Test
    fun cancellingDeferredAdaptedFromCompletionStageCancelsSourceFuture(): Unit {
        val future: CompletableFuture<String> = CompletableFuture()
        val deferred: Deferred<String> = future.asDeferred()

        deferred.cancel()

        assertThat(deferred.isCancelled).isTrue()
        assertThat(future.isCancelled).isTrue()
    }

    @Test
    fun deferredAsCompletableFutureMirrorsSuccessfulResult(): Unit {
        val deferred: CompletableDeferred<String> = CompletableDeferred()
        val future: CompletableFuture<String> = deferred.asCompletableFuture()

        assertThat(deferred.complete("deferred result")).isTrue()

        assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("deferred result")
        assertThat(deferred.isCompleted).isTrue()
    }

    @Test
    fun deferredAsCompletableFutureMirrorsExceptionalResult(): Unit {
        val cause: IllegalArgumentException = IllegalArgumentException("deferred failed")
        val deferred: CompletableDeferred<String> = CompletableDeferred()
        val future: CompletableFuture<String> = deferred.asCompletableFuture()

        deferred.completeExceptionally(cause)

        assertThatThrownBy { future.join() }
            .isInstanceOf(CompletionException::class.java)
            .hasCause(cause)
    }

    @Test
    fun cancellingCompletableFutureAdaptedFromDeferredCancelsDeferred(): Unit {
        val deferred: CompletableDeferred<String> = CompletableDeferred()
        val future: CompletableFuture<String> = deferred.asCompletableFuture()

        assertThat(future.cancel(true)).isTrue()

        assertThat(future.isCancelled).isTrue()
        assertThat(deferred.isCancelled).isTrue()
    }

    @Test
    fun jobAsCompletableFutureCompletesWithUnit(): Unit {
        val job: CompletableJob = Job()
        val future: CompletableFuture<Unit> = job.asCompletableFuture()

        assertThat(job.complete()).isTrue()

        assertThat(future.get(5, TimeUnit.SECONDS)).isSameAs(Unit)
        assertThat(future.isDone).isTrue()
    }

    @Test
    fun javaStreamConsumeAsFlowEmitsElementsAndClosesStream(): Unit = runBlocking {
        val closed: AtomicBoolean = AtomicBoolean(false)
        val stream: Stream<String> = Stream.of("first", "second", "third")
            .onClose { closed.set(true) }

        val collected: List<String> = stream.consumeAsFlow().toList()

        assertThat(collected).containsExactly("first", "second", "third")
        assertThat(closed).isTrue()
    }

    @Test
    fun javaStreamFlowCanBeCollectedOnlyOnce(): Unit {
        val flow = Stream.of(1, 2, 3).consumeAsFlow()

        assertThat(runBlocking { flow.toList() }).containsExactly(1, 2, 3)
        assertThatThrownBy { runBlocking { flow.toList() } }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("collected only once")
    }
}
