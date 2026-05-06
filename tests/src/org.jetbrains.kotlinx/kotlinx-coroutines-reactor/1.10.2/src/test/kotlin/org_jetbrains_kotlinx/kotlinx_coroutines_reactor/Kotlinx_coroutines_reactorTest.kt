/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_reactor

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactor.ReactorContext
import kotlinx.coroutines.reactor.asCoroutineContext
import kotlinx.coroutines.reactor.asCoroutineDispatcher
import kotlinx.coroutines.reactor.asFlux
import kotlinx.coroutines.reactor.asMono
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.reactor.flux
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.util.context.Context
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

public class Kotlinx_coroutines_reactorTest {
    @Test
    fun monoBuilderIsColdAndPropagatesSubscriberContext(): Unit {
        val subscriptions: AtomicInteger = AtomicInteger()
        val source: Mono<String> = mono(context = CoroutineName("mono-context-test")) {
            val reactorContext: ReactorContext = coroutineContext[ReactorContext]!!
            val requestId: String = reactorContext.context.get("requestId")
            val coroutineName: String = coroutineContext[CoroutineName]!!.name
            "$requestId-$coroutineName-${subscriptions.incrementAndGet()}"
        }

        val first: String? = source
            .contextWrite(Context.of("requestId", "alpha"))
            .block(TIMEOUT)
        val second: String? = source
            .contextWrite(Context.of("requestId", "beta"))
            .block(TIMEOUT)

        assertThat(first).isEqualTo("alpha-mono-context-test-1")
        assertThat(second).isEqualTo("beta-mono-context-test-2")
        assertThat(subscriptions).hasValue(2)
    }

