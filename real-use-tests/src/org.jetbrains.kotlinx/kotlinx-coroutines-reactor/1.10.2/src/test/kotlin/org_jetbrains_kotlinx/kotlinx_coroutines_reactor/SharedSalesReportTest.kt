/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_reactor

import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.reactor.asMono
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

/**
 * Exercises Deferred-to-Mono adaptation for a shared asynchronous sales-report job.
 * A disconnected client does not cancel report generation for other subscribers.
 */
@Timeout(60)
class SharedSalesReportTest {
    @Test
    fun deliversTheSharedReportAfterTheFirstClientDisconnects(): Unit {
        runBlocking {
            val sales: CompletableDeferred<List<Sale>> = CompletableDeferred()
            val generator: ReportGenerator = ReportGenerator(sales)
            val job: Deferred<SalesReport> = async(Dispatchers.Default) {
                generator.generate()
            }
            val download: Mono<SalesReport> = job.asMono(Dispatchers.Default)

            StepVerifier.create(download)
                .thenCancel()
                .verify(Duration.ofSeconds(10))

            assertFalse(job.isCancelled)
            assertFalse(job.isCompleted)

            val expected: SalesReport = SalesReport(3, 15750, mapOf("north" to 13500, "south" to 2250))
            StepVerifier.create(download)
                .then {
                    sales.complete(
                        listOf(
                            Sale("north", 10000),
                            Sale("south", 2250),
                            Sale("north", 3500)
                        )
                    )
                }
                .expectNext(expected)
                .expectComplete()
                .verify(Duration.ofSeconds(10))

            StepVerifier.create(download)
                .expectNext(expected)
                .expectComplete()
                .verify(Duration.ofSeconds(10))

            assertEquals(1, generator.generations.get())
        }
    }

    private class ReportGenerator(private val sales: Deferred<List<Sale>>) {
        val generations: AtomicInteger = AtomicInteger()

        suspend fun generate(): SalesReport {
            generations.incrementAndGet()
            val rows: List<Sale> = sales.await()
            val totals: Map<String, Int> = rows.groupBy { it.region }
                .mapValues { (_, regionalSales) -> regionalSales.sumOf { it.totalCents } }
            return SalesReport(rows.size, rows.sumOf { it.totalCents }, totals)
        }
    }

    private data class Sale(val region: String, val totalCents: Int)

    private data class SalesReport(
        val saleCount: Int,
        val totalCents: Int,
        val totalsByRegion: Map<String, Int>
    )
}
