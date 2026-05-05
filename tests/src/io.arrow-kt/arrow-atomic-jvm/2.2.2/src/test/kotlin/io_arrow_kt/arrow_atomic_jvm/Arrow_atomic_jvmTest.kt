/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_atomic_jvm

import arrow.atomic.Atomic
import arrow.atomic.AtomicBoolean
import arrow.atomic.AtomicInt
import arrow.atomic.AtomicLong
import arrow.atomic.getAndUpdate
import arrow.atomic.loop
import arrow.atomic.tryUpdate
import arrow.atomic.update
import arrow.atomic.updateAndGet
import arrow.atomic.value
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

public class Arrow_atomic_jvmTest {
    @Test
    fun atomicBooleanSupportsBasicCompareAndSetOperations() {
        val flag = AtomicBoolean(false)

        assertThat(flag.get()).isFalse()
        assertThat(flag.value).isFalse()

        flag.value = true
        assertThat(flag.get()).isTrue()

        flag.set(false)
        assertThat(flag.value).isFalse()
        assertThat(flag.compareAndSet(false, true)).isTrue()
        assertThat(flag.value).isTrue()
        assertThat(flag.compareAndSet(false, true)).isFalse()
        assertThat(flag.getAndSet(false)).isTrue()
        assertThat(flag.value).isFalse()
    }

    @Test
    fun atomicBooleanSupportsPublicUpdateFunctions() {
        val flag = AtomicBoolean(false)

        assertThat(flag.tryUpdate { current -> !current }).isTrue()
        assertThat(flag.value).isTrue()

        flag.update { current -> !current }
        assertThat(flag.value).isFalse()

        val previous = flag.getAndUpdate { current -> !current }
        assertThat(previous).isFalse()
        assertThat(flag.value).isTrue()

        val updated = flag.updateAndGet { current -> !current }
        assertThat(updated).isFalse()
        assertThat(flag.value).isFalse()
    }

    @Test
    fun atomicBooleanTryUpdateReportsCompareAndSetFailure() {
        val flag = AtomicBoolean(false)

        val updated = flag.tryUpdate { current ->
            flag.value = !current
            !current
        }

        assertThat(updated).isFalse()
        assertThat(flag.value).isTrue()
    }

    @Test
    fun atomicBooleanLoopContinuouslySuppliesLatestValueUntilActionStops() {
        val flag = AtomicBoolean(false)
        val observedValues = mutableListOf<Boolean>()

        val failure = assertThrows<IllegalStateException> {
            flag.loop { current ->
                observedValues += current
                if (current) {
                    throw IllegalStateException("boolean loop stopped")
                }
                flag.value = true
            }
        }

        assertThat(failure).hasMessage("boolean loop stopped")
        assertThat(observedValues).containsExactly(false, true)
        assertThat(flag.value).isTrue()
    }

    @Test
    fun atomicIntSupportsActualAtomicOperationsAndValueProperty() {
        val counter = AtomicInt(1)

        assertThat(counter.get()).isEqualTo(1)
        assertThat(counter.value).isEqualTo(1)

        counter.value = 2
        assertThat(counter.get()).isEqualTo(2)

        counter.set(3)
        assertThat(counter.value).isEqualTo(3)
        assertThat(counter.getAndSet(10)).isEqualTo(3)
        assertThat(counter.value).isEqualTo(10)
        assertThat(counter.incrementAndGet()).isEqualTo(11)
        assertThat(counter.decrementAndGet()).isEqualTo(10)
        assertThat(counter.addAndGet(5)).isEqualTo(15)
        assertThat(counter.compareAndSet(15, 20)).isTrue()
        assertThat(counter.compareAndSet(15, 30)).isFalse()
        assertThat(counter.value).isEqualTo(20)
    }

    @Test
    fun atomicIntSupportsGetThenArithmeticOperations() {
        val counter = AtomicInt(10)

        assertThat(counter.getAndIncrement()).isEqualTo(10)
        assertThat(counter.value).isEqualTo(11)
        assertThat(counter.getAndDecrement()).isEqualTo(11)
        assertThat(counter.value).isEqualTo(10)
        assertThat(counter.getAndAdd(5)).isEqualTo(10)
        assertThat(counter.value).isEqualTo(15)
    }

    @Test
    fun atomicIntArithmeticFollowsJvmOverflowSemantics() {
        val counter = AtomicInt(Int.MAX_VALUE)

        assertThat(counter.incrementAndGet()).isEqualTo(Int.MIN_VALUE)
        assertThat(counter.getAndAdd(-1)).isEqualTo(Int.MIN_VALUE)
        assertThat(counter.value).isEqualTo(Int.MAX_VALUE)
        assertThat(counter.addAndGet(2)).isEqualTo(Int.MIN_VALUE + 1)
    }