    @Test
    fun monoBuilderHandlesNullFailuresCancellationAndInvalidContext(): Unit = runBlocking {
        val empty: Mono<String> = mono { null }
        assertThat(empty.block(TIMEOUT)).isNull()

        val failure: IllegalStateException = IllegalStateException("boom")
        val failed: Mono<String> = mono { throw failure }
        assertThatThrownBy { failed.block(TIMEOUT) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("boom")

        assertThatThrownBy { mono(context = Job()) { "unused" } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Mono context cannot contain job")

        val started: CompletableDeferred<Unit> = CompletableDeferred()
        val finalized: CompletableDeferred<String> = CompletableDeferred()
        val cancellable: Mono<String> = mono(context = Dispatchers.Default) {
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                finalized.complete("disposed")
            }
        }

        val disposable = cancellable.subscribe()
        withTimeout(5_000) { started.await() }
        disposable.dispose()

        assertThat(withTimeout(5_000) { finalized.await() }).isEqualTo("disposed")
    }

    @Test
    fun awaitSingleReadsValuesEmptyCompletionFailureAndCancellation(): Unit = runBlocking {
        assertThat(Mono.just("value").awaitSingle()).isEqualTo("value")
        assertThat(Mono.empty<String>().awaitSingleOrNull()).isNull()
        assertThat(catchSuspend { Mono.empty<String>().awaitSingle() })
            .isInstanceOf(NoSuchElementException::class.java)

        val failure: IllegalArgumentException = IllegalArgumentException("bad mono")
        assertThat(catchSuspend { Mono.error<String>(failure).awaitSingle() })
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("bad mono")

        val subscribed: CompletableDeferred<Unit> = CompletableDeferred()
        val cancelled: CompletableDeferred<Unit> = CompletableDeferred()
        val pending: Mono<String> = Mono.create { sink ->
            subscribed.complete(Unit)
            sink.onCancel { cancelled.complete(Unit) }
        }
        val job = launch { pending.awaitSingleOrNull() }
        withTimeout(5_000) { subscribed.await() }

        job.cancelAndJoin()

        withTimeout(5_000) { cancelled.await() }
    }

    @Test
    fun reactorContextFromCoroutineIsInjectedIntoAwaitedMono(): Unit = runBlocking {
        val contextual: Mono<String> = Mono.deferContextual { contextView ->
            Mono.just(contextView.get<String>("token"))
        }

        val result: String = withContext(Context.of("token", "from-coroutine").asCoroutineContext()) {
            contextual.awaitSingle()
        }

        assertThat(result).isEqualTo("from-coroutine")
    }

    @Test
    fun fluxBuilderEmitsValuesIsColdAndPropagatesSubscriberContext(): Unit {
        val subscriptions: AtomicInteger = AtomicInteger()
        val source: Flux<String> = flux(context = CoroutineName("flux-context-test")) {
            val reactorContext: ReactorContext = coroutineContext[ReactorContext]!!
            val requestId: String = reactorContext.context.get("requestId")
            val sequence: Int = subscriptions.incrementAndGet()
            send("$requestId-${coroutineContext[CoroutineName]!!.name}-$sequence-a")
            send("$requestId-${coroutineContext[CoroutineName]!!.name}-$sequence-b")
        }

        val first: List<String> = source
            .contextWrite(Context.of("requestId", "first"))
            .collectList()
            .block(TIMEOUT)!!
        val second: List<String> = source
            .contextWrite(Context.of("requestId", "second"))
            .collectList()
            .block(TIMEOUT)!!

        assertThat(first).containsExactly("first-flux-context-test-1-a", "first-flux-context-test-1-b")
        assertThat(second).containsExactly("second-flux-context-test-2-a", "second-flux-context-test-2-b")
        assertThat(subscriptions).hasValue(2)
    }

    @Test
    fun fluxBuilderHandlesFailuresCancellationAndInvalidContext(): Unit = runBlocking {
        val failure: IllegalStateException = IllegalStateException("flux failed")
        val failed: Flux<Int> = flux {
            send(1)
            throw failure
        }
        assertThatThrownBy { failed.collectList().block(TIMEOUT) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("flux failed")

        assertThatThrownBy { flux<Int>(context = Job()) { send(1) } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Flux context cannot contain job")

        val started: CompletableDeferred<Unit> = CompletableDeferred()
        val finalized: CompletableDeferred<String> = CompletableDeferred()
        val cancellable: Flux<Int> = flux(context = Dispatchers.Default) {
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                finalized.complete("cancelled")
            }
        }

        val disposable = cancellable.subscribe()
        withTimeout(5_000) { started.await() }
        disposable.dispose()

        assertThat(withTimeout(5_000) { finalized.await() }).isEqualTo("cancelled")
    }

    @Test
    fun flowAsFluxTransfersValuesAndSubscriberContext(): Unit {
        val source = flow {
            val reactorContext: ReactorContext = coroutineContext[ReactorContext]!!
            emit(reactorContext.context.get<String>("token"))
            emit("completed")
        }

        val values: List<String> = source
            .asFlux()
            .contextWrite(Context.of("token", "flow-context"))
            .collectList()
            .block(TIMEOUT)!!

        assertThat(values).containsExactly("flow-context", "completed")
    }

    @Test
    fun flowAsFluxUsesReactorContextFromCoroutineContextArgument(): Unit {
        val source = flow {
            val reactorContext: ReactorContext = coroutineContext[ReactorContext]!!
            emit(reactorContext.context.get<String>("token"))
            emit(reactorContext.context.get<String>("traceId"))
        }

        val values: List<String> = source
            .asFlux(Context.of("token", "context-argument", "traceId", "trace-42").asCoroutineContext())
            .collectList()
            .block(TIMEOUT)!!

        assertThat(values).containsExactly("context-argument", "trace-42")
    }

    @Test
    fun schedulerDispatcherRunsCoroutineWorkAndFlowSignalsOnScheduler(): Unit = runBlocking {
        val scheduler = Schedulers.newSingle("coroutines-reactor-scheduler")
        val dispatcher = scheduler.asCoroutineDispatcher()
        try {
            val delayedThread: String = withTimeout(5_000) {
                withContext(dispatcher) {
                    delay(10)
                    Thread.currentThread().name
                }
            }
            assertThat(delayedThread).contains("coroutines-reactor-scheduler")
            assertThat(dispatcher.scheduler).isSameAs(scheduler)
            assertThat(dispatcher.toString()).isNotBlank()
            assertThat(scheduler.asCoroutineDispatcher()).isEqualTo(dispatcher)

            val flowThreads: List<String> = flow {
                emit(Thread.currentThread().name)
            }.asFlux(dispatcher).collectList().block(TIMEOUT)!!

            assertThat(flowThreads).hasSize(1)
            assertThat(flowThreads.single()).contains("coroutines-reactor-scheduler")
        } finally {
            scheduler.dispose()
        }
    }

    @Test
    fun deferredAndJobAsMonoMirrorCoroutineCompletionWithoutOwningSources(): Unit = runBlocking {
        coroutineScope {
            val valueDeferred: CompletableDeferred<String> = CompletableDeferred("deferred-value")
            val valueMono: Mono<String> = valueDeferred.asMono(Dispatchers.Default)
            assertThat(valueMono.block(TIMEOUT)).isEqualTo("deferred-value")

            val nullDeferred: CompletableDeferred<String?> = CompletableDeferred<String?>().apply { complete(null) }
            val nullMono: Mono<String> = nullDeferred.asMono(Dispatchers.Default)
            assertThat(nullMono.block(TIMEOUT)).isNull()

            val failure: IllegalStateException = IllegalStateException("deferred failed")
            val failedDeferred: CompletableDeferred<String> = CompletableDeferred()
            failedDeferred.completeExceptionally(failure)
            val failedMono: Mono<String> = failedDeferred.asMono(Dispatchers.Default)
            assertThatThrownBy { failedMono.block(TIMEOUT) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessage("deferred failed")

            val source: CompletableDeferred<String> = CompletableDeferred()
            val hotMono: Mono<String> = source.asMono(Dispatchers.Default)
            val disposable = hotMono.subscribe()
            disposable.dispose()
            assertThat(source.isActive).isTrue()
            source.complete("still usable")
            assertThat(source.await()).isEqualTo("still usable")

            val job = launch(Dispatchers.Default) { delay(10) }
            assertThat(job.asMono(Dispatchers.Default).block(TIMEOUT)).isNotNull()
        }
    }

    private suspend fun catchSuspend(block: suspend () -> Unit): Throwable? {
        return try {
            block()
            null
        } catch (throwable: Throwable) {
            throwable
        }
    }

    private companion object {
        val TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}
