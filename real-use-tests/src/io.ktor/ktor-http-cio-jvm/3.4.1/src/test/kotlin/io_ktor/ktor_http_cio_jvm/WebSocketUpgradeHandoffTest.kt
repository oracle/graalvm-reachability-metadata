/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_http_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSocketException
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.cio.Request
import io.ktor.http.cio.RequestResponseBuilder
import io.ktor.http.cio.expectHttpUpgrade
import io.ktor.http.cio.parseRequest
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writePacket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.RawWebSocket
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.Source
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.Base64

/**
 * Covers the HTTP/1.1 protocol-upgrade handoff: a chat gateway built on the CIO parser accepts a
 * WebSocket upgrade with `101 Switching Protocols`, negotiates a subprotocol, and hands the same
 * connection to a WebSocket session, while refusing upgrades it does not serve and plain requests.
 */
class WebSocketUpgradeHandoffTest {

    /** What the gateway saw in one upgrade or plain request head. */
    private data class Handshake(
        val target: String,
        val upgrade: Boolean,
        val key: String?,
        val websocketVersion: String?,
        val offeredProtocols: List<String>,
    )

    /**
     * A chat gateway: `/chat` upgrades to WebSocket and echoes each message in upper case; any other
     * upgrade target is refused with `403`, and a request that does not upgrade gets `426`.
     */
    private class ChatGateway(private val server: ServerSocket, scope: CoroutineScope) {

        val handshakes: Channel<Handshake> = Channel(Channel.UNLIMITED)

        val messages: Channel<String> = Channel(Channel.UNLIMITED)

        val port: Int = (server.localAddress as InetSocketAddress).port

        private val acceptor: Job = scope.launch {
            while (true) {
                val socket: Socket = server.accept()
                launch { serve(socket) }
            }
        }

        fun stop() {
            acceptor.cancel()
            server.close()
        }

        private suspend fun serve(socket: Socket) {
            val input: ByteReadChannel = socket.openReadChannel()
            val output: ByteWriteChannel = socket.openWriteChannel(autoFlush = true)
            try {
                val request: Request = parseRequest(input) ?: return
                val handshake: Handshake = request.use { describe(it) }
                handshakes.send(handshake)
                when {
                    !handshake.upgrade -> {
                        reject(output, HttpStatusCode.UpgradeRequired, "this endpoint only speaks websocket")
                    }
                    handshake.target != "/chat" -> {
                        reject(output, HttpStatusCode.Forbidden, "no websocket endpoint at ${handshake.target}")
                    }
                    else -> {
                        val key: String = requireNotNull(handshake.key) { "upgrade without Sec-WebSocket-Key" }
                        val protocol: String? = handshake.offeredProtocols.firstOrNull { it in SUPPORTED_PROTOCOLS }
                        switchProtocols(output, key, protocol)
                        chat(RawWebSocket(input, output, masking = false, coroutineContext = currentCoroutineContext()))
                    }
                }
            } finally {
                output.flushAndClose()
                socket.close()
            }
        }

        private fun describe(request: Request): Handshake = Handshake(
            target = request.uri.toString(),
            upgrade = expectHttpUpgrade(request) &&
                request.headers[HttpHeaders.Upgrade]?.toString().equals("websocket", ignoreCase = true),
            key = request.headers[HttpHeaders.SecWebSocketKey]?.toString(),
            websocketVersion = request.headers[HttpHeaders.SecWebSocketVersion]?.toString(),
            offeredProtocols = request.headers[HttpHeaders.SecWebSocketProtocol]?.toString()
                ?.split(',')?.map { it.trim() }.orEmpty(),
        )