    @Test
    fun atomicIntSupportsPublicUpdateFunctionsAndRetriesOnInterference() {
        val counter = AtomicInt(0)
        val seenValues = mutableListOf<Int>()

        assertThat(counter.tryUpdate { current -> current + 1 }).isTrue()
        assertThat(counter.value).isEqualTo(1)

        assertThat(
            counter.tryUpdate { current ->
                counter.value = 10
                current + 1
            },
        ).isFalse()
        assertThat(counter.value).isEqualTo(10)

        counter.update { current ->
            seenValues += current
            if (seenValues.size == 1) {
                counter.value = 20
            }
            current + 1
        }
        assertThat(seenValues).containsExactly(10, 20)
        assertThat(counter.value).isEqualTo(21)

        val previous = counter.getAndUpdate { current -> current + 4 }
        assertThat(previous).isEqualTo(21)
        assertThat(counter.value).isEqualTo(25)

        val updated = counter.updateAndGet { current -> current * 2 }
        assertThat(updated).isEqualTo(50)
        assertThat(counter.value).isEqualTo(50)
    }

    @Test
    fun atomicIntCompareAndExchangeReturnsWitnessValueAndOnlyUpdatesOnMatch() {
        val counter = AtomicInt(4)

        val failedWitness = counter.compareAndExchange(0, 9)

        assertThat(failedWitness).isEqualTo(4)
        assertThat(counter.value).isEqualTo(4)

        val successfulWitness = counter.compareAndExchange(4, 9)

        assertThat(successfulWitness).isEqualTo(4)
        assertThat(counter.value).isEqualTo(9)
    }

