/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_io_jvm

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.ClosedByteChannelException
import io.ktor.utils.io.copyAndClose
import io.ktor.utils.io.counted
import io.ktor.utils.io.discard
import io.ktor.utils.io.jvm.javaio.copyTo
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.utils.io.jvm.javaio.toOutputStream
import io.ktor.utils.io.jvm.nio.asSource
import io.ktor.utils.io.peek
import io.ktor.utils.io.pool.ByteArrayPool
import io.ktor.utils.io.pool.useInstance
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readByteArray
import io.ktor.utils.io.readDouble
import io.ktor.utils.io.readFloat
import io.ktor.utils.io.readInt
import io.ktor.utils.io.readLong
import io.ktor.utils.io.readShort
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.readUntil
import io.ktor.utils.io.skipIfFound
import io.ktor.utils.io.toByteArray
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeByteArray
import io.ktor.utils.io.writeDouble
import io.ktor.utils.io.writeFloat
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeInt
import io.ktor.utils.io.writeLong
import io.ktor.utils.io.writeShort
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
import kotlinx.io.bytestring.decodeToString
import kotlinx.io.bytestring.encodeToByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.charset.StandardCharsets
import kotlin.coroutines.EmptyCoroutineContext

public class Ktor_io_jvmTest {
    @Test
    fun byteReadChannelReadsTextLinesPeeksAndSkipsDelimiters(): Unit = runBlocking {
        withTimeout(5_000) {
            val channel: ByteReadChannel = ByteReadChannel("first line\r\n--payload--tail", StandardCharsets.UTF_8)

            val firstLine: String? = channel.readUTF8Line(64)
            val skipped: Boolean = channel.skipIfFound("--".encodeToByteString())
            val peeked: String = requireNotNull(channel.peek(7)).decodeToString()
            val payload: ByteArray = channel.readByteArray(7)
            val secondSkipped: Boolean = channel.skipIfFound("--".encodeToByteString())
            val remaining: ByteArray = channel.toByteArray()

            assertThat(firstLine).isEqualTo("first line")
            assertThat(skipped).isTrue()
            assertThat(peeked).isEqualTo("payload")
            assertThat(payload.decodeToString()).isEqualTo("payload")
            assertThat(secondSkipped).isTrue()
            assertThat(remaining.decodeToString()).isEqualTo("tail")
            assertThat(channel.isClosedForRead).isTrue()
        }
    }

    @Test
    fun byteChannelTransfersPrimitiveValuesAndCountsReadAndWrittenBytes(): Unit = runBlocking {
        withTimeout(5_000) {
            val channel: ByteChannel = ByteChannel(autoFlush = true)
            val writeChannel: ByteWriteChannel = channel
            val countedWriter = writeChannel.counted()

            countedWriter.writeByte(0x7F)
            countedWriter.writeShort(0x1234)
            countedWriter.writeInt(0x01020304)
            countedWriter.writeLong(0x0102030405060708L)
            countedWriter.writeFloat(12.5f)
            countedWriter.writeDouble(99.125)
            countedWriter.flush()

            val readChannel: ByteReadChannel = channel
            val countedReader = readChannel.counted()
            assertThat(countedReader.readByte()).isEqualTo(0x7F.toByte())
            assertThat(countedReader.readShort()).isEqualTo(0x1234.toShort())
            assertThat(countedReader.readInt()).isEqualTo(0x01020304)
            assertThat(countedReader.readLong()).isEqualTo(0x0102030405060708L)
            assertThat(countedReader.readFloat()).isEqualTo(12.5f)
            assertThat(countedReader.readDouble()).isEqualTo(99.125)

            assertThat(countedWriter.totalBytesWritten).isEqualTo(27L)
            assertThat(countedReader.totalBytesRead).isEqualTo(27L)
        }
    }

