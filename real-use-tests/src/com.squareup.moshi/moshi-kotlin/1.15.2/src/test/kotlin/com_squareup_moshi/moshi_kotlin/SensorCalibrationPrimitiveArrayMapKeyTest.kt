/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_moshi.moshi_kotlin

import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises primitive, array, and keyed-map property binding in `KotlinJsonAdapterFactory`:
 * every Kotlin primitive type including `Char`, `Byte`, `Short`, and `Float`, object and
 * primitive arrays, maps keyed by `Int`, `Long`, and enums, and the range and finiteness
 * checks that reject out-of-domain values with their JSON path. Bytes travel unsigned on
 * the wire, as the firmware images do.
 *
 * A fleet of industrial sensors downloads its calibration profile as JSON from a device
 * management service and uploads the profile it applied back for auditing.
 */
@OptIn(ExperimentalStdlibApi::class)
public class SensorCalibrationPrimitiveArrayMapKeyTest {

    /** Operating mode a sensor switches between during a shift. */
    public enum class Mode {
        @Json(name = "idle")
        IDLE,

        @Json(name = "burst")
        BURST,
    }

    /** Calibration profile for one sensor; arrays keep the wire layout of the firmware. */
    public class CalibrationProfile(
        public val serial: Long,
        public val grade: Char,
        public val channel: Byte,
        @Json(name = "sample_rate_hz") public val sampleRateHz: Short,
        public val gain: Float,
        public val offset: Double,
        public val enabled: Boolean,
        public val coefficients: DoubleArray,
        @Json(name = "pin_map") public val pinMap: IntArray,
        public val labels: Array<String>,
        public val firmware: ByteArray,
        @Json(name = "thresholds_by_channel") public val thresholdsByChannel: Map<Int, Float>,
        @Json(name = "pins_by_mode") public val pinsByMode: Map<Mode, List<Int>>,
        @Json(name = "notes_by_epoch") public val notesByEpoch: Map<Long, String> = emptyMap(),
    )

    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private val adapter: JsonAdapter<CalibrationProfile> = moshi.adapter<CalibrationProfile>()

    private val downloaded: String = """
        {
          "serial": 9007199254740993,
          "grade": "B",
          "channel": -7,
          "sample_rate_hz": 16000,
          "gain": 0.1,
          "offset": -2.75,
          "enabled": true,
          "coefficients": [1.0, 0.5, 0.125],
          "pin_map": [4, 17, 27],
          "labels": ["intake", "exhaust"],
          "firmware": [1, 2, 127, -128],
          "thresholds_by_channel": {"1": 0.25, "3": 12.5},
          "pins_by_mode": {"idle": [4], "burst": [17, 27]},
          "notes_by_epoch": {"1700000000000": "recalibrated"}
        }
    """.trimIndent()

    @Test
    fun downloadedProfileDecodesEveryPrimitiveArrayAndKeyedMap() {
        val profile: CalibrationProfile = adapter.fromJson(downloaded)!!

        assertEquals(9007199254740993L, profile.serial)
        assertEquals('B', profile.grade)
        assertEquals((-7).toByte(), profile.channel)
        assertEquals(16000.toShort(), profile.sampleRateHz)
        assertEquals(0.1f, profile.gain)
        assertEquals(-2.75, profile.offset)
        assertTrue(profile.enabled)
        assertArrayEquals(doubleArrayOf(1.0, 0.5, 0.125), profile.coefficients)
        assertArrayEquals(intArrayOf(4, 17, 27), profile.pinMap)
        assertArrayEquals(arrayOf("intake", "exhaust"), profile.labels)
        assertArrayEquals(byteArrayOf(1, 2, 127, -128), profile.firmware)
        assertEquals(mapOf(1 to 0.25f, 3 to 12.5f), profile.thresholdsByChannel)
        assertEquals(
            listOf(1, 3),
            profile.thresholdsByChannel.keys.toList(),
        )
        assertEquals(
            mapOf(Mode.IDLE to listOf(4), Mode.BURST to listOf(17, 27)),
            profile.pinsByMode,
        )
        assertEquals(mapOf(1700000000000L to "recalibrated"), profile.notesByEpoch)
    }

    @Test
    fun appliedProfileUploadsWithStringifiedMapKeysAndShortestFloatForm() {
        val applied = CalibrationProfile(
            serial = 42L,
            grade = 'A',
            channel = 3,
            sampleRateHz = 8000,
            gain = 0.1f,
            offset = 0.0,
            enabled = false,
            coefficients = doubleArrayOf(2.5),
            pinMap = intArrayOf(),
            labels = arrayOf("intake"),
            firmware = byteArrayOf(-1),
            thresholdsByChannel = mapOf(2 to 1.5f),
            pinsByMode = mapOf(Mode.BURST to listOf(5, 6)),
        )

        assertEquals(
            """{"serial":42,"grade":"A","channel":3,"sample_rate_hz":8000,"gain":0.1,""" +
                """"offset":0.0,"enabled":false,"coefficients":[2.5],"pin_map":[],""" +
                """"labels":["intake"],"firmware":[255],"thresholds_by_channel":{"2":1.5},""" +
                """"pins_by_mode":{"burst":[5,6]},"notes_by_epoch":{}}""",
            adapter.toJson(applied),
        )
    }

