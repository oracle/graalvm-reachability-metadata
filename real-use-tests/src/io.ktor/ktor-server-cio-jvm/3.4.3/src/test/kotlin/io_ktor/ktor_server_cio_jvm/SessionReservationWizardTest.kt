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
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sessions.SessionStorageMemory
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises CIO cookie-backed sessions through a multi-request workshop reservation wizard.
 */
class SessionReservationWizardTest {
    @Test
    @Timeout(60)
    fun `retains a browser draft across steps and clears it after confirmation`(): Unit = runBlocking {
        val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            install(Sessions) {
                cookie<ReservationDraft>("reservation-draft", SessionStorageMemory()) {
                    cookie.path = "/"
                    cookie.httpOnly = true
                    cookie.extensions["SameSite"] = "Lax"
                }
            }
            routing {
                post("/reservation/draft") {
                    val form: Parameters = call.receiveParameters()
                    val draft: ReservationDraft = ReservationDraft(
                        checkNotNull(form["workshop"]),
                        checkNotNull(form["seats"]).toInt(),
                    )
                    call.sessions.set(draft)
                    call.respondText("draft saved", ContentType.Text.Plain)
                }
                get("/reservation/review") {
                    val draft: ReservationDraft? = call.sessions.get<ReservationDraft>()
                    if (draft == null) {
                        call.respondText("no reservation draft", ContentType.Text.Plain, HttpStatusCode.NotFound)
                    } else {
                        call.respondText("${draft.workshop}:${draft.seats}", ContentType.Text.Plain)
                    }
                }
                post("/reservation/confirm") {
                    val draft: ReservationDraft? = call.sessions.get<ReservationDraft>()
                    if (draft == null) {
                        call.respondText("no reservation draft", ContentType.Text.Plain, HttpStatusCode.Conflict)
                    } else {
                        call.sessions.clear<ReservationDraft>()
                        call.respondText(
                            "confirmed:${draft.workshop}:${draft.seats}",
                            ContentType.Text.Plain,
                            HttpStatusCode.Created,
                        )
                    }
                }
            }
        }
        try {
            withTimeout(40_000) {
                server.start(wait = false)
                val port: Int = server.engine.resolvedConnectors().single().port
                val baseUrl: String = "http://127.0.0.1:$port/reservation"
                browser().use { customer: HttpClient ->
                    browser().use { visitor: HttpClient ->
                        assertEquals(HttpStatusCode.NotFound, customer.get("$baseUrl/review").status)
                        val saved: HttpResponse = customer.submitForm(
                            "$baseUrl/draft",
                            Parameters.build {
                                append("workshop", "Wood & Paper + Design")
                                append("seats", "2")
                            },
                        )
                        assertEquals(HttpStatusCode.OK, saved.status)
                        assertEquals("draft saved", saved.bodyAsText())
                        assertTrue(checkNotNull(saved.headers[HttpHeaders.SetCookie]).contains("HttpOnly", true))

                        val review: HttpResponse = customer.get("$baseUrl/review")
                        assertEquals(HttpStatusCode.OK, review.status)
                        assertEquals("Wood & Paper + Design:2", review.bodyAsText())
                        val separateBrowser: HttpResponse = visitor.get("$baseUrl/review")
                        assertEquals(HttpStatusCode.NotFound, separateBrowser.status)
                        assertEquals("no reservation draft", separateBrowser.bodyAsText())

                        val confirmation: HttpResponse = customer.post("$baseUrl/confirm")
                        assertEquals(HttpStatusCode.Created, confirmation.status)
                        assertEquals("confirmed:Wood & Paper + Design:2", confirmation.bodyAsText())
                        val cleared: HttpResponse = customer.get("$baseUrl/review")
                        assertEquals(HttpStatusCode.NotFound, cleared.status)
                        assertEquals("no reservation draft", cleared.bodyAsText())
                        val repeated: HttpResponse = customer.post("$baseUrl/confirm")
                        assertEquals(HttpStatusCode.Conflict, repeated.status)
                        assertEquals("no reservation draft", repeated.bodyAsText())
                    }
                }
            }
        } finally {
            server.stop(0, 10_000)
        }
    }

    private fun browser(): HttpClient = HttpClient(ClientCIO) {
        install(HttpCookies)
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 15_000
            socketTimeoutMillis = 15_000
        }
    }

    @Serializable
    data class ReservationDraft(val workshop: String, val seats: Int)
}
