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
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.Trace
import kotlinx.atomicfu.TraceFormat
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.getAndUpdate
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.synchronized as atomicSynchronized
import kotlinx.atomicfu.locks.withLock
import kotlinx.atomicfu.loop
import kotlinx.atomicfu.named
import kotlinx.atomicfu.update
import kotlinx.atomicfu.updateAndGet
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

public class AtomicfuTest {
    @Test
    fun atomicIntSupportsCasArithmeticAndFunctionalUpdates() {
        val counter = atomic(10)

        assertThat(counter.value).isEqualTo(10)
        counter.value = 11
        assertThat(counter.value).isEqualTo(11)
        counter.lazySet(12)
        assertThat(counter.value).isEqualTo(12)
        assertThat(counter.compareAndSet(11, 99)).isFalse()
        assertThat(counter.compareAndSet(12, 13)).isTrue()
        assertThat(counter.getAndSet(20)).isEqualTo(13)
        assertThat(counter.getAndIncrement()).isEqualTo(20)
        assertThat(counter.incrementAndGet()).isEqualTo(22)
        assertThat(counter.getAndDecrement()).isEqualTo(22)
        assertThat(counter.decrementAndGet()).isEqualTo(20)
        assertThat(counter.getAndAdd(5)).isEqualTo(20)
        assertThat(counter.addAndGet(-2)).isEqualTo(23)

        counter += 7
        counter -= 4
        counter.update { current -> current * 2 }

        assertThat(counter.getAndUpdate { current -> current + 1 }).isEqualTo(52)
        assertThat(counter.updateAndGet { current -> current / 3 }).isEqualTo(17)
        assertThat(counter.toString()).isEqualTo("17")
    }

    @Test
    fun atomicLongSupportsCasArithmeticAndFunctionalUpdates() {
        val sequence = atomic(1_000_000_000_000L)

        assertThat(sequence.value).isEqualTo(1_000_000_000_000L)
        sequence.value = 41L
        sequence.lazySet(42L)
        assertThat(sequence.value).isEqualTo(42L)
        assertThat(sequence.compareAndSet(41L, 0L)).isFalse()
        assertThat(sequence.compareAndSet(42L, 100L)).isTrue()
        assertThat(sequence.getAndSet(200L)).isEqualTo(100L)
        assertThat(sequence.getAndIncrement()).isEqualTo(200L)
        assertThat(sequence.incrementAndGet()).isEqualTo(202L)
        assertThat(sequence.getAndDecrement()).isEqualTo(202L)
        assertThat(sequence.decrementAndGet()).isEqualTo(200L)
        assertThat(sequence.getAndAdd(75L)).isEqualTo(200L)
        assertThat(sequence.addAndGet(25L)).isEqualTo(300L)

        sequence += 12L
        sequence -= 7L
        sequence.update { current -> current * 2L }

        assertThat(sequence.getAndUpdate { current -> current - 10L }).isEqualTo(610L)
        assertThat(sequence.updateAndGet { current -> current / 4L }).isEqualTo(150L)
        assertThat(sequence.toString()).isEqualTo("150")
    }

    @Test
    fun atomicBooleanAndReferencePreserveIdentityAndUpdateSemantics() {
        val enabled = atomic(false)

        assertThat(enabled.value).isFalse()
        enabled.value = true
        enabled.lazySet(false)
        assertThat(enabled.compareAndSet(true, false)).isFalse()
        assertThat(enabled.compareAndSet(false, true)).isTrue()
        assertThat(enabled.getAndSet(false)).isTrue()
        enabled.update { current -> !current }
        assertThat(enabled.value).isTrue()
        assertThat(enabled.getAndUpdate { current -> !current }).isTrue()
        assertThat(enabled.updateAndGet { current -> !current }).isTrue()
        assertThat(enabled.toString()).isEqualTo("true")

        val initial = VersionedState(1, "created")
        val replacement = VersionedState(2, "replacement")
        val reference = atomic(initial)

        assertThat(reference.value).isSameAs(initial)
        assertThat(reference.compareAndSet(VersionedState(1, "created"), replacement)).isFalse()
        assertThat(reference.compareAndSet(initial, replacement)).isTrue()
        assertThat(reference.value).isSameAs(replacement)
        reference.lazySet(VersionedState(3, "lazy"))
        assertThat(reference.value).isEqualTo(VersionedState(3, "lazy"))
        assertThat(reference.getAndSet(VersionedState(4, "set"))).isEqualTo(VersionedState(3, "lazy"))
        reference.update { state -> state.copy(version = state.version + 1) }
        assertThat(reference.getAndUpdate { state -> state.copy(label = "old-was-${state.version}") })
            .isEqualTo(VersionedState(5, "set"))
        assertThat(reference.updateAndGet { state -> state.copy(version = state.version + 10) })
            .isEqualTo(VersionedState(15, "old-was-5"))
        assertThat(reference.toString()).isEqualTo("VersionedState(version=15, label=old-was-5)")
    }

