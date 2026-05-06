/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_resilience_jvm

import arrow.core.Either
import arrow.resilience.CircuitBreaker
import arrow.resilience.Schedule
import arrow.resilience.retry
import arrow.resilience.retryEither
import arrow.resilience.saga
import arrow.resilience.transact
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

public class Arrow_resilience_jvmTest {
    @Test
    fun scheduleRepeatCollectsOutputsAndStopsAtDone(): Unit = runBlocking {
        var executions: Int = 0

        val outputs: List<Long> = Schedule.recurs<Unit>(3)
            .collect()
            .repeat {
                executions += 1
            }

        assertThat(outputs).containsExactly(0L, 1L, 2L)
        assertThat(executions).isEqualTo(4)
    }

    @Test
    fun scheduleRetryRetriesMatchingExceptionsUntilSuccess(): Unit = runBlocking {
        var attempts: Int = 0

        val value: String = Schedule.recurs<IllegalStateException>(2).retry {
            attempts += 1
            if (attempts < 3) {
                throw IllegalStateException("temporary failure $attempts")
            }
            "recovered"
        }

        assertThat(value).isEqualTo("recovered")
        assertThat(attempts).isEqualTo(3)
    }

    @Test
    fun scheduleRetryFallsBackWhenExhausted(): Unit = runBlocking {
        var attempts: Int = 0

        val result: Either<String, Int> = Schedule.recurs<String>(2).retryEither {
            attempts += 1
            if (attempts < 4) Either.Left("not-yet-$attempts") else Either.Right(42)
        }

        val fallback: String = result.fold(
            ifLeft = { it },
            ifRight = { value -> "unexpected success $value" },
        )
        assertThat(fallback).isEqualTo("not-yet-3")
        assertThat(attempts).isEqualTo(3)
    }

    @Test
    fun flowRetryRestartsUpstreamAccordingToSchedule(): Unit = runBlocking {
        var subscriptions: Int = 0
        val values: List<Int> = flow {
            subscriptions += 1
            emit(subscriptions)
            if (subscriptions < 3) {
                throw IllegalStateException("retry subscription $subscriptions")
            }
        }.retry(Schedule.recurs(2)).toList()

        assertThat(values).containsExactly(1, 2, 3)
        assertThat(subscriptions).isEqualTo(3)
    }

    @Test
    fun sagaTransactReturnsValueWithoutCompensatingSuccessfulTransaction(): Unit = runBlocking {
        val events: MutableList<String> = mutableListOf()

        val value: String = saga {
            val order: String = saga(
                action = {
                    events += "create-order"
                    "order-1"
                },
                compensation = { created -> events += "delete-$created" },
            )
            saga(
                action = {
                    events += "create-payment"
                    "payment-for-$order"
                },
                compensation = { created -> events += "refund-$created" },
            )
        }.transact()

        assertThat(value).isEqualTo("payment-for-order-1")
        assertThat(events).containsExactly("create-order", "create-payment")
    }

    @Test
    fun sagaTransactRunsCompensationsInReverseOrderWhenTransactionFails(): Unit = runBlocking {
        val events: MutableList<String> = mutableListOf()

        val failure: IllegalStateException = try {
            saga {
                saga(
                    action = {
                        events += "reserve-inventory"
                        "inventory"
                    },
                    compensation = { reserved -> events += "release-$reserved" },
                )
                saga(
                    action = {
                        events += "authorize-payment"
                        "payment"
                    },
                    compensation = { authorized -> events += "void-$authorized" },
                )
                throw IllegalStateException("shipment failed")
            }.transact()
            throw AssertionError("Expected saga transaction to fail")
        } catch (error: IllegalStateException) {
            error
        }

        assertThat(failure).hasMessage("shipment failed")
        assertThat(events).containsExactly(
            "reserve-inventory",
            "authorize-payment",
            "void-payment",
            "release-inventory",
        )
    }

