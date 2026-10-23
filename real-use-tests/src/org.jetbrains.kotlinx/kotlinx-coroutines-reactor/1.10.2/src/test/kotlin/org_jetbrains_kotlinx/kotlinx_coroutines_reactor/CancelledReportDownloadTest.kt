/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_reactor

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.reactor.mono
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

/**
 * Exercises structured child-coroutine cancellation when a coroutine Mono subscriber disconnects.
 * An abandoned invoice download releases its scratch file and stops its pending report work.
 */
@Timeout(60)
class CancelledReportDownloadTest {
    @Test
    fun cancelsAbandonedReportWorkAndAllowsANewDownload(): Unit {
        val invoices: CompletableDeferred<List<Invoice>> = CompletableDeferred()
        val abandoned: ReportDownload = ReportDownload(invoices)

        StepVerifier.create(abandoned.response())
            .then {
                assertTrue(abandoned.started.await(10, TimeUnit.SECONDS))
                assertTrue(Files.exists(checkNotNull(abandoned.scratchFile)))
            }
            .thenCancel()
            .verify(Duration.ofSeconds(10))

        assertTrue(abandoned.released.await(10, TimeUnit.SECONDS))
        assertEquals(0L, abandoned.childStopped.count)
        assertFalse(Files.exists(checkNotNull(abandoned.scratchFile)))
        assertFalse(invoices.isCancelled, "Disconnecting must not cancel the shared invoice source")

        invoices.complete(listOf(Invoice("INV-41", 2400), Invoice("INV-42", 1750)))
        val retry: ReportDownload = ReportDownload(invoices)

        StepVerifier.create(retry.response())
            .expectNext("invoice,total-cents\nINV-41,2400\nINV-42,1750\n")
            .expectComplete()
            .verify(Duration.ofSeconds(10))

        assertEquals(0L, retry.childStopped.count)
        assertEquals(0L, retry.released.count)
        assertFalse(Files.exists(checkNotNull(retry.scratchFile)))
    }

    private class ReportDownload(private val invoices: Deferred<List<Invoice>>) {
        val started: CountDownLatch = CountDownLatch(1)
        val childStopped: CountDownLatch = CountDownLatch(1)
        val released: CountDownLatch = CountDownLatch(1)
        @Volatile
        var scratchFile: Path? = null
            private set

        fun response(): Mono<String> = mono {
            val file: Path = Files.createTempFile("invoice-download-", ".csv")
            scratchFile = file
            try {
                coroutineScope {
                    val rows: Deferred<String> = async {
                        try {
                            started.countDown()
                            invoices.await().joinToString("\n") { invoice: Invoice ->
                                "${invoice.number},${invoice.totalCents}"
                            }
                        } finally {
                            childStopped.countDown()
                        }
                    }
                    Files.writeString(file, "invoice,total-cents\n${rows.await()}\n")
                    Files.readString(file)
                }
            } finally {
                Files.deleteIfExists(file)
                released.countDown()
            }
        }
    }

    private data class Invoice(val number: String, val totalCents: Int)
}
