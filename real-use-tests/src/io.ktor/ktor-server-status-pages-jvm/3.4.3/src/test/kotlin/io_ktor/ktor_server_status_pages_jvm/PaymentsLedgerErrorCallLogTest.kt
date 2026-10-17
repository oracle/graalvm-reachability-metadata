/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_status_pages_jvm

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.slf4j.event.Level
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * The statuses of calls answered by `StatusPages` handlers as the `CallLogging`
 * call log records them, where a payments ledger keeps an error log of failed calls.
 *
 * The log filter selects calls by the status the handlers chose, so a declined
 * charge is logged as its 402, a bare 404 as the status page that replaced it, and
 * an unexpected failure as the 500 of the safety net, while successful calls and
 * concurrent failures are never confused with one another.
 */
public class PaymentsLedgerErrorCallLogTest {
    /** A charge the card issuer refused. */
    class PaymentDeclined(val reason: String) : RuntimeException("Payment declined: $reason")

    /** The ledger, with every line its error log writes recorded in [errorLog]. */
    private fun ApplicationTestBuilder.paymentsLedger(errorLog: ConcurrentLinkedQueue<String>) {
        application {
            install(CallLogging) {
                level = Level.INFO
                filter { call -> (call.response.status()?.value ?: 0) >= 400 }
                format { call ->
                    val line: String =
                        "${call.request.httpMethod.value} ${call.request.path()} -> ${call.response.status()?.value}"
                    errorLog += line
                    line
                }
            }
            install(StatusPages) {
                exception<PaymentDeclined> { call, cause ->
                    call.respondText(
                        """{"error":"declined","reason":"${cause.reason}"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.PaymentRequired,
                    )
                }
                exception<Throwable> { call, cause ->
                    call.respondText(
                        """{"error":"ledger-failure","detail":"${cause.message}"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.InternalServerError,
                    )
                }
                status(HttpStatusCode.NotFound) { call, status ->
                    call.respondText(
                        """{"error":"not-found","path":"${call.request.path()}"}""",
                        ContentType.Application.Json,
                        status,
                    )
                }
            }
            routing {
                post("/payments/{amount}") {
                    val amount: Int = call.parameters["amount"]!!.toInt()
                    if (amount > CARD_LIMIT) {
                        throw PaymentDeclined("amount $amount exceeds the card limit")
                    }
                    call.respondText("Charged $amount", status = HttpStatusCode.Created)
                }
                get("/payments/{id}") {
                    val id: String = call.parameters["id"]!!
                    val payment: String? = PAYMENTS[id]
                    if (payment == null) {
                        call.respond(HttpStatusCode.NotFound)
                    } else {
                        call.respondText(payment)
                    }
                }
                get("/statements/{month}") {
                    val month: String = call.parameters["month"]!!
                    error("statement store is offline for $month")
                }
                get("/health") {
                    call.respondText("ok")
                }
            }
        }
    }

    @Test
    fun successfulCallsStayOutOfTheErrorLog() = testApplication {
        val errorLog: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()
        paymentsLedger(errorLog)

        val charge: HttpResponse = client.post("/payments/120")
        val payment: HttpResponse = client.get("/payments/p-1")
        val health: HttpResponse = client.get("/health")
        val declined: HttpResponse = client.post("/payments/9000")

        assertEquals(HttpStatusCode.Created, charge.status)
        assertEquals("Charged 120", charge.bodyAsText())
        assertEquals("p-1: 120 EUR settled", payment.bodyAsText())
        assertEquals("ok", health.bodyAsText())
        assertEquals(HttpStatusCode.PaymentRequired, declined.status)
        awaitLogLines(errorLog, 1)
        assertEquals(listOf("POST /payments/9000 -> 402"), errorLog.toList())
    }

    @Test
    fun declinedChargeIsLoggedWithTheHandlersStatus() = testApplication {
        val errorLog: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()
        paymentsLedger(errorLog)

        val response: HttpResponse = client.post("/payments/9000")

        assertEquals(HttpStatusCode.PaymentRequired, response.status)
        assertEquals(
            """{"error":"declined","reason":"amount 9000 exceeds the card limit"}""",
            response.bodyAsText(),
        )
        awaitLogLines(errorLog, 1)
        assertEquals(listOf("POST /payments/9000 -> 402"), errorLog.toList())
    }

    @Test
    fun bareNotFoundIsLoggedAsTheStatusPageThatReplacedIt() = testApplication {
        val errorLog: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()
        paymentsLedger(errorLog)

        val unknownPayment: HttpResponse = client.get("/payments/p-404")
        val unknownPath: HttpResponse = client.get("/refunds")

        assertEquals(HttpStatusCode.NotFound, unknownPayment.status)
        assertEquals("""{"error":"not-found","path":"/payments/p-404"}""", unknownPayment.bodyAsText())
        assertEquals(HttpStatusCode.NotFound, unknownPath.status)
        assertEquals("""{"error":"not-found","path":"/refunds"}""", unknownPath.bodyAsText())
        awaitLogLines(errorLog, 2)
        assertEquals(setOf("GET /payments/p-404 -> 404", "GET /refunds -> 404"), errorLog.toSet())
    }

    @Test
    fun unexpectedFailureIsLoggedAsTheSafetyNetsStatus() = testApplication {
        val errorLog: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()
        paymentsLedger(errorLog)

        val response: HttpResponse = client.get("/statements/2026-09")

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(
            """{"error":"ledger-failure","detail":"statement store is offline for 2026-09"}""",
            response.bodyAsText(),
        )
        awaitLogLines(errorLog, 1)
        assertEquals(listOf("GET /statements/2026-09 -> 500"), errorLog.toList())
    }

    @Test
    fun concurrentFailuresAreEachLoggedWithTheirOwnStatus() = testApplication {
        val errorLog: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()
        paymentsLedger(errorLog)
        val amounts: List<Int> = (1..6).map { index -> CARD_LIMIT + index }

        val statuses: List<HttpStatusCode> = coroutineScope {
            val declines = amounts.map { amount -> async { client.post("/payments/$amount").status } }
            val missing = (1..3).map { index -> async { client.get("/payments/gone-$index").status } }
            val broken = async { client.get("/statements/2026-08").status }
            (declines + missing + broken).awaitAll()
        }

        assertEquals(
            List(amounts.size) { HttpStatusCode.PaymentRequired } +
                List(3) { HttpStatusCode.NotFound } +
                HttpStatusCode.InternalServerError,
            statuses,
        )
        val expected: Set<String> =
            amounts.map { amount -> "POST /payments/$amount -> 402" }.toSet() +
                (1..3).map { index -> "GET /payments/gone-$index -> 404" } +
                "GET /statements/2026-08 -> 500"
        awaitLogLines(errorLog, expected.size)
        assertEquals(expected, errorLog.toSet())
    }

    /** Waits, bounded, for [count] log lines; `CallLogging` writes them after the response is sent. */
    private suspend fun awaitLogLines(errorLog: ConcurrentLinkedQueue<String>, count: Int) {
        withTimeout(LOG_TIMEOUT_MILLIS) {
            while (errorLog.size < count) {
                delay(LOG_POLL_MILLIS)
            }
        }
    }

    private companion object {
        const val CARD_LIMIT: Int = 2500

        const val LOG_TIMEOUT_MILLIS: Long = 10_000

        const val LOG_POLL_MILLIS: Long = 10

        val PAYMENTS: Map<String, String> = mapOf("p-1" to "p-1: 120 EUR settled")
    }
}
