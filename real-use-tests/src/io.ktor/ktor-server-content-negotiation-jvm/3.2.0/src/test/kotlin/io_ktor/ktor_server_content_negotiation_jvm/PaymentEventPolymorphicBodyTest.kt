/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_content_negotiation_jvm

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Polymorphic request and response bodies resolved by the negotiated converter from the declared body type.
 *
 * A payment gateway takes payment lifecycle events as one sealed hierarchy tagged with a `kind`
 * discriminator, takes settlements of an open abstract `Settlement` class whose implementations are
 * registered in the converter's `SerializersModule`, and keeps a back-office ledger whose entries
 * are Jackson-annotated with `@JsonTypeInfo` under a vendor JSON type. The test checks that the
 * plugin hands the declared base type to the converter so the route receives the right subtype,
 * that responses declared as the base type carry the discriminator while a response declared as
 * a concrete subtype does not, that an unknown discriminator is rejected before the route runs,
 * and that Jackson writes and reads its own type ids for the ledger.
 */
class PaymentEventPolymorphicBodyTest {

    @Serializable
    sealed interface PaymentEvent {
        val paymentId: String

        @Serializable
        @SerialName("authorized")
        data class Authorized(override val paymentId: String, val amountCents: Long, val currency: String) : PaymentEvent

        @Serializable
        @SerialName("captured")
        data class Captured(override val paymentId: String, val amountCents: Long) : PaymentEvent

        @Serializable
        @SerialName("refunded")
        data class Refunded(override val paymentId: String, val amountCents: Long, val reason: String) : PaymentEvent
    }

    @Serializable
    data class EventAck(val paymentId: String, val events: Int, val settledCents: Long)

    @Serializable
    abstract class Settlement {
        abstract val batchId: String
        abstract val totalCents: Long
    }

    @Serializable
    @SerialName("bank_transfer")
    data class BankTransfer(override val batchId: String, override val totalCents: Long, val iban: String) : Settlement()

    @Serializable
    @SerialName("card_network")
    data class CardNetwork(override val batchId: String, override val totalCents: Long, val network: String) : Settlement()

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes(
        JsonSubTypes.Type(value = LedgerEntry.Fee::class, name = "fee"),
        JsonSubTypes.Type(value = LedgerEntry.Payout::class, name = "payout"),
    )
    sealed class LedgerEntry {
        abstract val amountCents: Long

        data class Fee(override val amountCents: Long, val reason: String) : LedgerEntry()

        data class Payout(override val amountCents: Long, val iban: String) : LedgerEntry()
    }

    /** Wraps the entries so Jackson sees the `LedgerEntry` element type and writes each type id. */
    data class LedgerPage(val entries: List<LedgerEntry>, val balanceCents: Long)

    private val timelines: ConcurrentHashMap<String, ConcurrentLinkedQueue<PaymentEvent>> = ConcurrentHashMap()
    private val settlements: ConcurrentLinkedQueue<Settlement> = ConcurrentLinkedQueue()
    private val ledger: ConcurrentLinkedQueue<LedgerEntry> = ConcurrentLinkedQueue()

