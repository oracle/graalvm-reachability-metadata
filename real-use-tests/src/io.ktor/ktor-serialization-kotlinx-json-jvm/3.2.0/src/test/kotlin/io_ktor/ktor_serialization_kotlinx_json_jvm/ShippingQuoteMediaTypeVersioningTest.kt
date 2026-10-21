/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_serialization_kotlinx_json_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
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
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/**
 * Selects between kotlinx JSON dialects that `json(format, contentType)` registers for
 * versioned vendor media types, driven by the Content-Type and Accept headers.
 *
 * A shipping quote API speaks two representation versions: v1 tags shipments with `kind` and
 * writes every field, v2 tags them with `@type` and drops defaults and nulls. Clients pinned
 * to each version share quotes, and a raw integration sees the two wire formats side by side
 * and is turned away when it sends plain JSON or a v1 body labelled as v2.
 */
public class ShippingQuoteMediaTypeVersioningTest {

    /** What is being shipped; the dialect decides the discriminator key. */
    @Serializable
    private sealed interface Shipment {
        @Serializable
        @SerialName("parcel")
        data class Parcel(@SerialName("weight_grams") val weightGrams: Int) : Shipment

        @Serializable
        @SerialName("freight")
        data class Freight(val pallets: Int) : Shipment
    }

    /** A request for a price. */
    @Serializable
    private data class QuoteRequest(val destination: String, val shipment: Shipment, val insured: Boolean = false)

    /** A priced quote. */
    @Serializable
    private data class Quote(
        val id: Int,
        val destination: String,
        val shipment: Shipment,
        @SerialName("price_cents") val priceCents: Long,
        val insured: Boolean = false,
        val note: String? = null,
    )

    private val v1Type: ContentType = ContentType("application", "vnd.shipping.v1+json")
    private val v2Type: ContentType = ContentType("application", "vnd.shipping.v2+json")

    /** The legacy dialect: explicit about every field. */
    private val v1Json: Json = Json {
        classDiscriminator = "kind"
        encodeDefaults = true
    }

