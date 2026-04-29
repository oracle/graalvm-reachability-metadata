/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.atomicfu

import kotlinx.atomicfu.AtomicBooleanArray
import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.AtomicLongArray
import kotlinx.atomicfu.Trace
import kotlinx.atomicfu.TraceFormat
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.getAndUpdate
import kotlinx.atomicfu.loop
import kotlinx.atomicfu.update
import kotlinx.atomicfu.updateAndGet
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.named
import kotlinx.atomicfu.locks.synchronized as atomicSynchronized
import kotlinx.atomicfu.locks.withLock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference as JdkAtomicReference

public class AtomicfuTest {
    @Test
    fun atomicIntSupportsPrimitiveAndFunctionalOperations(): Unit {
        val counter = atomic(10)

        assertThat(counter.value).isEqualTo(10)
        counter.value = 11
        assertThat(counter.value).isEqualTo(11)
        counter.lazySet(12)
        assertThat(counter.value).isEqualTo(12)
        assertThat(counter.compareAndSet(99, 13)).isFalse()
        assertThat(counter.value).isEqualTo(12)
        assertThat(counter.compareAndSet(12, 13)).isTrue()
        assertThat(counter.getAndSet(20)).isEqualTo(13)
        assertThat(counter.getAndIncrement()).isEqualTo(20)
        assertThat(counter.incrementAndGet()).isEqualTo(22)
        assertThat(counter.getAndDecrement()).isEqualTo(22)
        assertThat(counter.decrementAndGet()).isEqualTo(20)
        assertThat(counter.getAndAdd(5)).isEqualTo(20)
        assertThat(counter.addAndGet(-2)).isEqualTo(23)

        counter += 7
        assertThat(counter.value).isEqualTo(30)
        counter -= 4
        assertThat(counter.value).isEqualTo(26)
        counter.update { it * 2 }
        assertThat(counter.value).isEqualTo(52)
        assertThat(counter.getAndUpdate { it - 10 }).isEqualTo(52)
        assertThat(counter.updateAndGet { it / 2 }).isEqualTo(21)
        assertThat(counter.toString()).isEqualTo("21")
    }

    @Test
    fun atomicLongSupportsWideValuesAndFunctionalOperations(): Unit {
        val sequence = atomic(Long.MAX_VALUE - 4)

        assertThat(sequence.getAndIncrement()).isEqualTo(Long.MAX_VALUE - 4)
        assertThat(sequence.incrementAndGet()).isEqualTo(Long.MAX_VALUE - 2)
        assertThat(sequence.getAndAdd(-10L)).isEqualTo(Long.MAX_VALUE - 2)
        assertThat(sequence.addAndGet(5L)).isEqualTo(Long.MAX_VALUE - 7)
        assertThat(sequence.compareAndSet(Long.MAX_VALUE, 0L)).isFalse()
        assertThat(sequence.compareAndSet(Long.MAX_VALUE - 7, -3L)).isTrue()
        assertThat(sequence.getAndDecrement()).isEqualTo(-3L)
        assertThat(sequence.decrementAndGet()).isEqualTo(-5L)

        sequence += 100L
        assertThat(sequence.value).isEqualTo(95L)
        sequence -= 40L
        assertThat(sequence.value).isEqualTo(55L)
        assertThat(sequence.getAndSet(8L)).isEqualTo(55L)
        sequence.update { it * it }
        assertThat(sequence.getAndUpdate { it + 1 }).isEqualTo(64L)
        assertThat(sequence.updateAndGet { it * 3 }).isEqualTo(195L)
        sequence.lazySet(200L)
        assertThat(sequence.value).isEqualTo(200L)
        assertThat(sequence.toString()).isEqualTo("200")
    }

    @Test
    fun atomicBooleanSupportsTransitionsAndUpdateExtensions(): Unit {
        val flag = atomic(false)

        assertThat(flag.value).isFalse()
        flag.value = true
        assertThat(flag.value).isTrue()
        assertThat(flag.compareAndSet(false, true)).isFalse()
        assertThat(flag.compareAndSet(true, false)).isTrue()
        assertThat(flag.getAndSet(true)).isFalse()
        flag.lazySet(false)
        assertThat(flag.value).isFalse()

        flag.update { !it }
        assertThat(flag.value).isTrue()
        assertThat(flag.getAndUpdate { !it }).isTrue()
        assertThat(flag.updateAndGet { !it }).isTrue()
        assertThat(flag.toString()).isEqualTo("true")
    }