    private fun runGateway(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) {
                json(GATEWAY_JSON)
                jackson(contentType = LEDGER_JSON)
            }
            routing {
                post("/payments/events") {
                    val event: PaymentEvent = call.receive()
                    val timeline: ConcurrentLinkedQueue<PaymentEvent> =
                        timelines.computeIfAbsent(event.paymentId) { ConcurrentLinkedQueue() }
                    timeline.add(event)
                    val settled: Long = timeline.sumOf { recorded: PaymentEvent ->
                        when (recorded) {
                            is PaymentEvent.Authorized -> 0L
                            is PaymentEvent.Captured -> recorded.amountCents
                            is PaymentEvent.Refunded -> -recorded.amountCents
                        }
                    }
                    call.respond(HttpStatusCode.Accepted, EventAck(event.paymentId, timeline.size, settled))
                }
                get("/payments/{id}/events") {
                    val events: List<PaymentEvent> = timelines[call.parameters["id"]!!]!!.toList()
                    call.respond(events)
                }
                get("/payments/{id}/latest") {
                    val latest: PaymentEvent = timelines[call.parameters["id"]!!]!!.last()
                    call.respond(latest)
                }
                get("/payments/{id}/capture") {
                    val capture: PaymentEvent.Captured =
                        timelines[call.parameters["id"]!!]!!.filterIsInstance<PaymentEvent.Captured>().last()
                    call.respond(capture)
                }
                post("/settlements") {
                    val settlement: Settlement = call.receive()
                    settlements.add(settlement)
                    val all: List<Settlement> = settlements.toList()
                    call.respond(HttpStatusCode.Created, all)
                }
                post("/ledger") {
                    val entry: LedgerEntry = call.receive()
                    ledger.add(entry)
                    val entries: List<LedgerEntry> = ledger.toList()
                    val balance: Long = entries.sumOf { recorded: LedgerEntry ->
                        when (recorded) {
                            is LedgerEntry.Fee -> -recorded.amountCents
                            is LedgerEntry.Payout -> recorded.amountCents
                        }
                    }
                    call.respond(LedgerPage(entries, balance))
                }
            }
        }
        block()
    }

    @Test
    fun sealedEventsAreDecodedByTheirDiscriminator() = runGateway {
        val bodies: List<String> = listOf(
            """{"kind":"authorized","paymentId":"pay-81","amountCents":12900,"currency":"EUR"}""",
            """{"paymentId":"pay-81","kind":"captured","amountCents":12900}""",
            """{"paymentId":"pay-81","amountCents":2500,"reason":"damaged lid","kind":"refunded"}""",
        )
        val acks: MutableList<EventAck> = mutableListOf()
        for (body: String in bodies) {
            val response: HttpResponse = client.post("/payments/events") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            assertEquals(HttpStatusCode.Accepted, response.status)
            assertFormat(ContentType.Application.Json, response)
            acks.add(GATEWAY_JSON.decodeFromString<EventAck>(response.bodyAsText()))
        }

        assertEquals(
            listOf(EventAck("pay-81", 1, 0), EventAck("pay-81", 2, 12_900), EventAck("pay-81", 3, 10_400)),
            acks,
        )
        assertEquals(TIMELINE, timelines["pay-81"]!!.toList())

        val events: HttpResponse = client.get("/payments/pay-81/events") {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }
        assertEquals(HttpStatusCode.OK, events.status)
        val text: String = events.bodyAsText()
        assertEquals(TIMELINE, GATEWAY_JSON.decodeFromString<List<PaymentEvent>>(text))
        val kinds: List<String> = GATEWAY_JSON.parseToJsonElement(text).jsonArray
            .map { element -> element.jsonObject["kind"]!!.jsonPrimitive.content }
        assertEquals(listOf("authorized", "captured", "refunded"), kinds)
    }

    @Test
    fun declaredResponseTypeDecidesWhetherTheDiscriminatorIsWritten() = runGateway {
        for (event: PaymentEvent in TIMELINE) {
            val posted: HttpResponse = client.post("/payments/events") {
                contentType(ContentType.Application.Json)
                setBody(GATEWAY_JSON.encodeToString(PaymentEvent.serializer(), event))
            }
            assertEquals(HttpStatusCode.Accepted, posted.status)
        }

        val latest: HttpResponse = client.get("/payments/pay-81/latest")
        assertEquals(HttpStatusCode.OK, latest.status)
        val latestJson: JsonObject = GATEWAY_JSON.parseToJsonElement(latest.bodyAsText()).jsonObject
        assertEquals("refunded", latestJson["kind"]!!.jsonPrimitive.content)
        assertEquals(TIMELINE.last(), GATEWAY_JSON.decodeFromString<PaymentEvent>(latest.bodyAsText()))

        val capture: HttpResponse = client.get("/payments/pay-81/capture")
        assertEquals(HttpStatusCode.OK, capture.status)
        val captureJson: JsonObject = GATEWAY_JSON.parseToJsonElement(capture.bodyAsText()).jsonObject
        assertFalse(captureJson.containsKey("kind"), captureJson.toString())
        assertEquals(TIMELINE[1], GATEWAY_JSON.decodeFromString<PaymentEvent.Captured>(capture.bodyAsText()))
    }

    @Test
    fun openSettlementHierarchyUsesTheConverterModule() = runGateway {
        val transfer: HttpResponse = client.post("/settlements") {
            contentType(ContentType.Application.Json)
            setBody("""{"kind":"bank_transfer","batchId":"b-0412","totalCents":880000,"iban":"DE89370400440532013000"}""")
        }
        assertEquals(HttpStatusCode.Created, transfer.status)

        val card: HttpResponse = client.post("/settlements") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            setBody("""{"kind":"card_network","batchId":"b-0413","totalCents":152300,"network":"visa"}""")
        }
        assertEquals(HttpStatusCode.Created, card.status)
        assertFormat(ContentType.Application.Json, card)

        val expected: List<Settlement> = listOf(
            BankTransfer("b-0412", 880_000, "DE89370400440532013000"),
            CardNetwork("b-0413", 152_300, "visa"),
        )
        assertEquals(expected, settlements.toList())
        assertEquals(expected, GATEWAY_JSON.decodeFromString<List<Settlement>>(card.bodyAsText()))
        assertEquals(880_000L + 152_300L, settlements.sumOf { settlement: Settlement -> settlement.totalCents })
    }

    @Test
    fun unknownEventKindIsRejectedBeforeTheRouteRuns() = runGateway {
        val response: HttpResponse = client.post("/payments/events") {
            contentType(ContentType.Application.Json)
            setBody("""{"kind":"chargeback","paymentId":"pay-90","amountCents":4000}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(timelines.isEmpty(), timelines.toString())
    }

    @Test
    fun jacksonLedgerEntriesCarryAnnotatedTypeIds() = runGateway {
        val fee: HttpResponse = client.post("/ledger") {
            contentType(LEDGER_JSON)
            header(HttpHeaders.Accept, LEDGER_JSON.toString())
            setBody("""{"type":"fee","amountCents":350,"reason":"card network fee"}""")
        }
        assertEquals(HttpStatusCode.OK, fee.status)

        val payout: HttpResponse = client.post("/ledger") {
            contentType(LEDGER_JSON)
            header(HttpHeaders.Accept, LEDGER_JSON.toString())
            setBody("""{"amountCents":99650,"iban":"FR7630006000011234567890189","type":"payout"}""")
        }
        assertEquals(HttpStatusCode.OK, payout.status)
        assertFormat(LEDGER_JSON, payout)

        assertEquals(
            listOf(LedgerEntry.Fee(350, "card network fee"), LedgerEntry.Payout(99_650, "FR7630006000011234567890189")),
            ledger.toList(),
        )
        val page: JsonObject = GATEWAY_JSON.parseToJsonElement(payout.bodyAsText()).jsonObject
        assertEquals("99300", page["balanceCents"]!!.jsonPrimitive.content)
        val written: List<JsonObject> = page["entries"]!!.jsonArray.map { element -> element.jsonObject }
        assertEquals(listOf("fee", "payout"), written.map { entry -> entry["type"]!!.jsonPrimitive.content })
        assertEquals(listOf("350", "99650"), written.map { entry -> entry["amountCents"]!!.jsonPrimitive.content })
        assertEquals("card network fee", written[0]["reason"]!!.jsonPrimitive.content)
        assertEquals("FR7630006000011234567890189", written[1]["iban"]!!.jsonPrimitive.content)
    }

    private fun assertFormat(expected: ContentType, response: HttpResponse) {
        val actual: ContentType? = response.contentType()
        assertTrue(actual != null && actual.match(expected), "expected $expected, got $actual")
    }

    private companion object {
        val LEDGER_JSON: ContentType = ContentType("application", "vnd.ledger+json")

        val GATEWAY_JSON: Json = Json {
            classDiscriminator = "kind"
            serializersModule = SerializersModule {
                polymorphic(Settlement::class) {
                    subclass(BankTransfer::class)
                    subclass(CardNetwork::class)
                }
            }
        }

        val TIMELINE: List<PaymentEvent> = listOf(
            PaymentEvent.Authorized("pay-81", 12_900, "EUR"),
            PaymentEvent.Captured("pay-81", 12_900),
            PaymentEvent.Refunded("pay-81", 2_500, "damaged lid"),
        )
    }
}
