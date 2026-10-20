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
import kotlinx.serialization.protobuf.ProtoIntegerType
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.protobuf.ProtoType
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Kotlin `@JvmInline` value classes encoded by `ProtoBuf` as the scalar fields they wrap.
 *
 * A shipment tracking service models its identifiers and measurements as value classes so that a
 * tracking code can never be passed where a carrier code is expected. Other services read the same
 * messages with plain `Long`, `String` and `Int` fields, so the service checks that each value class
 * reaches the wire exactly as its underlying scalar, in single, repeated, optional and map fields.
 */
@OptIn(ExperimentalSerializationApi::class)
class ShipmentValueClassFieldsTest {

    @Serializable
    @JvmInline
    value class ShipmentId(val value: Long)

    @Serializable
    @JvmInline
    value class TrackingCode(val value: String) {
        init {
            require(value.length == 10) { "tracking codes have ten characters" }
        }
    }

    @Serializable
    @JvmInline
    value class WeightGrams(val value: Int) {
        operator fun plus(other: WeightGrams): WeightGrams = WeightGrams(value + other.value)
    }

    @Serializable
    @JvmInline
    value class DepotCode(val value: String)

    @Serializable
    data class Parcel(
        @ProtoNumber(1) val code: TrackingCode,
        @ProtoNumber(2) val weight: WeightGrams,
    )

    @Serializable
    data class Shipment(
        @ProtoNumber(1) val id: ShipmentId,
        @ProtoNumber(2) val parcels: List<Parcel>,
        @ProtoNumber(3) val scannedWeights: List<WeightGrams> = emptyList(),
        @ProtoNumber(4) val returnLabel: TrackingCode? = null,
        @ProtoNumber(5) val dwellMinutesByDepot: Map<DepotCode, Int> = emptyMap(),
    ) {
        fun totalWeight(): WeightGrams = parcels.map { it.weight }.fold(WeightGrams(0)) { sum, w -> sum + w }
    }

    /** The view a legacy consumer keeps of the same parcel message, with plain scalar fields. */
    @Serializable
    data class LegacyParcel(
        @ProtoNumber(1) val code: String,
        @ProtoNumber(2) val weight: Int,
    )

    @Serializable
    data class CarrierHandover(
        @ProtoNumber(1) @ProtoType(ProtoIntegerType.FIXED) val shipment: ShipmentId,
        @ProtoNumber(2) @ProtoType(ProtoIntegerType.SIGNED) val weightCorrection: WeightGrams,
    )

    private val protoBuf: ProtoBuf = ProtoBuf

    @Test
    fun storesAndRestoresShipmentWithValueClassFields() {
        val shipment = Shipment(
            id = ShipmentId(9_000_000_123L),
            parcels = listOf(
                Parcel(TrackingCode("TRK0000001"), WeightGrams(1_250)),
                Parcel(TrackingCode("TRK0000002"), WeightGrams(830)),
            ),
            scannedWeights = listOf(WeightGrams(1_240), WeightGrams(835)),
            returnLabel = TrackingCode("RET0000042"),
            dwellMinutesByDepot = mapOf(DepotCode("BER") to 45, DepotCode("MUC") to 120),
        )

        val restored: Shipment = protoBuf.decodeFromByteArray(protoBuf.encodeToByteArray(shipment))

        assertEquals(shipment, restored)
        assertEquals(ShipmentId(9_000_000_123L), restored.id)
        assertEquals(WeightGrams(2_080), restored.totalWeight())
        assertEquals(TrackingCode("RET0000042"), restored.returnLabel)
        assertEquals(120, restored.dwellMinutesByDepot[DepotCode("MUC")])
    }

    @Test
    fun omitsAbsentOptionalValueClassField() {
        val shipment = Shipment(
            id = ShipmentId(7L),
            parcels = listOf(Parcel(TrackingCode("TRK0000003"), WeightGrams(500))),
        )

        val bytes: ByteArray = protoBuf.encodeToByteArray(shipment)
        val restored: Shipment = protoBuf.decodeFromByteArray(bytes)

        assertNull(restored.returnLabel)
        assertEquals(shipment, restored)
        val parcelBytes: ByteArray = protoBuf.encodeToByteArray(shipment.parcels.single())
        val expected: ByteArray = byteArrayOf(0x08, 0x07, 0x12, parcelBytes.size.toByte()) + parcelBytes
        assertArrayEquals(expected, bytes)
    }

    @Test
    fun encodesValueClassesExactlyAsTheirUnderlyingScalars() {
        val parcel = Parcel(TrackingCode("TRK0000004"), WeightGrams(150))

        val bytes: ByteArray = protoBuf.encodeToByteArray(parcel)

        val expected: ByteArray = byteArrayOf(0x0A, 0x0A) + "TRK0000004".encodeToByteArray() +
            byteArrayOf(0x10, 0x96.toByte(), 0x01)
        assertArrayEquals(expected, bytes)
        assertArrayEquals(protoBuf.encodeToByteArray(LegacyParcel("TRK0000004", 150)), bytes)
        assertEquals(LegacyParcel("TRK0000004", 150), protoBuf.decodeFromByteArray<LegacyParcel>(bytes))
        assertEquals(parcel, protoBuf.decodeFromByteArray<Parcel>(protoBuf.encodeToByteArray(LegacyParcel("TRK0000004", 150))))
    }

    @Test
    fun appliesIntegerTypeAnnotationsToValueClassFields() {
        val handover = CarrierHandover(shipment = ShipmentId(1L), weightCorrection = WeightGrams(-3))

        val bytes: ByteArray = protoBuf.encodeToByteArray(handover)

        val expected: ByteArray = byteArrayOf(0x09, 0x01, 0, 0, 0, 0, 0, 0, 0, 0x10, 0x05)
        assertArrayEquals(expected, bytes)
        assertEquals(handover, protoBuf.decodeFromByteArray<CarrierHandover>(bytes))
    }
}
