/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_core_jvm

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises open polymorphism registered in a `SerializersModule`: `polymorphic { subclass }`
 * scopes, `PolymorphicSerializer` dispatch, default deserializers and module composition.
 *
 * An order service appends domain events of an open `OrderEvent` hierarchy to a JSON journal;
 * the catalog and billing features each contribute their own event types through a module.
 */
@OptIn(ExperimentalSerializationApi::class)
public class OrderJournalPolymorphicModuleTest {

    /** Root of the open event hierarchy; features add their own implementations. */
    public interface OrderEvent {
        public val orderId: String
    }

    /** A customer placed an order. */
    public data class OrderPlaced(
        override val orderId: String,
        val customer: String,
        val totalCents: Long,
    ) : OrderEvent

    /** A catalog item was added to an order. */
    public data class ItemAdded(
        override val orderId: String,
        val sku: String,
        val quantity: Int,
    ) : OrderEvent

    /** Billing cancelled an order, optionally recording why. */
    public data class OrderCancelled(
        override val orderId: String,
        val reason: String?,
    ) : OrderEvent

    /** An event written by a newer service version this reader does not know. */
    public data class UnrecognizedEvent(
        override val orderId: String,
        val type: String,
    ) : OrderEvent

    /** An event type no feature registered with the module. */
    public data class RefundIssued(
        override val orderId: String,
        val amountCents: Long,
    ) : OrderEvent

    /** A journal entry: its position in the stream and the event it carries. */
    public data class JournalEntry(
        val sequence: Long,
        val event: OrderEvent,
    )

    /** Serializer for [OrderPlaced], tagged on the wire as `order.placed`. */
    public object OrderPlacedSerializer : KSerializer<OrderPlaced> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("order.placed") {
            element<String>("orderId")
            element<String>("customer")
            element<Long>("totalCents")
        }

        override fun serialize(encoder: Encoder, value: OrderPlaced) {
            encoder.encodeStructure(descriptor) {
                encodeStringElement(descriptor, 0, value.orderId)
                encodeStringElement(descriptor, 1, value.customer)
                encodeLongElement(descriptor, 2, value.totalCents)
            }
        }

