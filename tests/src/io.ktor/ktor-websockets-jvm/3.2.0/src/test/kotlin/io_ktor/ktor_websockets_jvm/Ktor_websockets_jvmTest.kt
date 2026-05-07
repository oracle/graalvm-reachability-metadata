/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_websockets_jvm

import io.ktor.utils.io.ByteChannel
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.FrameParser
import io.ktor.websocket.FrameTooBigException
import io.ktor.websocket.FrameType
import io.ktor.websocket.ProtocolViolationException
import io.ktor.websocket.RawWebSocket
import io.ktor.websocket.WebSocketDeflateExtension
import io.ktor.websocket.WebSocketExtensionHeader
import io.ktor.websocket.WebSocketExtensionsConfig
import io.ktor.websocket.WebSocketReader
import io.ktor.websocket.WebSocketWriter
import io.ktor.websocket.close
import io.ktor.websocket.parseWebSocketExtensions
import io.ktor.websocket.readBytes
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.Deflater

public class Ktor_websockets_jvmTest {
    @Test
    fun framesExposeTypedPayloadsCopyStateAndCloseReasons() {
        val text = Frame.Text("Hello, Καλημέρα")
        assertThat(text.frameType).isEqualTo(FrameType.TEXT)
        assertThat(text.fin).isTrue()
        assertThat(text.readText()).isEqualTo("Hello, Καλημέρα")
        assertThat(text.toString()).contains("TEXT", "buffer len")

        val binaryPayload = byteArrayOf(1, 2, 3, 4)
        val binary = Frame.Binary(false, binaryPayload, rsv1 = true, rsv2 = false, rsv3 = true)
        val binaryCopy = binary.copy()
        binaryPayload[0] = 99

        assertThat(binary.frameType).isEqualTo(FrameType.BINARY)
        assertThat(binary.fin).isFalse()
        assertThat(binary.rsv1).isTrue()
        assertThat(binary.rsv3).isTrue()
        assertThat(binary.readBytes()).containsExactly(99.toByte(), 2.toByte(), 3.toByte(), 4.toByte())
        assertThat(binaryCopy).isInstanceOf(Frame.Binary::class.java)
        assertThat(binaryCopy.data).containsExactly(1.toByte(), 2.toByte(), 3.toByte(), 4.toByte())
        assertThat(binaryCopy.data).isNotSameAs(binary.data)

        val reason = CloseReason(CloseReason.Codes.VIOLATED_POLICY, "policy failed")
        val close = Frame.Close(reason)
        val emptyClose = Frame.Close()

        assertThat(close.frameType.controlFrame).isTrue()
        assertThat(close.readReason()).isEqualTo(reason)
        assertThat(emptyClose.readReason()).isNull()
        assertThat(reason.knownReason).isEqualTo(CloseReason.Codes.VIOLATED_POLICY)
        assertThat(CloseReason.Codes.byCode(1000.toShort())).isEqualTo(CloseReason.Codes.NORMAL)
        assertThat(CloseReason.Codes.byCode(3999.toShort())).isNull()
        assertThat(reason.toString()).contains("VIOLATED_POLICY", "policy failed")

        val disposed = AtomicBoolean(false)
        val pongHandle = object : DisposableHandle {
            override fun dispose() {
                disposed.set(true)
            }
        }
        val ping = Frame.Ping(byteArrayOf(7, 8))
        val pong = Frame.Pong(byteArrayOf(7, 8), pongHandle)

        assertThat(ping.frameType).isEqualTo(FrameType.PING)
        assertThat(pong.frameType).isEqualTo(FrameType.PONG)
        assertThat(pong.readBytes()).containsExactly(7.toByte(), 8.toByte())
        pong.disposableHandle.dispose()
        assertThat(disposed).isTrue()
    }

