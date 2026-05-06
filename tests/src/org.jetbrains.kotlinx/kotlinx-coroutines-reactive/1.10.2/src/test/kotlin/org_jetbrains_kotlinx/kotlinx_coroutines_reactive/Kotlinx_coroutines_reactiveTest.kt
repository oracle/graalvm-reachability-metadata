/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_reactive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.asPublisher
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitLast
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactive.publish
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.util.NoSuchElementException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

public class Kotlinx_coroutines_reactiveTest {
    @Test
    fun awaitOperatorsReturnExpectedElementsAndCancelWhenAppropriate() = runBlockingWithTimeout {
        val multiElementPublisher: IterablePublisher<Int> = IterablePublisher(listOf(1, 2, 3))
        assertThat(multiElementPublisher.awaitFirst()).isEqualTo(1)
        assertThat(multiElementPublisher.lastSubscription?.cancelled).isEqualTo(true)

        assertThat(IterablePublisher(listOf("first", "last")).awaitLast()).isEqualTo("last")
        assertThat(IterablePublisher(listOf("only")).awaitSingle()).isEqualTo("only")
        assertThat(IterablePublisher(emptyList<String>()).awaitFirstOrNull()).isNull()
    }

    @Test
    fun awaitOperatorsPropagatePublisherErrorsAndCardinalityFailures() = runBlockingWithTimeout {
        val failure: IllegalStateException = IllegalStateException("boom")
        assertThat(assertFails { ErrorPublisher<Int>(failure).awaitFirst() }).isSameAs(failure)

        val noSuchElementFailure: Throwable = assertFails { IterablePublisher(emptyList<Int>()).awaitFirst() }
        assertThat(noSuchElementFailure is NoSuchElementException).isTrue()

        val tooManyElementsFailure: Throwable = assertFails { IterablePublisher(listOf(1, 2)).awaitSingle() }
        assertThat(tooManyElementsFailure is IllegalArgumentException).isTrue()
    }

    @Test
    fun publisherAsFlowCanBeCollectedTransformedAndCancelled() = runBlockingWithTimeout {
        val publisher: IterablePublisher<Int> = IterablePublisher((1..100).toList())

        val firstTransformedValues: List<Int> = publisher
            .asFlow()
            .map { value: Int -> value * 2 }
            .filter { value: Int -> value % 4 == 0 }
            .take(3)
            .toList()

        assertThat(firstTransformedValues).containsExactly(4, 8, 12)
        assertThat(publisher.lastSubscription?.cancelled).isEqualTo(true)
    }

    @Test
    fun publisherAsFlowPropagatesPublisherFailureAfterCollectedValues() = runBlockingWithTimeout {
        val expectedFailure: IllegalArgumentException = IllegalArgumentException("publisher failed")
        val publisher: ValuesThenErrorPublisher<Int> = ValuesThenErrorPublisher(listOf(1, 2), expectedFailure)
        val collectedValues: MutableList<Int> = mutableListOf()

        val failure: Throwable = assertFails { publisher.asFlow().toList(collectedValues) }

        assertThat(collectedValues).containsExactly(1, 2)
        assertThat(failure).isSameAs(expectedFailure)
    }

    @Test
    fun flowAsPublisherEmitsOnlyAfterSubscriberRequestsDemand() = runBlockingWithTimeout {
        val publisher: Publisher<Int> = flow {
            emit(10)
            emit(20)
            emit(30)
        }.asPublisher(Dispatchers.Unconfined)
        val subscriber: RecordingSubscriber<Int> = RecordingSubscriber()

        publisher.subscribe(subscriber)
        subscriber.awaitSubscription()
        subscriber.assertNoValueAvailable()
        subscriber.assertNotCompleted()

        subscriber.request(1)
        assertThat(subscriber.awaitNext()).isEqualTo(10)
        subscriber.assertNoValueAvailable()
        subscriber.assertNotCompleted()

        subscriber.request(2)
        assertThat(subscriber.awaitNext()).isEqualTo(20)
        assertThat(subscriber.awaitNext()).isEqualTo(30)
        subscriber.assertNotCompleted()

        subscriber.request(1)
        subscriber.awaitCompletion()
        assertThat(subscriber.failure).isNull()
    }

