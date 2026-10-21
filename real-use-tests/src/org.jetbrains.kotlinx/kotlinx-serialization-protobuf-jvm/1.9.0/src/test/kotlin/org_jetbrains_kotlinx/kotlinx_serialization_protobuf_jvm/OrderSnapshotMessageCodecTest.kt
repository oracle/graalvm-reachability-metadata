/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_protobuf_jvm

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Message encoding and decoding of annotated `@Serializable` classes with the default `ProtoBuf` format.
 *
 * An order service persists order snapshots as protobuf messages: each snapshot carries a nested
 * customer message, repeated line items, a map of applied coupons, an enum status and an optional
 * note. The service writes a snapshot to bytes, reads it back, and checks that the bytes on the
 * wire follow the protobuf field numbers it declared with `@ProtoNumber`.
 */
@OptIn(ExperimentalSerializationApi::class)
class OrderSnapshotMessageCodecTest {

    @Serializable
    enum class OrderStatus {
        @ProtoNumber(0)
        PENDING,

        @ProtoNumber(1)
        PAID,

        @ProtoNumber(5)
        SHIPPED,
    }

    @Serializable
    data class Customer(
        @ProtoNumber(1) val id: Long,
        @ProtoNumber(2) val email: String,
    )

    @Serializable
    data class LineItem(
        @ProtoNumber(1) val sku: String,
        @ProtoNumber(2) val quantity: Int,
        @ProtoNumber(3) val unitPriceCents: Long,
    )

    @Serializable
    data class OrderSnapshot(
        @ProtoNumber(1) val orderId: Int,
        @ProtoNumber(2) val customer: Customer,
        @ProtoNumber(3) val items: List<LineItem>,
        @ProtoNumber(4) val coupons: Map<String, Int> = emptyMap(),
        @ProtoNumber(5) val status: OrderStatus = OrderStatus.PENDING,
        @ProtoNumber(6) val note: String? = null,
        @ProtoNumber(7) val giftWrapped: Boolean = false,
    ) {
        fun totalCents(): Long =
            items.sumOf { it.unitPriceCents * it.quantity } - coupons.values.sum()
    }

    /** The minimal order that carries only the field the protobuf guide encodes as `08 96 01`. */
    @Serializable
    data class OrderReference(@ProtoNumber(1) val orderId: Int)

    private val protoBuf: ProtoBuf = ProtoBuf

    @Test
    fun storesAndRestoresCompleteOrderSnapshot() {
        val snapshot = OrderSnapshot(
            orderId = 4711,
            customer = Customer(id = 9_000_000_001L, email = "ada@example.com"),
            items = listOf(
                LineItem(sku = "BOOK-042", quantity = 2, unitPriceCents = 1_999),
                LineItem(sku = "MUG-007", quantity = 1, unitPriceCents = 1_250),
            ),
            coupons = mapOf("WELCOME" to 500, "AUTUMN" to 250),
            status = OrderStatus.SHIPPED,
            note = "Leave at the front desk",
            giftWrapped = true,
        )

        val stored: ByteArray = protoBuf.encodeToByteArray(snapshot)
        val restored: OrderSnapshot = protoBuf.decodeFromByteArray(stored)

        assertEquals(snapshot, restored)
        assertEquals(4_498L, restored.totalCents())
        assertEquals(listOf("BOOK-042", "MUG-007"), restored.items.map { it.sku })
        assertEquals(OrderStatus.SHIPPED, restored.status)
    }

    @Test
    fun omitsDefaultsAndRestoresThemOnRead() {
        val fresh = OrderSnapshot(
            orderId = 12,
            customer = Customer(id = 3L, email = "bo@example.com"),
            items = listOf(LineItem(sku = "PEN-001", quantity = 10, unitPriceCents = 99)),
        )
        val withNote = fresh.copy(note = "rush")

        val freshBytes: ByteArray = protoBuf.encodeToByteArray(fresh)
        val withNoteBytes: ByteArray = protoBuf.encodeToByteArray(withNote)
        val restored: OrderSnapshot = protoBuf.decodeFromByteArray(freshBytes)

        assertEquals(fresh, restored)
        assertNull(restored.note)
        assertEquals(OrderStatus.PENDING, restored.status)
        assertTrue(restored.coupons.isEmpty())
        // Field 6, wire type 2, length 4, then the UTF-8 bytes of "rush".
        assertEquals(freshBytes.size + 6, withNoteBytes.size)
    }

    @Test
    fun writesFieldNumbersAsDeclared() {
        val bytes: ByteArray = protoBuf.encodeToByteArray(OrderReference(orderId = 150))

        assertArrayEquals(byteArrayOf(0x08, 0x96.toByte(), 0x01), bytes)
        assertEquals(OrderReference(150), protoBuf.decodeFromByteArray<OrderReference>(bytes))
    }

    @Test
    fun readsEnumByItsProtoNumber() {
        val reference = OrderSnapshot(
            orderId = 1,
            customer = Customer(id = 1L, email = "c@example.com"),
            items = emptyList(),
            status = OrderStatus.SHIPPED,
        )

        val bytes: ByteArray = protoBuf.encodeToByteArray(reference)
        // The status is the last non-default field: tag 0x28 (field 5, varint), then the
        // declared proto number 5 rather than the ordinal 2.
        assertEquals(listOf<Byte>(0x28, 0x05), bytes.takeLast(2))
        assertEquals(OrderStatus.SHIPPED, protoBuf.decodeFromByteArray<OrderSnapshot>(bytes).status)
    }
}