    @Test
    fun atomicReferenceSupportsReferenceSwapsAndComputedUpdates(): Unit {
        val initial = Box("initial", 1)
        val replacement = Box("replacement", 2)
        val current = atomic(initial)

        assertThat(current.value).isSameAs(initial)
        assertThat(current.compareAndSet(Box("initial", 1), replacement)).isFalse()
        assertThat(current.compareAndSet(initial, replacement)).isTrue()
        assertThat(current.value).isSameAs(replacement)
        assertThat(current.getAndSet(initial)).isSameAs(replacement)
        current.lazySet(replacement)
        assertThat(current.value).isSameAs(replacement)

        current.update { it.copy(count = it.count + 10) }
        assertThat(current.value).isEqualTo(Box("replacement", 12))
        assertThat(current.getAndUpdate { it.copy(name = "old-${it.name}") })
            .isEqualTo(Box("replacement", 12))
        assertThat(current.updateAndGet { it.copy(count = it.count * 2) })
            .isEqualTo(Box("old-replacement", 24))
        assertThat(current.toString()).isEqualTo("Box(name=old-replacement, count=24)")
    }

    @Test
    fun atomicPropertiesCanBeUsedAsKotlinDelegates(): Unit {
        val state = DelegatedState()

        assertThat(state.number).isEqualTo(1)
        assertThat(state.enabled).isFalse()
        assertThat(state.label).isEqualTo("cold")

        state.number = 41
        state.enabled = true
        state.label = "warm"

        assertThat(state.bumpNumber()).isEqualTo(42)
        assertThat(state.flipEnabled()).isFalse()
        assertThat(state.replaceLabel("hot")).isEqualTo("warm")
        assertThat(state.snapshot()).isEqualTo("42:false:hot")
    }

    @Test
    fun atomicArraysExposeIndependentAtomicElements(): Unit {
        val ints = AtomicIntArray(3)
        val longs = AtomicLongArray(2)
        val booleans = AtomicBooleanArray(2)
        val refs = atomicArrayOfNulls<String>(3)

        assertThat(ints.size).isEqualTo(3)
        assertThat(longs.size).isEqualTo(2)
        assertThat(booleans.size).isEqualTo(2)
        assertThat(refs.size).isEqualTo(3)

        assertThat(ints[0].incrementAndGet()).isEqualTo(1)
        assertThat(ints[1].addAndGet(10)).isEqualTo(10)
        assertThat(ints[2].compareAndSet(0, -1)).isTrue()
        assertThat((0 until ints.size).map { ints[it].value }).containsExactly(1, 10, -1)

        assertThat(longs[0].getAndAdd(7L)).isEqualTo(0L)
        longs[1].lazySet(100L)
        assertThat((0 until longs.size).map { longs[it].value }).containsExactly(7L, 100L)

        assertThat(booleans[0].compareAndSet(false, true)).isTrue()
        assertThat(booleans[1].getAndSet(true)).isFalse()
        booleans[1].update { !it }
        assertThat((0 until booleans.size).map { booleans[it].value }).containsExactly(true, false)

        assertThat(refs[0].value).isNull()
        assertThat(refs[0].compareAndSet(null, "first")).isTrue()
        assertThat(refs[1].getAndSet("second")).isNull()
        refs[2].update { value -> value ?: "third" }
        assertThat((0 until refs.size).map { refs[it].value }).containsExactly("first", "second", "third")
    }

    @Test
    fun loopExtensionRetriesWithFreshValuesUntilCallerReturns(): Unit {
        val state = atomic(1)
        val observed = mutableListOf<Int>()

        fun claimEvenValue(): Int {
            state.loop { value ->
                observed.add(value)
                if (value % 2 != 0) {
                    state.incrementAndGet()
                    return@loop
                }
                if (state.compareAndSet(value, value + 10)) {
                    return value
                }
            }
        }

        assertThat(claimEvenValue()).isEqualTo(2)
        assertThat(state.value).isEqualTo(12)
        assertThat(observed).containsExactly(1, 2)
    }

