/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.cio.webSocketRaw
import io.ktor.server.application.install
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocketRaw
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.websocket.WebSockets as ServerWebSockets

/**
 * Raw WebSocket sessions of the CIO client engine (`webSocketRaw`), where the application handles control frames.
 *
 * A warehouse terminal holds a raw WebSocket to the inventory service. Because the session is raw,
 * the terminal itself answers the service's heartbeat ping with a pong carrying the same payload,
 * sends reservation commands as text frames and reads the running totals back, then ends the shift
 * with its own close frame and reads the close frame the service echoes.
 */
class WarehouseFeedRawWebSocketTest {

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    /** Commands the service received, in order. */
    private val commands: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()

    /** Payload of the pong the terminal answered the heartbeat with. */
    private val heartbeatAnswer: CompletableDeferred<String> = CompletableDeferred()

    /** Close reason the terminal ended the session with, as the service read it. */
    private val terminalClose: CompletableDeferred<CloseReason> = CompletableDeferred()

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(ServerWebSockets)
            routing {
                webSocketRaw("/inventory") {
                    val stock: MutableMap<String, Int> = ConcurrentHashMap()
                    outgoing.send(Frame.Ping(HEARTBEAT.toByteArray()))
                    val pong: Frame = incoming.receive()
                    heartbeatAnswer.complete(if (pong is Frame.Pong) String(pong.data) else "unexpected ${pong.frameType}")
                    outgoing.send(Frame.Text("ready"))

                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                val command: String = frame.readText()
                                commands += command
                                val (_, sku: String, quantity: String) = command.split(" ")
                                val total: Int = stock.merge(sku, quantity.toInt(), Int::plus)!!
                                outgoing.send(Frame.Text("reserved $sku $total"))
                            }
                            is Frame.Close -> {
                                terminalClose.complete(frame.readReason()!!)
                                outgoing.send(Frame.Close(CloseReason(CloseReason.Codes.NORMAL, "bye terminal")))
                                return@webSocketRaw
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stopServer() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun terminalHandlesControlFramesOfARawSessionItself() {
        val client = HttpClient(ClientCIO) {
            install(ClientWebSockets)
        }
        val replies: MutableList<String> = mutableListOf()
        lateinit var serviceClose: Frame

        client.use {
            runBlocking {
                withTimeout(TIMEOUT_MILLIS) {
                    it.webSocketRaw(host = "127.0.0.1", port = port, path = "/inventory") {
                        val ping: Frame.Ping = assertInstanceOf(Frame.Ping::class.java, incoming.receive())
                        outgoing.send(Frame.Pong(ping.data))
                        assertEquals("ready", (incoming.receive() as Frame.Text).readText())

                        for (command in listOf("reserve sku-17 3", "reserve sku-42 1", "reserve sku-17 2")) {
                            outgoing.send(Frame.Text(command))
                            replies += (incoming.receive() as Frame.Text).readText()
                        }

                        outgoing.send(Frame.Close(CloseReason(CloseReason.Codes.NORMAL, "shift over")))
                        serviceClose = incoming.receive()
                    }
                }
            }
        }

        assertEquals(listOf("reserved sku-17 3", "reserved sku-42 1", "reserved sku-17 5"), replies)
        val close: Frame.Close = assertInstanceOf(Frame.Close::class.java, serviceClose)
        assertEquals(CloseReason(CloseReason.Codes.NORMAL, "bye terminal"), close.readReason())

        runBlocking {
            withTimeout(TIMEOUT_MILLIS) {
                assertEquals(HEARTBEAT, heartbeatAnswer.await())
                assertEquals(CloseReason(CloseReason.Codes.NORMAL, "shift over"), terminalClose.await())
            }
        }
        assertEquals(listOf("reserve sku-17 3", "reserve sku-42 1", "reserve sku-17 2"), commands.toList())
    }

    private companion object {
        const val HEARTBEAT: String = "hb-0001"
        const val TIMEOUT_MILLIS: Long = 30_000
    }
}
