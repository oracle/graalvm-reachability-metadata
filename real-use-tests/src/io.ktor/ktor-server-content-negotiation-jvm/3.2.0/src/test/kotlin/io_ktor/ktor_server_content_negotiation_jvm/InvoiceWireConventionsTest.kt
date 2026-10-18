/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_content_negotiation_jvm

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.google.gson.FieldNamingPolicy
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
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
import io.ktor.serialization.gson.gson
import io.ktor.serialization.jackson.jackson
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Wire conventions configured on each registered converter: field naming, null handling and value encodings.
 *
 * An invoicing service hands the same invoices to three kinds of clients. Its web front end
 * gets kotlinx JSON with snake_case names, amounts as exact decimal strings, `java.time`
 * instants and UUIDs through contextual serializers, and absent notes left out. A legacy
 * ledger gets Gson-encoded JSON under its own vendor type, with snake_case field names, amounts
 * as JSON numbers and an explicit `null` for an absent note. An ERP connector gets Jackson JSON
 * with ISO-8601 instants from the Java time module and null properties left out. The test
 * checks that the converter the request negotiates writes and reads that client's conventions
 * for the same invoice, and that a body in another client's convention is refused with 400.
 */
class InvoiceWireConventionsTest {

    @Serializable
    data class Invoice(
        @Contextual val id: UUID,
        val customerName: String,
        @Contextual val total: BigDecimal,
        @Contextual val issuedAt: Instant,
        val note: String? = null,
    )

    /** Keeps the scale of an amount by writing it as a decimal string. */
    object BigDecimalAsString : KSerializer<BigDecimal> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("invoice.BigDecimal", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: BigDecimal) = encoder.encodeString(value.toPlainString())

