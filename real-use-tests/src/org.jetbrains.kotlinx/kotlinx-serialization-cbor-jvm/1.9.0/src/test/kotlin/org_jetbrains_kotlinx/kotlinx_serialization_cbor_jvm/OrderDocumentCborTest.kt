/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_cbor_jvm

import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Exercises the core `Cbor` binary format encoding and decoding an application's
 * `@Serializable` order documents.
 *
 * An order service stores each order as a CBOR document and reads it back; the
 * scenario checks the round trip of a nested model and the exact bytes of a small
 * message so the wire format other CBOR clients read is pinned down.
 */
public class OrderDocumentCborTest {

    @Serializable
    public enum class OrderStatus { PLACED, SHIPPED, DELIVERED }

    @Serializable
    public data class Address(val street: String, val city: String, val postalCode: String)

    @Serializable
    public data class LineItem(val sku: String, val quantity: Int, val unitPriceCents: Long)

    @Serializable
    public data class Order(
        val id: Long,
        val customer: String,
        val status: OrderStatus,
        val shippingAddress: Address,
        val items: List<LineItem>,
        val tags: Set<String> = emptySet(),
        val attributes: Map<String, String> = emptyMap(),
        val giftMessage: String? = null,
    ) {
        public fun totalCents(): Long = items.sumOf { item: LineItem -> item.quantity * item.unitPriceCents }
    }

    @Serializable
    public data class Point(val x: Int, val y: Int)

    @Test
    fun storesAndReloadsAnOrderDocument(): Unit {
        val order: Order = Order(
            id = 4_200_000_001L,
            customer = "Ada Lovelace",
            status = OrderStatus.SHIPPED,
            shippingAddress = Address("12 Analytical Way", "London", "NW1 6XE"),
            items = listOf(
                LineItem("BOOK-001", 2, 1_999L),
                LineItem("PEN-042", 10, 150L),
            ),
            tags = setOf("priority", "gift"),
            attributes = mapOf("channel" to "web", "coupon" to "AUTUMN10"),
            giftMessage = "Happy birthday!",
        )

        val document: ByteArray = Cbor.encodeToByteArray(order)
        val reloaded: Order = Cbor.decodeFromByteArray<Order>(document)

        assertEquals(order, reloaded)
        assertEquals(5_498L, reloaded.totalCents())
        assertEquals(OrderStatus.SHIPPED, reloaded.status)
        assertEquals("London", reloaded.shippingAddress.city)
        assertEquals(listOf("priority", "gift"), reloaded.tags.toList())
    }

    @Test
    fun omitsDefaultsAndRestoresThemOnDecode(): Unit {
        val minimal: Order = Order(
            id = 7L,
            customer = "Grace Hopper",
            status = OrderStatus.PLACED,
            shippingAddress = Address("1 Harbor Rd", "Arlington", "22201"),
            items = listOf(LineItem("CHIP-7", 1, 25_000L)),
        )
        val withDefaultsEncoded: Cbor = Cbor { encodeDefaults = true }

        val compact: ByteArray = Cbor.encodeToByteArray(minimal)
        val verbose: ByteArray = withDefaultsEncoded.encodeToByteArray(minimal)
        val reloaded: Order = Cbor.decodeFromByteArray<Order>(compact)

        assertEquals(minimal, reloaded)
        assertNull(reloaded.giftMessage)
        assertEquals(emptyMap<String, String>(), reloaded.attributes)
        assertEquals(minimal, Cbor.decodeFromByteArray<Order>(verbose))
        assertEquals(true, verbose.size > compact.size)
    }

    @Test
    fun writesIndefiniteAndDefiniteLengthMapLayouts(): Unit {
        val fields: ByteArray = byteArrayOf(
            0x61, 'x'.code.toByte(), 0x01,
            0x61, 'y'.code.toByte(), 0x38, 0x63,
        )
        val indefinite: ByteArray = byteArrayOf(0xBF.toByte()) + fields + byteArrayOf(0xFF.toByte())
        val definite: ByteArray = byteArrayOf(0xA2.toByte()) + fields
        val interoperable: Cbor = Cbor { useDefiniteLengthEncoding = true }

        assertArrayEquals(indefinite, Cbor.encodeToByteArray(Point(1, -100)))
        assertArrayEquals(definite, interoperable.encodeToByteArray(Point(1, -100)))
        assertEquals(Point(1, -100), Cbor.decodeFromByteArray<Point>(definite))
        assertEquals(Point(1, -100), interoperable.decodeFromByteArray<Point>(indefinite))
    }
}