    @Test
    fun traceRecordsManualAndNamedAtomicEventsWithCustomFormatting(): Unit {
        val trace = Trace(16, TraceFormat { index, event -> "[$index] $event" })
        val named = trace.named("state")
        val state = atomic(1, named)

        trace.append("start")
        state.value = 2
        assertThat(state.compareAndSet(9, 10)).isFalse()
        assertThat(state.compareAndSet(2, 3)).isTrue()
        assertThat(state.getAndSet(4)).isEqualTo(3)
        state.lazySet(5)
        trace.append("finish", state.value)

        assertThat(trace.toString().lines()).containsExactly(
            "[0] start",
            "[1] state.set(2)",
            "[2] state.CAS(2, 3)",
            "[3] state.getAndSet(4):3",
            "[4] state.lazySet(5)",
            "[5] finish",
            "[6] 5"
        )
    }

    @Test
    fun traceActsAsRingBufferAndValidatesCapacity(): Unit {
        assertThrows(IllegalArgumentException::class.java) {
            Trace(0)
        }

        val trace = Trace(4, TraceFormat { index, event -> "$index=$event" })
        trace.append("a", "b")
        trace.append("c", "d", "e")

        assertThat(trace.toString().lines()).containsExactly(
            "1=b",
            "2=c",
            "3=d",
            "4=e"
        )
    }

    @Test
    fun atomicOperationsRemainCorrectUnderContention(): Unit {
        val counter = atomic(0)
        val toggles = atomic(0L)
        val owner = atomic<String?>(null)

        runConcurrently(workerCount = 6) { workerIndex ->
            repeat(1_000) { iteration ->
                counter.getAndIncrement()
                toggles.update { it xor 1L }
                if (iteration == workerIndex) {
                    owner.compareAndSet(null, "worker-$workerIndex")
                }
            }
        }

        assertThat(counter.value).isEqualTo(6_000)
        assertThat(toggles.value).isEqualTo(0L)
        assertThat(owner.value).startsWith("worker-")
    }

    @Test
    fun locksSerializeCompoundActionsAndSupportReentrancy(): Unit {
        val lock = reentrantLock()
        val guarded = atomic(0)
        val monitor = Any()
        val log = Collections.synchronizedList(mutableListOf<Int>())

        lock.withLock {
            lock.withLock {
                guarded.value = 1
            }
        }

        runConcurrently(workerCount = 5) { workerIndex ->
            repeat(300) {
                lock.withLock {
                    val current = guarded.value
                    Thread.yield()
                    guarded.value = current + 1
                }
            }
            atomicSynchronized(monitor) {
                log.add(workerIndex)
            }
        }

        assertThat(guarded.value).isEqualTo(1 + 5 * 300)
        assertThat(log).containsExactlyInAnyOrder(0, 1, 2, 3, 4)
    }

    private fun runConcurrently(workerCount: Int, action: (Int) -> Unit): Unit {
        val start = CountDownLatch(1)
        val done = CountDownLatch(workerCount)
        val failure = JdkAtomicReference<Throwable?>()

        repeat(workerCount) { workerIndex ->
            Thread {
                try {
                    start.await()
                    action(workerIndex)
                } catch (throwable: Throwable) {
                    failure.compareAndSet(null, throwable)
                } finally {
                    done.countDown()
                }
            }.apply {
                name = "atomicfu-test-worker-$workerIndex"
                start()
            }
        }

        start.countDown()
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue()
        failure.get()?.let { throw it }
    }

    private data class Box(val name: String, val count: Int)

    private class DelegatedState {
        private val atomicNumber = atomic(1)
        private val atomicEnabled = atomic(false)
        private val atomicLabel = atomic("cold")

        var number by atomicNumber
        var enabled by atomicEnabled
        var label by atomicLabel

        fun bumpNumber(): Int = atomicNumber.incrementAndGet()

        fun flipEnabled(): Boolean = atomicEnabled.updateAndGet { !it }

        fun replaceLabel(newLabel: String): String = atomicLabel.getAndSet(newLabel)

        fun snapshot(): String = "$number:$enabled:$label"
    }
}