    @Test
    fun flowAsPublisherPropagatesFlowFailuresToReactiveSubscriber() = runBlockingWithTimeout {
        val failure: IllegalArgumentException = IllegalArgumentException("bad flow")
        val publisher: Publisher<Int> = flow<Int> {
            emit(1)
            throw failure
        }.asPublisher(Dispatchers.Unconfined)
        val subscriber: RecordingSubscriber<Int> = RecordingSubscriber()

        publisher.subscribe(subscriber)
        subscriber.awaitSubscription()
        subscriber.request(Long.MAX_VALUE)

        assertThat(subscriber.awaitNext()).isEqualTo(1)
        assertThat(subscriber.awaitFailure()).isSameAs(failure)
        subscriber.assertNotCompleted()
    }

    @Test
    fun coldFlowAsPublisherCreatesIndependentSubscriptions() = runBlockingWithTimeout {
        var collectionCount: Int = 0
        val publisher: Publisher<Int> = flow {
            collectionCount++
            emit(collectionCount)
            emit(collectionCount + 100)
        }.asPublisher(Dispatchers.Unconfined)

        val firstSubscriber: RecordingSubscriber<Int> = RecordingSubscriber()
        publisher.subscribe(firstSubscriber)
        firstSubscriber.awaitSubscription()
        firstSubscriber.request(Long.MAX_VALUE)
        assertThat(firstSubscriber.awaitNext()).isEqualTo(1)
        assertThat(firstSubscriber.awaitNext()).isEqualTo(101)
        firstSubscriber.awaitCompletion()

        val secondSubscriber: RecordingSubscriber<Int> = RecordingSubscriber()
        publisher.subscribe(secondSubscriber)
        secondSubscriber.awaitSubscription()
        secondSubscriber.request(Long.MAX_VALUE)
        assertThat(secondSubscriber.awaitNext()).isEqualTo(2)
        assertThat(secondSubscriber.awaitNext()).isEqualTo(102)
        secondSubscriber.awaitCompletion()
    }

    @Test
    fun kotlinIterableFlowRoundTripsThroughReactivePublisher() = runBlockingWithTimeout {
        val publisher: Publisher<String> = listOf("alpha", "beta", "gamma").asFlow().asPublisher(Dispatchers.Unconfined)

        val values: List<String> = publisher.asFlow().toList()

        assertThat(values).containsExactly("alpha", "beta", "gamma")
    }

    @Test
    fun publishBuilderSuspendsEmissionUntilDemandAndStopsOnCancellation() = runBlockingWithTimeout {
        val cancelled: CountDownLatch = CountDownLatch(1)
        val publisher: Publisher<Int> = publish(Dispatchers.Unconfined) {
            try {
                send(1)
                send(2)
                send(3)
            } finally {
                cancelled.countDown()
            }
        }
        val subscriber: RecordingSubscriber<Int> = RecordingSubscriber()

        publisher.subscribe(subscriber)
        subscriber.awaitSubscription()
        subscriber.assertNoValueAvailable()
        subscriber.assertNotCompleted()

        subscriber.request(1)
        assertThat(subscriber.awaitNext()).isEqualTo(1)
        subscriber.assertNoValueAvailable()

        subscriber.request(1)
        assertThat(subscriber.awaitNext()).isEqualTo(2)
        subscriber.cancel()

        assertThat(cancelled.await(5, TimeUnit.SECONDS)).isTrue()
        subscriber.assertNoValueAvailable()
        subscriber.assertNotCompleted()
        assertThat(subscriber.failure).isNull()
    }

    private fun <T> runBlockingWithTimeout(block: suspend () -> T): T = runBlocking {
        withTimeout(5_000) {
            block()
        }
    }

    private suspend fun assertFails(block: suspend () -> Unit): Throwable {
        var failure: Throwable? = null
        try {
            block()
        } catch (throwable: Throwable) {
            failure = throwable
        }
        return failure ?: throw AssertionError("Expected the publisher operation to fail")
    }
}

private class IterablePublisher<T>(private val values: Iterable<T>) : Publisher<T> {
    @Volatile
    var lastSubscription: IterableSubscription<T>? = null
        private set