        override fun deserialize(decoder: Decoder): BigDecimal = BigDecimal(decoder.decodeString())
    }

    object UuidAsString : KSerializer<UUID> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("invoice.UUID", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString())

        override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
    }

    object InstantAsIsoString : KSerializer<Instant> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("invoice.Instant", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())

        override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
    }

    /** Gson has no `java.time` support, so the ledger writes instants as ISO-8601 strings. */
    class InstantTypeAdapter : TypeAdapter<Instant>() {
        override fun write(out: JsonWriter, value: Instant?) {
            if (value == null) out.nullValue() else out.value(value.toString())
        }

        override fun read(reader: JsonReader): Instant = Instant.parse(reader.nextString())
    }

    private val invoices: ConcurrentHashMap<UUID, Invoice> = ConcurrentHashMap(mapOf(STORED.id to STORED))

    @OptIn(ExperimentalSerializationApi::class)
    private fun runService(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) {
                json(
                    Json {
                        namingStrategy = JsonNamingStrategy.SnakeCase
                        explicitNulls = false
                        serializersModule = SerializersModule {
                            contextual(BigDecimal::class, BigDecimalAsString)
                            contextual(UUID::class, UuidAsString)
                            contextual(Instant::class, InstantAsIsoString)
                        }
                    },
                )
                gson(contentType = LEDGER_JSON) {
                    setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                    registerTypeAdapter(Instant::class.java, InstantTypeAdapter())
                    serializeNulls()
                }
                jackson(contentType = ERP_JSON) {
                    registerModule(JavaTimeModule())
                    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
                    setSerializationInclusion(JsonInclude.Include.NON_NULL)
                }
            }
            routing {
                get("/invoices/{id}") {
                    val invoice: Invoice? = invoices[UUID.fromString(call.parameters["id"]!!)]
                    if (invoice == null) call.respond(HttpStatusCode.NotFound) else call.respond(invoice)
                }
                post("/invoices") {
                    val invoice: Invoice = call.receive()
                    invoices[invoice.id] = invoice
                    call.respond(HttpStatusCode.Created, invoice)
                }
            }
        }
        block()
    }

    @Test
    fun webFrontEndGetsSnakeCaseDecimalStringsAndNoNullNote() = runService {
        val invoice: JsonObject = fetch(ContentType.Application.Json)

        assertEquals(setOf("id", "customer_name", "total", "issued_at"), invoice.keys)
        assertEquals(JsonPrimitive(STORED.id.toString()), invoice["id"])
        assertEquals(JsonPrimitive("Brauerei Kessler GmbH"), invoice["customer_name"])
        assertEquals(JsonPrimitive("1234.50"), invoice["total"])
        assertEquals(JsonPrimitive("2026-03-31T09:15:00Z"), invoice["issued_at"])
    }

    @Test
    fun legacyLedgerGetsNumericAmountsAndExplicitNullNote() = runService {
        val invoice: JsonObject = fetch(LEDGER_JSON)

        assertEquals(setOf("id", "customer_name", "total", "issued_at", "note"), invoice.keys)
        assertEquals(JsonPrimitive(STORED.id.toString()), invoice["id"])
        assertEquals(JsonPrimitive("Brauerei Kessler GmbH"), invoice["customer_name"])
        val total: JsonPrimitive = invoice["total"]!!.jsonPrimitive
        assertFalse(total.isString, total.toString())
        assertEquals("1234.50", total.content)
        assertEquals(JsonPrimitive("2026-03-31T09:15:00Z"), invoice["issued_at"])
        assertEquals(JsonNull, invoice["note"])
    }

    @Test
    fun erpConnectorGetsIsoInstantsAndNoNullNote() = runService {
        val invoice: JsonObject = fetch(ERP_JSON)

        assertEquals(setOf("id", "customer_name", "total", "issued_at"), invoice.keys)
        assertEquals(JsonPrimitive(STORED.id.toString()), invoice["id"])
        assertEquals(JsonPrimitive("Brauerei Kessler GmbH"), invoice["customer_name"])
        val total: JsonPrimitive = invoice["total"]!!.jsonPrimitive
        assertFalse(total.isString, total.toString())
        assertEquals("1234.50", total.content)
        assertEquals(JsonPrimitive("2026-03-31T09:15:00Z"), invoice["issued_at"])
    }

    @Test
    fun eachConverterReadsItsClientsConventions() = runService {
        val bodies: Map<ContentType, String> = mapOf(
            ContentType.Application.Json to """
                {"id": "${WEB_ID}", "customer_name": "Hofladen Brandt", "total": "89.90",
                 "issued_at": "2026-04-02T14:30:00Z", "note": "Pick-up on Saturday"}
            """.trimIndent(),
            LEDGER_JSON to """
                {"id": "${LEDGER_ID}", "customer_name": "Hofladen Brandt", "total": 89.90,
                 "issued_at": "2026-04-02T14:30:00Z", "note": "Pick-up on Saturday"}
            """.trimIndent(),
            ERP_JSON to """
                {"id": "${ERP_ID}", "customer_name": "Hofladen Brandt", "total": 89.90,
                 "issued_at": "2026-04-02T14:30:00Z", "note": "Pick-up on Saturday"}
            """.trimIndent(),
        )
        val ids: Map<ContentType, UUID> = mapOf(ContentType.Application.Json to WEB_ID, LEDGER_JSON to LEDGER_ID, ERP_JSON to ERP_ID)

        for ((format: ContentType, body: String) in bodies) {
            val response: HttpResponse = client.post("/invoices") {
                contentType(format)
                header(HttpHeaders.Accept, format.toString())
                setBody(body)
            }

            assertEquals(HttpStatusCode.Created, response.status, format.toString())
            assertFormat(format, response)
            val expected = Invoice(ids[format]!!, "Hofladen Brandt", BigDecimal("89.90"), Instant.parse("2026-04-02T14:30:00Z"), "Pick-up on Saturday")
            assertEquals(expected, invoices[expected.id], format.toString())
            val echoed: JsonObject = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals(JsonPrimitive("Pick-up on Saturday"), echoed["note"], format.toString())
            assertEquals("89.90", echoed["total"]!!.jsonPrimitive.content, format.toString())
        }
    }

    @Test
    fun bodyInAnotherClientsConventionIsRefused() = runService {
        val camelCase: String = """
            {"id": "${WEB_ID}", "customerName": "Hofladen Brandt", "total": "89.90", "issuedAt": "2026-04-02T14:30:00Z"}
        """.trimIndent()

        val response: HttpResponse = client.post("/invoices") {
            contentType(ContentType.Application.Json)
            setBody(camelCase)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(invoices.keys == setOf(STORED.id), invoices.keys.toString())
    }

    private suspend fun ApplicationTestBuilder.fetch(format: ContentType): JsonObject {
        val response: HttpResponse = client.get("/invoices/${STORED.id}") { header(HttpHeaders.Accept, format.toString()) }
        assertEquals(HttpStatusCode.OK, response.status, format.toString())
        assertFormat(format, response)
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject
    }

    private fun assertFormat(expected: ContentType, response: HttpResponse) {
        val actual: ContentType? = response.contentType()
        assertTrue(actual != null && actual.match(expected), "expected $expected, got $actual")
    }

    private companion object {
        val LEDGER_JSON: ContentType = ContentType("application", "vnd.ledger+json")
        val ERP_JSON: ContentType = ContentType("application", "vnd.erp+json")

        val WEB_ID: UUID = UUID.fromString("0b6f6a8e-2c1d-4f0e-9a51-7d3c2e1f0a01")
        val LEDGER_ID: UUID = UUID.fromString("0b6f6a8e-2c1d-4f0e-9a51-7d3c2e1f0a02")
        val ERP_ID: UUID = UUID.fromString("0b6f6a8e-2c1d-4f0e-9a51-7d3c2e1f0a03")

        val STORED = Invoice(
            UUID.fromString("5d2c9e41-8b7a-4c3f-b0e6-1a9f4d7c2b10"),
            "Brauerei Kessler GmbH",
            BigDecimal("1234.50"),
            Instant.parse("2026-03-31T09:15:00Z"),
        )
    }
}
