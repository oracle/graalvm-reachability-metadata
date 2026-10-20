/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_cbor_jvm

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.cbor.CborArray
import kotlinx.serialization.cbor.CborLabel
import kotlinx.serialization.cbor.ObjectTags
import kotlinx.serialization.cbor.ValueTags
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Exercises COSE-style CBOR encoding with integer labels, CBOR arrays, byte strings and
 * CBOR tags through the `Cbor.CoseCompliant` configuration.
 *
 * A sensor gateway wraps each reading in a COSE_Mac0 message (RFC 9052): a tagged array
 * holding a byte-string protected header, an unprotected header keyed by integer labels,
 * the encoded reading, and an HMAC over the MAC_structure the library also encodes.
 */
@OptIn(ExperimentalSerializationApi::class)
public class CoseMacTaggedReadingTest {

    @Serializable
    public data class ProtectedHeader(@CborLabel(1) @SerialName("alg") val algorithm: Int)

    @Serializable
    public class UnprotectedHeader(@CborLabel(4) @SerialName("kid") @ByteString val keyId: ByteArray)

    @Serializable
    public data class SensorReading(
        @CborLabel(1) val sensorId: String,
        @CborLabel(2) val celsiusTenths: Int,
        @CborLabel(3) @ValueTags(EPOCH_SECONDS_TAG) val takenAtEpochSeconds: Long,
    )

    @Serializable
    @CborArray
    @ObjectTags(COSE_MAC0_TAG)
    public class CoseMac0(
        @ByteString val protectedHeader: ByteArray,
        val unprotectedHeader: UnprotectedHeader,
        @ByteString val payload: ByteArray,
        @ByteString val tag: ByteArray,
    )

    @Serializable
    @CborArray
    public class MacStructure(
        val context: String,
        @ByteString val protectedHeader: ByteArray,
        @ByteString val externalAad: ByteArray,
        @ByteString val payload: ByteArray,
    )

    /** Issues and verifies MAC-protected readings the way a gateway and its backend would. */
    public class ReadingSealer(private val keyId: ByteArray, private val key: ByteArray) {

        private val cose: Cbor = Cbor.CoseCompliant

        public fun seal(reading: SensorReading): ByteArray {
            val protectedHeader: ByteArray = cose.encodeToByteArray(ProtectedHeader(HMAC_256_256))
            val payload: ByteArray = cose.encodeToByteArray(reading)
            val tag: ByteArray = mac(protectedHeader, payload)
            return cose.encodeToByteArray(CoseMac0(protectedHeader, UnprotectedHeader(keyId), payload, tag))
        }

        public fun open(message: ByteArray): SensorReading {
            val envelope: CoseMac0 = cose.decodeFromByteArray<CoseMac0>(message)
            val header: ProtectedHeader = cose.decodeFromByteArray<ProtectedHeader>(envelope.protectedHeader)
            require(header.algorithm == HMAC_256_256) { "unsupported alg ${header.algorithm}" }
            require(envelope.unprotectedHeader.keyId.contentEquals(keyId)) { "unknown key id" }
            val expected: ByteArray = mac(envelope.protectedHeader, envelope.payload)
            if (!expected.contentEquals(envelope.tag)) {
                throw SecurityException("MAC mismatch")
            }
            return cose.decodeFromByteArray<SensorReading>(envelope.payload)
        }

        private fun mac(protectedHeader: ByteArray, payload: ByteArray): ByteArray {
            val toBeMaced: ByteArray = cose.encodeToByteArray(
                MacStructure("MAC0", protectedHeader, ByteArray(0), payload),
            )
            val hmac: Mac = Mac.getInstance("HmacSHA256")
            hmac.init(SecretKeySpec(key, "HmacSHA256"))
            return hmac.doFinal(toBeMaced)
        }
    }

    private val reading: SensorReading = SensorReading("greenhouse-3", 215, 1_760_000_000L)

    private val sealer: ReadingSealer = ReadingSealer(
        keyId = "k1".encodeToByteArray(),
        key = ByteArray(32) { index: Int -> (index * 7 + 3).toByte() },
    )

    @Test
    fun sealsAndOpensAReading(): Unit {
        val message: ByteArray = sealer.seal(reading)

        val envelopeHead: ByteArray = bytes(
            0xD1, 0x84,
            0x43, 0xA1, 0x01, 0x05,
            0xA1, 0x04, 0x42, 0x6B, 0x31,
            0x58, 0x19,
        )
        assertArrayEquals(envelopeHead, message.copyOfRange(0, envelopeHead.size))
        assertArrayEquals(bytes(0x58, 0x20), message.copyOfRange(message.size - 34, message.size - 32))
        assertEquals(reading, sealer.open(message))
    }

    @Test
    fun writesTheReadingWithIntegerLabelsAndAnEpochTag(): Unit {
        val payload: ByteArray = Cbor.CoseCompliant.encodeToByteArray(reading)

        val expected: ByteArray = bytes(0xA3, 0x01, 0x6C) +
            "greenhouse-3".encodeToByteArray() +
            bytes(0x02, 0x18, 0xD7, 0x03, 0xC1, 0x1A, 0x68, 0xE7, 0x78, 0x00)
        assertArrayEquals(expected, payload)
        assertEquals(reading, Cbor.CoseCompliant.decodeFromByteArray<SensorReading>(payload))

        val untagged: ByteArray = payload.filterIndexed { index: Int, _: Byte -> index != EPOCH_TAG_INDEX }.toByteArray()
        assertThrows(SerializationException::class.java) {
            Cbor.CoseCompliant.decodeFromByteArray<SensorReading>(untagged)
        }
        val lenient: Cbor = Cbor(Cbor.CoseCompliant) { verifyValueTags = false }
        assertEquals(reading, lenient.decodeFromByteArray<SensorReading>(untagged))
    }

    @Test
    fun rejectsUntaggedAndTamperedMessages(): Unit {
        val message: ByteArray = sealer.seal(reading)

        val untagged: ByteArray = message.copyOfRange(1, message.size)
        assertThrows(SerializationException::class.java) { sealer.open(untagged) }

        val tampered: ByteArray = message.copyOf()
        val celsiusIndex: Int = 13 + 17
        assertEquals(0xD7.toByte(), tampered[celsiusIndex])
        tampered[celsiusIndex] = 0xD8.toByte()
        val failure: SecurityException = assertThrows(SecurityException::class.java) { sealer.open(tampered) }
        assertEquals("MAC mismatch", failure.message)
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { index: Int -> values[index].toByte() }

    private companion object {
        const val COSE_MAC0_TAG: ULong = 17u
        const val EPOCH_SECONDS_TAG: ULong = 1u
        const val HMAC_256_256: Int = 5
        const val EPOCH_TAG_INDEX: Int = 19
    }
}