    /** The current dialect: compact and tolerant of new fields. */
    private val v2Json: Json = Json {
        classDiscriminator = "@type"
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    private val quotes: MutableMap<Int, Quote> = ConcurrentHashMap()
    private val nextId: AtomicInteger = AtomicInteger()

    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var v1Client: HttpClient
    private lateinit var v2Client: HttpClient
    private lateinit var rawClient: HttpClient
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(ServerContentNegotiation) {
                json(v1Json, v1Type)
                json(v2Json, v2Type)
            }
            routing {
                post("/quotes") {
                    val request: QuoteRequest = call.receive()
                    val base: Long = when (val shipment: Shipment = request.shipment) {
                        is Shipment.Parcel -> 400L + shipment.weightGrams / 100 * 35L
                        is Shipment.Freight -> shipment.pallets * 12_000L
                    }
                    val quote = Quote(
                        id = nextId.incrementAndGet(),
                        destination = request.destination,
                        shipment = request.shipment,
                        priceCents = if (request.insured) base + 250L else base,
                        insured = request.insured,
                        note = if (request.shipment is Shipment.Freight) "Tail lift required" else null,
                    )
                    quotes[quote.id] = quote
                    call.respond(HttpStatusCode.Created, quote)
                }
                get("/quotes/{id}") {
                    val quote: Quote? = call.parameters["id"]?.toIntOrNull()?.let { quotes[it] }
                    if (quote == null) {
                        call.respond(HttpStatusCode.NotFound, "")
                    } else {
                        call.respond(quote)
                    }
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
        v1Client = HttpClient(ClientCIO) {
            install(ClientContentNegotiation) {
                json(v1Json, v1Type)
            }
        }
        v2Client = HttpClient(ClientCIO) {
            install(ClientContentNegotiation) {
                json(v2Json, v2Type)
            }
        }
        rawClient = HttpClient(ClientCIO)
    }

    @AfterEach
    fun stopServer() {
        v1Client.close()
        v2Client.close()
        rawClient.close()
        server.stop(gracePeriodMillis = 0L, timeoutMillis = 10_000L)
    }

    private fun url(path: String): String = "http://127.0.0.1:$port$path"

    @Test
    fun `clients pinned to different versions share quotes as typed objects`() = runBlocking {
        val created: HttpResponse = v2Client.post(url("/quotes")) {
            contentType(v2Type)
            setBody(QuoteRequest(destination = "Lyon", shipment = Shipment.Parcel(weightGrams = 1_250), insured = true))
        }
        assertEquals(HttpStatusCode.Created, created.status)
        assertTrue(created.contentType()!!.match(v2Type))
        val expected = Quote(
            id = 1,
            destination = "Lyon",
            shipment = Shipment.Parcel(weightGrams = 1_250),
            priceCents = 1_070L,
            insured = true,
        )
        assertEquals(expected, created.body<Quote>())

        val legacyView: HttpResponse = v1Client.get(url("/quotes/1"))
        assertEquals(HttpStatusCode.OK, legacyView.status)
        assertTrue(legacyView.contentType()!!.match(v1Type))
        assertEquals(expected, legacyView.body<Quote>())

        val freight: Quote = v1Client.post(url("/quotes")) {
            contentType(v1Type)
            setBody(QuoteRequest(destination = "Gdansk", shipment = Shipment.Freight(pallets = 3)))
        }.body()
        assertEquals(Shipment.Freight(pallets = 3), freight.shipment)
        assertEquals(36_000L, freight.priceCents)
        assertEquals("Tail lift required", freight.note)
        assertEquals(freight, v2Client.get(url("/quotes/2")).body<Quote>())
        assertEquals(mapOf(1 to expected, 2 to freight), quotes.toMap())
    }

    @Test
    fun `integration sees each wire format and is refused on mismatched bodies`() = runBlocking {
        val v1Body: String = """
            {"destination": "Porto", "shipment": {"kind": "freight", "pallets": 2}}
        """.trimIndent()
        val created: HttpResponse = rawClient.post(url("/quotes")) {
            contentType(v1Type)
            header(HttpHeaders.Accept, v1Type.toString())
            setBody(v1Body)
        }
        assertEquals(HttpStatusCode.Created, created.status)
        assertTrue(created.contentType()!!.match(v1Type))
        val v1Wire: JsonObject = Json.parseToJsonElement(created.bodyAsText()).jsonObject
        assertEquals(
            setOf("id", "destination", "shipment", "price_cents", "insured", "note"),
            v1Wire.keys,
        )
        assertEquals("freight", v1Wire.getValue("shipment").jsonObject.getValue("kind").jsonPrimitive.content)
        assertFalse(v1Wire.getValue("insured").jsonPrimitive.boolean)
        assertEquals(24_000, v1Wire.getValue("price_cents").jsonPrimitive.int)

        val v2View: HttpResponse = rawClient.get(url("/quotes/1")) {
            header(HttpHeaders.Accept, v2Type.toString())
        }
        assertTrue(v2View.contentType()!!.match(v2Type))
        val v2Wire: JsonObject = Json.parseToJsonElement(v2View.bodyAsText()).jsonObject
        assertEquals(setOf("id", "destination", "shipment", "price_cents", "note"), v2Wire.keys)
        assertEquals(setOf("@type", "pallets"), v2Wire.getValue("shipment").jsonObject.keys)
        assertEquals("freight", v2Wire.getValue("shipment").jsonObject.getValue("@type").jsonPrimitive.content)

        val parcelBody: String = """
            {"destination": "Bergen", "shipment": {"@type": "parcel", "weight_grams": 400}, "channel": "kiosk"}
        """.trimIndent()
        val parcel: HttpResponse = rawClient.post(url("/quotes")) {
            contentType(v2Type)
            header(HttpHeaders.Accept, v1Type.toString())
            setBody(parcelBody)
        }
        assertEquals(HttpStatusCode.Created, parcel.status)
        assertTrue(parcel.contentType()!!.match(v1Type))
        val parcelWire: JsonObject = Json.parseToJsonElement(parcel.bodyAsText()).jsonObject
        assertEquals(JsonNull, parcelWire.getValue("note"))
        assertEquals("parcel", parcelWire.getValue("shipment").jsonObject.getValue("kind").jsonPrimitive.content)
        assertEquals(540, parcelWire.getValue("price_cents").jsonPrimitive.int)

        val plainJson: HttpResponse = rawClient.post(url("/quotes")) {
            contentType(ContentType.Application.Json)
            setBody(v1Body)
        }
        assertEquals(HttpStatusCode.UnsupportedMediaType, plainJson.status)

        val mislabelled: HttpResponse = rawClient.post(url("/quotes")) {
            contentType(v2Type)
            setBody(v1Body)
        }
        assertEquals(HttpStatusCode.BadRequest, mislabelled.status)
        assertEquals(setOf(1, 2), quotes.keys)
    }
}