    @Test
    fun atomicIntUpdatesAreSafeUnderContention() {
        val counter = AtomicInt(0)
        val workers = 4
        val incrementsPerWorker = 500
        val ready = CountDownLatch(workers)
        val start = CountDownLatch(1)
        val done = CountDownLatch(workers)
        val executor = Executors.newFixedThreadPool(workers)

        try {
            repeat(workers) {
                executor.execute {
                    ready.countDown()
                    if (start.await(5, TimeUnit.SECONDS)) {
                        repeat(incrementsPerWorker) {
                            counter.update { current -> current + 1 }
                        }
                    }
                    done.countDown()
                }
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()
            start.countDown()
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue()
        } finally {
            executor.shutdownNow()
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue()
        }

        assertThat(counter.value).isEqualTo(workers * incrementsPerWorker)
    }

    @Test
    fun atomicLoopContinuouslySuppliesLatestValueUntilActionStops() {
        val counter = AtomicInt(1)
        val observedValues = mutableListOf<Int>()

        val failure = assertThrows<IllegalStateException> {
            counter.loop { current ->
                observedValues += current
                if (current == 3) {
                    throw IllegalStateException("loop stopped")
                }
                counter.value = current + 1
            }
        }

        assertThat(failure).hasMessage("loop stopped")
        assertThat(observedValues).containsExactly(1, 2, 3)
        assertThat(counter.value).isEqualTo(3)
    }

    @Test
    fun atomicLongSupportsActualAtomicOperationsAndValueProperty() {
        val total = AtomicLong(100L)

        assertThat(total.get()).isEqualTo(100L)
        assertThat(total.value).isEqualTo(100L)

        total.value = 125L
        assertThat(total.get()).isEqualTo(125L)

        total.set(150L)
        assertThat(total.value).isEqualTo(150L)
        assertThat(total.getAndSet(200L)).isEqualTo(150L)
        assertThat(total.value).isEqualTo(200L)
        assertThat(total.incrementAndGet()).isEqualTo(201L)
        assertThat(total.decrementAndGet()).isEqualTo(200L)
        assertThat(total.addAndGet(50L)).isEqualTo(250L)
        assertThat(total.compareAndSet(250L, 300L)).isTrue()
        assertThat(total.compareAndSet(250L, 400L)).isFalse()
        assertThat(total.value).isEqualTo(300L)
    }

    @Test
    fun atomicLongSupportsGetThenArithmeticOperations() {
        val total = AtomicLong(10L)

        assertThat(total.getAndIncrement()).isEqualTo(10L)
        assertThat(total.value).isEqualTo(11L)
        assertThat(total.getAndDecrement()).isEqualTo(11L)
        assertThat(total.value).isEqualTo(10L)
        assertThat(total.getAndAdd(5L)).isEqualTo(10L)
        assertThat(total.value).isEqualTo(15L)
    }

    @Test
    fun atomicLongArithmeticFollowsJvmOverflowSemantics() {
        val total = AtomicLong(Long.MAX_VALUE)

        assertThat(total.incrementAndGet()).isEqualTo(Long.MIN_VALUE)
        assertThat(total.getAndAdd(-1L)).isEqualTo(Long.MIN_VALUE)
        assertThat(total.value).isEqualTo(Long.MAX_VALUE)
        assertThat(total.addAndGet(2L)).isEqualTo(Long.MIN_VALUE + 1L)
    }

    @Test
    fun atomicLongSupportsPublicUpdateFunctionsAndReportsFailedTryUpdate() {
        val total = AtomicLong(7L)

        assertThat(total.tryUpdate { current -> current + 1L }).isTrue()
        assertThat(total.value).isEqualTo(8L)

        assertThat(
            total.tryUpdate { current ->
                total.value = 99L
                current + 1L
            },
        ).isFalse()
        assertThat(total.value).isEqualTo(99L)

        total.update { current -> current / 3L }
        assertThat(total.value).isEqualTo(33L)

        val previous = total.getAndUpdate { current -> current + 9L }
        assertThat(previous).isEqualTo(33L)
        assertThat(total.value).isEqualTo(42L)

        val updated = total.updateAndGet { current -> current * 2L }
        assertThat(updated).isEqualTo(84L)
        assertThat(total.value).isEqualTo(84L)
    }

    @Test
    fun atomicLongCompareAndExchangeReturnsWitnessValueAndOnlyUpdatesOnMatch() {
        val total = AtomicLong(40L)

        val failedWitness = total.compareAndExchange(0L, 99L)

        assertThat(failedWitness).isEqualTo(40L)
        assertThat(total.value).isEqualTo(40L)

        val successfulWitness = total.compareAndExchange(40L, 99L)

        assertThat(successfulWitness).isEqualTo(40L)
        assertThat(total.value).isEqualTo(99L)
    }

    @Test
    fun atomicLongLoopContinuouslySuppliesLatestValueUntilActionStops() {
        val total = AtomicLong(1L)
        val observedValues = mutableListOf<Long>()

        val failure = assertThrows<IllegalStateException> {
            total.loop { current ->
                observedValues += current
                if (current == 3L) {
                    throw IllegalStateException("long loop stopped")
                }
                total.value = current + 1L
            }
        }

        assertThat(failure).hasMessage("long loop stopped")
        assertThat(observedValues).containsExactly(1L, 2L, 3L)
        assertThat(total.value).isEqualTo(3L)
    }

    @Test
    fun atomicReferenceSupportsActualAtomicOperationsAndValueProperty() {
        val state = Atomic(State(step = 1, label = "created"))

        assertThat(state.get()).isEqualTo(State(1, "created"))
        assertThat(state.value).isEqualTo(State(1, "created"))

        state.value = State(step = 2, label = "assigned")
        assertThat(state.get()).isEqualTo(State(2, "assigned"))

        state.set(State(step = 3, label = "set"))
        assertThat(state.value).isEqualTo(State(3, "set"))
        assertThat(state.getAndSet(State(step = 4, label = "swapped"))).isEqualTo(State(3, "set"))
        assertThat(state.value).isEqualTo(State(4, "swapped"))

        val expected = state.value
        val replacement = State(step = 5, label = "cas")
        assertThat(state.compareAndSet(expected, replacement)).isTrue()
        assertThat(state.compareAndSet(expected, State(step = 6, label = "stale"))).isFalse()
        assertThat(state.value).isSameAs(replacement)
    }

    @Test
    fun atomicReferenceSupportsPolymorphicStateTransitions() {
        val state = Atomic<ConnectionState>(Disconnected(reason = "idle"))

        val previous = state.getAndUpdate { current ->
            when (current) {
                is Disconnected -> Connected(activeRequests = current.reason.length)
                is Connected -> current.copy(activeRequests = current.activeRequests + 1)
            }
        }

        assertThat(previous).isEqualTo(Disconnected(reason = "idle"))
        assertThat(state.value).isEqualTo(Connected(activeRequests = 4))

        val updated = state.updateAndGet { current ->
            when (current) {
                is Disconnected -> current
                is Connected -> current.copy(activeRequests = current.activeRequests + 1)
            }
        }

        assertThat(updated).isEqualTo(Connected(activeRequests = 5))
        assertThat(state.value).isEqualTo(Connected(activeRequests = 5))
    }

    @Test
    fun atomicReferenceSupportsNullableValues() {
        val state = Atomic<String?>(null)

        assertThat(state.value).isNull()
        assertThat(state.compareAndSet(null, "ready")).isTrue()
        assertThat(state.value).isEqualTo("ready")
        assertThat(state.getAndSet(null)).isEqualTo("ready")
        assertThat(state.value).isNull()

        state.update { current -> current ?: "created" }
        assertThat(state.value).isEqualTo("created")
    }

    @Test
    fun atomicReferenceCompareAndSetRequiresTheSameExpectedInstance() {
        val initial = State(step = 1, label = "same value")
        val equalButDifferentInstance = initial.copy()
        val replacement = State(step = 2, label = "replacement")
        val state = Atomic(initial)

        assertThat(equalButDifferentInstance).isEqualTo(initial)
        assertThat(equalButDifferentInstance).isNotSameAs(initial)
        assertThat(state.compareAndSet(equalButDifferentInstance, replacement)).isFalse()
        assertThat(state.value).isSameAs(initial)

        assertThat(state.compareAndSet(initial, replacement)).isTrue()
        assertThat(state.value).isSameAs(replacement)
    }

    @Test
    fun atomicReferenceSupportsPublicUpdateFunctionsAndReportsFailedTryUpdate() {
        val state = Atomic(State(step = 1, label = "initial"))

        assertThat(state.tryUpdate { current -> current.copy(step = current.step + 1) }).isTrue()
        assertThat(state.value).isEqualTo(State(2, "initial"))

        assertThat(
            state.tryUpdate { current ->
                state.value = State(step = 50, label = "interfering write")
                current.copy(step = current.step + 1)
            },
        ).isFalse()
        assertThat(state.value).isEqualTo(State(50, "interfering write"))

        state.update { current -> current.copy(label = "updated") }
        assertThat(state.value).isEqualTo(State(50, "updated"))

        val previous = state.getAndUpdate { current -> current.copy(step = current.step + 1) }
        assertThat(previous).isEqualTo(State(50, "updated"))
        assertThat(state.value).isEqualTo(State(51, "updated"))

        val updated = state.updateAndGet { current -> current.copy(label = "finished") }
        assertThat(updated).isEqualTo(State(51, "finished"))
        assertThat(state.value).isEqualTo(State(51, "finished"))
    }

    @Test
    fun atomicReferenceLoopContinuouslySuppliesLatestValueUntilActionStops() {
        val state = Atomic(State(step = 1, label = "looping"))
        val observedValues = mutableListOf<State>()

        val failure = assertThrows<IllegalStateException> {
            state.loop { current ->
                observedValues += current
                if (current.step == 3) {
                    throw IllegalStateException("reference loop stopped")
                }
                state.value = current.copy(step = current.step + 1)
            }
        }

        assertThat(failure).hasMessage("reference loop stopped")
        assertThat(observedValues).containsExactly(
            State(step = 1, label = "looping"),
            State(step = 2, label = "looping"),
            State(step = 3, label = "looping"),
        )
        assertThat(state.value).isEqualTo(State(step = 3, label = "looping"))
    }

    @Test
    fun atomicReferenceUpdateOverloadReturnsTransformResultForCommittedTransition() {
        val state = Atomic(State(step = 1, label = "initial"))
        val attemptedValues = mutableListOf<State>()
        val committedTransitions = mutableListOf<String>()

        val result = state.update(
            { current ->
                attemptedValues += current
                if (attemptedValues.size == 1) {
                    state.value = State(step = 10, label = "interfering write")
                }
                current.copy(step = current.step + 1)
            },
            { previous, updated ->
                val transition = "${previous.step}->${updated.step}"
                committedTransitions += transition
                transition
            },
        )

        assertThat(attemptedValues).containsExactly(
            State(step = 1, label = "initial"),
            State(step = 10, label = "interfering write"),
        )
        assertThat(result).isEqualTo("10->11")
        assertThat(committedTransitions).containsExactly("10->11")
        assertThat(state.value).isEqualTo(State(step = 11, label = "interfering write"))
    }

    @Test
    fun atomicReferenceTryUpdateOverloadInvokesCallbackOnlyAfterSuccessfulCompareAndSet() {
        val state = Atomic(State(step = 5, label = "ready"))
        val transitions = mutableListOf<String>()

        val succeeded = state.tryUpdate(
            { current -> current.copy(step = current.step + 2) },
            { previous, updated -> transitions += "${previous.step}->${updated.step}" },
        )

        assertThat(succeeded).isTrue()
        assertThat(transitions).containsExactly("5->7")
        assertThat(state.value).isEqualTo(State(step = 7, label = "ready"))

        val failed = state.tryUpdate(
            { current ->
                state.value = State(step = 100, label = "interfering write")
                current.copy(step = current.step + 1)
            },
            { previous, updated -> transitions += "${previous.step}->${updated.step}" },
        )

        assertThat(failed).isFalse()
        assertThat(transitions).containsExactly("5->7")
        assertThat(state.value).isEqualTo(State(step = 100, label = "interfering write"))
    }

    private sealed interface ConnectionState

    private data class Disconnected(
        val reason: String,
    ) : ConnectionState

    private data class Connected(
        val activeRequests: Int,
    ) : ConnectionState

    private data class State(
        val step: Int,
        val label: String,
    )
}
