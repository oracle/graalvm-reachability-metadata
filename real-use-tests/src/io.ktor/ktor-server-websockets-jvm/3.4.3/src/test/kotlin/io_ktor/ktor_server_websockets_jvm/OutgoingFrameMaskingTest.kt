/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_websockets_jvm

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.OutputStream
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Covers outgoing frame masking in the plugin's sessions: a quote feed deployed behind an
 * intermediary that only forwards masked frames turns on the plugin's `masking` option, while
 * its direct route switches masking off per session, and a wire-level client checks both.
 */
class OutgoingFrameMaskingTest {

    /** One frame as it arrived on the wire, before and after removing its mask. */
    private class WireFrame(
        val opcode: Int,
        val masked: Boolean,
        val rawPayload: ByteArray,
        val payload: ByteArray,
    )

    /** A minimal RFC 6455 client over a plain socket, so the frame header bits stay visible. */
    private class WireClient(port: Int, path: String) : AutoCloseable {

        private val socket = Socket("127.0.0.1", port)

        private val input = DataInputStream(socket.getInputStream())

        private val output: OutputStream = socket.getOutputStream()

        init {
            socket.soTimeout = READ_TIMEOUT_MILLIS
            val key: String = Base64.getEncoder().encodeToString("quote-feed-key16".encodeToByteArray())
            val request: String = "GET $path HTTP/1.1\r\n" +
                "Host: 127.0.0.1:$port\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: $key\r\n" +
                "Sec-WebSocket-Version: 13\r\n\r\n"
            output.write(request.encodeToByteArray())
            output.flush()
            val headers: List<String> = readHeaderLines()
            assertEquals("HTTP/1.1 101 Switching Protocols", headers.first())
            val accept: String? = headers.firstOrNull { it.startsWith("Sec-WebSocket-Accept:", ignoreCase = true) }
                ?.substringAfter(':')
                ?.trim()
            assertEquals(expectedAccept(key), accept)
        }

        fun sendText(text: String) {
            writeMaskedFrame(OPCODE_TEXT, text.encodeToByteArray())
        }

        fun sendClose(code: Short, message: String) {
            val body = ByteArrayOutputStream()
            body.write(code.toInt() shr 8 and 0xFF)
            body.write(code.toInt() and 0xFF)
            body.write(message.encodeToByteArray())
            writeMaskedFrame(OPCODE_CLOSE, body.toByteArray())
        }

        fun readFrame(): WireFrame {
            val first: Int = input.readUnsignedByte()
            val second: Int = input.readUnsignedByte()
            assertTrue(first and 0x80 != 0, "quotes are sent unfragmented")
            val masked: Boolean = second and 0x80 != 0
            val length: Int = when (val shortLength: Int = second and 0x7F) {
                126 -> input.readUnsignedShort()
                127 -> input.readLong().toInt()
                else -> shortLength
            }
            val mask = ByteArray(if (masked) 4 else 0)
            input.readFully(mask)
            val rawPayload = ByteArray(length)
            input.readFully(rawPayload)
            val payload: ByteArray = if (masked) applyMask(rawPayload, mask) else rawPayload.copyOf()
            return WireFrame(first and 0x0F, masked, rawPayload, payload)
        }

        override fun close() {
            socket.close()
        }

        private fun writeMaskedFrame(opcode: Int, payload: ByteArray) {
            assertTrue(payload.size < 126, "client frames stay below the extended length forms")
            val mask: ByteArray = byteArrayOf(0x1F, 0x2E, 0x3D, 0x4C)
            output.write(0x80 or opcode)
            output.write(0x80 or payload.size)
            output.write(mask)
            output.write(applyMask(payload, mask))
            output.flush()
        }

        private fun readHeaderLines(): List<String> {
            val lines: MutableList<String> = mutableListOf()
            val line = StringBuilder()
            while (true) {
                val next: Int = input.readUnsignedByte()
                if (next == '\n'.code) {
                    val complete: String = line.toString().trimEnd('\r')
                    if (complete.isEmpty()) {
                        return lines
                    }
                    lines += complete
                    line.clear()
                } else {
                    line.append(next.toChar())
                }
            }
        }

        private fun applyMask(bytes: ByteArray, mask: ByteArray): ByteArray =
            ByteArray(bytes.size) { index: Int -> (bytes[index].toInt() xor mask[index % 4].toInt()).toByte() }

