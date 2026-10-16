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
import kotlinx.serialization.protobuf.ProtoPacked
import kotlinx.serialization.protobuf.ProtoType
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Scalar wire encodings chosen with `@ProtoType` integer types and `@ProtoPacked` repeated fields.
 *
 * A telemetry gateway ships compact sensor readings to a protobuf collector: device ids and
 * timestamps travel as fixed-width integers, temperature deltas as zigzag-encoded signed
 * integers, and the raw sample window as a packed repeated field. The gateway checks the exact
 * bytes it puts on the wire, that the collector reads every reading back, and that the chosen
 * encodings keep negative deltas and long sample windows small.
 */
@OptIn(ExperimentalSerializationApi::class)
class SensorTelemetryWireEncodingTest {

    @Serializable
    data class SensorReading(
        @ProtoNumber(1) @ProtoType(ProtoIntegerType.FIXED) val deviceId: Int,
        @ProtoNumber(2) @ProtoType(ProtoIntegerType.SIGNED) val temperatureDelta: Int,
        @ProtoNumber(3) @ProtoType(ProtoIntegerType.FIXED) val timestampMillis: Long,
        @ProtoNumber(4) @ProtoPacked val samples: List<Int>,
    )

    /** The same contract as [SensorReading] written by an older gateway with default encodings. */
    @Serializable
    data class LegacySensorReading(
        @ProtoNumber(1) @ProtoType(ProtoIntegerType.FIXED) val deviceId: Int,
        @ProtoNumber(2) val temperatureDelta: Int,
        @ProtoNumber(3) @ProtoType(ProtoIntegerType.FIXED) val timestampMillis: Long,
        @ProtoNumber(4) val samples: List<Int>,
    )

    @Serializable
    data class CalibrationProfile(
        @ProtoNumber(1) val name: String,
        @ProtoNumber(2) @ProtoPacked val offsets: List<Double>,
        @ProtoNumber(3) @ProtoPacked val enabledChannels: List<Boolean>,
        @ProtoNumber(4) @ProtoType(ProtoIntegerType.SIGNED) val driftPerHour: Long,
    )

    private val protoBuf: ProtoBuf = ProtoBuf

    @Test
    fun writesFixedSignedAndPackedFieldsOnTheWire() {
        val reading = SensorReading(
            deviceId = 1,
            temperatureDelta = -3,
            timestampMillis = 2L,
            samples = listOf(3, 270, 86_942),
        )

        val bytes: ByteArray = protoBuf.encodeToByteArray(reading)

        val expected: ByteArray = bytes(
            // Field 1, wire type 5 (32-bit): little-endian 1.
            0x0D, 0x01, 0x00, 0x00, 0x00,
            // Field 2, varint: zigzag(-3) = 5.
            0x10, 0x05,
            // Field 3, wire type 1 (64-bit): little-endian 2.
            0x19, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            // Field 4, length-delimited packed varints 3, 270, 86942.
            0x22, 0x06, 0x03, 0x8E, 0x02, 0x9E, 0xA7, 0x05,
        )
        assertArrayEquals(expected, bytes)
        assertEquals(reading, protoBuf.decodeFromByteArray<SensorReading>(bytes))
    }

    @Test
    fun collectorRestoresReadingsWithExtremeValues() {
        val readings: List<SensorReading> = listOf(
            SensorReading(Int.MAX_VALUE, -2_000_000, Long.MAX_VALUE, listOf(0, 1, 127, 128, Int.MAX_VALUE)),
            SensorReading(-7, 0, -1L, emptyList()),
            SensorReading(42, 18, 1_760_572_800_000L, List(64) { it * it + 100 }),
        )

        for (reading in readings) {
            val restored: SensorReading = protoBuf.decodeFromByteArray(protoBuf.encodeToByteArray(reading))
            assertEquals(reading, restored)
        }
    }

    @Test
    fun signedAndPackedEncodingsShrinkTheMessage() {
        val window: List<Int> = List(100) { 5 }
        val compact = SensorReading(9, -1, 1_000L, window)
        val legacy = LegacySensorReading(9, -1, 1_000L, window)

        val compactBytes: ByteArray = protoBuf.encodeToByteArray(compact)
        val legacyBytes: ByteArray = protoBuf.encodeToByteArray(legacy)

        // A default varint spends ten bytes on -1, zigzag spends one.
        // Unpacked samples repeat the one-byte tag per element, packed ones share a two-byte header.
        assertEquals(legacyBytes.size - 9 - 98, compactBytes.size)
        assertTrue(compactBytes.size < 130, "compact reading took ${compactBytes.size} bytes")
    }

    @Test
    fun legacyUnpackedSamplesStillDecodeIntoPackedField() {
        val legacy = LegacySensorReading(5, 7, 123L, listOf(10, 20, 30))
        val legacyBytes: ByteArray = protoBuf.encodeToByteArray(legacy)
        val unpackedSamples: ByteArray = legacyBytes.copyOfRange(legacyBytes.size - 6, legacyBytes.size)

        // The legacy gateway writes each sample under its own field-4 varint tag.
        assertArrayEquals(bytes(0x20, 0x0A, 0x20, 0x14, 0x20, 0x1E), unpackedSamples)

        val upgraded = SensorReading(5, 0, 123L, listOf(10, 20, 30))
        val upgradedBytes: ByteArray = protoBuf.encodeToByteArray(upgraded)
        val withLegacySamples: ByteArray =
            upgradedBytes.copyOfRange(0, upgradedBytes.size - 5) + unpackedSamples
        val restored: SensorReading = protoBuf.decodeFromByteArray(withLegacySamples)

        assertEquals(listOf(10, 20, 30), restored.samples)
        assertEquals(123L, restored.timestampMillis)
    }

    @Test
    fun storesCalibrationProfileWithPackedDoublesAndBooleans() {
        val profile = CalibrationProfile(
            name = "rack-7",
            offsets = listOf(0.5, -1.25, 3.0),
            enabledChannels = listOf(true, false, true, true),
            driftPerHour = -40_000_000_000L,
        )

        val bytes: ByteArray = protoBuf.encodeToByteArray(profile)
        val restored: CalibrationProfile = protoBuf.decodeFromByteArray(bytes)

        assertEquals(profile, restored)
        assertEquals(2.25, restored.offsets.sum())
        assertEquals(3, restored.enabledChannels.count { it })
        // Field 2 packed: tag 0x12, then 3 doubles of 8 bytes each.
        val offsetsHeader: Int = bytes.indexOf(0x12.toByte())
        assertEquals(24, bytes[offsetsHeader + 1].toInt())
        // Field 3 packed: tag 0x1A, then 4 one-byte booleans.
        val channelsStart: Int = offsetsHeader + 2 + 24
        assertArrayEquals(bytes(0x1A, 0x04, 0x01, 0x00, 0x01, 0x01), bytes.copyOfRange(channelsStart, channelsStart + 6))
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }
}
