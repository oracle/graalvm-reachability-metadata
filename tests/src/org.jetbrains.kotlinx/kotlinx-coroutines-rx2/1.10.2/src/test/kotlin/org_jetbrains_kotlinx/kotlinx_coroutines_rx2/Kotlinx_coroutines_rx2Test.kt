/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_rx2

import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.reactivex.subscribers.TestSubscriber
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.rx2.asCompletable
import kotlinx.coroutines.rx2.asCoroutineDispatcher
import kotlinx.coroutines.rx2.asFlow
import kotlinx.coroutines.rx2.asFlowable
import kotlinx.coroutines.rx2.asMaybe
import kotlinx.coroutines.rx2.asObservable
import kotlinx.coroutines.rx2.asScheduler
import kotlinx.coroutines.rx2.asSingle
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.awaitFirst
import kotlinx.coroutines.rx2.awaitFirstOrDefault
import kotlinx.coroutines.rx2.awaitFirstOrElse
import kotlinx.coroutines.rx2.awaitFirstOrNull
import kotlinx.coroutines.rx2.awaitLast
import kotlinx.coroutines.rx2.awaitSingle
import kotlinx.coroutines.rx2.awaitSingleOrNull
import kotlinx.coroutines.rx2.collect
import kotlinx.coroutines.rx2.rxCompletable
import kotlinx.coroutines.rx2.rxFlowable
import kotlinx.coroutines.rx2.rxMaybe
import kotlinx.coroutines.rx2.rxObservable
import kotlinx.coroutines.rx2.rxSingle
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import kotlin.coroutines.EmptyCoroutineContext