    @Test
    fun circuitBreakerSlidingWindowIgnoresExpiredFailuresBeforeOpening(): Unit = runBlocking {
        val breaker: CircuitBreaker = CircuitBreaker(
            resetTimeout = 1_000.milliseconds,
            openingStrategy = CircuitBreaker.OpeningStrategy.SlidingWindow(
                timeSource = TimeSource.Monotonic,
                windowDuration = 50.milliseconds,
                maxFailures = 1,
            ),
        )

        try {
            breaker.protectOrThrow { throw IllegalStateException("first failure") }
            throw AssertionError("Expected first call to fail")
        } catch (error: IllegalStateException) {
            assertThat(error).hasMessage("first failure")
        }
        assertThat(breaker.state() is CircuitBreaker.State.Closed).isTrue()

        delay(150.milliseconds)
        try {
            breaker.protectOrThrow { throw IllegalStateException("second failure") }
            throw AssertionError("Expected second call to fail")
        } catch (error: IllegalStateException) {
            assertThat(error).hasMessage("second failure")
        }
        assertThat(breaker.state() is CircuitBreaker.State.Closed).isTrue()

        try {
            breaker.protectOrThrow { throw IllegalStateException("third failure") }
            throw AssertionError("Expected third call to open the circuit breaker")
        } catch (error: IllegalStateException) {
            assertThat(error).hasMessage("third failure")
        }
        assertThat(breaker.state() is CircuitBreaker.State.Open).isTrue()
    }

    @Test
    fun circuitBreakerOpensRejectsAndThenClosesAfterSuccessfulResetAttempt(): Unit = runBlocking {
        val events: MutableList<String> = mutableListOf()
        val breaker: CircuitBreaker = CircuitBreaker(
            resetTimeout = 100.milliseconds,
            openingStrategy = CircuitBreaker.OpeningStrategy.Count(maxFailures = 0),
            onOpen = { events += "open" },
            onRejected = { events += "rejected" },
            onHalfOpen = { events += "half-open" },
            onClosed = { events += "closed" },
        )

        val overload: IllegalStateException = try {
            breaker.protectOrThrow { throw IllegalStateException("service overloaded") }
            throw AssertionError("Expected protected action to fail")
        } catch (error: IllegalStateException) {
            error
        }
        assertThat(overload).hasMessage("service overloaded")
        assertThat(breaker.state() is CircuitBreaker.State.Open).isTrue()

        var rejectedBlockExecuted: Boolean = false
        val rejected: Either<CircuitBreaker.ExecutionRejected, String> = breaker.protectEither {
            rejectedBlockExecuted = true
            "not executed"
        }
        val rejectionReason: String = rejected.fold(
            ifLeft = { it.reason },
            ifRight = { value -> "unexpected success $value" },
        )
        assertThat(rejectionReason).contains("Open")
        assertThat(rejectedBlockExecuted).isFalse()

        val waiter = async {
            breaker.awaitClose()
            events += "awaited-close"
        }

        delay(150.milliseconds)
        val resetValue: String = breaker.protectOrThrow { "healthy" }
        withTimeout(1_000.milliseconds) { waiter.await() }

        assertThat(resetValue).isEqualTo("healthy")
        assertThat(breaker.state() is CircuitBreaker.State.Closed).isTrue()
        assertThat(events.take(3)).containsExactly("open", "rejected", "half-open")
        assertThat(events.drop(3)).containsExactlyInAnyOrder("closed", "awaited-close")
    }

    @Test
    fun circuitBreakerReopensWithBoundedBackoffWhenHalfOpenAttemptFails(): Unit = runBlocking {
        val breaker: CircuitBreaker = CircuitBreaker(
            resetTimeout = 5.milliseconds,
            openingStrategy = CircuitBreaker.OpeningStrategy.Count(maxFailures = 0),
            exponentialBackoffFactor = 10.0,
            maxResetTimeout = 20.milliseconds,
        )

        try {
            breaker.protectOrThrow { throw IllegalStateException("first failure") }
            throw AssertionError("Expected first call to open the circuit breaker")
        } catch (error: IllegalStateException) {
            assertThat(error).hasMessage("first failure")
        }

        delay(10.milliseconds)
        try {
            breaker.protectOrThrow { throw IllegalStateException("half-open probe failed") }
            throw AssertionError("Expected half-open probe to fail")
        } catch (error: IllegalStateException) {
            assertThat(error).hasMessage("half-open probe failed")
        }

        val openState = breaker.state()
        assertThat(openState is CircuitBreaker.State.Open).isTrue()
        if (openState is CircuitBreaker.State.Open) {
            assertThat(openState.resetTimeout).isEqualTo(20.milliseconds)
        }
    }
}
