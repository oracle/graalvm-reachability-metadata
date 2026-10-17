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
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
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
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/**
 * Resolves body serializers through the `SerializersModule` of the format given to `json(format)`:
 * contextual serializers for JVM types such as `UUID`, `BigDecimal` and `Instant`, both as whole
 * bodies and inside generic collections, and an open polymorphic hierarchy registered in the module.
 *
 * A payment ledger accepts card payments and bank transfers through one interface-typed endpoint,
 * answers with the generated payment id as a bare JSON string, and reports balances and settlement
 * times; a typed accounting client shares the format, while an auditor reads the raw wire JSON.
 */
public class PaymentLedgerSerializersModuleTest {

    /** Writes a `UUID` as its canonical string. */
    private object UuidAsString : KSerializer<UUID> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("ledger.Uuid", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: UUID): Unit = encoder.encodeString(value.toString())

        override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
    }

    /** Writes a `BigDecimal` as a plain string so that no precision is lost. */
    private object BigDecimalAsString : KSerializer<BigDecimal> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("ledger.BigDecimal", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: BigDecimal): Unit =
            encoder.encodeString(value.toPlainString())

        override fun deserialize(decoder: Decoder): BigDecimal = BigDecimal(decoder.decodeString())
    }

    /** Writes an `Instant` in ISO-8601 form. */
    private object InstantAsIso : KSerializer<Instant> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("ledger.Instant", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: Instant): Unit = encoder.encodeString(value.toString())

        override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
    }

    /**
     * Any payment the ledger books; the module registers its concrete kinds and serves it as a
     * contextual polymorphic serializer, so a bare `Payment` body resolves too.
     */
    private interface Payment {
        val account: String
        val amount: BigDecimal
    }

    /** A card payment. */
    @Serializable
    @SerialName("card")
    private data class CardPayment(
        override val account: String,
        @Contextual override val amount: BigDecimal,
        @SerialName("card_last4") val cardLast4: String,
    ) : Payment

    /** A bank transfer. */
    @Serializable
    @SerialName("transfer")
    private data class BankTransfer(
        override val account: String,
        @Contextual override val amount: BigDecimal,
        val iban: String,
        val reference: String,
    ) : Payment

    /** A booked payment as the ledger reports it. */
    @Serializable
    private data class LedgerEntry(
        @Contextual val id: UUID,
        @Contextual @SerialName("booked_at") val bookedAt: Instant,
        val payment: Payment,
    )

    /** The wire format both sides agree on. */
    private val ledgerJson: Json = Json {
        classDiscriminator = "method"
        serializersModule = SerializersModule {
            contextual(UuidAsString)
            contextual(BigDecimalAsString)
            contextual(InstantAsIso)
            contextual(Payment::class, PolymorphicSerializer(Payment::class))
            polymorphic(Payment::class) {
                subclass(CardPayment::class)
                subclass(BankTransfer::class)
            }
        }
    }

    /** Booking times handed out in order, so the ledger's clock is deterministic. */
    private val bookingTimes: List<Instant> = listOf(
        Instant.parse("2026-03-01T09:15:00Z"),
        Instant.parse("2026-03-01T09:16:30Z"),
        Instant.parse("2026-03-01T09:18:45.250Z"),
    )

    private val entries: MutableMap<UUID, LedgerEntry> = ConcurrentHashMap()
    private val bookingOrder: MutableList<UUID> = mutableListOf()

    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var accounting: HttpClient
    private lateinit var auditor: HttpClient
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(ServerContentNegotiation) {
                json(ledgerJson)
            }
            routing {
                post("/payments") {
                    val payment: Payment = call.receive()
                    val entry: LedgerEntry = synchronized(bookingOrder) {
                        val booked = LedgerEntry(
                            id = UUID.nameUUIDFromBytes("payment-${bookingOrder.size}".toByteArray()),
                            bookedAt = bookingTimes[bookingOrder.size],
                            payment = payment,
                        )
                        bookingOrder.add(booked.id)
                        entries[booked.id] = booked
                        booked
                    }
                    call.respond(HttpStatusCode.Created, entry.id)
                }
                get("/payments") {
                    val booked: List<Payment> = synchronized(bookingOrder) {
                        bookingOrder.map { entries.getValue(it).payment }
                    }
                    call.respond(booked)
                }
                get("/entries/{id}") {
                    val id: UUID = UUID.fromString(call.parameters["id"]!!)
                    call.respond(entries.getValue(id))
                }
                get("/entries/{id}/booked-at") {
                    val id: UUID = UUID.fromString(call.parameters["id"]!!)
                    call.respond(entries.getValue(id).bookedAt)
                }
                get("/balances") {
                    val balances: Map<String, BigDecimal> = entries.values
                        .groupBy { it.payment.account }
                        .mapValues { (_, booked) -> booked.fold(BigDecimal.ZERO) { sum, e -> sum + e.payment.amount } }
                        .toSortedMap()
                    call.respond(balances)
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
        accounting = HttpClient(ClientCIO) {
            install(ClientContentNegotiation) {
                json(ledgerJson)
            }
        }
        auditor = HttpClient(ClientCIO)
    }

    @AfterEach
    fun stopServer() {
        accounting.close()
        auditor.close()
        server.stop(gracePeriodMillis = 0L, timeoutMillis = 10_000L)
    }

    private fun url(path: String): String = "http://127.0.0.1:$port$path"

    private suspend fun book(payment: Payment): UUID {
        val created: HttpResponse = accounting.post(url("/payments")) {
            contentType(ContentType.Application.Json)
            setBody<Payment>(payment)
        }
        assertEquals(HttpStatusCode.Created, created.status)
        return created.body()
    }

    @Test
    fun `accounting books polymorphic payments and reads contextual values back`() = runBlocking {
        val coffee: Payment = CardPayment(account = "ops", amount = BigDecimal("4.20"), cardLast4 = "4242")
        val rent: Payment = BankTransfer(
            account = "facilities",
            amount = BigDecimal("1850.00"),
            iban = "DE89370400440532013000",
            reference = "rent-2026-03",
        )
        val license: Payment = CardPayment(
            account = "ops",
            amount = BigDecimal("0.000000000000000001"),
            cardLast4 = "0005",
        )

        val coffeeId: UUID = book(coffee)
        val rentId: UUID = book(rent)
        val licenseId: UUID = book(license)
        assertEquals(UUID.nameUUIDFromBytes("payment-0".toByteArray()), coffeeId)
        assertEquals(3, setOf(coffeeId, rentId, licenseId).size)

        val listed: List<Payment> = accounting.get(url("/payments")).body()
        assertEquals(listOf(coffee, rent, license), listed)
        assertTrue(listed[1] is BankTransfer)

        val rentEntry: LedgerEntry = accounting.get(url("/entries/$rentId")).body()
        assertEquals(LedgerEntry(id = rentId, bookedAt = bookingTimes[1], payment = rent), rentEntry)

        val licenseBookedAt: Instant = accounting.get(url("/entries/$licenseId/booked-at")).body()
        assertEquals(Instant.parse("2026-03-01T09:18:45.250Z"), licenseBookedAt)

        val balances: Map<String, BigDecimal> = accounting.get(url("/balances")).body()
        assertEquals(
            mapOf(
                "facilities" to BigDecimal("1850.00"),
                "ops" to BigDecimal("4.200000000000000001"),
            ),
            balances,
        )
    }

    @Test
    fun `auditor reads the wire form and unregistered payment kinds are rejected`() = runBlocking {
        val created: HttpResponse = auditor.post(url("/payments")) {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {"method": "transfer", "account": "payroll", "amount": "12500.75",
                 "iban": "NL91ABNA0417164300", "reference": "salary-03"}
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val id: String = (Json.parseToJsonElement(created.bodyAsText()) as JsonPrimitive).also {
            assertTrue(it.isString)
        }.content
        assertEquals(UUID.nameUUIDFromBytes("payment-0".toByteArray()).toString(), id)

        val entry: JsonObject = Json.parseToJsonElement(auditor.get(url("/entries/$id")).bodyAsText()).jsonObject
        assertEquals(setOf("id", "booked_at", "payment"), entry.keys)
        assertEquals("2026-03-01T09:15:00Z", entry.getValue("booked_at").jsonPrimitive.content)
        val payment: JsonObject = entry.getValue("payment").jsonObject
        assertEquals("transfer", payment.getValue("method").jsonPrimitive.content)
        assertEquals(JsonPrimitive("12500.75"), payment.getValue("amount"))
        assertFalse(payment.containsKey("type"))

        val balances: JsonObject = Json.parseToJsonElement(auditor.get(url("/balances")).bodyAsText()).jsonObject
        assertEquals(JsonPrimitive("12500.75"), balances.getValue("payroll"))

        val unknownKind: HttpResponse = auditor.post(url("/payments")) {
            contentType(ContentType.Application.Json)
            setBody("""{"method": "crypto", "account": "ops", "amount": "1", "wallet": "bc1q"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, unknownKind.status)

        val missingDiscriminator: HttpResponse = auditor.post(url("/payments")) {
            contentType(ContentType.Application.Json)
            setBody("""{"account": "ops", "amount": "1", "card_last4": "1111"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, missingDiscriminator.status)

        val listing: JsonArray = Json.parseToJsonElement(auditor.get(url("/payments")).bodyAsText()).jsonArray
        assertEquals(1, listing.size)
        assertEquals("payroll", listing[0].jsonObject.getValue("account").jsonPrimitive.content)
    }
}
