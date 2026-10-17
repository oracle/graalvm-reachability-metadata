/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_json_jvm

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises `SerializersModule` configuration of `Json`: open polymorphism over an interface
 * whose subclasses are registered by separate modules combined with `+`, a default deserializer
 * for unregistered type names, and `@Contextual` properties resolved through `contextual` serializers.
 *
 * An order service stores domain events as JSON envelopes; the core module and a shipping plugin
 * each register their event types, the projection folds the stored stream into order summaries,
 * and events written by newer plugins the service does not know are kept as raw payloads.
 */
@OptIn(ExperimentalSerializationApi::class)
public class OrderEventStoreSerializersModuleTest {

    /** A domain event; concrete types are registered by modules, not known to this interface. */
    public interface DomainEvent

    @Serializable
    @SerialName("order.placed")
    public data class OrderPlaced(
        @Contextual val orderId: UUID,
        @Contextual val total: BigDecimal,
        val currency: String,
    ) : DomainEvent

    @Serializable
    @SerialName("order.cancelled")
    public data class OrderCancelled(@Contextual val orderId: UUID, val reason: String) : DomainEvent

    @Serializable
    @SerialName("shipment.dispatched")
    public data class ShipmentDispatched(
        @Contextual val orderId: UUID,
        val carrier: String,
        val parcels: Int,
    ) : DomainEvent

    /** An event whose type name no registered module knows, kept verbatim for later replay. */
    public data class UnknownEvent(val type: String, val payload: JsonObject) : DomainEvent

    @Serializable
    public data class Envelope(
        val sequence: Long,
        @Contextual val recordedAt: Instant,
        val event: DomainEvent,
    )

    public data class OrderSummary(
        val total: BigDecimal,
        val status: String,
        val carrier: String? = null,
    )

    public object UuidSerializer : KSerializer<UUID> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("app.Uuid", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: UUID): Unit = encoder.encodeString(value.toString())

