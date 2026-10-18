/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_serialization_kotlinx_json_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.charset
import io.ktor.http.contentType
import io.ktor.http.withCharset
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.charset.Charset
import java.util.concurrent.CopyOnWriteArrayList
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/**
 * Negotiates the character encoding of JSON bodies converted by `json()`: requests are decoded
 * with the charset their `Content-Type` names and responses are encoded in the charset the
 * caller's `Accept-Charset` selects, on server and client content negotiation.
 *
 * A hotel registers guests from three kinds of callers: lobby kiosks whose firmware only
 * speaks ISO-8859-1 and use a typed Ktor client, legacy terminals that write Latin-1 bytes by
 * hand, and a modern booking app that relies on the UTF-8 default. Guest names carry umlauts
 * and a sharp s, so a wrong charset anywhere in the round trip would corrupt them.
 */
public class HotelGuestRegistryCharsetTest {

    /** A guest as the front desk registers them. */
    @Serializable
    private data class Guest(val name: String, val city: String)

    /** The hotel's answer to a registration. */
    @Serializable
    private data class Registration(val room: Int, val greeting: String)

    private val latin1: Charset = Charsets.ISO_8859_1

    /** "Juergen Gross" with umlaut and sharp s, kept as escapes so the source stays ASCII. */
    private val juergen: String = "J\u00fcrgen Gro\u00df"

    private val zuerich: String = "Z\u00fcrich"

    private val guests: MutableList<Guest> = CopyOnWriteArrayList()

    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var kiosk: HttpClient
    private lateinit var bookingApp: HttpClient
    private lateinit var terminal: HttpClient
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(ServerContentNegotiation) {
                json()
            }
            routing {
                post("/guests") {
                    val guest: Guest = call.receive()
                    guests.add(guest)
                    call.respond(
                        HttpStatusCode.Created,
                        Registration(room = 100 + guests.size, greeting = "Gr\u00fc\u00df Gott, ${guest.name}"),
                    )
                }
                get("/guests") {
                    call.respond(guests.toList())
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
        kiosk = HttpClient(ClientCIO) {
            install(ClientContentNegotiation) {
                json()
            }
            defaultRequest {
                header(HttpHeaders.AcceptCharset, "ISO-8859-1")
            }
        }
        bookingApp = HttpClient(ClientCIO) {
            install(ClientContentNegotiation) {
                json()
            }
        }
        terminal = HttpClient(ClientCIO)
    }

    @AfterEach
    fun stopServer() {
        kiosk.close()
        bookingApp.close()
        terminal.close()
        server.stop(gracePeriodMillis = 0L, timeoutMillis = 10_000L)
    }

    private fun url(path: String): String = "http://127.0.0.1:$port$path"

    @Test
    fun `kiosk registers guests in Latin-1 through typed converters`() = runBlocking {
        val response: HttpResponse = kiosk.post(url("/guests")) {
            contentType(ContentType.Application.Json.withCharset(latin1))
            setBody(Guest(name = juergen, city = zuerich))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(latin1, response.contentType()!!.charset())
        assertEquals(Registration(room = 101, greeting = "Gr\u00fc\u00df Gott, $juergen"), response.body<Registration>())
        assertEquals(listOf(Guest(name = juergen, city = zuerich)), guests)

        val listed: List<Guest> = kiosk.get(url("/guests")).body()
        assertEquals(listOf(Guest(name = juergen, city = zuerich)), listed)
    }

    @Test
    fun `legacy terminal exchanges hand-written Latin-1 bytes`() = runBlocking {
        val payload: ByteArray = """{"name": "$juergen", "city": "$zuerich"}""".toByteArray(latin1)
        assertEquals(0xFC.toByte(), payload[11])

        val response: HttpResponse = terminal.post(url("/guests")) {
            header(HttpHeaders.AcceptCharset, "ISO-8859-1")
            contentType(ContentType.Application.Json.withCharset(latin1))
            setBody(payload)
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(listOf(Guest(name = juergen, city = zuerich)), guests)

        val wire: ByteArray = response.readRawBytes()
        assertEquals(latin1, response.contentType()!!.charset())
        assertTrue(wire.contains(0xDF.toByte()), "sharp s is the single Latin-1 byte 0xDF")
        assertFalse(wire.contains(0xC3.toByte()), "no UTF-8 lead byte may appear in a Latin-1 body")
        val registration: JsonObject = Json.parseToJsonElement(String(wire, latin1)).jsonObject
        assertEquals(101, registration.getValue("room").jsonPrimitive.int)
        assertEquals("Gr\u00fc\u00df Gott, $juergen", registration.getValue("greeting").jsonPrimitive.content)
    }

    @Test
    fun `accept charset chooses the encoding of every response`() = runBlocking {
        guests.add(Guest(name = juergen, city = zuerich))

        val utf16: HttpResponse = terminal.get(url("/guests")) {
            header(HttpHeaders.AcceptCharset, "UTF-16")
        }
        assertEquals(Charsets.UTF_16, utf16.contentType()!!.charset())
        val utf16Bytes: ByteArray = utf16.readRawBytes()
        assertArrayEquals(byteArrayOf(0xFE.toByte(), 0xFF.toByte()), utf16Bytes.copyOfRange(0, 2))
        assertEquals(
            listOf(Guest(name = juergen, city = zuerich)),
            Json.decodeFromString<List<Guest>>(String(utf16Bytes, Charsets.UTF_16)),
        )

        val preferred: HttpResponse = terminal.get(url("/guests")) {
            header(HttpHeaders.AcceptCharset, "UTF-16;q=0.2, ISO-8859-1;q=0.9")
        }
        assertEquals(latin1, preferred.contentType()!!.charset())
        assertEquals(
            """[{"name":"$juergen","city":"$zuerich"}]""",
            String(preferred.readRawBytes(), latin1),
        )
    }

    @Test
    fun `booking app relies on the UTF-8 default`() = runBlocking {
        val created: Registration = bookingApp.post(url("/guests")) {
            contentType(ContentType.Application.Json)
            setBody(Guest(name = juergen, city = zuerich))
        }.body()
        assertEquals(101, created.room)
        assertEquals(listOf(Guest(name = juergen, city = zuerich)), guests)

        val listing: HttpResponse = bookingApp.get(url("/guests"))
        assertEquals(Charsets.UTF_8, listing.contentType()!!.charset())
        val wire: ByteArray = listing.readRawBytes()
        val umlaut: ByteArray = "\u00fc".toByteArray(Charsets.UTF_8)
        assertTrue(
            wire.toList().windowed(2).any { it == umlaut.toList() },
            "u-umlaut travels as the UTF-8 pair C3 BC",
        )
        assertEquals(listOf(Guest(name = juergen, city = zuerich)), Json.decodeFromString<List<Guest>>(String(wire, Charsets.UTF_8)))
    }
}
