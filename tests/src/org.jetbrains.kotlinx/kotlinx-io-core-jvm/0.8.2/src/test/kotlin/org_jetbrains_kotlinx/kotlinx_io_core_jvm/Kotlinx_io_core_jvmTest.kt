/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_io_core_jvm

import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.asByteChannel
import kotlinx.io.asInputStream
import kotlinx.io.asOutputStream
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.bytestring.ByteString
import kotlinx.io.discardingSink
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.indexOf
import kotlinx.io.readAtMostTo
import kotlinx.io.readByteArray
import kotlinx.io.readByteString
import kotlinx.io.readCodePointValue
import kotlinx.io.readDecimalLong
import kotlinx.io.readDouble
import kotlinx.io.readDoubleLe
import kotlinx.io.readFloat
import kotlinx.io.readFloatLe
import kotlinx.io.readHexadecimalUnsignedLong
import kotlinx.io.readIntLe
import kotlinx.io.readLine
import kotlinx.io.readLineStrict
import kotlinx.io.readLongLe
import kotlinx.io.readShortLe
import kotlinx.io.readString
import kotlinx.io.readTo
import kotlinx.io.snapshot
import kotlinx.io.startsWith
import kotlinx.io.transferFrom
import kotlinx.io.write
import kotlinx.io.writeCodePointValue
import kotlinx.io.writeDecimalLong
import kotlinx.io.writeDouble
import kotlinx.io.writeDoubleLe
import kotlinx.io.writeFloat
import kotlinx.io.writeFloatLe
import kotlinx.io.writeHexadecimalUnsignedLong
import kotlinx.io.writeIntLe
import kotlinx.io.writeLongLe
import kotlinx.io.writeShortLe
import kotlinx.io.writeString
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files

public class Kotlinx_io_core_jvmTest {
    @Test
    fun bufferWritesAndReadsPrimitiveValuesInBothByteOrders() {
        val buffer: Buffer = Buffer()

        buffer.writeByte(0x7f.toByte())
        buffer.writeShort(0x1234.toShort())
        buffer.writeShortLe(0x4567.toShort())
        buffer.writeInt(0x01020304)
        buffer.writeIntLe(0x05060708)
        buffer.writeLong(0x0102030405060708L)
        buffer.writeLongLe(0x1122334455667788L)
        buffer.writeFloat(1.25f)
        buffer.writeFloatLe(-2.5f)
        buffer.writeDouble(Math.PI)
        buffer.writeDoubleLe(-123.5)

        assertFalse(buffer.exhausted())
        assertTrue(buffer.request(1))
        buffer.require(1)
        assertEquals(0x7f.toByte(), buffer.readByte())
        assertEquals(0x1234.toShort(), buffer.readShort())
        assertEquals(0x4567.toShort(), buffer.readShortLe())
        assertEquals(0x01020304, buffer.readInt())
        assertEquals(0x05060708, buffer.readIntLe())
        assertEquals(0x0102030405060708L, buffer.readLong())
        assertEquals(0x1122334455667788L, buffer.readLongLe())
        assertEquals(1.25f, buffer.readFloat())
        assertEquals(-2.5f, buffer.readFloatLe())
        assertEquals(Math.PI, buffer.readDouble())
        assertEquals(-123.5, buffer.readDoubleLe())
        assertTrue(buffer.exhausted())

        assertThrows(EOFException::class.java) { buffer.require(1) }
        assertThrows(EOFException::class.java) { buffer.readByte() }
    }

    @Test
    fun utf8StringsCodePointsNumbersAndLinesRoundTripThroughBuffers() {
        val text: Buffer = Buffer()
        text.writeString("ASCII ")
        text.writeString("Kotlin π ")
        text.writeCodePointValue(0x1f642)
        assertEquals("ASCII Kotlin π 🙂", text.readString())

        val codePoint: Buffer = Buffer()
        codePoint.writeCodePointValue(0x1f680)
        assertEquals(0x1f680, codePoint.readCodePointValue())
        assertTrue(codePoint.exhausted())

        val numbers: Buffer = Buffer()
        numbers.writeDecimalLong(Long.MIN_VALUE)
        numbers.writeByte('\n'.code.toByte())
        numbers.writeDecimalLong(0L)
        numbers.writeByte('\n'.code.toByte())
        numbers.writeHexadecimalUnsignedLong(0xfeed_beefL)
        assertEquals(Long.MIN_VALUE, numbers.readDecimalLong())
        assertEquals('\n'.code.toByte(), numbers.readByte())
        assertEquals(0L, numbers.readDecimalLong())
        assertEquals('\n'.code.toByte(), numbers.readByte())
        assertEquals(0xfeed_beefL, numbers.readHexadecimalUnsignedLong())

        val lines: Buffer = Buffer()
        lines.writeString("alpha\r\nbeta\ngamma")
        assertEquals("alpha", lines.readLineStrict())
        assertEquals("beta", lines.readLine())
        assertEquals("gamma", lines.readLine())
        assertTrue(lines.exhausted())

        val unterminated: Buffer = Buffer()
        unterminated.writeString("last line without newline")
        assertThrows(EOFException::class.java) { unterminated.readLineStrict() }

        val finalLine: Buffer = Buffer()
        finalLine.writeString("last line without newline")
        assertEquals("last line without newline", finalLine.readLine())
    }

