/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_cbor_jvm

import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises runtime serializer resolution for CBOR: serializers looked up by `KType`
 * for generic envelope classes, singleton objects, and `@Transient` properties.
 *
 * A message bus keeps a registry from topic name to the `KType` of its payload, and
 * publishes every message as a CBOR `Envelope<T>`; consumers resolve the serializer
 * from the registry at runtime instead of naming the payload class at compile time.
 */
@OptIn(ExperimentalSerializationApi::class)
public class MessageBusSerializerLookupCborTest {

    @Serializable
    public data class Envelope<T>(val topic: String, val sequence: Long, val payload: T)

    @Serializable
    public data class Page<T>(val items: List<T>, val nextCursor: String? = null)

    @Serializable
    public data class StockLevel(val sku: String, val onHand: Int) {
        @Transient
        public val lowStock: Boolean = onHand < 5
    }

    @Serializable
    public data class PriceChange(val sku: String, val oldCents: Long, val newCents: Long)

    @Serializable
    public object Heartbeat

    public class MessageBus(private val cbor: Cbor) {
        private val topics: MutableMap<String, KType> = mutableMapOf()
        private val log: MutableList<ByteArray> = mutableListOf()

        public fun register(topic: String, payloadType: KType): Unit {
            topics[topic] = payloadType
        }

        public inline fun <reified T> publish(topic: String, sequence: Long, payload: T): Unit {
            append(cbor().encodeToByteArray(Envelope(topic, sequence, payload)))
        }

        public fun cbor(): Cbor = cbor

        public fun append(frame: ByteArray): Unit {
            log.add(frame)
        }

        public fun frames(): List<ByteArray> = log.toList()

        /** Decodes [frame] with the serializer registered for [topic]. */
        @Suppress("UNCHECKED_CAST")
        public fun consume(topic: String, frame: ByteArray): Envelope<Any?> {
            val payloadType: KType = requireNotNull(topics[topic]) { "Unknown topic $topic" }
            val serializer: KSerializer<Any?> = cbor.serializersModule.serializer(envelopeTypeOf(payloadType))
            return cbor.decodeFromByteArray(serializer, frame) as Envelope<Any?>
        }

        private fun envelopeTypeOf(payloadType: KType): KType = when (payloadType) {
            typeOf<StockLevel>() -> typeOf<Envelope<StockLevel>>()
            typeOf<Page<PriceChange>>() -> typeOf<Envelope<Page<PriceChange>>>()
            typeOf<Heartbeat>() -> typeOf<Envelope<Heartbeat>>()
            typeOf<Map<String, List<Int>>>() -> typeOf<Envelope<Map<String, List<Int>>>>()
            else -> error("No envelope type for $payloadType")
        }
    }

    @Test
    fun consumersResolvePayloadSerializersFromTheTopicRegistry(): Unit {
        val bus: MessageBus = MessageBus(Cbor { useDefiniteLengthEncoding = true })
        bus.register("inventory", typeOf<StockLevel>())
        bus.register("prices", typeOf<Page<PriceChange>>())
        bus.register("heartbeat", typeOf<Heartbeat>())
        bus.register("histogram", typeOf<Map<String, List<Int>>>())

        bus.publish("inventory", 1L, StockLevel("BOLT-M6", 3))
        bus.publish(
            "prices",
            2L,
            Page(listOf(PriceChange("BOLT-M6", 15L, 18L), PriceChange("NUT-M6", 5L, 6L)), "c-2"),
        )
        bus.publish("heartbeat", 3L, Heartbeat)
        bus.publish("histogram", 4L, mapOf("latencyMs" to listOf(3, 5, 8)))

        val topicsInOrder: List<String> = listOf("inventory", "prices", "heartbeat", "histogram")
        val consumed: List<Envelope<Any?>> = bus.frames().mapIndexed { index: Int, frame: ByteArray ->
            bus.consume(topicsInOrder[index], frame)
        }

        assertEquals(listOf(1L, 2L, 3L, 4L), consumed.map { envelope: Envelope<Any?> -> envelope.sequence })
        assertEquals(topicsInOrder, consumed.map { envelope: Envelope<Any?> -> envelope.topic })

        val stock: StockLevel = consumed[0].payload as StockLevel
        assertEquals(StockLevel("BOLT-M6", 3), stock)
        assertTrue(stock.lowStock)

        @Suppress("UNCHECKED_CAST")
        val prices: Page<PriceChange> = consumed[1].payload as Page<PriceChange>
        assertEquals("c-2", prices.nextCursor)
        assertEquals(listOf(18L, 6L), prices.items.map { change: PriceChange -> change.newCents })

        assertSame(Heartbeat, consumed[2].payload)
        assertEquals(mapOf("latencyMs" to listOf(3, 5, 8)), consumed[3].payload)
    }

    @Test
    fun reifiedAndTypeBasedLookupsProduceTheSameWireFormat(): Unit {
        val cbor: Cbor = Cbor { useDefiniteLengthEncoding = true }
        val message: Envelope<StockLevel> = Envelope("inventory", 7L, StockLevel("WASHER-8", 40))

        val reified: ByteArray = cbor.encodeToByteArray(message)
        val byType: ByteArray = cbor.encodeToByteArray(serializer(typeOf<Envelope<StockLevel>>()), message)
        val byCompanion: ByteArray = cbor.encodeToByteArray(
            Envelope.serializer(StockLevel.serializer()),
            message,
        )

        assertEquals(reified.toList(), byType.toList())
        assertEquals(reified.toList(), byCompanion.toList())

        val decoded: Envelope<StockLevel> = cbor.decodeFromByteArray<Envelope<StockLevel>>(byType)
        assertEquals(message, decoded)
        assertEquals(false, decoded.payload.lowStock)

        val heartbeat: ByteArray = cbor.encodeToByteArray(Heartbeat)
        assertEquals(listOf(0xA0.toByte()), heartbeat.toList())
        assertSame(Heartbeat, cbor.decodeFromByteArray<Heartbeat>(heartbeat))
    }
}
