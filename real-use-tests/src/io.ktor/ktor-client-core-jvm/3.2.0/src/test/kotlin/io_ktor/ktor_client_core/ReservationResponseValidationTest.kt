/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_core

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import io.ktor.server.cio.CIO as ServerCIO

/** Exercises HTTP response validation by translating missing reservations into application errors. */
class ReservationResponseValidationTest {
    @Test
    @Timeout(60)
    fun reportsMissingReservationAndStillRetrievesAnExistingBooking(): Unit = runBlocking {
        withTimeout(45_000) {
            val server = embeddedServer(ServerCIO, host = "127.0.0.1", port = 0) {
                routing {
                    get("/reservations/cancelled-17") {
                        call.respondText("This reservation was cancelled", status = HttpStatusCode.NotFound)
                    }
                    get("/reservations/booking-42") {
                        call.respondText("Confirmed: booking-42, seats A12 and A13")
                    }
                }
            }
            try {
                server.start(wait = false)
                val port: Int = server.engine.resolvedConnectors().single().port
                val baseUrl: String = "http://127.0.0.1:$port"
                HttpClient(CIO) {
                    expectSuccess = true
                    HttpResponseValidator {
                        handleResponseExceptionWithRequest { exception, request ->
                            val failure: ClientRequestException = exception as? ClientRequestException
                                ?: return@handleResponseExceptionWithRequest
                            if (failure.response.status == HttpStatusCode.NotFound) {
                                throw ReservationNotFoundException(
                                    request.url.encodedPath.substringAfterLast('/'),
                                    failure.response.bodyAsText()
                                )
                            }
                        }
                    }
                    install(HttpTimeout) {
                        requestTimeoutMillis = 15_000
                        connectTimeoutMillis = 15_000
                        socketTimeoutMillis = 15_000
                    }
                }.use { client: HttpClient ->
                    var missingReservation: ReservationNotFoundException? = null
                    try {
                        client.get("$baseUrl/reservations/cancelled-17")
                    } catch (exception: ReservationNotFoundException) {
                        missingReservation = exception
                    }
                    val reported: ReservationNotFoundException = requireNotNull(missingReservation) {
                        "The missing reservation must be reported as an application error"
                    }
                    assertEquals("cancelled-17", reported.reservationId)
                    assertEquals("This reservation was cancelled", reported.message)

                    val booking: HttpResponse = client.get("$baseUrl/reservations/booking-42")
                    assertEquals(HttpStatusCode.OK, booking.status)
                    assertEquals("Confirmed: booking-42, seats A12 and A13", booking.bodyAsText())
                }
            } finally {
                server.stop(0, 10_000)
            }
        }
    }

    class ReservationNotFoundException(val reservationId: String, reason: String) : RuntimeException(reason)
}
