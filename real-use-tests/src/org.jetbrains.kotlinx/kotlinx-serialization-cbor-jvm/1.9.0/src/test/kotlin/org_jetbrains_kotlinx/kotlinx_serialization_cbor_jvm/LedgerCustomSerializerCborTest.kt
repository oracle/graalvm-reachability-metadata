/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_cbor_jvm

import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.UUID
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.cbor.CborDecoder
import kotlinx.serialization.cbor.CborEncoder
import kotlinx.serialization.cbor.ValueTags
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises format-aware custom and contextual serializers that talk to the CBOR
 * format through `CborEncoder` and `CborDecoder`.
 *
 * A ledger service writes payments as CBOR with RFC 8949 encodings: identifiers as
 * 16-byte byte strings, amounts as tagged decimal fractions and booking dates as
 * tagged epoch days. The same model is also rendered as JSON for an audit log, so
 * the identifier serializer switches representation by the format it is handed.
 */
@OptIn(ExperimentalSerializationApi::class)
public class LedgerCustomSerializerCborTest {

    /** Writes a UUID as a 16-byte CBOR byte string and as its text form elsewhere. */
    public object UuidSerializer : KSerializer<UUID> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ledger.Uuid", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: UUID): Unit {
            if (encoder is CborEncoder) {
                val bytes: ByteBuffer = ByteBuffer.allocate(16)
                bytes.putLong(value.mostSignificantBits).putLong(value.leastSignificantBits)
                encoder.encodeSerializableValue(ByteArraySerializer(), bytes.array())
            } else {
                encoder.encodeString(value.toString())
            }
        }

        override fun deserialize(decoder: Decoder): UUID {
            if (decoder is CborDecoder) {
                val bytes: ByteArray = decoder.decodeSerializableValue(ByteArraySerializer())
                require(bytes.size == 16) { "A ledger identifier holds 16 bytes, got ${bytes.size}" }
                val buffer: ByteBuffer = ByteBuffer.wrap(bytes)
                return UUID(buffer.long, buffer.long)
            }
            return UUID.fromString(decoder.decodeString())
        }
    }

    /** Writes an amount as the RFC 8949 decimal fraction `[exponent, mantissa]`. */
    public object DecimalFractionSerializer : KSerializer<BigDecimal> {
        private val parts: KSerializer<List<Long>> = ListSerializer(Long.serializer())

        override val descriptor: SerialDescriptor = parts.descriptor

        override fun serialize(encoder: Encoder, value: BigDecimal): Unit {
            val exponent: Long = -value.scale().toLong()
            encoder.encodeSerializableValue(parts, listOf(exponent, value.unscaledValue().longValueExact()))
        }

        override fun deserialize(decoder: Decoder): BigDecimal {
            val decoded: List<Long> = decoder.decodeSerializableValue(parts)
            require(decoded.size == 2) { "A decimal fraction has two parts, got ${decoded.size}" }
            return BigDecimal(BigInteger.valueOf(decoded[1]), (-decoded[0]).toInt())
        }
    }

    /** Writes a calendar date as days since 1970-01-01, the RFC 8943 tag 100 form. */
    public object EpochDaySerializer : KSerializer<LocalDate> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ledger.EpochDay", PrimitiveKind.LONG)

        override fun serialize(encoder: Encoder, value: LocalDate): Unit = encoder.encodeLong(value.toEpochDays())

        override fun deserialize(decoder: Decoder): LocalDate = LocalDate.fromEpochDays(decoder.decodeLong())
    }

    @Serializable
    public data class Payment(
        @Serializable(with = UuidSerializer::class) val id: UUID,
        @ValueTags(DECIMAL_FRACTION_TAG) @Serializable(with = DecimalFractionSerializer::class)
        val amount: BigDecimal,
        @ValueTags(EPOCH_DAY_TAG) @Contextual val bookedOn: LocalDate,
    )

    @Serializable
    public data class Statement(val account: String, val payments: List<Payment>) {
        public fun balance(): BigDecimal = payments.fold(BigDecimal.ZERO) { sum: BigDecimal, payment: Payment ->
            sum.add(payment.amount)
        }
    }

    private val ledgerModule: SerializersModule = SerializersModule {
        contextual(EpochDaySerializer)
    }

    private val ledgerCbor: Cbor = Cbor {
        serializersModule = ledgerModule
        alwaysUseByteString = true
        useDefiniteLengthEncoding = true
        encodeValueTags = true
        verifyValueTags = true
    }

    private val auditJson: Json = Json { serializersModule = ledgerModule }

    private val paymentId: UUID = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff")

    @Test
    fun writesPaymentWithStandardCborEncodings(): Unit {
        val payment: Payment = Payment(paymentId, BigDecimal("123.45"), LocalDate(2026, 10, 16))

        val expected: ByteArray = bytes(
            0xA3,
            0x62, 'i'.code, 'd'.code,
            0x50, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77,
            0x88, 0x99, 0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF,
            0x66, 'a'.code, 'm'.code, 'o'.code, 'u'.code, 'n'.code, 't'.code,
            0xC4, 0x82, 0x21, 0x19, 0x30, 0x39,
            0x68, 'b'.code, 'o'.code, 'o'.code, 'k'.code, 'e'.code, 'd'.code, 'O'.code, 'n'.code,
            0xD8, 0x64, 0x19, 0x51, 0x06,
        )

        assertArrayEquals(expected, ledgerCbor.encodeToByteArray(payment))
        assertEquals(payment, ledgerCbor.decodeFromByteArray<Payment>(expected))
    }

    @Test
    fun storesAndReloadsAStatement(): Unit {
        val statement: Statement = Statement(
            account = "DE89 3704 0044 0532 0130 00",
            payments = listOf(
                Payment(paymentId, BigDecimal("1250.00"), LocalDate(2026, 10, 1)),
                Payment(UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479"), BigDecimal("-49.99"), LocalDate(2026, 10, 3)),
                Payment(UUID.fromString("9b2f4e1c-0d3a-4c5b-8e7f-123456789abc"), BigDecimal("-0.015"), LocalDate(2026, 10, 15)),
            ),
        )

        val reloaded: Statement = ledgerCbor.decodeFromByteArray<Statement>(ledgerCbor.encodeToByteArray(statement))

        assertEquals(statement, reloaded)
        assertEquals(BigDecimal("1199.995"), reloaded.balance())
        assertEquals(3, reloaded.payments[2].amount.scale())
        assertEquals(LocalDate(2026, 10, 3), reloaded.payments[1].bookedOn)
    }

    @Test
    fun rendersTheSameModelAsJsonForTheAuditLog(): Unit {
        val payment: Payment = Payment(paymentId, BigDecimal("7.5"), LocalDate(1970, 1, 2))

        val audit: String = auditJson.encodeToString(Payment.serializer(), payment)

        assertEquals("""{"id":"00112233-4455-6677-8899-aabbccddeeff","amount":[-1,75],"bookedOn":1}""", audit)
        assertEquals(payment, auditJson.decodeFromString(Payment.serializer(), audit))
    }

    @Test
    fun rejectsADocumentWhoseAmountCarriesTheWrongTag(): Unit {
        val payment: Payment = Payment(paymentId, BigDecimal("1.00"), LocalDate(2026, 1, 1))
        val document: ByteArray = ledgerCbor.encodeToByteArray(payment)
        val decimalFractionTag: Int = document.indexOf(0xC4.toByte())
        assertTrue(decimalFractionTag > 0)
        val bigFloatTagged: ByteArray = document.copyOf()
        bigFloatTagged[decimalFractionTag] = 0xC5.toByte()

        assertThrows(SerializationException::class.java) {
            ledgerCbor.decodeFromByteArray<Payment>(bigFloatTagged)
        }
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { index: Int -> values[index].toByte() }

    public companion object {
        public const val DECIMAL_FRACTION_TAG: ULong = 4uL
        public const val EPOCH_DAY_TAG: ULong = 100uL
    }
}
