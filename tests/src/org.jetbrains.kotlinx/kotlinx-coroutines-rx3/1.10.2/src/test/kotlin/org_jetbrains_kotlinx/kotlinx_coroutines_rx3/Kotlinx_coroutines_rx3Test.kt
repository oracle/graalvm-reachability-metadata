/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_rx3

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.rx3.asCompletable
import kotlinx.coroutines.rx3.asCoroutineDispatcher
import kotlinx.coroutines.rx3.asFlow as asCoroutineFlow
import kotlinx.coroutines.rx3.asFlowable
import kotlinx.coroutines.rx3.asMaybe
import kotlinx.coroutines.rx3.asObservable
import kotlinx.coroutines.rx3.asScheduler
import kotlinx.coroutines.rx3.asSingle
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.rx3.awaitFirst
import kotlinx.coroutines.rx3.awaitFirstOrDefault
import kotlinx.coroutines.rx3.awaitFirstOrElse
import kotlinx.coroutines.rx3.awaitFirstOrNull
import kotlinx.coroutines.rx3.awaitLast
import kotlinx.coroutines.rx3.awaitSingle
import kotlinx.coroutines.rx3.awaitSingleOrNull
import kotlinx.coroutines.rx3.collect
import kotlinx.coroutines.rx3.rxCompletable
import kotlinx.coroutines.rx3.rxFlowable
import kotlinx.coroutines.rx3.rxMaybe
import kotlinx.coroutines.rx3.rxObservable
import kotlinx.coroutines.rx3.rxSingle
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

public class Kotlinx_coroutines_rx3Test {
    @BeforeEach
    public fun startRxJavaSchedulers(): Unit {
        Schedulers.start()
    }

    @AfterEach
    public fun stopRxJavaSchedulers(): Unit {
        Schedulers.shutdown()
    }

    @Test
    public fun awaitsRxSourcesInsideRxSingleBuilder(): Unit {
        val result: Int = rxSingle {
            val singleValue: Int = Single.just(20).await()
            val maybeValue: Int = Maybe.just(21).awaitSingle()
            Completable.complete().await()

            singleValue + maybeValue + Observable.just(1, 2, 3).awaitLast()
        }.blockingGet()

        assertThat(result).isEqualTo(44)
    }

    @Test
    public fun buildsMaybeAndCompletableFromCoroutines(): Unit {
        val present: Maybe<String> = rxMaybe { "value" }
        val empty: Maybe<String> = rxMaybe { null }
        val completed: Completable = rxCompletable {
            Completable.complete().await()
        }

        assertThat(present.blockingGet()).isEqualTo("value")
        assertThat(empty.blockingGet()).isNull()
        assertThat(completed.blockingAwait(5, TimeUnit.SECONDS)).isTrue()
    }

    @Test
    public fun buildsObservableAndFlowableWithSuspendingSends(): Unit {
        val observableValues: List<Int> = rxObservable {
            send(Single.just(1).await())
            send(Maybe.just(2).awaitSingle())
            send(3)
        }.toList().blockingGet()

        val flowableValues: List<String> = rxFlowable {
            send("a")
            send(Single.just("b").await())
            send("c")
        }.toList().blockingGet()

        assertThat(observableValues).containsExactly(1, 2, 3)
        assertThat(flowableValues).containsExactly("a", "b", "c")
    }

    @Test
    public fun awaitsObservableTerminalVariants(): Unit = runBlocking {
        withTimeout(5_000) {
            val source: Observable<String> = Observable.just("first", "middle", "last")

            assertThat(source.awaitFirst()).isEqualTo("first")
            assertThat(source.awaitFirstOrDefault("fallback")).isEqualTo("first")
            assertThat(Observable.empty<String>().awaitFirstOrDefault("fallback")).isEqualTo("fallback")
            assertThat(Observable.empty<String>().awaitFirstOrElse { "computed" }).isEqualTo("computed")
            assertThat(Observable.empty<String>().awaitFirstOrNull()).isNull()
            assertThat(source.awaitLast()).isEqualTo("last")
            assertThat(Observable.just("only").awaitSingle()).isEqualTo("only")
        }
    }

    @Test
    public fun collectsRxSourcesFromCoroutines(): Unit = runBlocking {
        withTimeout(5_000) {
            val collected = mutableListOf<Int>()

            Observable.just(1, 2).collect { value: Int -> collected.add(value) }
            Maybe.just(3).collect { value: Int -> collected.add(value) }

            assertThat(collected).containsExactly(1, 2, 3)
        }
    }