    @Test
    fun atomicArraysExposeIndependentAtomicCells() {
        val intArray = AtomicIntArray(3)
        assertThat(intArray.size).isEqualTo(3)
        intArray[0].value = 10
        assertThat(intArray[1].incrementAndGet()).isEqualTo(1)
        assertThat(intArray[2].addAndGet(5)).isEqualTo(5)
        assertThat(listOf(intArray[0].value, intArray[1].value, intArray[2].value)).containsExactly(10, 1, 5)

        val longArray = AtomicLongArray(2)
        assertThat(longArray.size).isEqualTo(2)
        assertThat(longArray[0].getAndAdd(9L)).isEqualTo(0L)
        assertThat(longArray[1].compareAndSet(0L, Long.MAX_VALUE)).isTrue()
        assertThat(listOf(longArray[0].value, longArray[1].value)).containsExactly(9L, Long.MAX_VALUE)

        val booleanArray = AtomicBooleanArray(2)
        assertThat(booleanArray.size).isEqualTo(2)
        assertThat(booleanArray[0].compareAndSet(false, true)).isTrue()
        assertThat(booleanArray[1].getAndSet(true)).isFalse()
        assertThat(listOf(booleanArray[0].value, booleanArray[1].value)).containsExactly(true, true)

        val referenceArray = atomicArrayOfNulls<String>(3)
        assertThat(referenceArray.size).isEqualTo(3)
        assertThat(referenceArray[0].value).isNull()
        assertThat(referenceArray[0].compareAndSet(null, "zero")).isTrue()
        referenceArray[1].value = "one"
        assertThat(referenceArray[2].getAndSet("two")).isNull()
        assertThat(listOf(referenceArray[0].value, referenceArray[1].value, referenceArray[2].value))
            .containsExactly("zero", "one", "two")
    }

    @Test
    fun delegatedPropertiesUseAtomicGettersAndSetters() {
        val holder = DelegatedAtomicHolder()

        assertThat(holder.count).isEqualTo(7)
        assertThat(holder.name).isEqualTo("initial")
        assertThat(holder.ready).isFalse()
        assertThat(holder.timestamp).isEqualTo(100L)

        holder.count += 5
        holder.name = "changed"
        holder.ready = true
        holder.timestamp = 250L

        assertThat(holder.count).isEqualTo(12)
        assertThat(holder.name).isEqualTo("changed")
        assertThat(holder.ready).isTrue()
        assertThat(holder.timestamp).isEqualTo(250L)
    }

    @Test
    fun tracesRecordManualNamedAndAtomicEvents() {
        val trace = Trace(size = 8, format = TraceFormat { index, event -> "$index|$event" })
        val counter = atomic(0, trace.named("counter"))

        trace { "start" }
        counter.value = 1
        assertThat(counter.compareAndSet(1, 2)).isTrue()
        assertThat(counter.compareAndSet(1, 3)).isFalse()
        assertThat(counter.getAndAdd(5)).isEqualTo(2)
        trace.named("manual").append("first", "second")
        counter.lazySet(10)
        trace.append("done")

        assertThat(trace.toString().lines()).containsExactly(
            "0|start",
            "1|counter.set(1)",
            "2|counter.CAS(1, 2)",
            "3|counter.getAndAdd(5):2",
            "4|manual.first",
            "5|manual.second",
            "6|counter.lazySet(10)",
            "7|done",
        )
    }