        private suspend fun chat(session: WebSocketSession) {
            try {
                for (frame: Frame in session.incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text: String = frame.readText()
                            messages.send(text)
                            session.send(Frame.Text(text.uppercase()))
                        }
                        is Frame.Close -> {
                            val reason: CloseReason = frame.readReason() ?: CloseReason(CloseReason.Codes.NORMAL, "")
                            session.send(Frame.Close(reason))
                            session.flush()
                            break
                        }
                        else -> {}
                    }
                }
            } finally {
                session.cancel()
            }
        }

        private suspend fun switchProtocols(output: ByteWriteChannel, key: String, protocol: String?) {
            val head = RequestResponseBuilder()
            try {
                head.responseLine("HTTP/1.1", HttpStatusCode.SwitchingProtocols.value, "Switching Protocols")
                head.headerLine(HttpHeaders.Upgrade, "websocket")
                head.headerLine(HttpHeaders.Connection, "Upgrade")
                head.headerLine(HttpHeaders.SecWebSocketAccept, accept(key))
                if (protocol != null) {
                    head.headerLine(HttpHeaders.SecWebSocketProtocol, protocol)
                }
                head.emptyLine()
                val packet: Source = head.build()
                output.writePacket(packet)
            } finally {
                head.release()
            }
            output.flush()
        }

        private suspend fun reject(output: ByteWriteChannel, status: HttpStatusCode, message: String) {
            val bytes: ByteArray = message.encodeToByteArray()
            val head = RequestResponseBuilder()
            try {
                head.responseLine("HTTP/1.1", status.value, status.description)
                if (status == HttpStatusCode.UpgradeRequired) {
                    head.headerLine(HttpHeaders.Upgrade, "websocket")
                }
                head.headerLine(HttpHeaders.Connection, "close")
                head.headerLine(HttpHeaders.ContentType, "text/plain")
                head.headerLine(HttpHeaders.ContentLength, bytes.size.toString())
                head.emptyLine()
                head.bytes(bytes, 0, bytes.size)
                val packet: Source = head.build()
                output.writePacket(packet)
            } finally {
                head.release()
            }
            output.flush()
        }
    }

    @Test
    fun upgradedConnectionCarriesTheChatAfterSwitchingProtocols(): Unit = runBlocking {
        withChatGateway { gateway: ChatGateway, client: HttpClient ->
            val replies: MutableList<String> = mutableListOf()
            var negotiated: String? = null
            var closedWith: CloseReason? = null

            client.webSocket(
                host = "127.0.0.1",
                port = gateway.port,
                path = "/chat",
                request = { header(HttpHeaders.SecWebSocketProtocol, "chat.v3, chat.v1") },
            ) {
                negotiated = call.response.headers[HttpHeaders.SecWebSocketProtocol]
                for (message: String in listOf("hello gateway", "order 1187 shipped")) {
                    send(Frame.Text(message))
                    replies += (incoming.receive() as Frame.Text).readText()
                }
                close(CloseReason(CloseReason.Codes.NORMAL, "client done"))
                closedWith = closeReason.await()
            }

            val handshake: Handshake = gateway.handshakes.receive()
            assertEquals("/chat", handshake.target)
            assertTrue(handshake.upgrade)
            assertEquals("13", handshake.websocketVersion)
            assertEquals(listOf("chat.v3", "chat.v1"), handshake.offeredProtocols)

            assertEquals("chat.v1", negotiated)
            assertEquals(listOf("HELLO GATEWAY", "ORDER 1187 SHIPPED"), replies)
            assertEquals("hello gateway", gateway.messages.receive())
            assertEquals("order 1187 shipped", gateway.messages.receive())
            val reason: CloseReason = requireNotNull(closedWith) { "the gateway never answered the close" }
            assertEquals(CloseReason.Codes.NORMAL, reason.knownReason)
        }
    }

    @Test
    fun refusesUpgradesItDoesNotServeAndPlainRequests(): Unit = runBlocking {
        withChatGateway { gateway: ChatGateway, client: HttpClient ->
            val failure: Throwable? = runCatching {
                client.webSocket(host = "127.0.0.1", port = gateway.port, path = "/admin") {}
            }.exceptionOrNull()
            val refused: WebSocketException = assertInstanceOf(WebSocketException::class.java, failure)
            assertTrue(refused.message.orEmpty().contains("403"), refused.message)
            val adminHandshake: Handshake = gateway.handshakes.receive()
            assertEquals("/admin", adminHandshake.target)
            assertTrue(adminHandshake.upgrade)

            val plain: HttpResponse = client.get("http://127.0.0.1:${gateway.port}/chat")
            assertEquals(HttpStatusCode.UpgradeRequired, plain.status)
            assertEquals("websocket", plain.headers[HttpHeaders.Upgrade])
            assertEquals("this endpoint only speaks websocket", plain.bodyAsText())
            val plainHandshake: Handshake = gateway.handshakes.receive()
            assertEquals("/chat", plainHandshake.target)
            assertFalse(plainHandshake.upgrade)
        }
    }

    private companion object {

        val SUPPORTED_PROTOCOLS: Set<String> = setOf("chat.v1", "chat.v2")

        const val WEBSOCKET_GUID: String = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

        /** Computes `Sec-WebSocket-Accept` for a client key as RFC 6455 section 4.2.2 defines it. */
        fun accept(key: String): String {
            val digest: ByteArray = MessageDigest.getInstance("SHA-1").digest((key + WEBSOCKET_GUID).toByteArray())
            return Base64.getEncoder().encodeToString(digest)
        }

        suspend fun withChatGateway(block: suspend (ChatGateway, HttpClient) -> Unit) {
            val selector = SelectorManager(Dispatchers.IO)
            val client = HttpClient(CIO) { install(WebSockets) }
            try {
                withTimeout(30_000) {
                    val gateway = ChatGateway(aSocket(selector).tcp().bind("127.0.0.1", 0), this)
                    try {
                        block(gateway, client)
                    } finally {
                        gateway.stop()
                    }
                }
            } finally {
                client.close()
                selector.close()
            }
        }
    }
}
