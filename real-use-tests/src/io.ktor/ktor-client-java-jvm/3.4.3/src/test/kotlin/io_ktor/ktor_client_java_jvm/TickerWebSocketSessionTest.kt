/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_java_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * WebSocket sessions through the Java engine's WebSocket bridge, as a trading desk application
 * subscribes to a stock ticker service and exchanges text and binary frames with it.
 */
public class TickerWebSocketSessionTest {

    /** The ticker service: answers quote requests and closes the session on request. */
    class TickerService : AutoCloseable {
        private val prices: ConcurrentHashMap<String, Long> = ConcurrentHashMap(
            mapOf("ACME" to 12_550L, "GLOBEX" to 9_875L),
        )

        private val server: EmbeddedServer<*, *> = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(ServerWebSockets)
            routing {
                webSocket("/ticker") {
                    val desk: String = call.request.headers["X-Desk"] ?: "anonymous"
                    send(Frame.Text("WELCOME $desk"))
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                val command: List<String> = frame.readText().split(' ')
                                when (command[0]) {
                                    "QUOTE" -> {
                                        val price: Long? = prices[command[1]]
                                        send(Frame.Text(if (price == null) "UNKNOWN ${command[1]}" else "${command[1]} $price"))
                                    }
                                    "SET" -> {
                                        prices[command[1]] = command[2].toLong()
                                        send(Frame.Text("ACK ${command[1]}"))
                                    }
                                    "LOGOUT" -> close(CloseReason(CloseReason.Codes.NORMAL, "goodbye $desk"))
                                    else -> close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "bad command ${command[0]}"))
                                }
                            }
                            is Frame.Binary -> {
                                // A binary batch is a sequence of big-endian cents; reply with their sum.
                                val batch: ByteBuffer = ByteBuffer.wrap(frame.readBytes())
                                var total: Long = 0L
                                while (batch.remaining() >= Long.SIZE_BYTES) {
                                    total += batch.getLong()
                                }
                                send(Frame.Binary(true, ByteBuffer.allocate(Long.SIZE_BYTES).putLong(total).array()))
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }

        val port: Int

        init {
            server.start(wait = false)
            port = runBlocking { server.engine.resolvedConnectors().first().port }
        }

        override fun close() {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 2_000)
        }
    }

    private fun tickerClient(): HttpClient = HttpClient(Java) {
        install(ClientWebSockets)
    }

    private suspend fun DefaultClientWebSocketSession.receiveText(): String {
        val frame: Frame = incoming.receive()
        assertThat(frame).isInstanceOf(Frame.Text::class.java)
        return (frame as Frame.Text).readText()
    }

    @Test
    fun exchangesQuotesAndReceivesTheServerCloseReason(): Unit = runBlocking {
        TickerService().use { service: TickerService ->
            tickerClient().use { client: HttpClient ->
                val replies: MutableList<String> = mutableListOf()
                var closeReason: CloseReason? = null
                client.webSocket(host = "127.0.0.1", port = service.port, path = "/ticker", request = {
                    header("X-Desk", "equities")
                }) {
                    replies += receiveText()
                    send(Frame.Text("QUOTE ACME"))
                    replies += receiveText()
                    send(Frame.Text("SET ACME 12600"))
                    replies += receiveText()
                    send(Frame.Text("QUOTE ACME"))
                    replies += receiveText()
                    send(Frame.Text("QUOTE INITECH"))
                    replies += receiveText()
                    send(Frame.Text("LOGOUT"))
                    closeReason = this.closeReason.await()
                }

                assertThat(replies).containsExactly(
                    "WELCOME equities",
                    "ACME 12550",
                    "ACK ACME",
                    "ACME 12600",
                    "UNKNOWN INITECH",
                )
                assertThat(closeReason?.knownReason).isEqualTo(CloseReason.Codes.NORMAL)
                assertThat(closeReason?.message).isEqualTo("goodbye equities")
            }
        }
    }

    @Test
    fun sendsBinaryBatchesOverALongLivedSession(): Unit = runBlocking {
        TickerService().use { service: TickerService ->
            tickerClient().use { client: HttpClient ->
                val session: DefaultClientWebSocketSession = client.webSocketSession(
                    host = "127.0.0.1",
                    port = service.port,
                    path = "/ticker",
                )
                assertThat(session.receiveText()).isEqualTo("WELCOME anonymous")

                val totals: MutableList<Long> = mutableListOf()
                for (batch: List<Long> in listOf(listOf(100L, 250L, 650L), listOf(1_000_000L, -1L))) {
                    val payload: ByteBuffer = ByteBuffer.allocate(batch.size * Long.SIZE_BYTES)
                    batch.forEach { cents: Long -> payload.putLong(cents) }
                    session.send(Frame.Binary(true, payload.array()))
                    val reply: Frame = session.incoming.receive()
                    assertThat(reply).isInstanceOf(Frame.Binary::class.java)
                    totals += ByteBuffer.wrap(reply.readBytes()).getLong()
                }
                assertThat(totals).containsExactly(1_000L, 999_999L)

                session.send(Frame.Text("HALT"))
                val reason: CloseReason? = session.closeReason.await()
                assertThat(reason?.knownReason).isEqualTo(CloseReason.Codes.CANNOT_ACCEPT)
                assertThat(reason?.message).isEqualTo("bad command HALT")
            }
        }
    }
}