    @Test
    fun loopRetriesWithLatestAtomicReferenceValueUntilOperationCompletes() {
        val pending = atomic(listOf("alpha", "beta"))
        val observedSnapshots = mutableListOf<List<String>>()

        val removed = removeFirstAfterSyntheticRace(pending, observedSnapshots)

        assertThat(removed).isEqualTo("urgent")
        assertThat(observedSnapshots).containsExactly(
            listOf("alpha", "beta"),
            listOf("urgent", "alpha", "beta"),
        )
        assertThat(pending.value).containsExactly("alpha", "beta")
    }

    @Test
    fun locksAndMonitorSynchronizationCoordinateMutableStateAcrossThreads() {
        val lock = reentrantLock()
        val monitor = Any()
        val lockedTotal = atomic(0)
        val synchronizedTotal = atomic(0)
        val workerCount = 4
        val iterationsPerWorker = 250
        val start = CountDownLatch(1)
        val finished = CountDownLatch(workerCount)

        val workers = (1..workerCount).map {
            Thread {
                start.await()
                repeat(iterationsPerWorker) {
                    lock.withLock {
                        lockedTotal.value = lockedTotal.value + 1
                    }
                    atomicSynchronized(monitor) {
                        synchronizedTotal.value = synchronizedTotal.value + 1
                    }
                }
                finished.countDown()
            }
        }

        workers.forEach(Thread::start)
        start.countDown()

        assertThat(finished.await(10, TimeUnit.SECONDS)).isTrue()
        workers.forEach { worker ->
            worker.join(1_000L)
            assertThat(worker.isAlive).isFalse()
        }
        assertThat(lockedTotal.value).isEqualTo(workerCount * iterationsPerWorker)
        assertThat(synchronizedTotal.value).isEqualTo(workerCount * iterationsPerWorker)
    }

    @Test
    fun atomicUpdatesRemainCorrectUnderContention() {
        val intCounter = atomic(0)
        val longCounter = atomic(0L)
        val winnerChosen = atomic(false)
        val winners = atomic(0)
        val seenWorkers = atomic(emptySet<Int>())
        val workerCount = 4
        val iterationsPerWorker = 500
        val start = CountDownLatch(1)
        val finished = CountDownLatch(workerCount)

        val workers = (0 until workerCount).map { workerId ->
            Thread {
                start.await()
                if (winnerChosen.compareAndSet(false, true)) {
                    winners.incrementAndGet()
                }
                seenWorkers.update { current -> current + workerId }
                repeat(iterationsPerWorker) {
                    intCounter.incrementAndGet()
                    longCounter.getAndAdd(2L)
                }
                finished.countDown()
            }
        }

        workers.forEach(Thread::start)
        start.countDown()

        assertThat(finished.await(10, TimeUnit.SECONDS)).isTrue()
        workers.forEach { worker ->
            worker.join(1_000L)
            assertThat(worker.isAlive).isFalse()
        }
        assertThat(intCounter.value).isEqualTo(workerCount * iterationsPerWorker)
        assertThat(longCounter.value).isEqualTo(workerCount * iterationsPerWorker * 2L)
        assertThat(winnerChosen.value).isTrue()
        assertThat(winners.value).isEqualTo(1)
        assertThat(seenWorkers.value).containsExactlyInAnyOrder(0, 1, 2, 3)
    }
}

private fun removeFirstAfterSyntheticRace(
    pending: AtomicRef<List<String>>,
    observedSnapshots: MutableList<List<String>>,
): String? {
    var interfereOnce = true
    return pending.loop { snapshot ->
        observedSnapshots += snapshot
        if (snapshot.isEmpty()) {
            return null
        }
        if (interfereOnce) {
            interfereOnce = false
            pending.value = listOf("urgent") + snapshot
        }
        if (pending.compareAndSet(snapshot, snapshot.drop(1))) {
            return snapshot.first()
        }
    }
}

public data class VersionedState(public val version: Int, public val label: String)

public class DelegatedAtomicHolder {
    private val atomicCount = atomic(7)
    private val atomicName = atomic("initial")
    private val atomicReady = atomic(false)
    private val atomicTimestamp = atomic(100L)

    public var count: Int by atomicCount
    public var name: String by atomicName
    public var ready: Boolean by atomicReady
    public var timestamp: Long by atomicTimestamp
}