    @Test
    public fun convertsBetweenKotlinFlowsAndRxStreams(): Unit = runBlocking {
        withTimeout(5_000) {
            val observableValues: List<Int> = flowOf(1, 2, 3)
                .map { value: Int -> value * 2 }
                .asObservable()
                .toList()
                .blockingGet()
            val flowableValues: List<String> = listOf("x", "y", "z")
                .asFlow()
                .asFlowable()
                .toList()
                .blockingGet()
            val flowValues: List<Int> = Observable.just(4, 5, 6)
                .asCoroutineFlow()
                .map { value: Int -> value + 1 }
                .toList()

            assertThat(observableValues).containsExactly(2, 4, 6)
            assertThat(flowableValues).containsExactly("x", "y", "z")
            assertThat(flowValues).containsExactly(5, 6, 7)
        }
    }

    @Test
    public fun convertsCompletedCoroutinePrimitivesToRxTypes(): Unit {
        val deferredSingle: CompletableDeferred<String> = CompletableDeferred("single")
        val deferredMaybe: CompletableDeferred<String> = CompletableDeferred("maybe")
        val job: CompletableJob = Job()
        val completableObserver = job.asCompletable(EmptyCoroutineContext).test()

        val singleObserver = deferredSingle.asSingle(EmptyCoroutineContext).test()
        val maybeObserver = deferredMaybe.asMaybe(EmptyCoroutineContext).test()
        job.complete()

        singleObserver.awaitDone(5, TimeUnit.SECONDS).assertResult("single")
        maybeObserver.awaitDone(5, TimeUnit.SECONDS).assertResult("maybe")
        completableObserver.awaitDone(5, TimeUnit.SECONDS).assertComplete()
    }

    @Test
    public fun bridgesSchedulersAndCoroutineDispatchers(): Unit {
        val executor = Executors.newSingleThreadExecutor { runnable: Runnable ->
            Thread(runnable, "rx3-coroutine-dispatcher-test").apply { isDaemon = true }
        }

        try {
            val rxScheduler = Schedulers.from(executor)
            val dispatcher = rxScheduler.asCoroutineDispatcher()
            val threadName: String = runBlocking {
                withTimeout(5_000) {
                    withContext(dispatcher) { Thread.currentThread().name }
                }
            }
            val coroutineScheduler = dispatcher.asScheduler()
            val scheduled = CountDownLatch(1)
            val disposable = coroutineScheduler.scheduleDirect({ scheduled.countDown() }, 0, TimeUnit.MILLISECONDS)

            try {
                assertThat(threadName).startsWith("rx3-coroutine-dispatcher-test")
                assertThat(scheduled.await(5, TimeUnit.SECONDS)).isTrue()
            } finally {
                disposable.dispose()
                coroutineScheduler.shutdown()
                rxScheduler.shutdown()
            }
        } finally {
            executor.shutdownNow()
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
        }
    }

    @Test
    public fun disposingRxSingleCancelsRunningCoroutine(): Unit {
        val started = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val observer = rxSingle<Int> {
            try {
                started.countDown()
                awaitCancellation()
            } finally {
                cancelled.countDown()
            }
        }.test()

        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()
        observer.dispose()

        assertThat(cancelled.await(5, TimeUnit.SECONDS)).isTrue()
        observer.assertNotComplete()
    }

    @Test
    public fun cancellingAwaitingCoroutineDisposesRxSubscription(): Unit = runBlocking {
        withTimeout(5_000) {
            val disposed = AtomicBoolean(false)
            val job: Job = launch(start = CoroutineStart.UNDISPATCHED) {
                Single.never<Int>()
                    .doOnDispose { disposed.set(true) }
                    .await()
            }

            job.cancelAndJoin()

            assertThat(disposed.get()).isTrue()
        }
    }

    @Test
    public fun cancellingFlowConvertedFromObservableDisposesSubscription(): Unit = runBlocking {
        withTimeout(5_000) {
            val disposed = AtomicBoolean(false)
            val value: Int = Observable.create<Int> { emitter ->
                emitter.setCancellable { disposed.set(true) }
                emitter.onNext(7)
            }.asCoroutineFlow().first()

            assertThat(value).isEqualTo(7)
            assertThat(disposed.get()).isTrue()
        }
    }

    @Test
    public fun awaitsMaybeSourcesWithNullableResults(): Unit = runBlocking {
        withTimeout(5_000) {
            assertThat(Maybe.just("present").awaitSingle()).isEqualTo("present")
            assertThat(Maybe.empty<String>().awaitSingleOrNull()).isNull()
        }
    }
}
