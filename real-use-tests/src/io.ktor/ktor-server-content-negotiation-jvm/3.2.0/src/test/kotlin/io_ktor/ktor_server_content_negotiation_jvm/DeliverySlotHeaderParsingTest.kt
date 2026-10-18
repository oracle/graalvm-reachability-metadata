/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_content_negotiation_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Header parsing and empty-body handling the plugin performs before any converter runs.
 *
 * A delivery-slot booking service sits behind a legacy edge proxy that sometimes mangles
 * headers: it forwards `Content-Type: json` without a type, or an `Accept` item without a
 * subtype. The test checks that the plugin answers such requests with `400 Bad Request` before
 * the route stores anything, that well-formed requests through the same routes still book and
 * list slots, and that a request with an empty body is decoded as `null` by `receiveNullable`
 * (a "book the next free slot" request) while `receive` of a non-null type refuses it with
 * `415 Unsupported Media Type`.
 */
class DeliverySlotHeaderParsingTest {

    @Serializable
    data class SlotRequest(val day: String, val window: String)

    @Serializable
    data class Booking(val reference: String, val day: String, val window: String)

    private val bookings: ConcurrentLinkedQueue<Booking> = ConcurrentLinkedQueue()
    private val handled: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private lateinit var client: HttpClient
    private var port: Int = 0

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(ContentNegotiation) {
                json(Json { encodeDefaults = true })
            }
            routing {
                post("/slots") {
                    handled.add("book")
                    val request: SlotRequest = call.receive()
                    val booking = Booking("B-${bookings.size + 1}", request.day, request.window)
                    bookings.add(booking)
                    call.respond(HttpStatusCode.Created, booking)
                }
                post("/slots/next") {
                    handled.add("next")
                    val preferred: SlotRequest? = call.receiveNullable()
                    val request: SlotRequest = preferred ?: SlotRequest("2026-10-20", "08:00-10:00")
                    val booking = Booking("B-${bookings.size + 1}", request.day, request.window)
                    bookings.add(booking)
                    call.respond(HttpStatusCode.Created, booking)
                }
                get("/slots") {
                    handled.add("list")
                    call.respond(bookings.toList())
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
        client = HttpClient(CIO)
    }

    @AfterEach
    fun stop() {
        client.close()
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun wellFormedRequestsBookAndListSlots() = runBlocking {
        val booked: HttpResponse = client.post("http://127.0.0.1:$port/slots") {
            contentType(ContentType.Application.Json)
            setBody("""{"day":"2026-10-21","window":"14:00-16:00"}""")
        }
        assertEquals(HttpStatusCode.Created, booked.status)
        assertEquals(Booking("B-1", "2026-10-21", "14:00-16:00"), Json.decodeFromString<Booking>(booked.bodyAsText()))

        val listed: HttpResponse = client.get("http://127.0.0.1:$port/slots") {
            header(HttpHeaders.Accept, "application/json")
        }
        assertEquals(HttpStatusCode.OK, listed.status)
        assertTrue(listed.headers[HttpHeaders.ContentType]!!.startsWith("application/json"))
        assertEquals(
            listOf(Booking("B-1", "2026-10-21", "14:00-16:00")),
            Json.decodeFromString<List<Booking>>(listed.bodyAsText()),
        )
    }

    @Test
    fun contentTypeWithoutSubtypeIsBadRequest() {
        val response: RawResponse = rawPost("/slots", "json", """{"day":"2026-10-21","window":"14:00-16:00"}""")

        assertEquals(HttpStatusCode.BadRequest.value, response.status)
        assertEquals(listOf("book"), handled.toList())
        assertEquals(emptyList<Booking>(), bookings.toList())
    }

    @Test
    fun acceptItemWithoutSubtypeIsBadRequest() = runBlocking {
        val mangled: HttpResponse = client.get("http://127.0.0.1:$port/slots") {
            header(HttpHeaders.Accept, "application/json, json")
        }
        assertEquals(HttpStatusCode.BadRequest, mangled.status)
        assertTrue(!mangled.bodyAsText().startsWith("["), mangled.bodyAsText())

        val clean: HttpResponse = client.get("http://127.0.0.1:$port/slots") {
            header(HttpHeaders.Accept, "application/json, text/plain;q=0.5")
        }
        assertEquals(HttpStatusCode.OK, clean.status)
        assertEquals("[]", clean.bodyAsText())
    }

    @Test
    fun emptyBodyIsNullForReceiveNullableAndRefusedByReceive() = runBlocking {
        val next: HttpResponse = client.post("http://127.0.0.1:$port/slots/next") {
            contentType(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.Created, next.status)
        assertEquals(Booking("B-1", "2026-10-20", "08:00-10:00"), Json.decodeFromString<Booking>(next.bodyAsText()))

        val preferred: HttpResponse = client.post("http://127.0.0.1:$port/slots/next") {
            contentType(ContentType.Application.Json)
            setBody("""{"day":"2026-10-22","window":"18:00-20:00"}""")
        }
        assertEquals(HttpStatusCode.Created, preferred.status)
        assertEquals(Booking("B-2", "2026-10-22", "18:00-20:00"), Json.decodeFromString<Booking>(preferred.bodyAsText()))

        val empty: HttpResponse = client.post("http://127.0.0.1:$port/slots") {
            contentType(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.UnsupportedMediaType, empty.status)
        assertEquals(2, bookings.size)
        assertEquals(listOf("next", "next", "book"), handled.toList())
    }

    /** Status line and body of a response read from a hand-written HTTP/1.1 exchange. */
    data class RawResponse(val status: Int, val body: String)

    /** Sends a request the way the legacy proxy forwards it, with its header verbatim. */
    private fun rawPost(path: String, contentType: String, body: String): RawResponse {
        val payload: ByteArray = body.toByteArray(Charsets.UTF_8)
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 10_000
            val head: String = "POST $path HTTP/1.1\r\n" +
                "Host: 127.0.0.1:$port\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${payload.size}\r\n" +
                "Connection: close\r\n\r\n"
            socket.getOutputStream().apply {
                write(head.toByteArray(Charsets.US_ASCII))
                write(payload)
                flush()
            }
            val text: String = readAll(socket.getInputStream()).toString(Charsets.UTF_8)
            val status: Int = text.substringAfter(' ').substringBefore(' ').toInt()
            return RawResponse(status, text.substringAfter("\r\n\r\n"))
        }
    }

    private fun readAll(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val read: Int = input.read(buffer)
            if (read < 0) {
                return out.toByteArray()
            }
            out.write(buffer, 0, read)
        }
    }
}