        override fun deserialize(decoder: Decoder): OrderPlaced = decoder.decodeStructure(descriptor) {
            var orderId: String? = null
            var customer: String? = null
            var totalCents: Long? = null
            while (true) {
                when (val index: Int = decodeElementIndex(descriptor)) {
                    0 -> orderId = decodeStringElement(descriptor, 0)
                    1 -> customer = decodeStringElement(descriptor, 1)
                    2 -> totalCents = decodeLongElement(descriptor, 2)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> throw SerializationException("Unexpected element index $index")
                }
            }
            OrderPlaced(
                orderId = orderId ?: throw SerializationException("orderId is missing"),
                customer = customer ?: throw SerializationException("customer is missing"),
                totalCents = totalCents ?: throw SerializationException("totalCents is missing"),
            )
        }
    }

    /** Serializer for [ItemAdded], tagged on the wire as `order.item-added`. */
    public object ItemAddedSerializer : KSerializer<ItemAdded> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("order.item-added") {
            element<String>("orderId")
            element<String>("sku")
            element<Int>("quantity")
        }

        override fun serialize(encoder: Encoder, value: ItemAdded) {
            encoder.encodeStructure(descriptor) {
                encodeStringElement(descriptor, 0, value.orderId)
                encodeStringElement(descriptor, 1, value.sku)
                encodeIntElement(descriptor, 2, value.quantity)
            }
        }

        override fun deserialize(decoder: Decoder): ItemAdded = decoder.decodeStructure(descriptor) {
            var orderId: String? = null
            var sku: String? = null
            var quantity: Int? = null
            while (true) {
                when (val index: Int = decodeElementIndex(descriptor)) {
                    0 -> orderId = decodeStringElement(descriptor, 0)
                    1 -> sku = decodeStringElement(descriptor, 1)
                    2 -> quantity = decodeIntElement(descriptor, 2)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> throw SerializationException("Unexpected element index $index")
                }
            }
            ItemAdded(
                orderId = orderId ?: throw SerializationException("orderId is missing"),
                sku = sku ?: throw SerializationException("sku is missing"),
                quantity = quantity ?: throw SerializationException("quantity is missing"),
            )
        }
    }

    /** Serializer for [OrderCancelled]; the reason is optional and omitted when absent. */
    public object OrderCancelledSerializer : KSerializer<OrderCancelled> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("order.cancelled") {
            element<String>("orderId")
            element<String>("reason", isOptional = true)
        }

        override fun serialize(encoder: Encoder, value: OrderCancelled) {
            encoder.encodeStructure(descriptor) {
                encodeStringElement(descriptor, 0, value.orderId)
                value.reason?.let { reason: String -> encodeStringElement(descriptor, 1, reason) }
            }
        }

        override fun deserialize(decoder: Decoder): OrderCancelled = decoder.decodeStructure(descriptor) {
            var orderId: String? = null
            var reason: String? = null
            while (true) {
                when (val index: Int = decodeElementIndex(descriptor)) {
                    0 -> orderId = decodeStringElement(descriptor, 0)
                    1 -> reason = decodeStringElement(descriptor, 1)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> throw SerializationException("Unexpected element index $index")
                }
            }
            OrderCancelled(orderId ?: throw SerializationException("orderId is missing"), reason)
        }
    }

    /** Fallback for unknown discriminators: keeps the order id and remembers the type tag. */
    public class UnrecognizedEventSerializer(private val type: String) : KSerializer<UnrecognizedEvent> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("order.unrecognized") {
            element<String>("orderId")
        }

        override fun serialize(encoder: Encoder, value: UnrecognizedEvent) {
            throw SerializationException("Unrecognized '${value.type}' events are read-only")
        }

        override fun deserialize(decoder: Decoder): UnrecognizedEvent = decoder.decodeStructure(descriptor) {
            var orderId: String? = null
            while (true) {
                when (val index: Int = decodeElementIndex(descriptor)) {
                    0 -> orderId = decodeStringElement(descriptor, 0)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> throw SerializationException("Unexpected element index $index")
                }
            }
            UnrecognizedEvent(orderId ?: throw SerializationException("orderId is missing"), type)
        }
    }

    /** Serializer for a journal entry whose event slot is polymorphic. */
    public object JournalEntrySerializer : KSerializer<JournalEntry> {
        private val eventSerializer: PolymorphicSerializer<OrderEvent> = PolymorphicSerializer(OrderEvent::class)

        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("order.JournalEntry") {
            element<Long>("sequence")
            element("event", eventSerializer.descriptor)
        }

        override fun serialize(encoder: Encoder, value: JournalEntry) {
            encoder.encodeStructure(descriptor) {
                encodeLongElement(descriptor, 0, value.sequence)
                encodeSerializableElement(descriptor, 1, eventSerializer, value.event)
            }
        }

        override fun deserialize(decoder: Decoder): JournalEntry = decoder.decodeStructure(descriptor) {
            var sequence: Long? = null
            var event: OrderEvent? = null
            while (true) {
                when (val index: Int = decodeElementIndex(descriptor)) {
                    0 -> sequence = decodeLongElement(descriptor, 0)
                    1 -> event = decodeSerializableElement(descriptor, 1, eventSerializer)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> throw SerializationException("Unexpected element index $index")
                }
            }
            JournalEntry(
                sequence = sequence ?: throw SerializationException("sequence is missing"),
                event = event ?: throw SerializationException("event is missing"),
            )
        }
    }

    /** Event types the catalog feature contributes. */
    private val catalogModule: SerializersModule = SerializersModule {
        polymorphic(OrderEvent::class) {
            subclass(OrderPlacedSerializer)
            subclass(ItemAddedSerializer)
        }
    }

    /** Event types the billing feature contributes, plus the fallback for unknown events. */
    private val billingModule: SerializersModule = SerializersModule {
        polymorphic(OrderEvent::class) {
            subclass(OrderCancelled::class, OrderCancelledSerializer)
            defaultDeserializer { type: String? -> UnrecognizedEventSerializer(type ?: "<none>") }
        }
    }

    private val journalModule: SerializersModule = catalogModule + billingModule

    private val json: Json = Json {
        serializersModule = journalModule
        ignoreUnknownKeys = true
    }

    private val journalSerializer: KSerializer<List<JournalEntry>> = ListSerializer(JournalEntrySerializer)

    private val journal: List<JournalEntry> = listOf(
        JournalEntry(1L, OrderPlaced("o-17", "ada", 12_950L)),
        JournalEntry(2L, ItemAdded("o-17", "sku-lamp", 2)),
        JournalEntry(3L, OrderCancelled("o-17", "payment declined")),
        JournalEntry(4L, OrderCancelled("o-18", null)),
    )

    @Test
    fun journalRoundTripsEveryRegisteredEventType() {
        val text: String = json.encodeToString(journalSerializer, journal)
        val restored: List<JournalEntry> = json.decodeFromString(journalSerializer, text)

        assertEquals(journal, restored)
        assertNull((restored[3].event as OrderCancelled).reason)
    }

    @Test
    fun discriminatorIsTheSubclassSerialName() {
        val text: String = json.encodeToString(
            PolymorphicSerializer(OrderEvent::class),
            ItemAdded("o-3", "sku-desk", 1),
        )

        assertEquals("""{"type":"order.item-added","orderId":"o-3","sku":"sku-desk","quantity":1}""", text)
        assertEquals(
            """{"sequence":9,"event":{"type":"order.cancelled","orderId":"o-4"}}""",
            json.encodeToString(JournalEntrySerializer, JournalEntry(9L, OrderCancelled("o-4", null))),
        )
    }

    @Test
    fun customDiscriminatorKeyIsHonoured() {
        val kindJson: Json = Json(json) { classDiscriminator = "kind" }
        val event: OrderEvent = OrderPlaced("o-5", "grace", 400L)
        val text: String = kindJson.encodeToString(PolymorphicSerializer(OrderEvent::class), event)

        assertEquals("""{"kind":"order.placed","orderId":"o-5","customer":"grace","totalCents":400}""", text)
        assertEquals(event, kindJson.decodeFromString(PolymorphicSerializer(OrderEvent::class), text))
    }

    @Test
    fun unknownEventTypesFallBackToTheDefaultDeserializer() {
        val text: String = """
            [
              {"sequence":1,"event":{"type":"order.placed","orderId":"o-9","customer":"lin","totalCents":5}},
              {"sequence":2,"event":{"type":"order.shipped","orderId":"o-9","carrier":"dhl"}}
            ]
        """.trimIndent()

        val restored: List<JournalEntry> = json.decodeFromString(journalSerializer, text)

        assertEquals(OrderPlaced("o-9", "lin", 5L), restored[0].event)
        assertEquals(UnrecognizedEvent("o-9", "order.shipped"), restored[1].event)
    }

    @Test
    fun moduleResolvesSubclassesByInstanceAndBySerialName() {
        assertSame(
            OrderPlacedSerializer,
            journalModule.getPolymorphic(OrderEvent::class, OrderPlaced("o-1", "ada", 1L)),
        )
        assertSame(OrderCancelledSerializer, journalModule.getPolymorphic(OrderEvent::class, "order.cancelled"))
        assertNull(journalModule.getPolymorphic(OrderEvent::class, RefundIssued("o-1", 10L)))
        assertNull(catalogModule.getPolymorphic(OrderEvent::class, "order.cancelled"))

        val fallback = journalModule.getPolymorphic(OrderEvent::class, "order.archived")
        assertEquals("order.unrecognized", fallback?.descriptor?.serialName)

        val rootDescriptor: SerialDescriptor = PolymorphicSerializer(OrderEvent::class).descriptor
        assertEquals(PolymorphicKind.OPEN, rootDescriptor.kind)
        assertTrue(rootDescriptor.serialName.contains("OrderEvent"))
    }

    @Test
    fun unregisteredSubclassIsRejectedOnEncode() {
        val error: SerializationException = assertThrows(SerializationException::class.java) {
            json.encodeToString(JournalEntrySerializer, JournalEntry(5L, RefundIssued("o-17", 300L)))
        }

        val message: String = error.message.orEmpty()
        assertTrue(message.contains("RefundIssued"), message)
        assertTrue(message.contains("polymorphic scope"), message)
    }

    @Test
    fun conflictingRegistrationsCannotBeCombined() {
        val sameRegistration: SerializersModule = SerializersModule {
            polymorphic(OrderEvent::class) {
                subclass(OrderCancelled::class, OrderCancelledSerializer)
            }
        }
        val competingFallback: SerializersModule = SerializersModule {
            polymorphic(OrderEvent::class) {
                defaultDeserializer { _: String? -> UnrecognizedEventSerializer("legacy") }
            }
        }

        val merged: SerializersModule = billingModule + sameRegistration
        assertSame(OrderCancelledSerializer, merged.getPolymorphic(OrderEvent::class, "order.cancelled"))
        assertThrows(IllegalArgumentException::class.java) {
            SerializersModule {
                include(billingModule)
                include(competingFallback)
            }
        }
    }
}