    override fun subscribe(subscriber: Subscriber<in T>) {
        val subscription: IterableSubscription<T> = IterableSubscription(values.iterator(), subscriber)
        lastSubscription = subscription
        subscriber.onSubscribe(subscription)
    }
}

private class IterableSubscription<T>(
    private val iterator: Iterator<T>,
    private val subscriber: Subscriber<in T>,
) : Subscription {
    @Volatile
    var cancelled: Boolean = false
        private set

    private var completed: Boolean = false

    override fun request(n: Long) {
        if (cancelled || completed) {
            return
        }
        if (n <= 0) {
            cancelled = true
            subscriber.onError(IllegalArgumentException("Reactive Streams demand must be positive"))
            return
        }

        var emitted: Long = 0
        while (emitted < n && !cancelled && !completed) {
            if (iterator.hasNext()) {
                subscriber.onNext(iterator.next())
                emitted++
            } else {
                completed = true
                subscriber.onComplete()
            }
        }
    }

    override fun cancel() {
        cancelled = true
    }
}

private class ErrorPublisher<T>(private val failure: Throwable) : Publisher<T> {
    override fun subscribe(subscriber: Subscriber<in T>) {
        subscriber.onSubscribe(object : Subscription {
            private var terminated: Boolean = false

            override fun request(n: Long) {
                if (!terminated) {
                    terminated = true
                    subscriber.onError(failure)
                }
            }

            override fun cancel() {
                terminated = true
            }
        })
    }
}

private class ValuesThenErrorPublisher<T>(
    private val values: Iterable<T>,
    private val failure: Throwable,
) : Publisher<T> {
    override fun subscribe(subscriber: Subscriber<in T>) {
        subscriber.onSubscribe(object : Subscription {
            private val iterator: Iterator<T> = values.iterator()
            private var terminated: Boolean = false

            override fun request(n: Long) {
                if (terminated) {
                    return
                }
                if (n <= 0) {
                    terminated = true
                    subscriber.onError(IllegalArgumentException("Reactive Streams demand must be positive"))
                    return
                }

                var emitted: Long = 0
                while (emitted < n && !terminated) {
                    if (iterator.hasNext()) {
                        subscriber.onNext(iterator.next())
                        emitted++
                    } else {
                        terminated = true
                        subscriber.onError(failure)
                    }
                }
            }

            override fun cancel() {
                terminated = true
            }
        })
    }
}

private class RecordingSubscriber<T> : Subscriber<T> {
    private val subscribed: CountDownLatch = CountDownLatch(1)
    private val completed: CountDownLatch = CountDownLatch(1)
    private val failed: CountDownLatch = CountDownLatch(1)
    private val values: LinkedBlockingQueue<T> = LinkedBlockingQueue()
    private val received: CopyOnWriteArrayList<T> = CopyOnWriteArrayList()
    private val subscriptionRef: AtomicReference<Subscription> = AtomicReference()

    @Volatile
    var failure: Throwable? = null
        private set

    override fun onSubscribe(subscription: Subscription) {
        check(subscriptionRef.compareAndSet(null, subscription)) { "Subscriber received multiple subscriptions" }
        subscribed.countDown()
    }

    override fun onNext(value: T) {
        received.add(value)
        values.offer(value)
    }

    override fun onError(throwable: Throwable) {
        failure = throwable
        failed.countDown()
    }

    override fun onComplete() {
        completed.countDown()
    }

    fun awaitSubscription(): Subscription {
        assertThat(subscribed.await(5, TimeUnit.SECONDS)).isTrue()
        return subscriptionRef.get()
    }

    fun request(n: Long) {
        awaitSubscription().request(n)
    }

    fun cancel() {
        awaitSubscription().cancel()
    }

    fun awaitNext(): T = values.poll(5, TimeUnit.SECONDS)
        ?: throw AssertionError("Expected the next value, received values were $received and failure was $failure")

    fun awaitCompletion() {
        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue()
    }

    fun awaitFailure(): Throwable {
        assertThat(failed.await(5, TimeUnit.SECONDS)).isTrue()
        return failure ?: throw AssertionError("Subscriber failed without storing the exception")
    }

    fun assertNoValueAvailable() {
        assertThat(values.poll()).isNull()
    }

    fun assertNotCompleted() {
        assertThat(completed.count).isEqualTo(1L)
    }
}