    @Test
    fun copyingPeekingSearchingAndSnapshotsDoNotConsumeOriginalData() {
        val payload: ByteArray = byteArrayOf(0, 1, 2, 3, 4, 2, 3, 5, 6, 7)
        val buffer: Buffer = Buffer()
        buffer.write(payload)

        assertEquals(payload.size.toLong(), buffer.size)
        assertEquals(2L, buffer.indexOf(2.toByte()))
        assertEquals(5L, buffer.indexOf(2.toByte(), startIndex = 3))
        assertEquals(-1L, buffer.indexOf(99.toByte()))
        assertEquals(0.toByte(), buffer[0])
        assertEquals(7.toByte(), buffer[buffer.size - 1])
        assertArrayEquals(payload, buffer.snapshot().toByteArray())

        val copiedRange: Buffer = Buffer()
        buffer.copyTo(copiedRange, startIndex = 2, endIndex = 7)
        assertArrayEquals(byteArrayOf(2, 3, 4, 2, 3), copiedRange.readByteArray())
        assertEquals(payload.size.toLong(), buffer.size, "copyTo must leave the source intact")

        val copy: Buffer = buffer.copy()
        copy.writeByte(99.toByte())
        assertArrayEquals(payload, buffer.snapshot().toByteArray())
        assertEquals(payload.size + 1L, copy.size)

        val peek = buffer.peek()
        assertEquals(0.toByte(), peek.readByte())
        assertEquals(1.toByte(), peek.readByte())
        assertEquals(payload.size.toLong(), buffer.size, "peek reads must not consume the owner buffer")

        buffer.skip(4)
        assertArrayEquals(byteArrayOf(4, 2, 3, 5, 6, 7), buffer.readByteArray())
        assertTrue(buffer.exhausted())
        assertThrows(EOFException::class.java) { buffer.skip(1) }
    }

    @Test
    fun byteStringsCanBeWrittenReadAndSearchedAsBinarySequences() {
        val payload: ByteString = ByteString(byteArrayOf(9, 1, 2, 3, 2, 3, 4, 9))
        val buffer: Buffer = Buffer()
        buffer.write(payload, startIndex = 1, endIndex = 7)

        val needle: ByteString = ByteString(2.toByte(), 3.toByte())
        assertEquals(1L, buffer.indexOf(needle))
        assertEquals(3L, buffer.indexOf(needle, startIndex = 2))
        assertEquals(-1L, buffer.indexOf(ByteString(4.toByte(), 9.toByte())))
        assertEquals(6L, buffer.size, "sequence searches must not consume buffer content")

        assertEquals(ByteString(1.toByte(), 2.toByte(), 3.toByte()), buffer.readByteString(byteCount = 3))
        assertEquals(ByteString(2.toByte(), 3.toByte(), 4.toByte()), buffer.readByteString())
        assertTrue(buffer.exhausted())
    }

