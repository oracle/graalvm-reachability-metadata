/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_guava

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.guava.asDeferred
import kotlinx.coroutines.guava.asListenableFuture
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

public class Kotlinx_coroutines_guavaTest {
    @Test
    fun futureBuilderCompletesWithValueAndNotifiesListeners(): Unit = runBlocking {
        val listenerCalled: AtomicBoolean = AtomicBoolean(false)
        val listenable = future(context = Dispatchers.Default + CoroutineName("guava-bridge")) {
            assertThat(coroutineContext[CoroutineName]?.name).isEqualTo("guava-bridge")
            "computed-value"
        }

        listenable.addListener({ listenerCalled.set(true) }, MoreExecutors.directExecutor())

        assertThat(listenable.get(5, TimeUnit.SECONDS)).isEqualTo("computed-value")
        assertThat(listenerCalled).isTrue()
        assertThat(listenable.isDone).isTrue()
        assertThat(listenable.isCancelled).isFalse()
    }

    @Test
    fun futureBuilderPropagatesExceptionsToListenableFuture(): Unit {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val failure: IllegalStateException = IllegalStateException("coroutine failed")
            val listenable = scope.future<Int> { throw failure }

            assertThatThrownBy { listenable.get(5, TimeUnit.SECONDS) }
                .isInstanceOf(ExecutionException::class.java)
                .hasCause(failure)
            assertThat(listenable.isDone).isTrue()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun cancellingFutureBuilderResultCancelsCoroutineAndRunsFinally(): Unit = runBlocking {
        val started: CompletableDeferred<Unit> = CompletableDeferred()
        val finalized: CompletableDeferred<String> = CompletableDeferred()
        val listenable = future(context = Dispatchers.Default) {
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                finalized.complete("cancelled")
            }
        }

        withTimeout(5_000) { started.await() }
        assertThat(listenable.cancel(true)).isTrue()

        assertThat(withTimeout(5_000) { finalized.await() }).isEqualTo("cancelled")
        assertThat(listenable.isCancelled).isTrue()
        assertThat(listenable.isDone).isTrue()
    }

