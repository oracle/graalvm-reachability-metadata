/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_protobuf_jvm

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Generic `@Serializable` message classes encoded by `ProtoBuf` through serializers built for each type argument.
 *
 * A catalog RPC service wraps every reply in one generic envelope that carries a request id and
 * a typed payload, and pages its listings through a generic page message. The client looks up
 * the envelope serializer for each concrete reply type, either from the reified type or by
 * passing the payload serializer to the generated factory, and ships the bytes as hex text.
 */
@OptIn(ExperimentalSerializationApi::class)
class CatalogRpcGenericEnvelopeTest {

    @Serializable
    data class Envelope<T>(
        @ProtoNumber(1) val requestId: Long,
        @ProtoNumber(2) val payload: T,
        @ProtoNumber(3) val traceTag: String = "",
    )

    @Serializable
    data class Page<T>(
        @ProtoNumber(1) val items: List<T>,
        @ProtoNumber(2) val nextCursor: String = "",
    )

    @Serializable
    data class Product(
        @ProtoNumber(1) val sku: String,
        @ProtoNumber(2) val priceCents: Int,
    )

    @Serializable
    data class StockLevel(
        @ProtoNumber(1) val sku: String,
        @ProtoNumber(2) val warehouse: String,
        @ProtoNumber(3) val onHand: Int,
    )

    private val protoBuf: ProtoBuf = ProtoBuf

    @Test
    fun roundTripsEnvelopesForDifferentPayloadTypes() {
        val productReply = Envelope(requestId = 41L, payload = Product("LAMP-01", 4_500), traceTag = "edge-a")
        val stockReply = Envelope(requestId = 42L, payload = StockLevel("LAMP-01", "BER", 17))

        val productBytes: ByteArray = protoBuf.encodeToByteArray(productReply)
        val stockBytes: ByteArray = protoBuf.encodeToByteArray(stockReply)

        assertEquals(productReply, protoBuf.decodeFromByteArray<Envelope<Product>>(productBytes))
        val stock: Envelope<StockLevel> = protoBuf.decodeFromByteArray(stockBytes)
        assertEquals(stockReply, stock)
        assertEquals(17, stock.payload.onHand)
    }

    @Test
    fun nestsGenericPagesInsideGenericEnvelopes() {
        val listing = Envelope(
            requestId = 7L,
            payload = Page(
                items = listOf(Product("DESK-10", 21_900), Product("CHAIR-3", 8_900), Product("RUG-22", 12_000)),
                nextCursor = "after:RUG-22",
            ),
        )
        val replySerializer: KSerializer<Envelope<Page<Product>>> =
            Envelope.serializer(Page.serializer(Product.serializer()))

        val bytes: ByteArray = protoBuf.encodeToByteArray(replySerializer, listing)
        val restored: Envelope<Page<Product>> = protoBuf.decodeFromByteArray(replySerializer, bytes)

        assertEquals(listing, restored)
        assertEquals(42_800, restored.payload.items.sumOf { it.priceCents })
        assertEquals("after:RUG-22", restored.payload.nextCursor)
        assertEquals(replySerializer.descriptor, serializer<Envelope<Page<Product>>>().descriptor)
    }

    @Test
    fun encodesScalarPayloadsAsPlainProtobufFields() {
        val ack = Envelope(requestId = 1L, payload = 150)

        val bytes: ByteArray = protoBuf.encodeToByteArray(ack)

        // Field 1 varint 1, then field 2 varint 150: the generic payload is an ordinary int field.
        assertArrayEquals(byteArrayOf(0x08, 0x01, 0x10, 0x96.toByte(), 0x01), bytes)
        assertEquals(ack, protoBuf.decodeFromByteArray<Envelope<Int>>(bytes))
    }

    @Test
    fun repeatsListPayloadsAndShipsThemAsHexText() {
        val batch = Envelope(
            requestId = 99L,
            payload = listOf(StockLevel("PEN-5", "MUC", 300), StockLevel("PEN-5", "HAM", 120)),
            traceTag = "batch",
        )
        val batchSerializer: KSerializer<Envelope<List<StockLevel>>> =
            Envelope.serializer(ListSerializer(StockLevel.serializer()))

        val hex: String = protoBuf.encodeToHexString(batchSerializer, batch)
        val restored: Envelope<List<StockLevel>> = protoBuf.decodeFromHexString(hex)

        assertEquals(batch, restored)
        assertEquals("0863", hex.take(4))
        assertEquals(420, restored.payload.sumOf { it.onHand })
        assertEquals(listOf("MUC", "HAM"), restored.payload.map { it.warehouse })
    }
}