        private fun expectedAccept(key: String): String {
            val digest: ByteArray = MessageDigest.getInstance("SHA-1")
                .digest("$key$WEBSOCKET_GUID".encodeToByteArray())
            return Base64.getEncoder().encodeToString(digest)
        }
    }

    @Test
    fun masksEveryQuoteSentThroughTheIntermediaryRoute(): Unit = withQuoteFeed { port: Int ->
        WireClient(port, "/quotes").use { client: WireClient ->
            client.sendText("subscribe ACME,GLOBEX")
            val quotes: List<WireFrame> = listOf(client.readFrame(), client.readFrame())

            assertTrue(quotes.all { it.opcode == OPCODE_TEXT && it.masked })
            assertEquals(
                listOf("ACME 101.25", "GLOBEX 47.50"),
                quotes.map { it.payload.decodeToString() },
            )

            client.sendClose(CloseReason.Codes.NORMAL.code, "market closed")
            val close: WireFrame = client.readFrame()
            assertEquals(OPCODE_CLOSE, close.opcode)
            assertTrue(close.masked)
            assertEquals(CloseReason.Codes.NORMAL.code.toInt(), closeCode(close.payload))
        }
    }

    @Test
    fun sendsPlainQuotesWhenTheDirectRouteSwitchesMaskingOff(): Unit = withQuoteFeed { port: Int ->
        WireClient(port, "/quotes/direct").use { client: WireClient ->
            client.sendText("subscribe INITECH")
            val greeting: WireFrame = client.readFrame()
            val quote: WireFrame = client.readFrame()

            assertFalse(greeting.masked)
            assertEquals("masking was on, now off", greeting.payload.decodeToString())
            assertFalse(quote.masked)
            assertEquals("INITECH 12.75", quote.rawPayload.decodeToString())

            val close: WireFrame = client.readFrame()
            assertEquals(OPCODE_CLOSE, close.opcode)
            assertFalse(close.masked)
            assertEquals(CloseReason.Codes.NORMAL.code.toInt(), closeCode(close.payload))
            assertEquals("snapshot sent", close.payload.copyOfRange(2, close.payload.size).decodeToString())
        }
    }

    private fun closeCode(payload: ByteArray): Int =
        (payload[0].toInt() and 0xFF shl 8) or (payload[1].toInt() and 0xFF)

    /** Starts the quote feed on an ephemeral port, runs [block] against it, and stops it. */
    private fun withQuoteFeed(block: (Int) -> Unit) {
        val server: EmbeddedServer<*, *> = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            quoteFeed()
        }
        server.start(wait = false)
        try {
            val port: Int = runBlocking { server.engine.resolvedConnectors().first().port }
            block(port)
        } finally {
            server.stop(gracePeriodMillis = 0, timeoutMillis = STOP_TIMEOUT_MILLIS)
        }
    }

    private fun Application.quoteFeed() {
        install(WebSockets) {
            masking = true
        }
        routing {
            webSocket("/quotes") {
                val symbols: List<String> = subscribedSymbols(incoming.receive())
                for (symbol: String in symbols) {
                    send(Frame.Text("$symbol ${PRICES.getValue(symbol)}"))
                }
                for (frame: Frame in incoming) {
                    // Drain until the client's close frame ends the session.
                }
            }
            webSocket("/quotes/direct") {
                val wasMasking: Boolean = masking
                masking = false
                val symbols: List<String> = subscribedSymbols(incoming.receive())
                send(Frame.Text("masking was ${if (wasMasking) "on" else "off"}, now ${if (masking) "on" else "off"}"))
                for (symbol: String in symbols) {
                    send(Frame.Text("$symbol ${PRICES.getValue(symbol)}"))
                }
                close(CloseReason(CloseReason.Codes.NORMAL, "snapshot sent"))
            }
        }
    }

    private fun subscribedSymbols(frame: Frame): List<String> =
        (frame as Frame.Text).readText().removePrefix("subscribe ").split(',')

    private companion object {

        const val OPCODE_TEXT: Int = 0x1

        const val OPCODE_CLOSE: Int = 0x8

        const val READ_TIMEOUT_MILLIS: Int = 10_000

        const val STOP_TIMEOUT_MILLIS: Long = 5_000L

        const val WEBSOCKET_GUID: String = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

        val PRICES: Map<String, String> = mapOf(
            "ACME" to "101.25",
            "GLOBEX" to "47.50",
            "INITECH" to "12.75",
        )
    }
}