    @Test
    fun futureBuilderRejectsLazyStart(): Unit = runBlocking {
        assertThatThrownBy {
            future(start = CoroutineStart.LAZY) { "never started" }
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("LAZY start is not supported")
    }

    @Test
    fun cancellingParentScopeCancelsFutureBuilderResult(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val started: CompletableDeferred<Unit> = CompletableDeferred()
        val listenable = scope.future {
            started.complete(Unit)
            awaitCancellation()
        }

        try {
            withTimeout(5_000) { started.await() }
            scope.cancel()

            assertThatThrownBy { listenable.get(5, TimeUnit.SECONDS) }
                .isInstanceOf(CancellationException::class.java)
            assertThat(listenable.isCancelled).isTrue()
            assertThat(listenable.isDone).isTrue()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun awaitReturnsImmediateAndDelayedFutureResults(): Unit = runBlocking {
        val immediate = Futures.immediateFuture("already-complete")
        assertThat(immediate.await()).isEqualTo("already-complete")

        val pending: SettableFuture<String> = SettableFuture.create()
        val deferred = async { pending.await() }
        yield()

        assertThat(deferred.isCompleted).isFalse()
        assertThat(pending.set("completed-later")).isTrue()
        assertThat(withTimeout(5_000) { deferred.await() }).isEqualTo("completed-later")
    }

    @Test
    fun awaitUnwrapsFailedAndCancelledListenableFutures(): Unit = runBlocking {
        val failure: IllegalArgumentException = IllegalArgumentException("bad future")
        val failed = Futures.immediateFailedFuture<String>(failure)
        val failedThrowable: Throwable? = catchSuspend { failed.await() }

        assertThat(failedThrowable).isSameAs(failure)

        val cancelled: SettableFuture<String> = SettableFuture.create()
        assertThat(cancelled.cancel(false)).isTrue()
        val cancelledThrowable: Throwable? = catchSuspend { cancelled.await() }

        assertThat(cancelledThrowable).isInstanceOf(CancellationException::class.java)
    }

    @Test
    fun cancellingCoroutineAwaitCancelsUnderlyingListenableFuture(): Unit = runBlocking {
        val pending: SettableFuture<String> = SettableFuture.create()
        val job = launch { pending.await() }
        yield()

        assertThat(pending.isCancelled).isFalse()
        job.cancelAndJoin()

        assertThat(pending.isCancelled).isTrue()
    }

    @Test
    fun cancellingCoroutineAwaitingNonCancellationPropagatingFutureLeavesSourceUsable(): Unit = runBlocking {
        val source: SettableFuture<String> = SettableFuture.create()
        val nonPropagating = Futures.nonCancellationPropagating(source)
        val job = launch { nonPropagating.await() }
        yield()

        job.cancelAndJoin()

        assertThat(nonPropagating.isCancelled).isTrue()
        assertThat(source.isCancelled).isFalse()
        assertThat(source.isDone).isFalse()
        assertThat(source.set("completed-after-await-cancelled")).isTrue()
        assertThat(source.get(5, TimeUnit.SECONDS)).isEqualTo("completed-after-await-cancelled")
    }

    @Test
    fun asDeferredMirrorsListenableFutureCompletionFailureAndCancellation(): Unit = runBlocking {
        val successfulSource: SettableFuture<String> = SettableFuture.create()
        val successfulDeferred = successfulSource.asDeferred()
        assertThat(successfulSource.set("from-guava")).isTrue()
        assertThat(withTimeout(5_000) { successfulDeferred.await() }).isEqualTo("from-guava")

        val failure: IllegalStateException = IllegalStateException("source failed")
        val failedSource: SettableFuture<String> = SettableFuture.create()
        val failedDeferred = failedSource.asDeferred()
        assertThat(failedSource.setException(failure)).isTrue()
        assertThat(catchSuspend { failedDeferred.await() })
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("source failed")

        val cancelledSource: SettableFuture<String> = SettableFuture.create()
        val cancelledDeferred = cancelledSource.asDeferred()
        assertThat(cancelledSource.cancel(false)).isTrue()
        assertThat(catchSuspend { cancelledDeferred.await() }).isInstanceOf(CancellationException::class.java)
    }

    @Test
    fun cancellingDeferredFromAsDeferredCancelsUnderlyingListenableFuture(): Unit = runBlocking {
        val pending: SettableFuture<String> = SettableFuture.create()
        val deferred = pending.asDeferred()

        deferred.cancel()

        assertThat(pending.isCancelled).isTrue()
        assertThat(catchSuspend { deferred.await() }).isInstanceOf(CancellationException::class.java)
    }

    @Test
    fun asListenableFutureMirrorsDeferredCompletionFailureAndCancellation(): Unit = runBlocking {
        val listenerCalled: AtomicBoolean = AtomicBoolean(false)
        val successfulDeferred: CompletableDeferred<String> = CompletableDeferred()
        val successfulFuture = successfulDeferred.asListenableFuture()
        successfulFuture.addListener({ listenerCalled.set(true) }, MoreExecutors.directExecutor())

        assertThat(successfulDeferred.complete("from-deferred")).isTrue()
        assertThat(successfulFuture.get(5, TimeUnit.SECONDS)).isEqualTo("from-deferred")
        assertThat(listenerCalled).isTrue()
        assertThat(successfulFuture.isDone).isTrue()
        assertThat(successfulFuture.isCancelled).isFalse()

        val failure: IllegalArgumentException = IllegalArgumentException("deferred failed")
        val failedDeferred: CompletableDeferred<String> = CompletableDeferred()
        val failedFuture = failedDeferred.asListenableFuture()
        assertThat(failedDeferred.completeExceptionally(failure)).isTrue()
        assertThatThrownBy { failedFuture.get(5, TimeUnit.SECONDS) }
            .isInstanceOf(ExecutionException::class.java)
            .hasCause(failure)

        val cancelledDeferred: CompletableDeferred<String> = CompletableDeferred()
        val cancelledFuture = cancelledDeferred.asListenableFuture()
        cancelledDeferred.cancel()
        assertThat(cancelledFuture.isCancelled).isTrue()
        assertThatThrownBy { cancelledFuture.get(5, TimeUnit.SECONDS) }
            .isInstanceOf(CancellationException::class.java)
    }

    @Test
    fun cancellingListenableFutureFromAsListenableFutureCancelsUnderlyingDeferred(): Unit = runBlocking {
        val deferred: CompletableDeferred<String> = CompletableDeferred()
        val listenable = deferred.asListenableFuture()

        assertThat(listenable.cancel(false)).isTrue()

        assertThat(deferred.isCancelled).isTrue()
        assertThat(listenable.isCancelled).isTrue()
    }

    private suspend fun catchSuspend(block: suspend () -> Unit): Throwable? {
        return try {
            block()
            null
        } catch (throwable: Throwable) {
            throwable
        }
    }
}
