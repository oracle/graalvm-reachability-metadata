/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_websockets_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.util.AttributeKey
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.FrameType
import io.ktor.websocket.WebSocketExtension
import io.ktor.websocket.WebSocketExtensionFactory
import io.ktor.websocket.WebSocketExtensionHeader
import io.ktor.websocket.close
import io.ktor.websocket.extensionOrNull
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Collections
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Covers application-defined extension negotiation in the plugin's upgrade: a game server
 * installs its own `x-scramble` extension factory, which the upgrade negotiates per session from
 * the client's `Sec-WebSocket-Extensions` offer and which then rewrites every data frame on the
 * wire under the RSV2 bit, while an offer the server rejects leaves the session plain.
 */
class CustomExtensionNegotiationTest {

    /** One data frame as the extension saw it on the wire, before it was unscrambled. */
    private data class WireFrame(val type: FrameType, val rsv2: Boolean, val payload: ByteArray)

    /**
     * XORs every data frame payload with a one-byte key agreed during the handshake and flags the
     * scrambled frames with RSV2; one instance is built per session from the installed config.
     */
    private class Scramble(private val config: Config) : WebSocketExtension<Scramble.Config> {

        /** The key a client offers; the server accepts whatever valid key it is offered. */
        class Config {
            var key: Int = 0
        }

        /** The data frames that reached this session still scrambled. */
        val wireFrames: MutableList<WireFrame> = Collections.synchronizedList(mutableListOf())

        /** The key both sides agreed on during the handshake. */
        var activeKey: Int = config.key
            private set

        override val factory: WebSocketExtensionFactory<Config, Scramble> = Scramble

        override val protocols: List<WebSocketExtensionHeader>
            get() = listOf(WebSocketExtensionHeader(NAME, listOf("key=${config.key}")))

        override fun clientNegotiation(negotiatedProtocols: List<WebSocketExtensionHeader>): Boolean {
            val accepted: Int = negotiatedProtocols.firstNotNullOfOrNull(::keyOf) ?: return false
            return accepted == config.key
        }

        override fun serverNegotiation(
            requestedProtocols: List<WebSocketExtensionHeader>,
        ): List<WebSocketExtensionHeader> {
            val offered: Int = requestedProtocols.firstNotNullOfOrNull(::keyOf) ?: return emptyList()
            if (offered !in VALID_KEYS) {
                return emptyList()
            }
            activeKey = offered
            return listOf(WebSocketExtensionHeader(NAME, listOf("key=$offered")))
        }

        override fun processOutgoingFrame(frame: Frame): Frame {
            if (!frame.isData()) {
                return frame
            }
            return Frame.byType(frame.fin, frame.frameType, xor(frame.data), frame.rsv1, true, frame.rsv3)
        }

        override fun processIncomingFrame(frame: Frame): Frame {
            if (!frame.isData() || !frame.rsv2) {
                return frame
            }
            wireFrames += WireFrame(frame.frameType, frame.rsv2, frame.data.copyOf())
            return Frame.byType(frame.fin, frame.frameType, xor(frame.data), frame.rsv1, false, frame.rsv3)
        }

        private fun Frame.isData(): Boolean = frameType == FrameType.TEXT || frameType == FrameType.BINARY

        private fun xor(data: ByteArray): ByteArray =
            ByteArray(data.size) { index: Int -> (data[index].toInt() xor activeKey).toByte() }

        /** Reads the `key` parameter of an `x-scramble` header, or null for any other header. */
        private fun keyOf(header: WebSocketExtensionHeader): Int? {
            if (header.name != NAME) {
                return null
            }
            return header.parseParameters()
                .firstOrNull { (name: String, _: String) -> name == "key" }
                ?.second
                ?.toIntOrNull()
        }

        companion object : WebSocketExtensionFactory<Config, Scramble> {

            const val NAME: String = "x-scramble"

            val VALID_KEYS: IntRange = 1..255

            override val key: AttributeKey<Scramble> = AttributeKey(NAME)

            override val rsv1: Boolean = false

            override val rsv2: Boolean = true

            override val rsv3: Boolean = false

            override fun install(config: Config.() -> Unit): Scramble = Scramble(Config().apply(config))
        }
    }

