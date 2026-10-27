/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_logging_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodedPath
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Collections

/**
 * Request filtering with `filter` predicates in the `Logging` client plugin.
 *
 * A payment service client talks to its backend for payments, health probes, and catalog
 * lookups. Operations wants only the money-moving traffic in the log: every call under
 * `/payments`, plus any call an operator has flagged with an `X-Debug` header. Health probes
 * and ordinary catalog reads must still succeed but leave no trace in the log.
 */
class PaymentApiRequestFilterTest {

    /** Collects every message the plugin emits, as an operations log would. */
    class OperationsLog : Logger {
        private val entries: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override fun log(message: String) {
            entries += message
        }

        fun lines(): List<String> = entries.toList().flatMap { it.lines() }.map { it.trim() }
    }

    private val backend: MockEngine = MockEngine { request ->
        val reply: String = when (request.url.encodedPath) {
            "/health" -> "UP"
            "/payments" -> if (request.method == HttpMethod.Post) "pay-1001" else "[pay-1001]"
            "/payments/pay-1001" -> "cancelled"
            "/catalog/items" -> "[book,pen]"
            else -> "unknown"
        }
        respond(reply, HttpStatusCode.OK, headersOf("Content-Type", "text/plain"))
    }

    @Test
    fun onlyPaymentAndFlaggedCallsAreLogged() {
        val operationsLog = OperationsLog()
        val client = HttpClient(backend) {
            install(Logging) {
                logger = operationsLog
                level = LogLevel.INFO
                filter { request -> request.url.encodedPath.startsWith("/payments") }
                filter { request -> request.headers.contains("X-Debug") }
            }
        }

        val replies: List<String> = client.use {
            runBlocking {
                listOf(
                    it.get("https://pay.example.test/health").bodyAsText(),
                    it.post("https://pay.example.test/payments").bodyAsText(),
                    it.get("https://pay.example.test/catalog/items").bodyAsText(),
                    it.get("https://pay.example.test/payments").bodyAsText(),
                    it.get("https://pay.example.test/catalog/items?sku=pen") {
                        header("X-Debug", "ticket-77")
                    }.bodyAsText(),
                    it.delete("https://pay.example.test/payments/pay-1001").bodyAsText(),
                )
            }
        }

        assertEquals(listOf("UP", "pay-1001", "[book,pen]", "[pay-1001]", "[book,pen]", "cancelled"), replies)
        assertEquals(6, backend.requestHistory.size, "every call reaches the backend, logged or not")

        val lines = operationsLog.lines()
        val everything = lines.joinToString("\n")
        val requestLines: List<String> = lines.filter { it.startsWith("REQUEST:") }
        assertEquals(
            listOf(
                "REQUEST: https://pay.example.test/payments",
                "REQUEST: https://pay.example.test/payments",
                "REQUEST: https://pay.example.test/catalog/items?sku=pen",
                "REQUEST: https://pay.example.test/payments/pay-1001",
            ),
            requestLines,
            everything,
        )
        val methods: List<String> = lines.indices
            .filter { lines[it].startsWith("REQUEST:") }
            .map { start -> lines[start + 1] }
        assertEquals(listOf("METHOD: POST", "METHOD: GET", "METHOD: GET", "METHOD: DELETE"), methods, everything)
        assertEquals(4, lines.count { it == "RESPONSE: 200 OK" }, everything)
        assertFalse(everything.contains("/health"), "health probes must not be logged:\n$everything")
        assertTrue(
            lines.none { it.endsWith("/catalog/items") },
            "unflagged catalog reads must not be logged:\n$everything",
        )
    }
}