    @Test
    fun frameFactoriesAndParserHandleOpcodesFragmentsMasksAndProtocolErrors() {
        assertThat(FrameType[FrameType.TEXT.opcode]).isEqualTo(FrameType.TEXT)
        assertThat(FrameType[FrameType.PONG.opcode]).isEqualTo(FrameType.PONG)
        assertThat(FrameType[0]).isNull()
        assertThat(FrameType[11]).isNull()

        val generated = Frame.byType(
            fin = false,
            frameType = FrameType.TEXT,
            data = "fragment".toByteArray(),
            rsv1 = true,
            rsv2 = false,
            rsv3 = false,
        )
        assertThat(generated).isInstanceOf(Frame.Text::class.java)
        assertThat(generated.fin).isFalse()
        assertThat(generated.rsv1).isTrue()
        assertThat(generated.readBytes()).containsExactly(*"fragment".toByteArray())

        val fragmentedParser = FrameParser()
        fragmentedParser.frame(ByteBuffer.wrap(byteArrayOf(0x01, 0x00)).order(ByteOrder.BIG_ENDIAN))
        assertThat(fragmentedParser.bodyReady).isTrue()
        assertThat(fragmentedParser.fin).isFalse()
        assertThat(fragmentedParser.frameType).isEqualTo(FrameType.TEXT)
        assertThat(fragmentedParser.length).isZero()
        fragmentedParser.bodyComplete()

        fragmentedParser.frame(ByteBuffer.wrap(byteArrayOf(0x80.toByte(), 0x00)).order(ByteOrder.BIG_ENDIAN))
        assertThat(fragmentedParser.bodyReady).isTrue()
        assertThat(fragmentedParser.fin).isTrue()
        assertThat(fragmentedParser.frameType).isEqualTo(FrameType.TEXT)
        fragmentedParser.bodyComplete()

        val maskedParser = FrameParser()
        maskedParser.frame(ByteBuffer.wrap(byteArrayOf(0x82.toByte(), 0x85.toByte())).order(ByteOrder.BIG_ENDIAN))
        assertThat(maskedParser.bodyReady).isFalse()
        maskedParser.frame(ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4)).order(ByteOrder.BIG_ENDIAN))
        assertThat(maskedParser.bodyReady).isTrue()
        assertThat(maskedParser.frameType).isEqualTo(FrameType.BINARY)
        assertThat(maskedParser.length).isEqualTo(5)
        assertThat(maskedParser.mask).isTrue()
        assertThat(maskedParser.maskKey).isEqualTo(0x01020304)
        maskedParser.bodyComplete()

        val extendedLengthParser = FrameParser()
        extendedLengthParser.frame(
            ByteBuffer.wrap(byteArrayOf(0x81.toByte(), 126, 0, 130.toByte())).order(ByteOrder.BIG_ENDIAN),
        )
        assertThat(extendedLengthParser.bodyReady).isTrue()
        assertThat(extendedLengthParser.length).isEqualTo(130)
        assertThat(extendedLengthParser.frameType).isEqualTo(FrameType.TEXT)

        assertThatThrownBy {
            FrameParser().frame(ByteBuffer.wrap(byteArrayOf(0x89.toByte(), 126, 0, 126)).order(ByteOrder.BIG_ENDIAN))
        }.isInstanceOf(ProtocolViolationException::class.java)

        assertThatThrownBy {
            FrameParser().frame(ByteBuffer.wrap(byteArrayOf(0x00, 0x00)).order(ByteOrder.BIG_ENDIAN))
        }.isInstanceOf(ProtocolViolationException::class.java)
    }

    @Test
    fun extensionHeadersAndConfigurationParseRenderAndBuildDeflateExtension() {
        val headers = parseWebSocketExtensions(
            "permessage-deflate; client_no_context_takeover; server_max_window_bits=15, " +
                "x-custom; flag; key=value; empty=",
        )

        assertThat(headers).hasSize(2)
        assertThat(headers[0].name).isEqualTo("permessage-deflate")
        assertThat(headers[0].parameters).containsExactly("client_no_context_takeover", "server_max_window_bits=15")
        assertThat(headers[0].parseParameters().toList()).containsExactly(
            "client_no_context_takeover" to "",
            "server_max_window_bits" to "15",
        )
        assertThat(headers[0].toString()).isEqualTo(
            "permessage-deflate ; client_no_context_takeover;server_max_window_bits=15",
        )
        assertThat(headers[1].parseParameters().toList()).containsExactly(
            "flag" to "",
            "key" to "value",
            "empty" to "",
        )

        val config = WebSocketExtensionsConfig()
        config.install(WebSocketDeflateExtension) {
            clientNoContextTakeOver = true
            serverNoContextTakeOver = true
            compressionLevel = Deflater.BEST_SPEED
            compressIfBiggerThan(16)
            configureProtocols { protocols ->
                protocols += WebSocketExtensionHeader("x-test-extension", listOf("mode=metadata"))
            }
        }

        val extensions = config.build()
        val deflateExtension = extensions.single { extension -> extension is WebSocketDeflateExtension }
            as WebSocketDeflateExtension

        assertThat(deflateExtension.factory.key).isSameAs(WebSocketDeflateExtension.key)
        assertThat(deflateExtension.protocols).hasSize(2)
        assertThat(deflateExtension.protocols[0].name).isEqualTo("permessage-deflate")
        assertThat(deflateExtension.protocols[0].parameters).containsExactly(
            "client_no_context_takeover",
            "server_no_context_takeover",
        )
        assertThat(deflateExtension.protocols[1].toString()).isEqualTo("x-test-extension ; mode=metadata")
    }

    @Test
    fun deflateExtensionNegotiatesAndRoundTripsTextAndBinaryFrames() {
        val client = WebSocketDeflateExtension.install {
            clientNoContextTakeOver = true
            serverNoContextTakeOver = true
            compressionLevel = Deflater.BEST_SPEED
        }
        val server = WebSocketDeflateExtension.install {
            compressionLevel = Deflater.BEST_SPEED
        }

        val negotiated = server.serverNegotiation(client.protocols)
        assertThat(negotiated).hasSize(1)
        assertThat(negotiated[0].name).isEqualTo("permessage-deflate")
        assertThat(negotiated[0].parameters).containsExactly(
            "client_no_context_takeover",
            "server_no_context_takeover",
        )
        assertThat(client.clientNegotiation(negotiated)).isTrue()
        assertThat(client.clientNegotiation(listOf(WebSocketExtensionHeader("unknown", emptyList())))).isFalse()

        val textPayload = "Ktor websocket compression ".repeat(32)
        val compressedText = client.processOutgoingFrame(Frame.Text(textPayload))
        assertThat(compressedText).isInstanceOf(Frame.Text::class.java)
        assertThat(compressedText.rsv1).isTrue()
        assertThat(compressedText.data.size).isLessThan(textPayload.toByteArray().size)

        val inflatedText = server.processIncomingFrame(compressedText) as Frame.Text
        assertThat(inflatedText.rsv1).isFalse()
        assertThat(inflatedText.readText()).isEqualTo(textPayload)

        val binaryPayload = ByteArray(256) { index -> (index % 13).toByte() }
        val compressedBinary = client.processOutgoingFrame(Frame.Binary(true, binaryPayload))
        val inflatedBinary = server.processIncomingFrame(compressedBinary)
        assertThat(inflatedBinary).isInstanceOf(Frame.Binary::class.java)
        assertThat(inflatedBinary.readBytes()).containsExactly(*binaryPayload)

        val thresholdExtension = WebSocketDeflateExtension.install { compressIfBiggerThan(1_000) }
        val smallText = Frame.Text("small")
        val ping = Frame.Ping(byteArrayOf(1, 2, 3))
        assertThat(thresholdExtension.processOutgoingFrame(smallText)).isSameAs(smallText)
        assertThat(thresholdExtension.processOutgoingFrame(ping)).isSameAs(ping)
    }

    @Test
    fun webSocketReaderRejectsFramesLargerThanConfiguredMaximum() = runBlocking {
        val channel = ByteChannel(autoFlush = true)
        val writer = WebSocketWriter(channel, coroutineContext, false)
        val reader = WebSocketReader(channel, coroutineContext, 4)
        val payload = "exceeds"

        try {
            writer.send(Frame.Text(payload))
            writer.flush()

            val failure = runCatching {
                withTimeout(5_000) { reader.incoming.receive() }
            }.exceptionOrNull()

            assertThat(failure).isInstanceOf(FrameTooBigException::class.java)
            assertThat((failure as FrameTooBigException).frameSize).isEqualTo(payload.toByteArray().size.toLong())
        } finally {
            writer.outgoing.close()
            channel.close()
        }
    }

    @Test
    fun rawWebSocketSessionsExchangeMaskedFramesAndCloseHandshake() = runBlocking {
        val clientToServer = ByteChannel(autoFlush = true)
        val serverToClient = ByteChannel(autoFlush = true)
        val client = RawWebSocket(
            input = serverToClient,
            output = clientToServer,
            maxFrameSize = 4096,
            masking = true,
            coroutineContext = coroutineContext,
        )
        val server = RawWebSocket(
            input = clientToServer,
            output = serverToClient,
            maxFrameSize = 4096,
            masking = false,
            coroutineContext = coroutineContext,
        )

        try {
            client.send("client text")
            val text = withTimeout(5_000) { server.incoming.receive() } as Frame.Text
            assertThat(text.readText()).isEqualTo("client text")

            val binaryPayload = byteArrayOf(9, 8, 7, 6)
            server.send(binaryPayload)
            val binary = withTimeout(5_000) { client.incoming.receive() } as Frame.Binary
            assertThat(binary.readBytes()).containsExactly(*binaryPayload)

            client.outgoing.send(Frame.Ping(byteArrayOf(4, 5, 6)))
            val ping = withTimeout(5_000) { server.incoming.receive() } as Frame.Ping
            assertThat(ping.readBytes()).containsExactly(4.toByte(), 5.toByte(), 6.toByte())

            server.outgoing.send(Frame.Pong(ping.readBytes()))
            val pong = withTimeout(5_000) { client.incoming.receive() } as Frame.Pong
            assertThat(pong.readBytes()).containsExactly(4.toByte(), 5.toByte(), 6.toByte())

            val closeReason = CloseReason(CloseReason.Codes.NORMAL, "done")
            client.close(closeReason)
            val close = withTimeout(5_000) { server.incoming.receive() } as Frame.Close
            assertThat(close.readReason()).isEqualTo(closeReason)
            server.close(closeReason)
        } finally {
            client.cancel()
            server.cancel()
            clientToServer.close()
            serverToClient.close()
        }
    }
}
