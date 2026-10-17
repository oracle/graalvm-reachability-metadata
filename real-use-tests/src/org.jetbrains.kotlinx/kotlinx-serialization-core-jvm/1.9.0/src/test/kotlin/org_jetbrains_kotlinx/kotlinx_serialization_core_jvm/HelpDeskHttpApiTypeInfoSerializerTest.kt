/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_core_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/**
 * Exercises serializer resolution from runtime type information in an HTTP API: Ktor's content
 * negotiation looks up generated serializers by `KType` for request and response bodies,
 * including generic envelopes, lists and maps of application classes, sealed response
 * hierarchies and enums with serial names, and rejects bodies the generated serializer refuses.
 *
 * A help desk exposes its tickets over a small JSON API served by Ktor CIO and consumed by a
 * Ktor client, both configured with the same `Json` instance.
 */
public class HelpDeskHttpApiTypeInfoSerializerTest {

    /** Ticket urgency, written on the wire in lower case. */
    @Serializable
    public enum class Priority {
        @SerialName("low") LOW,
        @SerialName("normal") NORMAL,
        @SerialName("urgent") URGENT,
    }

    /** Support agent a ticket may be assigned to. */
    @Serializable
    public data class Agent(val login: String, val displayName: String)

    /** Stored help desk ticket. */
    @Serializable
    public data class Ticket(
        val id: Long,
        val title: String,
        val priority: Priority,
        val tags: Set<String>,
        val assignee: Agent? = null,
    )

    /** Ticket submitted by a customer before it has an id. */
    @Serializable
    public data class NewTicket(
        val title: String,
        val priority: Priority,
        val tags: Set<String> = emptySet(),
    )

    /** Cursor-paged listing envelope shared by every collection endpoint. */
    @Serializable
    public data class Page<T>(val items: List<T>, val total: Int, val nextCursor: String?)

    /** Outcome of a ticket submission. */
    @Serializable
    public sealed interface Submission {

        /** The ticket was stored under a fresh id. */
        @Serializable
        @SerialName("accepted")
        public data class Accepted(val ticket: Ticket) : Submission

        /** The ticket was refused because one field is invalid. */
        @Serializable
        @SerialName("rejected")
        public data class Rejected(val field: String, val reason: String) : Submission
    }

    /** In-memory ticket store behind the HTTP routes. */
    public class TicketDesk {
        private val nextId: AtomicLong = AtomicLong(100)
        private val tickets: MutableMap<Long, Ticket> = ConcurrentHashMap()

        /** Validates and stores [request], returning the submission outcome. */
        public fun submit(request: NewTicket): Submission {
            if (request.title.isBlank()) {
                return Submission.Rejected("title", "must not be blank")
            }
            val ticket = Ticket(nextId.getAndIncrement(), request.title.trim(), request.priority, request.tags)
            tickets[ticket.id] = ticket
            return Submission.Accepted(ticket)
        }

        /** Assigns ticket [id] to [agent] and returns the updated ticket. */
        public fun assign(id: Long, agent: Agent): Ticket? {
            val current: Ticket = tickets[id] ?: return null
            val updated: Ticket = current.copy(assignee = agent)
            tickets[id] = updated
            return updated
        }

        /** Lists tickets in id order, optionally filtered by [priority], [pageSize] at a time. */
        public fun list(priority: Priority?, after: Long?, pageSize: Int): Page<Ticket> {
            val matching: List<Ticket> = tickets.values
                .filter { priority == null || it.priority == priority }
                .sortedBy { it.id }
            val remaining: List<Ticket> = matching.filter { after == null || it.id > after }
            val page: List<Ticket> = remaining.take(pageSize)
            val cursor: String? = if (remaining.size > pageSize) page.last().id.toString() else null
            return Page(page, matching.size, cursor)
        }

        /** Counts open tickets per priority. */
        public fun countsByPriority(): Map<Priority, Int> =
            tickets.values.groupingBy { it.priority }.eachCount().toSortedMap()
    }