    @Test
    fun bytesUploadUnsignedAndDecodeBackToTheSameSignedValue() {
        val firmwareAdapter: JsonAdapter<ByteArray> = moshi.adapter<ByteArray>()

        assertEquals("[0,127,128,255]", firmwareAdapter.toJson(byteArrayOf(0, 127, -128, -1)))
        assertArrayEquals(
            byteArrayOf(0, 127, -128, -1),
            firmwareAdapter.fromJson("[0,127,128,255]"),
        )
        assertThrows(JsonDataException::class.java) { firmwareAdapter.fromJson("[256]") }
    }

    @Test
    fun roundTripPreservesArrayContentsAndMapKeyTypes() {
        val original: CalibrationProfile = adapter.fromJson(downloaded)!!
        val reloaded: CalibrationProfile = adapter.fromJson(adapter.toJson(original))!!

        assertEquals(original.serial, reloaded.serial)
        assertEquals(original.gain, reloaded.gain)
        assertArrayEquals(original.coefficients, reloaded.coefficients)
        assertArrayEquals(original.firmware, reloaded.firmware)
        assertArrayEquals(original.labels, reloaded.labels)
        assertEquals(original.thresholdsByChannel, reloaded.thresholdsByChannel)
        assertEquals(original.pinsByMode, reloaded.pinsByMode)
        assertEquals(original.notesByEpoch, reloaded.notesByEpoch)
    }

    @Test
    fun outOfRangeNumbersAreRejectedWithTheirJsonPath() {
        val tooFastRate: JsonDataException = assertThrows(JsonDataException::class.java) {
            adapter.fromJson(downloaded.replace("16000", "70000"))
        }
        assertTrue(tooFastRate.message!!.contains("\$.sample_rate_hz"), tooFastRate.message)

        val wideGrade: JsonDataException = assertThrows(JsonDataException::class.java) {
            adapter.fromJson(downloaded.replace("\"B\"", "\"B+\""))
        }
        assertTrue(wideGrade.message!!.contains("\$.grade"), wideGrade.message)

        val fractionalPin: JsonDataException = assertThrows(JsonDataException::class.java) {
            adapter.fromJson(downloaded.replace("[4, 17, 27]", "[4, 17.5, 27]"))
        }
        assertTrue(fractionalPin.message!!.contains("\$.pin_map[1]"), fractionalPin.message)
    }

    @Test
    fun mapKeysThatDoNotParseAsTheKeyTypeAreRejected() {
        val badChannel: JsonDataException = assertThrows(JsonDataException::class.java) {
            adapter.fromJson(downloaded.replace("\"3\": 12.5", "\"left\": 12.5"))
        }
        assertTrue(
            badChannel.message!!.contains("\$.thresholds_by_channel"),
            badChannel.message,
        )

        val unknownMode: JsonDataException = assertThrows(JsonDataException::class.java) {
            adapter.fromJson(downloaded.replace("\"burst\": [17, 27]", "\"turbo\": [17, 27]"))
        }
        assertTrue(unknownMode.message!!.contains("turbo"), unknownMode.message)
        assertTrue(unknownMode.message!!.contains("\$.pins_by_mode"), unknownMode.message)
    }

    @Test
    fun nonFiniteGainIsRefusedOnUploadUnlessTheWriterIsLenient() {
        val diverged = CalibrationProfile(
            serial = 7L,
            grade = 'C',
            channel = 0,
            sampleRateHz = 1,
            gain = Float.POSITIVE_INFINITY,
            offset = 0.0,
            enabled = true,
            coefficients = doubleArrayOf(),
            pinMap = intArrayOf(),
            labels = arrayOf(),
            firmware = byteArrayOf(),
            thresholdsByChannel = emptyMap(),
            pinsByMode = emptyMap(),
        )

        assertThrows(IllegalArgumentException::class.java) { adapter.toJson(diverged) }

        val lenientJson: String = adapter.lenient().toJson(diverged)
        assertTrue(lenientJson.contains("\"gain\":Infinity"), lenientJson)

        val infiniteDownload: String = downloaded.replace("\"gain\": 0.1", "\"gain\": Infinity")
        assertThrows(JsonEncodingException::class.java) { adapter.fromJson(infiniteDownload) }
        assertEquals(Float.POSITIVE_INFINITY, adapter.lenient().fromJson(infiniteDownload)!!.gain)
    }
}
