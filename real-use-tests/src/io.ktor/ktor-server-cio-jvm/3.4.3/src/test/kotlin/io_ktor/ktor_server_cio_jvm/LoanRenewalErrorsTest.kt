/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises CIO exception and status response handling through a library loan renewal API using StatusPages.
 */
class LoanRenewalErrorsTest {
    @Test
    @Timeout(60)
    fun `returns actionable renewal errors without losing the ability to process valid requests`(): Unit = runBlocking {
        val loans: ConcurrentHashMap<String, Loan> = ConcurrentHashMap(
            mapOf("loan-17" to Loan(7, false), "loan-23" to Loan(7, true)),
        )
        val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            install(StatusPages) {
                exception<BadRequestException> { call, _ ->
                    call.respondText(
                        "renewal days must be an integer from 1 to 14",
                        ContentType.Text.Plain,
                        HttpStatusCode.BadRequest,
                    )
                }
                exception<RenewalBlocked> { call, _ ->
                    call.respondText(
                        "renewal unavailable: another reader has a hold",
                        ContentType.Text.Plain,
                        HttpStatusCode.Conflict,
                    )
                }
                status(HttpStatusCode.NotFound) { call, status ->
                    call.respondText("loan not found", ContentType.Text.Plain, status)
                }
            }
            routing {
                post("/loans/{id}/renew") {
                    val id: String = checkNotNull(call.parameters["id"])
                    val loan: Loan? = loans[id]
                    if (loan == null) {
                        call.respond(HttpStatusCode.NotFound)
                        return@post
                    }
                    val days: Int = call.receiveText().toIntOrNull()
                        ?: throw BadRequestException("Invalid renewal duration")
                    if (days !in 1..14) throw BadRequestException("Renewal duration outside policy")
                    if (loan.onHold) throw RenewalBlocked("Hold belongs to internal reader account reader-42")
                    val renewed: Loan = loan.copy(daysRemaining = loan.daysRemaining + days)
                    loans[id] = renewed
                    call.respondText("renewed:$id:${renewed.daysRemaining}", ContentType.Text.Plain)
                }
            }
        }
        try {
            withTimeout(40_000) {
                server.start(wait = false)
                val port: Int = server.engine.resolvedConnectors().single().port
                HttpClient(ClientCIO) {
                    expectSuccess = false
                    install(HttpTimeout) {
                        connectTimeoutMillis = 15_000
                        requestTimeoutMillis = 15_000
                        socketTimeoutMillis = 15_000
                    }
                }.use { client: HttpClient ->
                    val baseUrl: String = "http://127.0.0.1:$port/loans"
                    assertReply(
                        renew(client, baseUrl, "loan-17", "next week"),
                        HttpStatusCode.BadRequest,
                        "renewal days must be an integer from 1 to 14",
                    )
                    assertReply(
                        renew(client, baseUrl, "loan-17", "15"),
                        HttpStatusCode.BadRequest,
                        "renewal days must be an integer from 1 to 14",
                    )
                    assertReply(
                        renew(client, baseUrl, "loan-23", "7"),
                        HttpStatusCode.Conflict,
                        "renewal unavailable: another reader has a hold",
                    )
                    assertReply(
                        renew(client, baseUrl, "missing", "7"),
                        HttpStatusCode.NotFound,
                        "loan not found",
                    )
                    // Failed renewals must not extend a loan, and later requests must still succeed.
                    assertReply(
                        renew(client, baseUrl, "loan-17", "7"),
                        HttpStatusCode.OK,
                        "renewed:loan-17:14",
                    )
                    assertReply(
                        renew(client, baseUrl, "loan-17", "3"),
                        HttpStatusCode.OK,
                        "renewed:loan-17:17",
                    )
                }
            }
        } finally {
            server.stop(0, 10_000)
        }
    }

    private suspend fun renew(client: HttpClient, baseUrl: String, id: String, days: String): HttpResponse =
        client.post("$baseUrl/$id/renew") {
            contentType(ContentType.Text.Plain)
            setBody(days)
        }

    private suspend fun assertReply(response: HttpResponse, status: HttpStatusCode, body: String): Unit {
        assertEquals(status, response.status)
        assertEquals(ContentType.Text.Plain, response.contentType()?.withoutParameters())
        assertEquals(body, response.bodyAsText())
    }

    private data class Loan(val daysRemaining: Int, val onHold: Boolean)

    private class RenewalBlocked(message: String) : RuntimeException(message)
}