    @Test
    fun copyOperationsMoveDataBetweenChannelsAndOutputStreams(): Unit = runBlocking {
        withTimeout(5_000) {
            val source: ByteReadChannel = ByteReadChannel("copy source data", StandardCharsets.UTF_8)
            val destination: ByteChannel = ByteChannel(autoFlush = true)

            val copiedToChannel: Long = source.copyAndClose(destination)
            val copiedText: String = destination.readByteArray(copiedToChannel.toInt()).decodeToString()

            val outputStream = ByteArrayOutputStream()
            val copiedToStream: Long = ByteReadChannel("stream copy", StandardCharsets.UTF_8).copyTo(outputStream)

            assertThat(copiedToChannel).isEqualTo("copy source data".length.toLong())
            assertThat(copiedText).isEqualTo("copy source data")
            assertThat(copiedToStream).isEqualTo("stream copy".length.toLong())
            assertThat(outputStream.toString(StandardCharsets.UTF_8)).isEqualTo("stream copy")
        }
    }

    @Test
    fun readUntilAndDiscardSupportDelimiterOrientedParsing(): Unit = runBlocking {
        withTimeout(5_000) {
            val input: ByteReadChannel = ByteReadChannel("header::body::ignored", StandardCharsets.UTF_8)
            val header: ByteChannel = ByteChannel(autoFlush = true)
            val body: ByteChannel = ByteChannel(autoFlush = true)

            val headerBytes: Long = input.readUntil("::".encodeToByteString(), header, limit = 64, ignoreMissing = false)
            val bodyBytes: Long = input.readUntil("::".encodeToByteString(), body, limit = 64, ignoreMissing = false)
            val discarded: Long = input.discard(7)

            assertThat(headerBytes).isEqualTo(6L)
            assertThat(header.readByteArray(headerBytes.toInt()).decodeToString()).isEqualTo("header")
            assertThat(bodyBytes).isEqualTo(4L)
            assertThat(body.readByteArray(bodyBytes.toInt()).decodeToString()).isEqualTo("body")
            assertThat(discarded).isEqualTo(7L)
            assertThat(input.isClosedForRead).isTrue()
        }
    }

    @Test
    fun byteBufferWriteFullyTransfersRemainingBytes(): Unit = runBlocking {
        withTimeout(5_000) {
            val channel: ByteChannel = ByteChannel(autoFlush = true)
            val payload: ByteBuffer = ByteBuffer.wrap("buffered bytes".toByteArray(StandardCharsets.UTF_8))
            payload.position("buffered ".length)

            channel.writeFully(payload)
            val remaining: ByteArray = channel.readByteArray("bytes".length)

            assertThat(payload.hasRemaining()).isFalse()
            assertThat(remaining.decodeToString()).isEqualTo("bytes")
        }
    }

    @Test
    fun readAvailableAndWriteFullyHandlePartialByteArrays(): Unit = runBlocking {
        withTimeout(5_000) {
            val channel: ByteChannel = ByteChannel(autoFlush = true)
            val source: ByteArray = "0123456789".toByteArray(StandardCharsets.UTF_8)
            val target: ByteArray = ByteArray(6)

            channel.writeFully(source, startIndex = 2, endIndex = 8)
            val firstRead: Int = channel.readAvailable(target, offset = 1, length = 4)
            val secondRead: Int = channel.readAvailable(target, offset = 5, length = 1)
            val lastByte: Byte = channel.readByte()

            assertThat(firstRead).isEqualTo(4)
            assertThat(secondRead).isEqualTo(1)
            assertThat(lastByte).isEqualTo('7'.code.toByte())
            assertThat(String(target, StandardCharsets.UTF_8)).isEqualTo("\u000023456")
        }
    }

    @Test
    fun javaInputAndOutputStreamAdaptersBridgeBlockingIoWithChannels(): Unit = runBlocking {
        withTimeout(5_000) {
            val fromInputStream: ByteReadChannel = ByteArrayInputStream(
                "from input stream".toByteArray(StandardCharsets.UTF_8)
            ).toByteReadChannel(EmptyCoroutineContext)
            val inputBytes: ByteArray = fromInputStream.toByteArray()

            val outputChannel: ByteChannel = ByteChannel(autoFlush = true)
            val outputStream = outputChannel.toOutputStream()
            outputStream.write("to output stream".toByteArray(StandardCharsets.UTF_8))
            outputStream.flush()
            val outputBytes: ByteArray = outputChannel.readByteArray("to output stream".length)
            outputStream.close()

            val blockingInput = ByteReadChannel("blocking input", StandardCharsets.UTF_8).toInputStream()
            val blockingBytes: ByteArray = blockingInput.readAllBytes()
            blockingInput.close()

            assertThat(inputBytes.decodeToString()).isEqualTo("from input stream")
            assertThat(outputBytes.decodeToString()).isEqualTo("to output stream")
            assertThat(blockingBytes.decodeToString()).isEqualTo("blocking input")
            assertThat(outputChannel.isClosedForWrite).isTrue()
        }
    }

