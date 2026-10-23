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
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises request-conversion failure handling with negotiated JSON errors for a support-ticket API.
 */
@Timeout(60)
class RequestConversionFailureTest {
    @Test
    fun rejectsUnreadableTicketsWithoutPersistingThemAndAcceptsTheNextValidTicket(): Unit = testApplication {
        val tickets: ConcurrentLinkedQueue<Ticket> = ConcurrentLinkedQueue()
        application {
            install(ContentNegotiation) {
                json()
            }
            install(StatusPages) {
                exception<BadRequestException> { call, _ ->
                    call.respond(HttpStatusCode.BadRequest, Problem("invalid-ticket"))
                }
                status(HttpStatusCode.UnsupportedMediaType) { call, status ->
                    call.respond(status, Problem("json-required"))
                }
            }
            routing {
                post("/tickets") {
                    val ticket: Ticket = call.receive<Ticket>()
                    tickets.add(ticket)
                    call.respond(HttpStatusCode.Created, ticket)
                }
                get("/tickets") {
                    call.respond(tickets.toList())
                }
            }
        }

        val helpdeskClient = createClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 20_000
                socketTimeoutMillis = 20_000
            }
        }

        val malformedResponse = helpdeskClient.post("/tickets") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody("""{"title":"Printer offline","priority": }""")
        }
        assertEquals(HttpStatusCode.BadRequest, malformedResponse.status)
        assertEquals(ContentType.Application.Json, malformedResponse.contentType()?.withoutParameters())
        assertEquals(Problem("invalid-ticket"), Json.decodeFromString<Problem>(malformedResponse.bodyAsText()))

        val unsupportedResponse = helpdeskClient.post("/tickets") {
            contentType(ContentType.Text.Plain)
            accept(ContentType.Application.Json)
            setBody("Printer offline, priority 2")
        }
        assertEquals(HttpStatusCode.UnsupportedMediaType, unsupportedResponse.status)
        assertEquals(ContentType.Application.Json, unsupportedResponse.contentType()?.withoutParameters())
        assertEquals(Problem("json-required"), Json.decodeFromString<Problem>(unsupportedResponse.bodyAsText()))

        val emptyQueueResponse = helpdeskClient.get("/tickets") {
            accept(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, emptyQueueResponse.status)
        assertEquals(emptyList<Ticket>(), Json.decodeFromString<List<Ticket>>(emptyQueueResponse.bodyAsText()))

        val validResponse = helpdeskClient.post("/tickets") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody("""{"title":"Printer offline","priority":2}""")
        }
        val expectedTicket: Ticket = Ticket("Printer offline", 2)
        assertEquals(HttpStatusCode.Created, validResponse.status)
        assertEquals(expectedTicket, Json.decodeFromString<Ticket>(validResponse.bodyAsText()))

        val queueResponse = helpdeskClient.get("/tickets") {
            accept(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, queueResponse.status)
        assertEquals(listOf(expectedTicket), Json.decodeFromString<List<Ticket>>(queueResponse.bodyAsText()))
    }

    @Serializable
    data class Ticket(val title: String, val priority: Int)

    @Serializable
    data class Problem(val code: String)
}
