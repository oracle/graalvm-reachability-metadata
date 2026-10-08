/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_projectreactor_kotlin.reactor_kotlin_extensions

import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Observable
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.adapter.rxjava.toCompletable as toCompletableRx
import reactor.kotlin.adapter.rxjava.toFlux as toFluxRx
import reactor.kotlin.adapter.rxjava.toFlowable as toFlowableRx
import reactor.kotlin.adapter.rxjava.toMaybe as toMaybeRx
import reactor.kotlin.adapter.rxjava.toMono as toMonoRx
import reactor.kotlin.adapter.rxjava.toObservable as toObservableRx
import reactor.kotlin.adapter.rxjava.toSingle as toSingleRx
import reactor.kotlin.core.publisher.cast
import reactor.kotlin.core.publisher.collectMap
import reactor.kotlin.core.publisher.doOnError
import reactor.kotlin.core.publisher.onErrorMap
import reactor.kotlin.core.publisher.onErrorResume
import reactor.kotlin.core.publisher.onErrorReturn
import reactor.kotlin.core.publisher.ofType
import reactor.kotlin.core.publisher.split
import reactor.kotlin.core.publisher.switchIfEmpty
import reactor.kotlin.core.publisher.switchIfEmptyDeferred
import reactor.kotlin.core.publisher.toFlux
import reactor.kotlin.core.publisher.toMono
import reactor.kotlin.core.publisher.whenComplete
import reactor.kotlin.core.publisher.zip
import reactor.kotlin.core.util.function.*
import reactor.kotlin.extra.bool.*
import reactor.kotlin.extra.math.*
import reactor.kotlin.extra.retry.*
import reactor.kotlin.test.*
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Stream
import reactor.util.function.Tuples

class Reactor_kotlin_extensionsTest {
    @Test
    fun convertsCollectionsArraysAndPublishersToFlux() {
        assertThat(listOf("one", "two").toFlux().awaitList()).containsExactly("one", "two")
        assertThat(listOf(3, 4).iterator().toFlux().awaitList()).containsExactly(3, 4)
        assertThat(sequenceOf(5, 6).toFlux().awaitList()).containsExactly(5, 6)

        val stream = Stream.of("seven", "eight")
        try {
            assertThat(stream.toFlux().awaitList()).containsExactly("seven", "eight")
        } finally {
            stream.close()
        }

        assertThat(arrayOf("nine", "ten").toFlux().awaitList()).containsExactly("nine", "ten")
        assertThat(booleanArrayOf(true, false).toFlux().awaitList()).containsExactly(true, false)
        assertThat(byteArrayOf(1, 2).toFlux().awaitList()).containsExactly(1.toByte(), 2.toByte())
        assertThat(shortArrayOf(3, 4).toFlux().awaitList()).containsExactly(3.toShort(), 4.toShort())
        assertThat(intArrayOf(5, 6).toFlux().awaitList()).containsExactly(5, 6)
        assertThat(longArrayOf(7, 8).toFlux().awaitList()).containsExactly(7L, 8L)
        assertThat(floatArrayOf(1.5f, 2.5f).toFlux().awaitList()).containsExactly(1.5f, 2.5f)
        assertThat(doubleArrayOf(3.5, 4.5).toFlux().awaitList()).containsExactly(3.5, 4.5)

        val publisher: Publisher<Int> = Flux.just(11, 12)
        assertThat(publisher.toFlux().awaitList()).containsExactly(11, 12)
    }

