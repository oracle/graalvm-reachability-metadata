/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_logging_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.LoggingFormat
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.Base64
import java.util.Collections
import java.util.concurrent.TimeUnit
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.websocket.WebSockets as ServerWebSockets

/**
 * Logging of WebSocket upgrade handshakes in the `Logging` client plugin.
 *
 * A chat client joins a room on an embedded CIO server through the client `WebSockets`
 * plugin while its wire log runs at the `ALL` level. The log must record the upgrade as an
 * ordinary HTTP exchange: the `ws://` request with its upgrade headers and the
 * `101 Switching Protocols` response whose `Sec-WebSocket-Accept` answers the logged key.
 * The chat itself must stay out of the log: no message the client sends or receives is
 * written, in either layout, and the client still receives every reply in order.
 */
class ChatRoomWebSocketHandshakeLoggingTest {

    /** A wire log that keeps every call-log message, as an application log appender would. */
    class ChatLog : Logger {
        private val entries: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override fun log(message: String) {
            entries += message
        }

        /** Polls until a message starts with [prefix], bounded by [timeoutMillis], and returns all messages. */
        fun awaitMessage(prefix: String, timeoutMillis: Long): List<String> {
            val deadline: Long = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
            var current: List<String> = entries.toList()
            while (current.none { it.startsWith(prefix) } && System.nanoTime() < deadline) {
                Thread.onSpinWait()
                current = entries.toList()
            }
            return current
        }
    }

    private val chatLines: List<String> = listOf("alice: hi all", "alice: lunch at noon?")

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private lateinit var roomUrl: String

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(ServerWebSockets)
            routing {
                webSocket("/rooms/lobby") {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            send(Frame.Text("lobby <- " + frame.readText()))
                        }
                    }
                }
            }
        }.start(wait = false)
        val port: Int = runBlocking { server.engine.resolvedConnectors().first().port }
        roomUrl = "ws://127.0.0.1:$port/rooms/lobby"
    }

    @AfterEach
    fun stopServer() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    /** Joins the lobby through a client logging into [log], chats, and returns the replies received. */
    private fun chat(log: ChatLog, layout: LoggingFormat): List<String> {
        val client = HttpClient(ClientCIO) {
            install(WebSockets)
            install(Logging) {
                logger = log
                level = LogLevel.ALL
                format = layout
            }
        }
        return client.use {
            runBlocking {
                val replies: MutableList<String> = mutableListOf()
                it.webSocket(roomUrl) {
                    assertEquals(HttpStatusCode.SwitchingProtocols, call.response.status)
                    for (message in chatLines) {
                        send(Frame.Text(message))
                        replies += (incoming.receive() as Frame.Text).readText()
                    }
                    close(CloseReason(CloseReason.Codes.NORMAL, "bye"))
                }
                replies
            }
        }
    }

    @Test
    fun defaultLayoutLogsTheUpgradeHandshake() {
        val log = ChatLog()

        val replies: List<String> = chat(log, LoggingFormat.Default)
        assertEquals(chatLines.map { "lobby <- $it" }, replies, "the client receives every reply in order")

        val messages: List<String> = log.awaitMessage("RESPONSE: ", timeoutMillis = 10_000)
        val everything: String = messages.joinToString("\n----\n")
        val request: List<String> = messages.first { it.startsWith("REQUEST: ") }.lines()
        assertEquals("REQUEST: $roomUrl", request.first(), "the request keeps the ws scheme:\n$everything")
        assertTrue(request.contains("METHOD: GET"), everything)
        assertTrue(request.contains("-> Upgrade: websocket"), "the upgrade is logged:\n$everything")
        assertTrue(request.contains("-> Connection: Upgrade"), everything)
        assertTrue(request.contains("-> Sec-WebSocket-Version: 13"), everything)
        val key: String = headerValue(request, "-> Sec-WebSocket-Key: ", everything)

        val response: List<String> = messages.first { it.startsWith("RESPONSE: ") }.lines()
        assertEquals("RESPONSE: 101 Switching Protocols", response.first(), everything)
        assertTrue(response.contains("FROM: $roomUrl"), everything)
        assertTrue(response.contains("-> Upgrade: websocket"), everything)
        val accept: String = headerValue(response, "-> Sec-WebSocket-Accept: ", everything)
        assertEquals(expectedAccept(key), accept, "the logged accept answers the logged key:\n$everything")
        val bodyStart: Int = response.indexOf("BODY START")
        val bodyEnd: Int = response.indexOf("BODY END")
        assertTrue(bodyStart in 0 until bodyEnd, "the response has a body section:\n$everything")
        assertTrue(
            response.subList(bodyStart + 1, bodyEnd).all { it.isBlank() },
            "the body section stays empty for an upgraded connection:\n$everything",
        )
        assertNoChatLogged(everything)
    }

    @Test
    fun okHttpLayoutLogsTheUpgradeAsAnEmptyExchange() {
        val log = ChatLog()

        val replies: List<String> = chat(log, LoggingFormat.OkHttp)
        assertEquals(chatLines.map { "lobby <- $it" }, replies, "the client receives every reply in order")

        val messages: List<String> = log.awaitMessage("<-- 101", timeoutMillis = 10_000)
        val everything: String = messages.joinToString("\n----\n")
        val request: List<String> = messages.first { it.startsWith("--> GET") }.lines()
        assertEquals("--> GET $roomUrl", request.first(), everything)
        assertEquals("--> END GET", request.last(), "a handshake carries no request body:\n$everything")

        val response: List<String> = messages.first { it.startsWith("<-- 101") }.lines()
        assertTrue(
            Regex("""<-- 101 Switching Protocols \Q$roomUrl\E \(\d+ms\)""").matches(response.first()),
            everything,
        )
        assertTrue(response.contains("Upgrade: websocket"), everything)
        assertTrue(response.contains("Connection: Upgrade"), everything)
        assertTrue(response.any { it.startsWith("Sec-WebSocket-Accept: ") }, everything)
        assertTrue(
            Regex("""<-- END HTTP \(\d+ms, 0-byte body\)""").matches(response.last()),
            "the upgraded connection is logged with no body bytes:\n$everything",
        )
        assertNoChatLogged(everything)
    }

    /** Returns the value of the single logged header line starting with [prefix]. */
    private fun headerValue(lines: List<String>, prefix: String, everything: String): String {
        val matching: List<String> = lines.filter { it.startsWith(prefix) }
        assertEquals(1, matching.size, "one $prefix line is logged:\n$everything")
        return matching.single().removePrefix(prefix)
    }

    /** Computes the RFC 6455 `Sec-WebSocket-Accept` value a server must answer [key] with. */
    private fun expectedAccept(key: String): String {
        val digest: ByteArray = MessageDigest.getInstance("SHA-1")
            .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.US_ASCII))
        return Base64.getEncoder().encodeToString(digest)
    }

    private fun assertNoChatLogged(everything: String) {
        for (message in chatLines) {
            assertFalse(everything.contains(message), "'$message' stays out of the log:\n$everything")
        }
        assertFalse(everything.contains("lobby <- "), "no reply reaches the log:\n$everything")
        assertFalse(everything.contains("bye"), "the close reason stays out of the log:\n$everything")
    }
}