    @Test
    fun nioReadableByteChannelCanBeConsumedAsRawSource(): Unit {
        val javaChannel = Channels.newChannel(ByteArrayInputStream("nio-source".toByteArray(StandardCharsets.UTF_8)))
        val source = javaChannel.asSource()
        val buffer = Buffer()

        val bytesRead: Long = source.readAtMostTo(buffer, 32)
        val textBytes: ByteArray = ByteArray(bytesRead.toInt())
        buffer.readAtMostTo(textBytes, 0, textBytes.size)
        source.close()

        assertThat(bytesRead).isEqualTo("nio-source".length.toLong())
        assertThat(textBytes.decodeToString()).isEqualTo("nio-source")
    }

    @Test
    fun stringAndByteArrayChannelsRespectOffsetsAndCharsets(): Unit = runBlocking {
        withTimeout(5_000) {
            val bytes: ByteArray = "prefix-данные-suffix".toByteArray(StandardCharsets.UTF_8)
            val prefixSize: Int = "prefix-".toByteArray(StandardCharsets.UTF_8).size
            val contentSize: Int = "данные".toByteArray(StandardCharsets.UTF_8).size
            val byteChannel: ByteReadChannel = ByteReadChannel(bytes, prefixSize, contentSize)
            val textChannel: ByteReadChannel = ByteReadChannel("Grüße", StandardCharsets.UTF_16BE)

            val offsetText: String = String(byteChannel.toByteArray(), StandardCharsets.UTF_8)
            val encodedText: String = String(textChannel.toByteArray(), StandardCharsets.UTF_16BE)

            assertThat(offsetText).isEqualTo("данные")
            assertThat(encodedText).isEqualTo("Grüße")
        }
    }

    @Test
    fun byteArrayPoolReusesBorrowedInstancesAfterRecycle(): Unit {
        val firstSize: Int = ByteArrayPool.useInstance { borrowed: ByteArray ->
            borrowed[0] = 42
            borrowed.size
        }
        val secondSize: Int = ByteArrayPool.useInstance { borrowed: ByteArray ->
            borrowed[0] = 7
            borrowed.size
        }

        assertThat(ByteArrayPool.capacity).isGreaterThan(0)
        assertThat(firstSize).isGreaterThan(0)
        assertThat(secondSize).isEqualTo(firstSize)
    }

    @Test
    fun lineReaderReturnsCompleteLineWithinConfiguredLimit(): Unit = runBlocking {
        withTimeout(5_000) {
            val line: String? = ByteReadChannel("abcdef\n", StandardCharsets.UTF_8).readUTF8Line(16)

            assertThat(line).isEqualTo("abcdef")
        }
    }

    @Test
    fun byteBufferContentCanBeWrittenThroughChannelApi(): Unit = runBlocking {
        withTimeout(5_000) {
            val channel: ByteChannel = ByteChannel(autoFlush = true)
            val source: ByteBuffer = ByteBuffer.wrap("byte-buffer".toByteArray(StandardCharsets.UTF_8))
            val bytes: ByteArray = ByteArray(source.remaining())
            source.get(bytes)

            channel.writeByteArray(bytes)
            val readBack: ByteArray = channel.readByteArray(bytes.size)

            assertThat(readBack.decodeToString()).isEqualTo("byte-buffer")
        }
    }

    @Test
    fun cancelRecordsCloseCauseForReadAndWriteSides(): Unit {
        val channel: ByteChannel = ByteChannel(autoFlush = true)
        val cause = IllegalStateException("cancelled by test")

        channel.cancel(cause)

        assertThat(channel.isClosedForRead).isTrue()
        assertThat(channel.isClosedForWrite).isTrue()
        assertThat(channel.closedCause)
            .isInstanceOf(ClosedByteChannelException::class.java)
            .hasMessageContaining("cancelled by test")
        assertThat(channel.closedCause?.cause).isSameAs(cause)
    }
}