    @Test
    fun convertsCommonKotlinSourcesToMono() {
        val publisher: Publisher<Int> = Flux.just(1, 2)
        assertThat(publisher.toMono().await()).isEqualTo(1)

        val supplier: () -> String? = { "supplied" }
        assertThat(supplier.toMono().await()).isEqualTo("supplied")

        val present: String? = "present"
        assertThat(present.toMono().await()).isEqualTo("present")
        val absent: String? = null
        assertThat(absent.toMono<String>().await()).isNull()

        assertThat(CompletableFuture.completedFuture("future").toMono().await()).isEqualTo("future")
        val callable = Callable<String?> { "called" }
        assertThat(callable.toMono().await()).isEqualTo("called")

        assertThatThrownBy { IllegalStateException("flux-error").toFlux<Int>().awaitList() }
            .isInstanceOf(IllegalStateException::class.java)
        assertThatThrownBy { IllegalStateException("mono-error").toMono<Int>().await() }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun appliesFluxTypeAndErrorOperators() {
        assertThat(Flux.just<Any>("text", 42).ofType<String>().awaitList()).containsExactly("text")
        assertThat(Flux.just<Any>("42").cast<String>().awaitList()).containsExactly("42")

        val observedErrors = mutableListOf<String>()
        val recovered = Flux.error<Int>(IllegalStateException("failure"))
            .doOnError(IllegalStateException::class) { observedErrors += it.message.orEmpty() }
            .onErrorMap(IllegalStateException::class) { IllegalArgumentException(it.message) }
            .onErrorResume(IllegalArgumentException::class) { Flux.just(7, 8) }
        assertThat(recovered.awaitList()).containsExactly(7, 8)
        assertThat(observedErrors).containsExactly("failure")

        val fallback = Flux.error<Int>(IllegalStateException("fallback"))
            .onErrorReturn(IllegalStateException::class, 99)
        assertThat(fallback.awaitList()).containsExactly(99)
    }

    @Test
    fun flattensCollectsAndDefersFluxValues() {
        val split = Flux.just(listOf(1, 2), listOf(3, 4)).split()
        assertThat(split.awaitList()).containsExactly(1, 2, 3, 4)

        var fallbackInvoked = false
        val deferred = Flux.empty<Int>().switchIfEmptyDeferred {
            fallbackInvoked = true
            Flux.just(5, 6)
        }
        assertThat(deferred.awaitList()).containsExactly(5, 6)
        assertThat(fallbackInvoked).isTrue

        val collected: Map<String, Int> =
            Flux.just("first" to 1, "second" to 2).collectMap().await()!!
        assertThat(collected).containsEntry("first", 1).containsEntry("second", 2)
    }

    @Test
    fun appliesMonoTypeAndErrorOperators() {
        assertThat(Mono.just<Any>("value").cast<String>().await()).isEqualTo("value")
        assertThat(Mono.just<Any>("value").ofType<String>().await()).isEqualTo("value")
        assertThat(Mono.just<Any>(42).ofType<String>().await()).isNull()

        val observedErrors = mutableListOf<String>()
        val mapped = Mono.error<Int>(IllegalArgumentException("bad"))
            .doOnError(IllegalArgumentException::class) { observedErrors += it.message.orEmpty() }
            .onErrorMap(
                IllegalArgumentException::class,
                { it.message == "bad" },
                { IllegalStateException("mapped") }
            )
        assertThatThrownBy { mapped.await() }.isInstanceOf(IllegalStateException::class.java)
        assertThat(observedErrors).containsExactly("bad")

        val resumed = Mono.error<Int>(IllegalStateException("resume"))
            .onErrorResume(IllegalStateException::class) { Mono.just(12) }
        assertThat(resumed.await()).isEqualTo(12)

        val returned = Mono.error<Int>(IllegalStateException("return"))
            .onErrorReturn(IllegalStateException::class, 13)
        assertThat(returned.await()).isEqualTo(13)
    }

    @Test
    fun switchesAndCombinesMonos() {
        val switched = Mono.empty<Int>().switchIfEmpty { Mono.just(14) }
        assertThat(switched.await()).isEqualTo(14)

        assertThat(whenComplete(Mono.just("first"), Flux.just("second")).await()).isNull()
        assertThat(
            listOf<Publisher<*>>(Mono.just("first"), Mono.just("second")).whenComplete().await()
        ).isNull()

        val iterableZip = listOf(Mono.just(2), Mono.just(3)).zip { values -> values.sum() }
        assertThat(iterableZip.await()).isEqualTo(5)

        val varargZip = zip(Mono.just(4), Mono.just(5)) { values ->
            (values[0] as Int) + (values[1] as Int)
        }
        assertThat(varargZip.await()).isEqualTo(9)
    }

    @Test
    fun destructuresReactorTuples() {
        val tuple = Tuples.of("one", 2, 3L, 4.0, '5', true, 7.toShort(), 8.toByte())
        val (one, two, three, four, five, six, seven, eight) = tuple

        assertThat(one).isEqualTo("one")
        assertThat(two).isEqualTo(2)
        assertThat(three).isEqualTo(3L)
        assertThat(four).isEqualTo(4.0)
        assertThat(five).isEqualTo('5')
        assertThat(six).isTrue
        assertThat(seven).isEqualTo(7.toShort())
        assertThat(eight).isEqualTo(8.toByte())
    }

    @Test
    fun evaluatesBooleanMonos() {
        assertThat(Mono.just(true).not().await()).isFalse
        assertThat(Mono.just(true).logicalAnd(Mono.just(false)).await()).isFalse
        assertThat(Mono.just(true).logicalNAnd(Mono.just(true)).await()).isFalse
        assertThat(Mono.just(false).logicalOr(Mono.just(true)).await()).isTrue
        assertThat(Mono.just(false).logicalNOr(Mono.just(false)).await()).isTrue
        assertThat(Mono.just(true).logicalXOr(Mono.just(false)).await()).isTrue
    }

    @Test
    fun calculatesFluxMathResults() {
        assertThat(Flux.just(1, 2, 3).sumAll<Int>().await()).isEqualTo(6)
        assertThat(Flux.just(1, 2, 3).sumAsInt().await()).isEqualTo(6)
        assertThat(Flux.just(1, 2, 3).sumAsLong().await()).isEqualTo(6L)
        assertThat(Flux.just(1, 2, 3).sumAsDouble().await()).isEqualTo(6.0)
        assertThat(Flux.just(1, 2, 3).sumAsBigInt().await()).isEqualTo(BigInteger.valueOf(6))
        assertThat(Flux.just(2, 4).averageAll<Int>().await()).isEqualTo(3)
        assertThat(Flux.just(2, 4).averageAsDouble().await()).isEqualTo(3.0)
        assertThat(Flux.just(4, 1, 3).min().await()).isEqualTo(1)
        assertThat(Flux.just(4, 1, 3).max().await()).isEqualTo(4)
        assertThat(Flux.just("one", "two").sumAll { it.length }.await()).isEqualTo(6)
        assertThat(Flux.just("one", "two").min { left, right -> left.length - right.length }.await())
            .isEqualTo("one")
    }

    @Test
    fun calculatesBigDecimalMathResults() {
        assertThat(
            Flux.just(BigDecimal("1.25"), BigDecimal("2.75"))
                .sumAsBigDecimal()
                .await()
        ).isEqualByComparingTo(BigDecimal("4.00"))
        assertThat(
            Flux.just(BigDecimal("1.25"), BigDecimal("2.75"))
                .averageAsBigDecimal()
                .await()
        ).isEqualByComparingTo(BigDecimal("2.00"))
    }

    @Test
    fun retriesAndRepeatsReactiveSources() {
        var repeatSubscriptions = 0
        val repeated = Mono.defer {
            repeatSubscriptions++
            Mono.just("value")
        }.repeatExponentialBackoff(2, Duration.ofMillis(1))
        assertThat(repeated.awaitList()).containsExactly("value", "value", "value")
        assertThat(repeatSubscriptions).isEqualTo(3)

        val retrySubscriptions = AtomicInteger()
        val retried = Mono.defer {
            if (retrySubscriptions.incrementAndGet() < 3) {
                Mono.error<String>(IllegalStateException("try again"))
            } else {
                Mono.just("recovered")
            }
        }.retryRandomBackoff(2, Duration.ofMillis(1))
        assertThat(retried.await()).isEqualTo("recovered")
        assertThat(retrySubscriptions).hasValue(3)

        val repeatedFlux = Flux.just(1).repeatRandomBackoff(2, Duration.ofMillis(1))
        assertThat(repeatedFlux.awaitList()).containsExactly(1, 1, 1)
    }

    @Test
    fun adaptsBetweenReactorAndRxJava() {
        assertThat(Flowable.just(1, 2).toFluxRx().awaitList()).containsExactly(1, 2)
        assertThat(Flux.just(3, 4).toFlowableRx().toFluxRx().awaitList()).containsExactly(3, 4)
        assertThat(Mono.just("single").toSingleRx().toMonoRx().await()).isEqualTo("single")
        assertThat(Maybe.just("maybe").toMonoRx().await()).isEqualTo("maybe")
        assertThat(
            Observable.just(5, 6).toFluxRx(BackpressureStrategy.BUFFER).awaitList()
        ).containsExactly(5, 6)
        assertThat(
            Flux.just(7, 8).toObservableRx().toFluxRx(BackpressureStrategy.BUFFER).awaitList()
        ).containsExactly(7, 8)
        assertThat(Mono.empty<Void>().toCompletableRx().toMonoRx().await()).isNull()
    }

    @Test
    fun verifiesPublishersWithKotlinStepVerifierExtensions() {
        Flux.just("a", "b").test()
            .expectNext("a", "b")
            .expectComplete()
            .verify(Duration.ofSeconds(10))

        Mono.error<Int>(IllegalStateException("expected")).test()
            .expectError(IllegalStateException::class.java)
            .verify(Duration.ofSeconds(10))
    }

    @Test
    fun verifiesDelayedPublishersWithVirtualTime() {
        {
            Flux.interval(Duration.ofHours(1)).take(2)
        }.testUsingVirtualTime()
            .thenAwait(Duration.ofHours(2))
            .expectNext(0L, 1L)
            .expectComplete()
            .verify(Duration.ofSeconds(10))
    }

    private fun <T> Mono<T>.await(): T? = block(timeout)

    private fun <T> Flux<T>.awaitList(): List<T> = collectList().block(timeout) ?: error("Flux did not complete")

    private companion object {
        val timeout: Duration = Duration.ofSeconds(10)
    }
}
