/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_content_negotiation_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.TextContent
import io.ktor.http.withCharset
import io.ktor.serialization.kotlinx.cbor.cbor
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Request body deserialization selected by the request's `Content-Type` header.
 *
 * An order-intake service accepts orders as JSON, as CBOR from embedded point-of-sale
 * terminals, and as a lenient vendor JSON type whose partners send fields the service does not
 * know. Each route reads the body with `call.receive`, and the plugin picks the converter by
 * the request's content type and charset. The test checks that every format decodes into the
 * same order, that an unknown content type is rejected with `415 Unsupported Media Type`, that
 * a malformed body is rejected with `400 Bad Request` unless its converter is lenient, and that
 * reading the body as text bypasses the converters.
 */
@OptIn(ExperimentalSerializationApi::class)
class OrderIntakeRequestBodyTest {

    @Serializable
    data class Line(val sku: String, val quantity: Int)

    @Serializable
    data class Order(val orderId: String, val customer: String, val lines: List<Line>)

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private lateinit var client: HttpClient
    private val accepted: ConcurrentLinkedQueue<Order> = ConcurrentLinkedQueue()
    private var port: Int = 0

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(ContentNegotiation) {
                json()
                cbor()
                json(Json { ignoreUnknownKeys = true }, contentType = PARTNER_JSON)
            }
            routing {
                post("/orders") {
                    val order: Order = call.receive()
                    accepted.add(order)
                    val units: Int = order.lines.sumOf { it.quantity }
                    call.respondText("${order.orderId}:${order.customer}:$units", status = HttpStatusCode.Created)
                }
                post("/orders/raw") {
                    val raw: String = call.receiveText()
                    call.respondText("length=${raw.length}")
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
    fun everyRegisteredFormatDecodesTheSameOrder() = runBlocking {
        val json: HttpResponse = post("/orders", TextContent(Json.encodeToString(ORDER), ContentType.Application.Json))
        assertEquals(HttpStatusCode.Created, json.status)
        assertEquals("ord-1001:Zo\u00eb M\u00fcller:7", json.bodyAsText())

        val cborBytes: ByteArray = Cbor.encodeToByteArray(ORDER)
        val cbor: HttpResponse = post("/orders", ByteArrayContent(cborBytes, ContentType.Application.Cbor))
        assertEquals(HttpStatusCode.Created, cbor.status)
        assertEquals("ord-1001:Zo\u00eb M\u00fcller:7", cbor.bodyAsText())

        val partnerBody: String = """
            {"orderId":"ord-1001","customer":"Zo\u00eb M\u00fcller","channel":"marketplace",
             "lines":[{"sku":"valve-22","quantity":3,"note":"gift"},{"sku":"pump-7","quantity":4}]}
        """.trimIndent()
        val partner: HttpResponse = post("/orders", TextContent(partnerBody, PARTNER_JSON))
        assertEquals(HttpStatusCode.Created, partner.status)
        assertEquals("ord-1001:Zo\u00eb M\u00fcller:7", partner.bodyAsText())

        assertEquals(listOf(ORDER, ORDER, ORDER), accepted.toList())
    }

    @Test
    fun requestCharsetIsHonoured() = runBlocking {
        val utf16: ContentType = ContentType.Application.Json.withCharset(Charsets.UTF_16)
        val response: HttpResponse = post("/orders", TextContent(Json.encodeToString(ORDER), utf16))
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("ord-1001:Zo\u00eb M\u00fcller:7", response.bodyAsText())
        assertEquals(listOf(ORDER), accepted.toList())
    }

    @Test
    fun unknownContentTypeIsUnsupported() = runBlocking {
        val csv: HttpResponse = post("/orders", TextContent("ord-1001,valve-22,3", ContentType.Text.CSV))
        assertEquals(HttpStatusCode.UnsupportedMediaType, csv.status)
        assertEquals(emptyList<Order>(), accepted.toList())
    }

    @Test
    fun malformedBodiesAreBadRequests() = runBlocking {
        val truncated: HttpResponse = post("/orders", TextContent("""{"orderId":"ord-1001","lines":[""", ContentType.Application.Json))
        assertEquals(HttpStatusCode.BadRequest, truncated.status)

        val strictUnknownField: String = """{"orderId":"ord-1","customer":"A","lines":[],"channel":"web"}"""
        val strict: HttpResponse = post("/orders", TextContent(strictUnknownField, ContentType.Application.Json))
        assertEquals(HttpStatusCode.BadRequest, strict.status)

        val lenient: HttpResponse = post("/orders", TextContent(strictUnknownField, PARTNER_JSON))
        assertEquals(HttpStatusCode.Created, lenient.status)
        assertEquals("ord-1:A:0", lenient.bodyAsText())

        assertEquals(listOf(Order("ord-1", "A", emptyList())), accepted.toList())
    }

    @Test
    fun textBodiesBypassTheConverters() = runBlocking {
        val body: String = Json.encodeToString(ORDER)
        val response: HttpResponse = post("/orders/raw", TextContent(body, ContentType.Application.Json))
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("length=${body.length}", response.bodyAsText())
        assertEquals(emptyList<Order>(), accepted.toList())
    }

    private suspend fun post(path: String, content: Any): HttpResponse =
        client.post("http://127.0.0.1:$port$path") { setBody(content) }

    private companion object {
        val PARTNER_JSON: ContentType = ContentType("application", "vnd.acme.partner-order+json")
        val ORDER: Order = Order(
            "ord-1001",
            "Zo\u00eb M\u00fcller",
            listOf(Line("valve-22", 3), Line("pump-7", 4)),
        )
    }
}
