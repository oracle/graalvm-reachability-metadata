/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_cbor_jvm

import java.io.ByteArrayOutputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises the `Cbor` parser on indefinite-length framing written by streaming encoders:
 * indefinite maps and arrays, chunked text and byte strings, skipped indefinite fields, the
 * default indefinite output framing, and rejection of truncated or mistyped frames.
 *
 * A telemetry gateway receives frames from devices whose firmware streams CBOR without knowing
 * lengths up front. The gateway must read those frames, drop fields newer firmware adds, refuse
 * truncated or mistyped frames, and accept its own re-encoded frames in either framing.
 */
@OptIn(ExperimentalSerializationApi::class)
public class StreamingFrameInteropCborTest {

    @Serializable
    public data class Frame(
        val device: String,
        val seq: Int,
        val samples: List<Int>,
        @ByteString val payload: ByteArray,
        val meta: Map<String, String>,
    ) {
        override fun equals(other: Any?): Boolean =
            other is Frame &&
                device == other.device &&
                seq == other.seq &&
                samples == other.samples &&
                payload.contentEquals(other.payload) &&
                meta == other.meta

        override fun hashCode(): Int = device.hashCode() * 31 + seq
    }

    /** Writes raw CBOR items the way a streaming device encoder does. */
    public class StreamWriter {
        private val out: ByteArrayOutputStream = ByteArrayOutputStream()

        fun raw(vararg bytes: Int): StreamWriter {
            bytes.forEach { byte: Int -> out.write(byte) }
            return this
        }

        fun text(value: String): StreamWriter {
            val utf8: ByteArray = value.encodeToByteArray()
            require(utf8.size < 24) { "short strings only" }
            out.write(0x60 or utf8.size)
            out.write(utf8)
            return this
        }

        fun bytes(vararg value: Int): StreamWriter {
            require(value.size < 24) { "short byte strings only" }
            out.write(0x40 or value.size)
            value.forEach { byte: Int -> out.write(byte) }
            return this
        }

        fun chunkedText(vararg chunks: String): StreamWriter {
            out.write(0x7F)
            chunks.forEach { chunk: String -> text(chunk) }
            out.write(BREAK)
            return this
        }

        fun chunkedBytes(vararg chunks: IntArray): StreamWriter {
            out.write(0x5F)
            chunks.forEach { chunk: IntArray -> bytes(*chunk) }
            out.write(BREAK)
            return this
        }

        fun startMap(): StreamWriter = raw(0xBF)

        fun startArray(): StreamWriter = raw(0x9F)

        fun end(): StreamWriter = raw(BREAK)

        fun toByteArray(): ByteArray = out.toByteArray()
    }

    private val gateway: Cbor = Cbor { ignoreUnknownKeys = true }

