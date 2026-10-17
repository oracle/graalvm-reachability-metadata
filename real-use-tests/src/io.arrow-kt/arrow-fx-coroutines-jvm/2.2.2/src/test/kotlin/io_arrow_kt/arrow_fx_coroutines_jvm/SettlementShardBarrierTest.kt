/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_fx_coroutines_jvm

import arrow.fx.coroutines.CountDownLatch
import arrow.fx.coroutines.CyclicBarrier
import arrow.fx.coroutines.CyclicBarrierCancellationException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises Arrow's suspending synchronization primitives: a `CyclicBarrier` that holds shard
 * workers between the phases of a job and a `CountDownLatch` a coordinator awaits until every
 * shard has finished.
 *
 * A nightly settlement job totals each regional shard concurrently, waits at the barrier until all
 * subtotals exist, computes each region's share of the grand total, and meets again before the
 * coordinator, released by the latch, publishes the report.
 */
public class SettlementShardBarrierTest {

    /** The settlement published once every shard has finished both phases. */
    public data class SettlementReport(val grandTotal: Long, val sharePercent: Map<String, Long>)

    /** A two-phase settlement over [shards], with one worker coroutine per shard. */
    public class SettlementJob(private val shards: Map<String, List<Long>>) {

        private val closedPhases: AtomicInteger = AtomicInteger()

        /** Phase boundaries in the order the barrier closed them. */
        public val phaseLog: MutableList<String> = Collections.synchronizedList(mutableListOf())

        private val barrier: CyclicBarrier =
            CyclicBarrier(shards.size) { phaseLog += "phase ${closedPhases.incrementAndGet()} closed" }

        /** Counts down once per shard that has finished both phases. */
        public val finished: CountDownLatch = CountDownLatch(shards.size.toLong())

        private val subtotals: MutableMap<String, Long> = ConcurrentHashMap()
        private val shares: MutableMap<String, Long> = ConcurrentHashMap()

        public suspend fun run(): SettlementReport = coroutineScope {
            val report = async {
                finished.await()
                SettlementReport(subtotals.values.sum(), shares.toSortedMap())
            }
            for ((region, payments) in shards) {
                launch(Dispatchers.Default) {
                    subtotals[region] = payments.sum()
                    barrier.await()

                    val grandTotal: Long = subtotals.values.sum()
                    shares[region] = subtotals.getValue(region) * 100 / grandTotal
                    barrier.await()

                    finished.countDown()
                }
            }
            report.await()
        }
    }

    @Test
    fun shardsMeetAtEachPhaseBeforeTheCoordinatorPublishes() {
        val job = SettlementJob(
            mapOf(
                "emea" to listOf(120L, 80L, 50L),
                "apac" to listOf(300L, 200L),
                "amer" to listOf(150L, 100L),
            ),
        )

        val report: SettlementReport = runBlocking { withTimeout(30_000) { job.run() } }

        assertEquals(1_000L, report.grandTotal)
        assertEquals(mapOf("amer" to 25L, "apac" to 50L, "emea" to 25L), report.sharePercent)
        assertEquals(listOf("phase 1 closed", "phase 2 closed"), job.phaseLog.toList())
        assertEquals(0L, job.finished.count())
    }

    @Test
    fun resetCancelsWaitingShardsAndTheBarrierServesTheNextRun() {
        val trips: AtomicInteger = AtomicInteger()
        val barrier = CyclicBarrier(2) { trips.incrementAndGet() }
        val waiterFailure: AtomicReference<Throwable> = AtomicReference()

        runBlocking {
            val stranded: Job = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    barrier.await()
                } catch (cancelled: CyclicBarrierCancellationException) {
                    waiterFailure.set(cancelled)
                    throw cancelled
                }
            }
            barrier.reset()
            stranded.join()
            assertTrue(stranded.isCancelled, "a party waiting at reset must be cancelled")

            val latch = CountDownLatch(2)
            repeat(2) {
                launch {
                    barrier.await()
                    latch.countDown()
                }
            }
            withTimeout(30_000) { latch.await() }
            assertEquals(0L, latch.count())
        }

        assertTrue(waiterFailure.get() is CyclicBarrierCancellationException)
        assertEquals(2, barrier.capacity)
        assertEquals(1, trips.get(), "only the full second run trips the barrier")
    }
}
