/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_content_negotiation_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.cbor.cbor
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.request.contentType
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Client content negotiation against a server that negotiates too, as a helpdesk application talks
 * to a ticket service running the server ContentNegotiation plugin: the service picks JSON or CBOR
 * from the Accept header the client plugin advertises, decodes CBOR request bodies the client
 * encodes, and error bodies are decoded from the ClientRequestException that expectSuccess raises.
 */
public class HelpdeskServerNegotiationRoundTripTest {

    @Serializable
    data class Ticket(val id: Int, val subject: String, val priority: String, val labels: List<String>)

    @Serializable
    data class NewTicket(val subject: String, val priority: String, val labels: List<String> = emptyList())

    @Serializable
    data class ApiError(val code: String, val message: String)

    /** What the ticket service saw of one request, and the format it answered in. */
    data class Exchange(val path: String, val accept: String?, val requestContentType: String?)

    /** The ticket service; it negotiates with the same formats the helpdesk client registers. */
    class TicketService : AutoCloseable {
        val exchanges: MutableList<Exchange> = CopyOnWriteArrayList()
        private val nextId: AtomicInteger = AtomicInteger(100)
        private val tickets: MutableMap<Int, Ticket> = mutableMapOf(
            7 to Ticket(7, "Printer jams on duplex", "high", listOf("hardware", "floor-2")),
        )

        private val server: EmbeddedServer<*, *> = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(ServerContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
                cbor(Cbor { ignoreUnknownKeys = true })
            }
            routing {
                get("/tickets/{id}") {
                    record(call.request.local.uri, call.request.header(HttpHeaders.Accept), null)
                    val ticket: Ticket? = call.parameters["id"]?.toIntOrNull()?.let { id: Int -> tickets[id] }
                    if (ticket == null) {
                        call.respond(HttpStatusCode.NotFound, ApiError("ticket.missing", "No ticket ${call.parameters["id"]}"))
                    } else {
                        call.respond(ticket)
                    }
                }
                post("/tickets") {
                    val requestType: String = call.request.contentType().withoutParameters().toString()
                    record(call.request.local.uri, call.request.header(HttpHeaders.Accept), requestType)
                    val draft: NewTicket = call.receive()
                    if (draft.subject.isBlank()) {
                        call.respond(HttpStatusCode.UnprocessableEntity, ApiError("ticket.subject", "Subject is required"))
                        return@post
                    }
                    val created: Ticket = Ticket(nextId.getAndIncrement(), draft.subject, draft.priority, draft.labels)
                    tickets[created.id] = created
                    call.respond(HttpStatusCode.Created, created)
                }
            }
        }

        val baseUrl: String

        init {
            server.start(wait = false)
            val port: Int = runBlocking { server.engine.resolvedConnectors().first().port }
            baseUrl = "http://127.0.0.1:$port"
        }

        private fun record(path: String, accept: String?, requestContentType: String?) {
            exchanges += Exchange(path, accept, requestContentType)
        }

        override fun close() {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 2_000)
        }
    }

    private fun helpdeskClient(baseUrl: String): HttpClient = HttpClient(ClientCIO) {
        expectSuccess = true
        install(ClientContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
            cbor(Cbor { ignoreUnknownKeys = true })
        }
        defaultRequest { url("$baseUrl/") }
    }

    /** Runs a call the service must refuse and returns the exception expectSuccess raised for it. */
    private suspend fun rejectedBy(call: suspend () -> HttpResponse): ClientRequestException {
        try {
            val response: HttpResponse = call()
            fail<Unit>("Expected a 4xx response but got ${response.status}")
        } catch (refused: ClientRequestException) {
            return refused
        }
        throw IllegalStateException("unreachable")
    }

    @Test
    fun theServerAnswersInTheFirstFormatTheClientAdvertises(): Unit = runBlocking {
        TicketService().use { service: TicketService ->
            helpdeskClient(service.baseUrl).use { client: HttpClient ->
                val response: HttpResponse = client.get("tickets/7")

                assertThat(response.contentType()?.withoutParameters()).isEqualTo(ContentType.Application.Json)
                assertThat(response.body<Ticket>())
                    .isEqualTo(Ticket(7, "Printer jams on duplex", "high", listOf("hardware", "floor-2")))
                val accept: String = service.exchanges.single().accept!!
                assertThat(accept).contains("application/json", "application/cbor")
                assertThat(accept.indexOf("application/json")).isLessThan(accept.indexOf("application/cbor"))
            }
        }
    }

    @Test
    fun anExplicitAcceptMakesTheServerAnswerInCbor(): Unit = runBlocking {
        TicketService().use { service: TicketService ->
            helpdeskClient(service.baseUrl).use { client: HttpClient ->
                val response: HttpResponse = client.get("tickets/7") {
                    accept(ContentType.Application.Cbor)
                }

                assertThat(response.contentType()?.withoutParameters()).isEqualTo(ContentType.Application.Cbor)
                assertThat(response.body<Ticket>().labels).containsExactly("hardware", "floor-2")
                assertThat(service.exchanges.single().accept).startsWith("application/cbor")
            }
        }
    }

    @Test
    fun aCborEncodedDraftIsDecodedByTheServerAndTheCreatedTicketReadBack(): Unit = runBlocking {
        TicketService().use { service: TicketService ->
            helpdeskClient(service.baseUrl).use { client: HttpClient ->
                val response: HttpResponse = client.post("tickets") {
                    contentType(ContentType.Application.Cbor)
                    setBody(NewTicket("VPN drops every hour", "medium", listOf("network")))
                }

                assertThat(response.status).isEqualTo(HttpStatusCode.Created)
                val created: Ticket = response.body()
                assertThat(created).isEqualTo(Ticket(100, "VPN drops every hour", "medium", listOf("network")))
                assertThat(service.exchanges.single().requestContentType).isEqualTo("application/cbor")

                val fetched: Ticket = client.get("tickets/100").body()
                assertThat(fetched).isEqualTo(created)
            }
        }
    }

    @Test
    fun errorBodiesAreDecodedFromTheRaisedException(): Unit = runBlocking {
        TicketService().use { service: TicketService ->
            helpdeskClient(service.baseUrl).use { client: HttpClient ->
                val missing: ClientRequestException = rejectedBy { client.get("tickets/404") }
                assertThat(missing.response.status).isEqualTo(HttpStatusCode.NotFound)
                assertThat(missing.response.body<ApiError>()).isEqualTo(ApiError("ticket.missing", "No ticket 404"))

                val rejected: ClientRequestException = rejectedBy {
                    client.post("tickets") {
                        contentType(ContentType.Application.Json)
                        setBody(NewTicket("  ", "low"))
                    }
                }
                assertThat(rejected.response.status).isEqualTo(HttpStatusCode.UnprocessableEntity)
                assertThat(rejected.response.body<ApiError>().code).isEqualTo("ticket.subject")
                assertThat(service.exchanges.last().requestContentType).isEqualTo("application/json")
            }
        }
    }
}
