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
import kotlinx.serialization.protobuf.ProtoOneOf
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Protobuf `oneof` fields modeled as a sealed hierarchy with `@ProtoOneOf`.
 *
 * A payment gateway records each payment event with exactly one payment method: a card, a bank
 * transfer or a voucher. Its protobuf contract declares the method as a `oneof` group, so the
 * gateway models it as a sealed interface whose cases each carry one field number. The gateway
 * writes events, reads them back, reads events another service produced, and checks that each
 * case lands on the wire as a plain field of the enclosing message.
 */
@OptIn(ExperimentalSerializationApi::class)
class PaymentMethodOneOfTest {

    @Serializable
    sealed interface PaymentMethod

    @Serializable
    data class Card(@ProtoNumber(3) val details: CardDetails) : PaymentMethod

    @Serializable
    data class CardDetails(
        @ProtoNumber(1) val last4: String,
        @ProtoNumber(2) val expiryMonth: Int,
    )

    @Serializable
    @JvmInline
    value class BankTransfer(@ProtoNumber(4) val iban: String) : PaymentMethod

    @Serializable
    @JvmInline
    value class Voucher(@ProtoNumber(5) val code: Int) : PaymentMethod

    @Serializable
    data class PaymentEvent(
        @ProtoNumber(1) val eventId: Int,
        @ProtoNumber(2) val amountCents: Long,
        @ProtoOneOf val method: PaymentMethod? = null,
        @ProtoNumber(6) val reference: String = "",
    )

    private val protoBuf: ProtoBuf = ProtoBuf

    /** Describes a recorded method the way the gateway's statement printer does. */
    private fun describe(event: PaymentEvent): String =
        when (val method: PaymentMethod? = event.method) {
            is Card -> "card ending ${method.details.last4}"
            is BankTransfer -> "transfer from ${method.iban}"
            is Voucher -> "voucher #${method.code}"
            null -> "unpaid"
        }

    @Test
    fun storesAndRestoresEveryPaymentMethod() {
        val events: List<PaymentEvent> = listOf(
            PaymentEvent(1, 4_200L, Card(CardDetails(last4 = "4242", expiryMonth = 11)), "A-1"),
            PaymentEvent(2, 15_000L, BankTransfer("DE89370400440532013000"), "A-2"),
            PaymentEvent(3, 999L, Voucher(31_337), "A-3"),
        )

        val restored: List<PaymentEvent> = events.map { event ->
            protoBuf.decodeFromByteArray<PaymentEvent>(protoBuf.encodeToByteArray(event))
        }

        assertEquals(events, restored)
        assertEquals(
            listOf(
                "card ending 4242",
                "transfer from DE89370400440532013000",
                "voucher #31337",
            ),
            restored.map { describe(it) },
        )
    }

    @Test
    fun writesChosenCaseAsPlainFieldOfTheMessage() {
        val bytes: ByteArray = protoBuf.encodeToByteArray(PaymentEvent(1, 2L, Voucher(150)))

        // Field 1 = 1, field 2 = 2, then the voucher case as field 5 (varint 150).
        assertArrayEquals(
            byteArrayOf(0x08, 0x01, 0x10, 0x02, 0x28, 0x96.toByte(), 0x01),
            bytes,
        )
    }

    @Test
    fun readsMethodSetByAnotherService() {
        // Produced elsewhere: event 7, amount 500, field 4 = "NL91", field 6 = "X".
        val bytes = byteArrayOf(
            0x08, 0x07,
            0x10, 0xF4.toByte(), 0x03,
            0x22, 0x04, 'N'.code.toByte(), 'L'.code.toByte(), '9'.code.toByte(), '1'.code.toByte(),
            0x32, 0x01, 'X'.code.toByte(),
        )

        val event: PaymentEvent = protoBuf.decodeFromByteArray(bytes)

        assertEquals(PaymentEvent(7, 500L, BankTransfer("NL91"), "X"), event)
        assertEquals("transfer from NL91", describe(event))
    }

    @Test
    fun leavesMethodUnsetWhenNoCaseIsPresent() {
        val pending = PaymentEvent(eventId = 9, amountCents = 1_000L)

        val bytes: ByteArray = protoBuf.encodeToByteArray(pending)
        val restored: PaymentEvent = protoBuf.decodeFromByteArray(bytes)

        assertArrayEquals(byteArrayOf(0x08, 0x09, 0x10, 0xE8.toByte(), 0x07), bytes)
        assertNull(restored.method)
        assertEquals("unpaid", describe(restored))
    }
}