public class Kotlinx_coroutines_rx2Test {
    @Test
    fun singleMaybeAndCompletableBuildersAreColdAndAwaitable(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val subscriptions: AtomicInteger = AtomicInteger()
            val single: Single<String> = rxSingle {
                val currentSubscription: Int = subscriptions.incrementAndGet()
                "single-$currentSubscription"
            }

            assertThat(single.await()).isEqualTo("single-1")
            assertThat(single.await()).isEqualTo("single-2")
            assertThat(subscriptions.get()).isEqualTo(2)

            assertThat(rxMaybe<String> { "present" }.awaitSingleOrNull()).isEqualTo("present")
            assertThat(rxMaybe<String> { null }.awaitSingleOrNull()).isNull()

            var completed: Boolean = false
            rxCompletable {
                completed = true
            }.await()
            assertThat(completed).isTrue()
        }
    }

    @Test
    fun buildersPropagateFailuresToRxSubscribersAndAwaiters(): Unit {
        assertThatThrownBy {
            runBlocking {
                withTimeout(TEST_TIMEOUT_MILLIS) {
                    rxSingle<String> { throw IllegalStateException("single failed") }.await()
                }
            }
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessage("single failed")

        rxMaybe<String> { throw IllegalArgumentException("maybe failed") }
            .test()
            .awaitDone(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .assertNoValues()
            .assertError(IllegalArgumentException::class.java)

        rxCompletable { throw IllegalStateException("completable failed") }
            .test()
            .awaitDone(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .assertNotComplete()
            .assertError(IllegalStateException::class.java)
    }

    @Test
    fun observableBuilderAndAwaitOperationsHandleValuesEmptySourcesAndSingleValidation(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val observable: Observable<Int> = rxObservable {
                send(1)
                send(2)
                send(3)
            }

            observable.test()
                .awaitDone(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .assertValues(1, 2, 3)
                .assertComplete()
                .assertNoErrors()

            assertThat(observable.awaitFirst()).isEqualTo(1)
            assertThat(observable.awaitLast()).isEqualTo(3)
            assertThat(Observable.just("only").awaitSingle()).isEqualTo("only")
            assertThat(Observable.empty<String>().awaitFirstOrDefault("fallback")).isEqualTo("fallback")
            assertThat(Observable.empty<String>().awaitFirstOrElse { "computed" }).isEqualTo("computed")
            assertThat(Observable.empty<String>().awaitFirstOrNull()).isNull()

            assertThatThrownBy {
                runBlocking { Observable.just(1, 2).awaitSingle() }
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("More than one")
        }
    }

    @Test
    fun flowableBuilderHonorsBackpressureRequests(): Unit {
        val subscriber: TestSubscriber<Int> = TestSubscriber(0L)
        rxFlowable {
            for (value: Int in 1..4) {
                send(value)
            }
        }.subscribe(subscriber)

        subscriber.assertNoValues()
        subscriber.request(2)
        subscriber.awaitCount(2)
        subscriber.assertValues(1, 2)
        subscriber.assertNotComplete()

        subscriber.request(2)
        subscriber.awaitDone(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .assertValues(1, 2, 3, 4)
            .assertComplete()
            .assertNoErrors()
    }

    @Test
    fun coroutinePrimitivesConvertToRxTypes(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val deferredSingle = async { "deferred-value" }
            assertThat(deferredSingle.asSingle(EmptyCoroutineContext).await()).isEqualTo("deferred-value")

            val deferredMaybeValue = async<String?> { "maybe-value" }
            assertThat(deferredMaybeValue.asMaybe(EmptyCoroutineContext).awaitSingleOrNull()).isEqualTo("maybe-value")

            val deferredMaybeEmpty = async<String?> { null }
            assertThat(deferredMaybeEmpty.asMaybe(EmptyCoroutineContext).awaitSingleOrNull()).isNull()

            val jobSignal: CompletableDeferred<Unit> = CompletableDeferred()
            val completable: Completable = jobSignal.asCompletable(EmptyCoroutineContext)
            val observer = completable.test()
            jobSignal.complete(Unit)
            observer.awaitDone(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .assertComplete()
                .assertNoErrors()
        }
    }

    @Test
    fun rxSourcesAndFlowsConvertInBothDirections(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val flowValues: List<Int> = Observable.range(1, 3).asFlow().toList()
            assertThat(flowValues).containsExactly(1, 2, 3)

            flowOf("alpha", "beta").asObservable()
                .test()
                .awaitDone(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .assertValues("alpha", "beta")
                .assertComplete()
                .assertNoErrors()

            flowOf(4, 5, 6).asFlowable()
                .test()
                .awaitDone(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .assertValues(4, 5, 6)
                .assertComplete()
                .assertNoErrors()
        }
    }

    @Test
    fun rxSourceCollectExtensionsProcessObservableAndMaybeValues(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val observableValues: MutableList<Int> = mutableListOf()
            Observable.just(2, 4, 6).collect { value: Int ->
                observableValues += value
            }
            assertThat(observableValues).containsExactly(2, 4, 6)

            val maybeValues: MutableList<String> = mutableListOf()
            Maybe.just("present").collect { value: String ->
                maybeValues += value
            }
            Maybe.empty<String>().collect { value: String ->
                maybeValues += value
            }
            assertThat(maybeValues).containsExactly("present")
        }
    }

    @Test
    fun schedulerDispatcherAdaptersRunWorkOnAdaptedExecutors(): Unit {
        val executor = Executors.newSingleThreadExecutor { runnable: Runnable ->
            Thread(runnable, "rx2-adapter-test-thread").also { thread: Thread ->
                thread.isDaemon = true
            }
        }
        val scheduler = Schedulers.from(executor)
        val schedulerFromDispatcher = Dispatchers.Default.asScheduler()

        try {
            runBlocking {
                withTimeout(TEST_TIMEOUT_MILLIS) {
                    val threadName: String = withContext(scheduler.asCoroutineDispatcher()) {
                        Thread.currentThread().name
                    }
                    assertThat(threadName).startsWith("rx2-adapter-test-thread")

                    val scheduled = CountDownLatch(1)
                    val disposable = schedulerFromDispatcher.scheduleDirect {
                        scheduled.countDown()
                    }

                    assertThat(scheduled.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue()
                    assertThat(disposable.isDisposed).isFalse()
                    disposable.dispose()
                }
            }
        } finally {
            schedulerFromDispatcher.shutdown()
            scheduler.shutdown()
            executor.shutdownNow()
            assertThat(executor.awaitTermination(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue()
        }
    }

    @Test
    fun cancellationDisposesAwaitedSourcesAndBuilderSubscriptions(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val singleSubscribed = CountDownLatch(1)
            val singleDisposed = CountDownLatch(1)
            val neverSingle: Single<String> = Single.create { emitter ->
                singleSubscribed.countDown()
                emitter.setCancellable { singleDisposed.countDown() }
            }

            val awaitingSingle = launch(start = CoroutineStart.UNDISPATCHED) { neverSingle.await() }
            assertThat(singleSubscribed.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue()
            awaitingSingle.cancelAndJoin()
            assertThat(singleDisposed.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue()

            val observableStarted = CountDownLatch(1)
            val observableCancelled = CountDownLatch(1)
            val disposable = rxObservable<Int> {
                observableStarted.countDown()
                try {
                    awaitCancellation()
                } finally {
                    observableCancelled.countDown()
                }
            }.subscribe()

            assertThat(observableStarted.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue()
            disposable.dispose()
            assertThat(observableCancelled.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue()
        }
    }

    @Test
    fun topLevelBuildersRejectContextsWithJobs(): Unit {
        val contextWithJob: Job = Job()
        try {
            assertThatThrownBy { rxSingle(contextWithJob) { "value" } }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Single context cannot contain job")
            assertThatThrownBy { rxMaybe<String>(contextWithJob) { "value" } }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Maybe context cannot contain job")
            assertThatThrownBy { rxCompletable(contextWithJob) { } }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Completable context cannot contain job")
            assertThatThrownBy { rxObservable<Int>(contextWithJob) { send(1) } }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Observable context cannot contain job")
            assertThatThrownBy { rxFlowable<Int>(contextWithJob) { send(1) } }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Flowable context cannot contain job")
        } finally {
            contextWithJob.cancel()
        }
    }

    private companion object {
        private const val TEST_TIMEOUT_MILLIS: Long = 5_000L
    }
}
