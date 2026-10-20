/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_cbor_jvm

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.cbor.KeyTags
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises property-level field policies on the `Cbor` wire: `@EncodeDefault` modes,
 * `@Required` defaults, null encoding, and `@KeyTags` with `encodeKeyTags`/`verifyKeyTags`.
 *
 * A device-management console pushes settings documents to field devices. The console
 * keeps pushes small by omitting defaults, but the schema version must always travel,
 * the region must always be stated explicitly, and the device-id key carries a private
 * CBOR tag that firmware checks before applying a push.
 */
@OptIn(ExperimentalSerializationApi::class)
public class DeviceSettingsFieldPolicyCborTest {

    /** The settings document the console pushes and the firmware applies. */
    @Serializable
    public data class DeviceSettings(
        @SerialName("dev") @KeyTags(1337uL) val deviceId: String,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) val schema: Int = 3,
        @Required val region: String = "eu",
        @EncodeDefault(EncodeDefault.Mode.NEVER) val diagnostics: Boolean = false,
        val brightness: Int = 80,
        val proxy: String? = null,
        val note: String?,
    )

    /** A push from an older console that knew neither the region nor the key tag. */
    @Serializable
    public data class LegacyPush(
        @SerialName("dev") val deviceId: String,
        val schema: Int,
        val note: String?,
    )

    private val console: Cbor = Cbor {
        useDefiniteLengthEncoding = true
        encodeKeyTags = true
    }

    private val firmware: Cbor = Cbor {
        useDefiniteLengthEncoding = true
        verifyKeyTags = true
    }

    private val lenientReader: Cbor = Cbor {
        useDefiniteLengthEncoding = true
        verifyKeyTags = false
    }

    @Test
    fun minimalPushCarriesOnlyMandatoryFields(): Unit {
        val push: DeviceSettings = DeviceSettings(deviceId = "a1", note = null)

        val encoded: ByteArray = console.encodeToByteArray(push)

        val expected: ByteArray = bytes(0xA4) +
            bytes(0xD9, 0x05, 0x39) + text("dev") + text("a1") +
            text("schema") + bytes(0x03) +
            text("region") + text("eu") +
            text("note") + bytes(0xF6)
        assertArrayEquals(expected, encoded)

        val applied: DeviceSettings = firmware.decodeFromByteArray<DeviceSettings>(encoded)
        assertEquals(push, applied)
        assertEquals(80, applied.brightness)
        assertEquals(false, applied.diagnostics)
        assertNull(applied.proxy)
        assertNull(applied.note)
    }

    @Test
    fun verboseConsoleStillDropsNeverEncodedDefaults(): Unit {
        val verboseConsole: Cbor = Cbor(console) { encodeDefaults = true }
        val routine: DeviceSettings = DeviceSettings(deviceId = "b2", note = "lobby")
        val troubleshooting: DeviceSettings = routine.copy(diagnostics = true, proxy = "10.0.0.8:3128")

        val routineBytes: ByteArray = verboseConsole.encodeToByteArray(routine)
        val troubleshootingBytes: ByteArray = verboseConsole.encodeToByteArray(troubleshooting)

        val expectedRoutine: ByteArray = bytes(0xA6) +
            bytes(0xD9, 0x05, 0x39) + text("dev") + text("b2") +
            text("schema") + bytes(0x03) +
            text("region") + text("eu") +
            text("brightness") + bytes(0x18, 0x50) +
            text("proxy") + bytes(0xF6) +
            text("note") + text("lobby")
        assertArrayEquals(expectedRoutine, routineBytes)
        assertTrue(containsSequence(troubleshootingBytes, text("diagnostics") + bytes(0xF5)))
        assertEquals(troubleshooting, firmware.decodeFromByteArray<DeviceSettings>(troubleshootingBytes))
        assertEquals(routine, firmware.decodeFromByteArray<DeviceSettings>(routineBytes))
    }

    @Test
    fun firmwareRejectsPushesWithoutTheRequiredRegion(): Unit {
        val legacyConsole: Cbor = Cbor { useDefiniteLengthEncoding = true }
        val legacy: ByteArray = legacyConsole.encodeToByteArray(LegacyPush("c3", 3, null))

        val failure: MissingFieldException = assertThrows(MissingFieldException::class.java) {
            lenientReader.decodeFromByteArray<DeviceSettings>(legacy)
        }
        assertEquals(listOf("region"), failure.missingFields)

        val stated: ByteArray = console.encodeToByteArray(DeviceSettings(deviceId = "c3", region = "us", note = null))
        val applied: DeviceSettings = lenientReader.decodeFromByteArray<DeviceSettings>(stated)
        assertEquals("us", applied.region)
        assertEquals(80, applied.brightness)
    }

    @Test
    fun firmwareVerifiesTheDeviceIdKeyTag(): Unit {
        val untaggedConsole: Cbor = Cbor(console) { encodeKeyTags = false }
        val push: DeviceSettings = DeviceSettings(deviceId = "d4", region = "ap", brightness = 40, note = "dock")

        val tagged: ByteArray = console.encodeToByteArray(push)
        val untagged: ByteArray = untaggedConsole.encodeToByteArray(push)

        assertEquals(tagged.size - 3, untagged.size)
        assertEquals(push, firmware.decodeFromByteArray<DeviceSettings>(tagged))
        assertThrows(SerializationException::class.java) {
            firmware.decodeFromByteArray<DeviceSettings>(untagged)
        }
        assertEquals(push, lenientReader.decodeFromByteArray<DeviceSettings>(untagged))
        assertEquals(push, lenientReader.decodeFromByteArray<DeviceSettings>(tagged))
    }

    private fun bytes(vararg values: Int): ByteArray =
        ByteArray(values.size) { index: Int -> values[index].toByte() }

    private fun text(value: String): ByteArray {
        val utf8: ByteArray = value.encodeToByteArray()
        check(utf8.size < 24) { "short keys only" }
        return bytes(0x60 + utf8.size) + utf8
    }

    private fun containsSequence(haystack: ByteArray, needle: ByteArray): Boolean =
        (0..haystack.size - needle.size).any { start: Int ->
            needle.indices.all { offset: Int -> haystack[start + offset] == needle[offset] }
        }
}
