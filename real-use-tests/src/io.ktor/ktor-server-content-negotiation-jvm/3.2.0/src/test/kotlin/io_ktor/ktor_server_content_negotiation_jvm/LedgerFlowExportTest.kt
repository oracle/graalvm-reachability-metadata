/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_content_negotiation_jvm

import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises negotiated streaming JSON responses from a Kotlin Flow for an accounting ledger export.
 */
@Timeout(60)
class LedgerFlowExportTest {
    @Test
    fun exportsFilteredRowsAsACompleteJsonArrayIncludingAnEmptyLedger(): Unit = testApplication {
        val entries: List<LedgerEntry> = listOf(
            LedgerEntry("TX-101", "sales", 4200, "Invoice \"April\", first line\nsecond line"),
            LedgerEntry("TX-102", "expenses", -800, "Packaging"),
            LedgerEntry("TX-103", "sales", -300, "Refund \\ adjustment")
        )
        application {
            install(ContentNegotiation) {
                json()
            }
            routing {
                get("/ledger/export") {
                    val account: String = call.request.queryParameters["account"] ?: "sales"
                    // The response stays a Flow; content negotiation writes the JSON array framing.
                    val export: Flow<LedgerEntry> = entries.asFlow().filter { it.account == account }
                    call.respond(export)
                }
            }
        }

        val accountingClient = createClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 20_000
                socketTimeoutMillis = 20_000
            }
        }

        val salesResponse = accountingClient.get("/ledger/export?account=sales") {
            accept(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, salesResponse.status)
        assertEquals(ContentType.Application.Json, salesResponse.contentType()?.withoutParameters())
        val salesExport: List<LedgerEntry> = Json.decodeFromString(salesResponse.bodyAsText())
        assertEquals(listOf(entries[0], entries[2]), salesExport)
        assertEquals(3900, salesExport.sumOf { it.amountCents })

        // An account without transactions must still produce a complete, parseable JSON document.
        val emptyResponse = accountingClient.get("/ledger/export?account=closed") {
            accept(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, emptyResponse.status)
        assertEquals(ContentType.Application.Json, emptyResponse.contentType()?.withoutParameters())
        assertEquals("[]", emptyResponse.bodyAsText())

        // A subsequent export has its own flow and cannot reuse rows from the preceding account.
        val expensesResponse = accountingClient.get("/ledger/export?account=expenses") {
            accept(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, expensesResponse.status)
        assertEquals(ContentType.Application.Json, expensesResponse.contentType()?.withoutParameters())
        val expensesExport: List<LedgerEntry> = Json.decodeFromString(expensesResponse.bodyAsText())
        assertEquals(listOf(entries[1]), expensesExport)
    }

    @Serializable
    data class LedgerEntry(val id: String, val account: String, val amountCents: Int, val memo: String)
}