    private val apiJson: Json = Json {
        encodeDefaults = true
        classDiscriminator = "outcome"
    }

    private fun Application.helpDeskModule(desk: TicketDesk) {
        install(ServerContentNegotiation) { json(apiJson) }
        routing {
            post("/tickets") {
                val outcome: Submission = desk.submit(call.receive<NewTicket>())
                val status: HttpStatusCode =
                    if (outcome is Submission.Accepted) HttpStatusCode.Created else HttpStatusCode.UnprocessableEntity
                call.respond<Submission>(status, outcome)
            }
            post("/tickets/batch") {
                val outcomes: List<Submission> = call.receive<List<NewTicket>>().map(desk::submit)
                call.respond<List<Submission>>(outcomes)
            }
            post("/tickets/{id}/assignee") {
                val id: Long = call.parameters["id"]!!.toLong()
                val updated: Ticket? = desk.assign(id, call.receive<Agent>())
                if (updated == null) {
                    call.respond(HttpStatusCode.NotFound, Submission.Rejected("id", "no ticket $id"))
                } else {
                    call.respond(updated)
                }
            }
            get("/tickets") {
                val priority: Priority? = call.request.queryParameters["priority"]
                    ?.let { name -> Priority.entries.first { apiJson.encodeToString(it) == "\"$name\"" } }
                val after: Long? = call.request.queryParameters["after"]?.toLong()
                call.respond<Page<Ticket>>(desk.list(priority, after, pageSize = 2))
            }
            get("/stats") {
                call.respond<Map<Priority, Int>>(desk.countsByPriority())
            }
        }
    }