    private val expectedFrame: Frame = Frame(
        device = "temp-01",
        seq = 42,
        samples = listOf(1, 2, 100),
        payload = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()),
        meta = mapOf("site" to "lab", "unit" to "celsius"),
    )

    private fun streamedFrame(seq: Int, withFirmwareField: Boolean): StreamWriter {
        val writer: StreamWriter = StreamWriter()
            .startMap()
            .text("device").chunkedText("temp", "-01")
            .text("seq").raw(0x18, seq)
            .text("samples").startArray().raw(0x01, 0x02, 0x18, 0x64).end()
        if (withFirmwareField) {
            writer.text("firmware")
                .startMap()
                .text("rev").startArray().raw(0x01, 0x07).end()
                .text("notes").chunkedText("hot", "fix")
                .text("blob").chunkedBytes(intArrayOf(0x01), intArrayOf(0x02, 0x03))
                .end()
        }
        return writer
            .text("payload").chunkedBytes(intArrayOf(0xDE, 0xAD), intArrayOf(0xBE, 0xEF))
            .text("meta").startMap()
            .text("site").text("lab")
            .text("unit").chunkedText("cel", "si", "us")
            .end()
            .end()
    }

    @Test
    fun decodesAFrameStreamedWithIndefiniteLengthItems(): Unit {
        val wire: ByteArray = streamedFrame(seq = 42, withFirmwareField = false).toByteArray()

        val frame: Frame = gateway.decodeFromByteArray<Frame>(wire)

        assertEquals(expectedFrame, frame)
        assertEquals("temp-01", frame.device)
        assertEquals(listOf(1, 2, 100), frame.samples)
        assertEquals("celsius", frame.meta["unit"])
    }

    @Test
    fun skipsIndefiniteFieldsAddedByNewerFirmware(): Unit {
        val wire: ByteArray = streamedFrame(seq = 42, withFirmwareField = true).toByteArray()

        assertEquals(expectedFrame, gateway.decodeFromByteArray<Frame>(wire))
        assertThrows(SerializationException::class.java) {
            Cbor.decodeFromByteArray<Frame>(wire)
        }
    }

    @Test
    fun decodesABatchOfFramesInAnIndefiniteArray(): Unit {
        val batch: StreamWriter = StreamWriter().startArray()
        val first: ByteArray = streamedFrame(seq = 1, withFirmwareField = true).toByteArray()
        val second: ByteArray = streamedFrame(seq = 2, withFirmwareField = false).toByteArray()
        val wire: ByteArray = batch.toByteArray() + first + second + byteArrayOf(BREAK.toByte())

        val frames: List<Frame> = gateway.decodeFromByteArray<List<Frame>>(wire)

        assertEquals(listOf(1, 2), frames.map { frame: Frame -> frame.seq })
        assertEquals(listOf(expectedFrame.copy(seq = 1), expectedFrame.copy(seq = 2)), frames)
    }

    @Test
    fun reEncodesFramesInEitherFramingAndReadsBothBack(): Unit {
        val streaming: ByteArray = Cbor.encodeToByteArray(expectedFrame)
        val definite: Cbor = Cbor { useDefiniteLengthEncoding = true }
        val sized: ByteArray = definite.encodeToByteArray(expectedFrame)

        assertEquals(0xBF, streaming.first().toInt() and 0xFF)
        assertEquals(BREAK, streaming.last().toInt() and 0xFF)
        assertEquals(0xA5, sized.first().toInt() and 0xFF)
        assertEquals(sized.size + 3, streaming.size)
        assertEquals(expectedFrame, gateway.decodeFromByteArray<Frame>(streaming))
        assertEquals(expectedFrame, gateway.decodeFromByteArray<Frame>(sized))

        val payloadKey: ByteArray = StreamWriter().text("payload").bytes(0xDE, 0xAD, 0xBE, 0xEF)
            .toByteArray()
        val definitePayload: Boolean = (0..sized.size - payloadKey.size).any { index: Int ->
            sized.copyOfRange(index, index + payloadKey.size).contentEquals(payloadKey)
        }
        assertTrue(definitePayload, "payload is written as one definite byte string")
    }

    @Test
    fun rejectsTruncatedAndMistypedFrames(): Unit {
        val sized: ByteArray = Cbor { useDefiniteLengthEncoding = true }.encodeToByteArray(expectedFrame)
        val truncated: ByteArray = sized.copyOfRange(0, sized.size / 2)
        val eof: IllegalStateException = assertThrows(IllegalStateException::class.java) {
            gateway.decodeFromByteArray<Frame>(truncated)
        }
        assertTrue(eof.message!!.startsWith("Unexpected EOF"), eof.message)

        val mistyped: ByteArray = StreamWriter()
            .startMap()
            .text("device").text("temp-01")
            .text("seq").text("forty-two")
            .end()
            .toByteArray()
        assertThrows(SerializationException::class.java) {
            gateway.decodeFromByteArray<Frame>(mistyped)
        }
    }

    private companion object {
        const val BREAK: Int = 0xFF
    }
}