    @Test
    fun byteArrayAndByteBufferBulkOperationsRespectPositionsAndBounds() {
        val buffer: Buffer = Buffer()
        buffer.write(byteArrayOf(10, 20, 30, 40, 50), startIndex = 1, endIndex = 4)
        buffer.transferFrom(ByteBuffer.wrap(byteArrayOf(60, 70, 80)))

        val firstDestination: ByteArray = ByteArray(6) { (-1).toByte() }
        assertEquals(4, buffer.readAtMostTo(firstDestination, startIndex = 1, endIndex = 5))
        assertArrayEquals(byteArrayOf(-1, 20, 30, 40, 60, -1), firstDestination)

        val secondDestination: ByteBuffer = ByteBuffer.allocate(4)
        assertEquals(2, buffer.readAtMostTo(secondDestination))
        assertEquals(2, secondDestination.position())
        secondDestination.flip()
        val observed: ByteArray = ByteArray(2)
        secondDestination.get(observed)
        assertArrayEquals(byteArrayOf(70, 80), observed)
        assertEquals(-1, buffer.readAtMostTo(ByteArray(1)))

        val source: Buffer = Buffer()
        source.write(byteArrayOf(1, 2, 3, 4, 5))
        val sink: Buffer = Buffer()
        assertEquals(3L, source.readAtMostTo(sink, byteCount = 3))
        assertArrayEquals(byteArrayOf(1, 2, 3), sink.readByteArray())
        source.readTo(sink, byteCount = 2)
        assertArrayEquals(byteArrayOf(4, 5), sink.readByteArray())
        assertTrue(source.exhausted())
    }

    @Test
    fun rawSourceAndRawSinkAdaptersBufferStreamsAndChannels() {
        val inputPayload: ByteArray = "stream payload".toByteArray(StandardCharsets.UTF_8)
        val source = ByteArrayInputStream(inputPayload).asSource().buffered()
        assertTrue(source.startsWith('s'.code.toByte()))
        assertEquals("stream", source.readString(byteCount = 6))
        assertEquals(' '.code.toByte(), source.readByte())
        assertEquals("payload", source.readString())
        source.close()

        val outputBytes: ByteArrayOutputStream = ByteArrayOutputStream()
        val sink = outputBytes.asSink().buffered()
        sink.writeString("answer=")
        sink.writeIntLe(42)
        sink.flush()
        sink.close()
        assertArrayEquals(byteArrayOf(97, 110, 115, 119, 101, 114, 61, 42, 0, 0, 0), outputBytes.toByteArray())

        val bufferedInput: Buffer = Buffer()
        bufferedInput.writeString("abc")
        val inputStream = bufferedInput.asInputStream()
        assertEquals('a'.code, inputStream.read())
        assertEquals('b'.code, inputStream.read())
        assertEquals('c'.code, inputStream.read())
        assertEquals(-1, inputStream.read())

        val bufferedOutput: Buffer = Buffer()
        val outputStream = bufferedOutput.asOutputStream()
        outputStream.write("xyz".toByteArray(StandardCharsets.UTF_8))
        outputStream.flush()
        assertEquals("xyz", bufferedOutput.readString())

        val channelBuffer: Buffer = Buffer()
        val byteChannel = channelBuffer.asByteChannel()
        assertEquals(3, byteChannel.write(ByteBuffer.wrap(byteArrayOf(1, 2, 3))))
        val channelDestination: ByteBuffer = ByteBuffer.allocate(4)
        assertEquals(3, byteChannel.read(channelDestination))
        channelDestination.flip()
        assertEquals(1.toByte(), channelDestination.get())
        assertEquals(2.toByte(), channelDestination.get())
        assertEquals(3.toByte(), channelDestination.get())
        byteChannel.close()
        assertTrue(byteChannel.isOpen, "Buffer-backed channels are lightweight adapters and remain open")
    }

    @Test
    fun sourceAndSinkByteChannelAdaptersHonorByteBufferPositions() {
        val inputPayload: ByteArray = "nio source".toByteArray(StandardCharsets.UTF_8)
        val readableChannel: ReadableByteChannel = ByteArrayInputStream(inputPayload).asSource().buffered().asByteChannel()
        val destination: ByteBuffer = ByteBuffer.allocate(inputPayload.size + 4)
        destination.position(2)

        assertEquals(inputPayload.size, readableChannel.read(destination))
        assertEquals(inputPayload.size + 2, destination.position())
        destination.flip()
        destination.position(2)
        val decodedInput: ByteArray = ByteArray(inputPayload.size)
        destination.get(decodedInput)
        assertArrayEquals(inputPayload, decodedInput)
        readableChannel.close()
        assertFalse(readableChannel.isOpen)

        val outputBytes: ByteArrayOutputStream = ByteArrayOutputStream()
        val writableChannel: WritableByteChannel = outputBytes.asSink().buffered().asByteChannel()
        val expectedOutput: ByteArray = "nio sink".toByteArray(StandardCharsets.UTF_8)
        val outputPayload: ByteArray = byteArrayOf(0) + expectedOutput + byteArrayOf(0)
        val source: ByteBuffer = ByteBuffer.wrap(outputPayload)
        source.position(1)
        source.limit(outputPayload.size - 1)

        assertEquals(expectedOutput.size, writableChannel.write(source))
        assertEquals(outputPayload.size - 1, source.position())
        writableChannel.close()
        assertFalse(writableChannel.isOpen)
        assertArrayEquals(expectedOutput, outputBytes.toByteArray())
    }

