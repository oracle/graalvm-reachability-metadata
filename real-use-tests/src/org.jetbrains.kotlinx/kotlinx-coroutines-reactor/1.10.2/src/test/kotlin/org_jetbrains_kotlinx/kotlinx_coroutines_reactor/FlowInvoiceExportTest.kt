/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_reactor

import java.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactor.asFlux
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import reactor.core.publisher.Flux
import reactor.test.StepVerifier

/**
 * Exercises Flow-to-Flux adaptation for a demand-driven paid-invoice export.
 * A reactive download consumes rows from a coroutine flow in bounded batches.
 */
@Timeout(60)
class FlowInvoiceExportTest {
    @Test
    fun streamsPaidInvoicesInOrderAsTheDownloadRequestsRows(): Unit {
        val exporter: InvoiceExporter = InvoiceExporter(
            listOf(
                Invoice("INV-101", "north", 12500, true),
                Invoice("INV-102", "south", 9000, false),
                Invoice("INV-103", "east", 4200, true),
                Invoice("INV-104", "west", 7500, true)
            )
        )
        val download: Flux<String> = exporter.paidRows().asFlux()

        StepVerifier.create(download, 0)
            .thenRequest(1)
            .expectNext("INV-101,north,12500\n")
            .thenRequest(3)
            .expectNext("INV-103,east,4200\n", "INV-104,west,7500\n")
            .expectComplete()
            .verify(Duration.ofSeconds(10))
    }

    private class InvoiceExporter(private val invoices: List<Invoice>) {
        fun paidRows(): Flow<String> = flow {
            for (invoice: Invoice in invoices) {
                emit(invoice)
            }
        }.filter { it.paid }
            .map { "${it.number},${it.customer},${it.totalCents}\n" }
    }

    private data class Invoice(
        val number: String,
        val customer: String,
        val totalCents: Int,
        val paid: Boolean
    )
}