    private fun withHelpDesk(block: suspend (HttpClient, String) -> Unit) {
        val desk = TicketDesk()
        val server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") { helpDeskModule(desk) }
        server.start(wait = false)
        try {
            runBlocking {
                val port: Int = server.engine.resolvedConnectors().first().port
                HttpClient(ClientCIO) {
                    install(ClientContentNegotiation) { json(apiJson) }
                    expectSuccess = false
                }.use { client -> block(client, "http://127.0.0.1:$port") }
            }
        } finally {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 2_000)
        }
    }

    private suspend fun HttpClient.submit(baseUrl: String, ticket: NewTicket): Submission =
        post("$baseUrl/tickets") {
            contentType(ContentType.Application.Json)
            setBody(ticket)
        }.body()

    @Test
    fun submittedTicketRoundTripsThroughSealedResponse() = withHelpDesk { client, baseUrl ->
        val response = client.post("$baseUrl/tickets") {
            contentType(ContentType.Application.Json)
            setBody(NewTicket("  Printer on fire ", Priority.URGENT, setOf("hardware", "floor-3")))
        }
        assertEquals(HttpStatusCode.Created, response.status)

        val wire = apiJson.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("accepted", wire.getValue("outcome").jsonPrimitive.content)
        val wireTicket = wire.getValue("ticket").jsonObject
        assertEquals("urgent", wireTicket.getValue("priority").jsonPrimitive.content)
        assertEquals(listOf("hardware", "floor-3"), wireTicket.getValue("tags").jsonArray.map { it.jsonPrimitive.content })
        assertTrue(wireTicket.containsKey("assignee"), "encodeDefaults writes the null assignee")

        val decoded: Submission = apiJson.decodeFromString(Submission.serializer(), response.bodyAsText())
        assertEquals(
            Submission.Accepted(Ticket(100, "Printer on fire", Priority.URGENT, setOf("hardware", "floor-3"))),
            decoded,
        )
    }

    @Test
    fun invalidTicketIsRejectedWithTheOtherSubtype() = withHelpDesk { client, baseUrl ->
        val response = client.post("$baseUrl/tickets") {
            contentType(ContentType.Application.Json)
            setBody(NewTicket("   ", Priority.LOW))
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val outcome: Submission = response.body()
        assertEquals(Submission.Rejected("title", "must not be blank"), outcome)
    }

    @Test
    fun batchSubmissionUsesListSerializersOnBothSides() = withHelpDesk { client, baseUrl ->
        val outcomes: List<Submission> = client.post("$baseUrl/tickets/batch") {
            contentType(ContentType.Application.Json)
            setBody(
                listOf(
                    NewTicket("VPN drops hourly", Priority.NORMAL, setOf("network")),
                    NewTicket("", Priority.URGENT),
                    NewTicket("Laptop battery swollen", Priority.URGENT),
                ),
            )
        }.body()

        assertEquals(3, outcomes.size)
        assertEquals(101L, (outcomes[2] as Submission.Accepted).ticket.id)
        assertEquals(Submission.Rejected("title", "must not be blank"), outcomes[1])
        assertEquals(setOf("network"), (outcomes[0] as Submission.Accepted).ticket.tags)

        val stats: Map<Priority, Int> = client.get("$baseUrl/stats").body()
        assertEquals(mapOf(Priority.NORMAL to 1, Priority.URGENT to 1), stats)
        assertEquals("""{"normal":1,"urgent":1}""", client.get("$baseUrl/stats").bodyAsText())
    }

    @Test
    fun genericPageEnvelopeIsResolvedWithItsTypeArgument() = withHelpDesk { client, baseUrl ->
        listOf(
            NewTicket("Reset password", Priority.LOW),
            NewTicket("Disk full on build agent", Priority.URGENT, setOf("ci")),
            NewTicket("New hire laptop", Priority.NORMAL),
            NewTicket("Mail server down", Priority.URGENT, setOf("mail")),
            NewTicket("Badge reader stuck", Priority.URGENT),
        ).forEach { client.submit(baseUrl, it) }

        val first: Page<Ticket> = client.get("$baseUrl/tickets?priority=urgent").body()
        assertEquals(3, first.total)
        assertEquals(listOf("Disk full on build agent", "Mail server down"), first.items.map { it.title })
        assertEquals("103", first.nextCursor)

        val second: Page<Ticket> = client.get("$baseUrl/tickets?priority=urgent&after=${first.nextCursor}").body()
        assertEquals(listOf(104L), second.items.map { it.id })
        assertNull(second.nextCursor)

        val wire = apiJson.parseToJsonElement(client.get("$baseUrl/tickets").bodyAsText()).jsonObject
        assertEquals(5, wire.getValue("total").jsonPrimitive.int)
        assertEquals(setOf("items", "total", "nextCursor"), wire.keys)
    }

    @Test
    fun assignmentUpdatesNestedNullableClass() = withHelpDesk { client, baseUrl ->
        val accepted = client.submit(baseUrl, NewTicket("Monitor flickers", Priority.NORMAL)) as Submission.Accepted

        val updated: Ticket = client.post("$baseUrl/tickets/${accepted.ticket.id}/assignee") {
            contentType(ContentType.Application.Json)
            setBody(Agent("jdoe", "Jamie Doe"))
        }.body()
        assertEquals(Agent("jdoe", "Jamie Doe"), updated.assignee)

        val missing = client.post("$baseUrl/tickets/999/assignee") {
            contentType(ContentType.Application.Json)
            setBody(Agent("jdoe", "Jamie Doe"))
        }
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertEquals(Submission.Rejected("id", "no ticket 999"), missing.body<Submission.Rejected>())

        val listed: Page<Ticket> = client.get("$baseUrl/tickets?priority=normal").body()
        assertEquals("jdoe", listed.items.single().assignee?.login)
    }

    @Test
    fun bodiesTheGeneratedSerializerRefusesAreBadRequests() = withHelpDesk { client, baseUrl ->
        val missingField = client.post("$baseUrl/tickets") {
            contentType(ContentType.Application.Json)
            setBody("""{"priority":"low"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, missingField.status)

        val unknownEnum = client.post("$baseUrl/tickets") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Coffee machine","priority":"LOW"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, unknownEnum.status)

        val page: Page<Ticket> = client.get("$baseUrl/tickets").body()
        assertEquals(0, page.total)
    }
}
