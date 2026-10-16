/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_protobuf_jvm

import kotlinx.datetime.LocalDate
import kotlinx.datetime.serializers.LocalDateComponentSerializer
import kotlinx.datetime.serializers.LocalDateIso8601Serializer
import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Custom `KSerializer` and `@Contextual` serializers resolved through the `SerializersModule` of a `ProtoBuf` instance.
 *
 * A hotel booking service stores reservations as protobuf messages. Stay dates are third-party
 * `kotlinx-datetime` values marked `@Contextual`, so each deployment picks their wire form in the
 * module of its `ProtoBuf` instance: a nested year/month/day message or an ISO-8601 string. Prices
 * are a domain type written as integer cents by a hand-written serializer, and room numbers are a
 * value class that travels as its underlying integer.
 */
@OptIn(ExperimentalSerializationApi::class)
class HotelBookingCustomSerializersTest {

    /** A price in a currency's minor unit; the domain type itself is not `@Serializable`. */
    class Money(val cents: Long) {
        operator fun plus(other: Money): Money = Money(cents + other.cents)

        operator fun times(nights: Int): Money = Money(cents * nights)

        override fun equals(other: Any?): Boolean = other is Money && other.cents == cents

        override fun hashCode(): Int = cents.hashCode()

        override fun toString(): String = "Money($cents)"
    }

    /** Writes [Money] as a single protobuf varint holding the cents. */
    object MoneyAsCentsSerializer : KSerializer<Money> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("hotel.Money", PrimitiveKind.LONG)

        override fun serialize(encoder: Encoder, value: Money) {
            encoder.encodeLong(value.cents)
        }

        override fun deserialize(decoder: Decoder): Money = Money(decoder.decodeLong())
    }

    @JvmInline
    @Serializable
    value class RoomNumber(val value: Int)

    @Serializable
    data class Reservation(
        @ProtoNumber(1) val guest: String,
        @ProtoNumber(2) val room: RoomNumber,
        @ProtoNumber(3) @Contextual val checkIn: LocalDate,
        @ProtoNumber(4) @Contextual val checkOut: LocalDate,
        @ProtoNumber(5) @Serializable(with = MoneyAsCentsSerializer::class) val nightlyRate: Money,
        @ProtoNumber(6) val extras: List<@Serializable(with = MoneyAsCentsSerializer::class) Money> =
            emptyList(),
    ) {
        fun nights(): Int = checkOut.toEpochDays().toInt() - checkIn.toEpochDays().toInt()

        fun total(): Money = extras.fold(nightlyRate * nights()) { sum, extra -> sum + extra }
    }

    /** The smallest message that carries one contextual date as field 1. */
    @Serializable
    data class StayDate(@ProtoNumber(1) @Contextual val date: LocalDate)

    private val componentDates: ProtoBuf = ProtoBuf {
        serializersModule = SerializersModule {
            contextual(LocalDateComponentSerializer)
        }
    }

    private val isoDates: ProtoBuf = ProtoBuf {
        serializersModule = SerializersModule {
            contextual(LocalDate::class, LocalDateIso8601Serializer)
        }
    }

    private val reservation: Reservation = Reservation(
        guest = "Grace Hopper",
        room = RoomNumber(412),
        checkIn = LocalDate(2026, 10, 16),
        checkOut = LocalDate(2026, 10, 19),
        nightlyRate = Money(12_900),
        extras = listOf(Money(1_500), Money(2_250)),
    )

    @Test
    fun storesAndRestoresReservationWithCustomSerializers() {
        val stored: ByteArray = componentDates.encodeToByteArray(reservation)
        val restored: Reservation = componentDates.decodeFromByteArray(stored)

        assertEquals(reservation, restored)
        assertEquals(3, restored.nights())
        assertEquals(Money(42_450), restored.total())
        assertEquals(RoomNumber(412), restored.room)
    }

    @Test
    fun writesMoneyAndRoomNumberAsPlainVarints() {
        val stored: ByteArray = componentDates.encodeToByteArray(reservation)

        // Field 2 (varint) holds 412 = 0x9C 0x03; the value class adds no nested message.
        val room: ByteArray = byteArrayOf(0x10, 0x9C.toByte(), 0x03)
        // Field 5 (varint) holds 12900 = 0xE4 0x64, written by the hand-written serializer.
        val rate: ByteArray = byteArrayOf(0x28, 0xE4.toByte(), 0x64)
        assertTrue(stored.containsSlice(room), "room number field")
        assertTrue(stored.containsSlice(rate), "nightly rate field")
    }

    @Test
    fun encodesContextualDateAsNestedComponentMessage() {
        val bytes: ByteArray = componentDates.encodeToByteArray(StayDate(LocalDate(2026, 10, 16)))

        // Field 1, length 7: year 2026 (field 1), month 10 (field 2), day 16 (field 3).
        assertArrayEquals(
            byteArrayOf(0x0A, 0x07, 0x08, 0xEA.toByte(), 0x0F, 0x10, 0x0A, 0x18, 0x10),
            bytes,
        )
        assertEquals(LocalDate(2026, 10, 16), componentDates.decodeFromByteArray<StayDate>(bytes).date)
    }

    @Test
    fun encodesContextualDateAsIsoStringInAnotherDeployment() {
        val bytes: ByteArray = isoDates.encodeToByteArray(StayDate(LocalDate(2026, 10, 16)))

        assertArrayEquals(byteArrayOf(0x0A, 0x0A) + "2026-10-16".encodeToByteArray(), bytes)
        assertEquals(LocalDate(2026, 10, 16), isoDates.decodeFromByteArray<StayDate>(bytes).date)

        val restored: Reservation = isoDates.decodeFromByteArray(isoDates.encodeToByteArray(reservation))
        assertEquals(reservation, restored)
    }

    @Test
    fun fallsBackToTheDateDefaultSerializerWithoutRegisteredContext() {
        val stay = StayDate(LocalDate(2026, 10, 16))

        // With no contextual entry, the date's own default serializer (ISO-8601 text) is used.
        val bytes: ByteArray = ProtoBuf.encodeToByteArray(stay)

        assertArrayEquals(isoDates.encodeToByteArray(stay), bytes)
        assertEquals(stay, ProtoBuf.decodeFromByteArray<StayDate>(bytes))
    }

    private fun ByteArray.containsSlice(slice: ByteArray): Boolean =
        (0..size - slice.size).any { start ->
            slice.indices.all { offset -> this[start + offset] == slice[offset] }
        }
}
