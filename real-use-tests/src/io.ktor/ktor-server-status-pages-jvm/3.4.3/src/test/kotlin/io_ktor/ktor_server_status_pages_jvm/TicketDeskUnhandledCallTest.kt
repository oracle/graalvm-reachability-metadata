/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_status_pages_jvm

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The fallback handler for calls no route answers, where a help-desk API turns
 * every request left without a response into one "no such endpoint" document.
 *
 * Unknown paths and routes that decline to answer both reach `unhandled`, while
 * routes that respond themselves, even with an error status, and failures that
 * an exception handler maps are never rewritten by it.
 */
public class TicketDeskUnhandledCallTest {
    /** The help desk, with every call the fallback handler sees recorded in [unhandledCalls]. */
    private fun ApplicationTestBuilder.ticketDesk(unhandledCalls: MutableList<String>) {
        application {
            install(StatusPages) {
                exception<IllegalStateException> { call, cause ->
                    call.respondText(
                        "Ticket desk failure: ${cause.message}",
                        status = HttpStatusCode.Conflict,
                    )
                }
                unhandled { call ->
                    val endpoint: String = "${call.request.httpMethod.value} ${call.request.uri}"
                    unhandledCalls += endpoint
                    call.respondText(
                        """{"error":"no-such-endpoint","endpoint":"$endpoint"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.NotFound,
                    )
                }
            }
            routing {
                get("/tickets/{id}") {
                    val id: Int = call.parameters["id"]!!.toInt()
                    val subject: String? = TICKETS[id]
                    if (subject == null) {
                        call.respondText("Ticket $id does not exist", status = HttpStatusCode.NotFound)
                    } else {
                        call.respondText("#$id $subject")
                    }
                }
                get("/reports/{name}") {
                    // Reports are rolled out one by one; an unknown report leaves the call unanswered.
                    val name: String = call.parameters["name"]!!
                    if (name in PUBLISHED_REPORTS) {
                        call.respondText("Report $name: ${TICKETS.size} open tickets")
                    }
                }
                post("/tickets/{id}/close") {
                    val id: Int = call.parameters["id"]!!.toInt()
                    check(id in TICKETS) { "ticket $id is already closed" }
                    call.respondText("Closed #$id")
                }
            }
        }
    }

    @Test
    fun answeredRoutesNeverReachTheFallback() = testApplication {
        val unhandledCalls: MutableList<String> = mutableListOf()
        ticketDesk(unhandledCalls)

        val ticket: HttpResponse = client.get("/tickets/7")
        val report: HttpResponse = client.get("/reports/weekly")

        assertEquals(HttpStatusCode.OK, ticket.status)
        assertEquals("#7 Printer jams on page two", ticket.bodyAsText())
        assertEquals(HttpStatusCode.OK, report.status)
        assertEquals("Report weekly: 2 open tickets", report.bodyAsText())
        assertEquals(emptyList<String>(), unhandledCalls)
    }

    @Test
    fun unknownEndpointsGetTheFallbackDocument() = testApplication {
        val unhandledCalls: MutableList<String> = mutableListOf()
        ticketDesk(unhandledCalls)

        val unknownGet: HttpResponse = client.get("/customers?page=2")
        val unknownDelete: HttpResponse = client.delete("/tickets")

        assertEquals(HttpStatusCode.NotFound, unknownGet.status)
        assertTrue(unknownGet.contentType()!!.match(ContentType.Application.Json))
        assertEquals(
            """{"error":"no-such-endpoint","endpoint":"GET /customers?page=2"}""",
            unknownGet.bodyAsText(),
        )
        assertEquals(HttpStatusCode.NotFound, unknownDelete.status)
        assertEquals(
            """{"error":"no-such-endpoint","endpoint":"DELETE /tickets"}""",
            unknownDelete.bodyAsText(),
        )
        assertEquals(listOf("GET /customers?page=2", "DELETE /tickets"), unhandledCalls)
    }

    @Test
    fun routeThatDeclinesToAnswerFallsBack() = testApplication {
        val unhandledCalls: MutableList<String> = mutableListOf()
        ticketDesk(unhandledCalls)

        val response: HttpResponse = client.get("/reports/quarterly")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(
            """{"error":"no-such-endpoint","endpoint":"GET /reports/quarterly"}""",
            response.bodyAsText(),
        )
        assertEquals(listOf("GET /reports/quarterly"), unhandledCalls)
    }

    @Test
    fun explicitErrorsAndMappedFailuresKeepTheirOwnResponse() = testApplication {
        val unhandledCalls: MutableList<String> = mutableListOf()
        ticketDesk(unhandledCalls)

        val missingTicket: HttpResponse = client.get("/tickets/404")
        val closedTicket: HttpResponse = client.post("/tickets/3/close")

        assertEquals(HttpStatusCode.NotFound, missingTicket.status)
        assertTrue(missingTicket.contentType()!!.match(ContentType.Text.Plain))
        assertEquals("Ticket 404 does not exist", missingTicket.bodyAsText())
        assertEquals(HttpStatusCode.Conflict, closedTicket.status)
        assertEquals("Ticket desk failure: ticket 3 is already closed", closedTicket.bodyAsText())
        assertEquals(emptyList<String>(), unhandledCalls)
    }

    private companion object {
        val TICKETS: Map<Int, String> = mapOf(7 to "Printer jams on page two", 12 to "VPN drops at noon")

        val PUBLISHED_REPORTS: Set<String> = setOf("weekly")
    }
}