    @Test
    fun transferOperationsWorkWithCustomRawSourcesAndSinks() {
        val rawSource = ChunkedRawSource(
            listOf(
                byteArrayOf(1, 2),
                byteArrayOf(3, 4, 5),
                byteArrayOf(6)
            )
        )
        val sink: Buffer = Buffer()
        assertEquals(6L, sink.transferFrom(rawSource))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), sink.snapshot().toByteArray())
        assertFalse(rawSource.closed)
        rawSource.close()
        assertTrue(rawSource.closed)

        val recordingSink = RecordingRawSink()
        assertEquals(6L, sink.transferTo(recordingSink))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), recordingSink.bytes())
        assertTrue(sink.exhausted())

        val discarded: Buffer = Buffer()
        discarded.writeString("unneeded")
        assertEquals(discarded.size, discarded.transferTo(discardingSink()))
        assertTrue(discarded.exhausted())
    }

    @Test
    fun systemFileSystemReadsWritesListsMetadataAndMovesFiles() {
        val temporaryRoot = Files.createTempDirectory("kotlinx-io-core-jvm-test").toFile()
        try {
            val root: Path = Path(temporaryRoot.absolutePath)
            val directory: Path = Path(root, "nested")
            val firstFile: Path = Path(directory, "first.txt")
            val movedFile: Path = Path(directory, "moved.txt")

            SystemFileSystem.createDirectories(directory)
            assertTrue(SystemFileSystem.exists(directory))
            assertTrue(SystemFileSystem.metadataOrNull(directory)!!.isDirectory)

            val sink = SystemFileSystem.sink(firstFile).buffered()
            sink.writeString("file contents")
            sink.close()
            assertTrue(SystemFileSystem.exists(firstFile))

            val metadata = SystemFileSystem.metadataOrNull(firstFile)
            assertTrue(metadata!!.isRegularFile)
            assertEquals("file contents".toByteArray(StandardCharsets.UTF_8).size.toLong(), metadata.size)

            val source = SystemFileSystem.source(firstFile).buffered()
            assertEquals("file contents", source.readString())
            source.close()

            val listed: List<String> = SystemFileSystem.list(directory).map { path: Path -> path.name }.sorted()
            assertEquals(listOf("first.txt"), listed)

            SystemFileSystem.atomicMove(firstFile, movedFile)
            assertFalse(SystemFileSystem.exists(firstFile))
            assertTrue(SystemFileSystem.exists(movedFile))
            assertEquals(SystemFileSystem.resolve(movedFile), movedFile)

            SystemFileSystem.delete(movedFile)
            assertFalse(SystemFileSystem.exists(movedFile))
        } finally {
            temporaryRoot.deleteRecursively()
        }
    }

    private class ChunkedRawSource(private val chunks: List<ByteArray>) : RawSource {
        var closed: Boolean = false
            private set

        private var nextChunkIndex: Int = 0

        override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
            require(byteCount >= 0L) { "byteCount must be non-negative" }
            if (closed) {
                throw IllegalStateException("source is closed")
            }
            if (nextChunkIndex == chunks.size) {
                return -1L
            }

            val chunk: ByteArray = chunks[nextChunkIndex++]
            val byteCountToWrite: Int = minOf(byteCount.toInt(), chunk.size)
            sink.write(chunk, startIndex = 0, endIndex = byteCountToWrite)
            return byteCountToWrite.toLong()
        }

        override fun close() {
            closed = true
        }
    }

    private class RecordingRawSink : RawSink {
        private val output: ByteArrayOutputStream = ByteArrayOutputStream()
        private var closed: Boolean = false

        override fun write(source: Buffer, byteCount: Long) {
            if (closed) {
                throw IllegalStateException("sink is closed")
            }
            source.readTo(output, byteCount)
        }

        override fun flush() {
            if (closed) {
                throw IllegalStateException("sink is closed")
            }
        }

        override fun close() {
            closed = true
        }

        fun bytes(): ByteArray = output.toByteArray()
    }
}