        override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
    }

    /** Money travels as a string so no precision is lost to a JSON number. */
    public object BigDecimalSerializer : KSerializer<BigDecimal> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("app.BigDecimal", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: BigDecimal): Unit =
            encoder.encodeString(value.toPlainString())

        override fun deserialize(decoder: Decoder): BigDecimal = BigDecimal(decoder.decodeString())
    }

    public object InstantSerializer : KSerializer<Instant> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("app.Instant", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: Instant): Unit = encoder.encodeString(value.toString())

        override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
    }

    /** Captures the whole event object for a type name that no module registers. */
    public class UnknownEventSerializer(private val type: String) : KSerializer<UnknownEvent> {
        override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

        override fun serialize(encoder: Encoder, value: UnknownEvent): Unit =
            throw SerializationException("Unknown event ${value.type} is read-only")

        override fun deserialize(decoder: Decoder): UnknownEvent {
            val element: JsonObject = (decoder as JsonDecoder).decodeJsonElement().jsonObject
            return UnknownEvent(type, JsonObject(element - "kind"))
        }
    }

    /** Folds a stored event stream into per-order summaries. */
    public class OrderProjection {
        private val summaries: MutableMap<UUID, OrderSummary> = linkedMapOf()
        public val skipped: MutableList<UnknownEvent> = mutableListOf()

        public fun apply(envelope: Envelope) {
            when (val event: DomainEvent = envelope.event) {
                is OrderPlaced -> summaries[event.orderId] = OrderSummary(event.total, "placed")
                is OrderCancelled -> summaries.computeIfPresent(event.orderId) { _, s -> s.copy(status = "cancelled") }
                is ShipmentDispatched -> summaries.computeIfPresent(event.orderId) { _, s ->
                    s.copy(status = "shipped", carrier = event.carrier)
                }
                is UnknownEvent -> skipped += event
                else -> throw IllegalStateException("Unhandled event $event")
            }
        }

        public fun summaries(): Map<UUID, OrderSummary> = summaries.toMap()
    }

    private val valueTypes: SerializersModule = SerializersModule {
        contextual(UuidSerializer)
        contextual(BigDecimalSerializer)
        contextual(InstantSerializer)
    }

    private val coreEvents: SerializersModule = SerializersModule {
        polymorphic(DomainEvent::class) {
            subclass(OrderPlaced::class)
            subclass(OrderCancelled::class)
            defaultDeserializer { type -> UnknownEventSerializer(type ?: "<missing>") }
        }
    }

    private val shippingPlugin: SerializersModule = SerializersModule {
        polymorphic(DomainEvent::class) {
            subclass(ShipmentDispatched::class)
        }
    }

    private val store: Json = Json {
        classDiscriminator = "kind"
        serializersModule = valueTypes + coreEvents + shippingPlugin
    }

    private val first: UUID = UUID.fromString("0b9c5a9e-3f4d-4b8a-9a51-1f6c2d7e8a01")
    private val second: UUID = UUID.fromString("7d2e1c44-90ab-4cde-8f12-3456789abcde")

    @Test
    fun envelopesFromEveryModuleRoundTripWithContextualValues() {
        val envelopes: List<Envelope> = listOf(
            Envelope(1, Instant.parse("2026-03-01T10:15:30Z"), OrderPlaced(first, BigDecimal("129.90"), "EUR")),
            Envelope(2, Instant.parse("2026-03-01T11:00:00Z"), ShipmentDispatched(first, "DHL", 2)),
            Envelope(3, Instant.parse("2026-03-02T08:30:00.250Z"), OrderCancelled(second, "customer request")),
        )

        val written: String = store.encodeToString(envelopes)

        val placed: String = """{"sequence":1,"recordedAt":"2026-03-01T10:15:30Z","event":""" +
            """{"kind":"order.placed","orderId":"$first","total":"129.90","currency":"EUR"}}"""
        assertTrue(written.startsWith("[$placed,"), written)
        assertTrue(written.contains(""""event":{"kind":"shipment.dispatched","orderId":"$first","""), written)
        assertTrue(written.contains(""""recordedAt":"2026-03-02T08:30:00.250Z""""), written)

        val restored: List<Envelope> = store.decodeFromString(written)
        assertEquals(envelopes, restored)
        assertEquals(BigDecimal("129.90"), (restored[0].event as OrderPlaced).total)
    }

    @Test
    fun projectionKeepsEventsFromUnregisteredPluginsAsRawPayloads() {
        val stream: String = """
            [
              {"sequence": 10, "recordedAt": "2026-04-01T09:00:00Z",
               "event": {"kind": "order.placed", "orderId": "$first", "total": "49.5", "currency": "EUR"}},
              {"sequence": 11, "recordedAt": "2026-04-01T09:05:00Z",
               "event": {"kind": "loyalty.points-awarded", "orderId": "$first", "points": 40}},
              {"sequence": 12, "recordedAt": "2026-04-01T10:00:00Z",
               "event": {"orderId": "$first", "carrier": "UPS", "parcels": 1, "kind": "shipment.dispatched"}},
              {"sequence": 13, "recordedAt": "2026-04-02T07:45:00Z",
               "event": {"kind": "order.placed", "orderId": "$second", "total": "1000000.01", "currency": "EUR"}},
              {"sequence": 14, "recordedAt": "2026-04-02T08:00:00Z",
               "event": {"kind": "order.cancelled", "orderId": "$second", "reason": "fraud check"}}
            ]
        """.trimIndent()

        val projection: OrderProjection = OrderProjection()
        store.decodeFromString<List<Envelope>>(stream).forEach(projection::apply)

        assertEquals(
            mapOf(
                first to OrderSummary(BigDecimal("49.5"), "shipped", "UPS"),
                second to OrderSummary(BigDecimal("1000000.01"), "cancelled"),
            ),
            projection.summaries(),
        )
        assertEquals(1, projection.skipped.size)
        val loyalty: UnknownEvent = projection.skipped.single()
        assertEquals("loyalty.points-awarded", loyalty.type)
        assertEquals(setOf("orderId", "points"), loyalty.payload.keys)
        assertEquals(40, loyalty.payload.getValue("points").jsonPrimitive.int)
    }

    @Test
    fun serviceWithoutThePluginModuleRejectsAndDefersItsEvents() {
        val coreOnly: Json = Json {
            classDiscriminator = "kind"
            serializersModule = valueTypes + coreEvents
        }
        val dispatched: Envelope =
            Envelope(5, Instant.parse("2026-05-05T05:05:05Z"), ShipmentDispatched(second, "GLS", 3))

        assertThrows(SerializationException::class.java) { coreOnly.encodeToString(dispatched) }

        val stored: String = store.encodeToString(dispatched)
        val deferred: Envelope = coreOnly.decodeFromString(stored)
        val event: UnknownEvent = deferred.event as UnknownEvent
        assertEquals("shipment.dispatched", event.type)
        assertEquals("GLS", event.payload.getValue("carrier").jsonPrimitive.content)
        assertEquals(Instant.parse("2026-05-05T05:05:05Z"), deferred.recordedAt)
    }

    @Test
    fun contextualValuesRequireTheirModule() {
        assertNotNull(store.serializersModule.getContextual(UUID::class))
        val eventsOnly: Json = Json {
            classDiscriminator = "kind"
            serializersModule = coreEvents + shippingPlugin
        }
        val placed: Envelope = Envelope(1, Instant.EPOCH, OrderPlaced(first, BigDecimal.TEN, "USD"))

        val failure: SerializationException =
            assertThrows(SerializationException::class.java) { eventsOnly.encodeToString(placed) }
        assertTrue(failure.message!!.contains("Instant"), failure.message)

        val text: String = """{"sequence":1,"recordedAt":"x","event":{"kind":"order.placed"}}"""
        assertThrows(SerializationException::class.java) { eventsOnly.decodeFromString<Envelope>(text) }
    }
}