    @Test
    fun negotiatesTheOfferedKeyAndScramblesFramesBothWays(): Unit = withGameServer { port: Int ->
        val client: HttpClient = scrambleClient(SESSION_KEY)
        val move: String = "move e2 e4"
        val snapshot: ByteArray = byteArrayOf(1, 2, 3, 4, 5)
        var negotiatedHeader: String? = null
        var clientExtension: Scramble? = null
        val replies: MutableList<String> = mutableListOf()
        var mirrored: ByteArray = ByteArray(0)

        client.webSocket(host = "127.0.0.1", port = port, path = "/moves") {
            negotiatedHeader = call.response.headers[HttpHeaders.SecWebSocketExtensions]
            clientExtension = extensionOrNull(Scramble)
            replies += (incoming.receive() as Frame.Text).readText()
            send(Frame.Text(move))
            replies += (incoming.receive() as Frame.Text).readText()
            send(Frame.Binary(true, snapshot))
            mirrored = (incoming.receive() as Frame.Binary).readBytes()
            send(Frame.Text("stats"))
            replies += (incoming.receive() as Frame.Text).readText()
            close(CloseReason(CloseReason.Codes.NORMAL, "game over"))
        }
        client.close()

        assertNotNull(negotiatedHeader, "the upgrade should answer the x-scramble offer")
        assertTrue(negotiatedHeader!!.startsWith("x-scramble"), negotiatedHeader)
        assertTrue(negotiatedHeader!!.contains("key=$SESSION_KEY"), negotiatedHeader)
        assertEquals(
            listOf("scramble=on key=$SESSION_KEY", "ack $move", "server unscrambled 3 frames: TEXT,BINARY,TEXT"),
            replies,
        )
        assertArrayEquals(snapshot.reversedArray(), mirrored)

        assertNotNull(clientExtension, "the client should keep the extension")
        val extension: Scramble = clientExtension!!
        assertEquals(SESSION_KEY, extension.activeKey)
        val wire: List<WireFrame> = extension.wireFrames.toList()
        assertEquals(listOf(FrameType.TEXT, FrameType.TEXT, FrameType.BINARY, FrameType.TEXT), wire.map { it.type })
        assertTrue(wire.all { it.rsv2 }, "every server data frame should carry RSV2")
        val firstPlain: ByteArray = "scramble=on key=$SESSION_KEY".toByteArray()
        assertFalse(firstPlain.contentEquals(wire.first().payload), "the wire payload should be scrambled")
        assertArrayEquals(
            firstPlain,
            ByteArray(firstPlain.size) { index: Int -> (wire.first().payload[index].toInt() xor SESSION_KEY).toByte() },
        )
    }

    @Test
    fun keepsTheSessionPlainWhenTheOfferIsRejected(): Unit = withGameServer { port: Int ->
        val client: HttpClient = scrambleClient(0)
        var negotiatedHeader: String? = "unset"
        var clientHasExtension = true
        val replies: MutableList<String> = mutableListOf()

        client.webSocket(host = "127.0.0.1", port = port, path = "/moves") {
            negotiatedHeader = call.response.headers[HttpHeaders.SecWebSocketExtensions]
            clientHasExtension = extensionOrNull(Scramble) != null
            replies += (incoming.receive() as Frame.Text).readText()
            send(Frame.Text("move d2 d4"))
            replies += (incoming.receive() as Frame.Text).readText()
            send(Frame.Text("stats"))
            replies += (incoming.receive() as Frame.Text).readText()
            close(CloseReason(CloseReason.Codes.NORMAL, "game over"))
        }
        client.close()

        assertNull(negotiatedHeader, "a rejected offer should not be answered")
        assertFalse(clientHasExtension, "the client should drop an extension the server declined")
        assertEquals(listOf("scramble=off", "ack move d2 d4", "server unscrambled 0 frames: "), replies)
    }

    private fun scrambleClient(offeredKey: Int): HttpClient = HttpClient(ClientCIO) {
        install(ClientWebSockets) {
            extensions {
                install(Scramble) {
                    key = offeredKey
                }
            }
        }
    }

    /** Starts the game server on an ephemeral port, runs [block] against it, and stops it. */
    private fun withGameServer(block: suspend (Int) -> Unit) {
        val server: EmbeddedServer<*, *> = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            gameServer()
        }
        server.start(wait = false)
        try {
            runBlocking {
                val port: Int = server.engine.resolvedConnectors().first().port
                block(port)
            }
        } finally {
            server.stop(gracePeriodMillis = 0, timeoutMillis = STOP_TIMEOUT_MILLIS)
        }
    }

    private fun Application.gameServer() {
        install(WebSockets) {
            extensions {
                install(Scramble)
            }
        }
        routing {
            webSocket("/moves") {
                val scramble: Scramble? = extensionOrNull(Scramble)
                val greeting: String = if (scramble == null) {
                    "scramble=off"
                } else {
                    "scramble=on key=${scramble.activeKey}"
                }
                send(Frame.Text(greeting))
                for (frame: Frame in incoming) {
                    when (frame) {
                        is Frame.Binary -> send(Frame.Binary(true, frame.readBytes().reversedArray()))
                        is Frame.Text -> {
                            val text: String = frame.readText()
                            if (text == "stats") {
                                val seen: List<WireFrame> = scramble?.wireFrames?.toList() ?: emptyList()
                                val types: String = seen.joinToString(",") { it.type.name }
                                send(Frame.Text("server unscrambled ${seen.size} frames: $types"))
                            } else {
                                send(Frame.Text("ack $text"))
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    private companion object {

        const val SESSION_KEY: Int = 0x2A

        const val STOP_TIMEOUT_MILLIS: Long = 5_000L
    }
}
