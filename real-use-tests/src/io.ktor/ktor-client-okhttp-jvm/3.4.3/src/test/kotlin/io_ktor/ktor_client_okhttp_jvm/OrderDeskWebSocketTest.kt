/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_okhttp_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.server.websocket.receiveDeserialized as serverReceiveDeserialized
import io.ktor.server.websocket.sendSerialized as serverSendSerialized

/**
 * WebSocket sessions that the OkHttp engine opens on top of OkHttp's own WebSocket client.
 *
 * A trading desk connects to an order service over a WebSocket through the OkHttp engine.
 * The handshake carries the desk id as a header, the service greets the desk with a text
 * frame, orders and acknowledgements travel as JSON through the WebSocket serialization
 * converter, a batch of quantities goes out as a binary frame and comes back as its checksum,
 * and the service ends the session with a close frame whose reason the client reads back.
 */
class OrderDeskWebSocketTest {

    @Serializable
    data class Order(val sku: String, val quantity: Int)

    @Serializable
    data class Ack(val sku: String, val filled: Int, val position: Int)

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0
    private val serverSeen: MutableList<String> = CopyOnWriteArrayList()

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(ServerWebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(Json)
            }
            routing {
                webSocket("/orders") {
                    val desk: String = call.request.headers["X-Desk"] ?: "anonymous"
                    serverSeen += "open $desk"
                    send(Frame.Text("welcome $desk"))

                    var position = 0
                    repeat(ORDER_COUNT) {
                        val order: Order = serverReceiveDeserialized()
                        position += order.quantity
                        serverSeen += "order ${order.sku} ${order.quantity}"
                        serverSendSerialized(Ack(order.sku, order.quantity, position))
                    }

                    val batch: Frame = incoming.receive()
                    val quantities: ByteArray = (batch as Frame.Binary).readBytes()
                    val checksum: Int = quantities.fold(0) { sum: Int, b: Byte -> sum + b }
                    serverSeen += "batch ${quantities.size}"
                    send(Frame.Binary(true, byteArrayOf(quantities.size.toByte(), checksum.toByte())))

                    val farewell: Frame = incoming.receive()
                    serverSeen += (farewell as Frame.Text).readText()
                    close(CloseReason(CloseReason.Codes.NORMAL, "desk $desk settled at $position"))
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stop() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun deskTradesOverOneWebSocketSessionUntilTheServiceClosesIt() {
        val client = HttpClient(OkHttp) {
            install(ClientWebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(Json)
            }
        }

        val acks: MutableList<Ack> = ArrayList()
        lateinit var greeting: String
        lateinit var batchReply: ByteArray
        var closeReason: CloseReason? = null
        val framesAfterFarewell: MutableList<Frame> = ArrayList()

        client.use {
            runBlocking {
                it.webSocket("ws://127.0.0.1:$port/orders", request = { header("X-Desk", "desk-7") }) {
                    greeting = (incoming.receive() as Frame.Text).readText()

                    listOf(Order("bolt-m6", 40), Order("bolt-m6", -15), Order("washer-m6", 200)).forEach { order: Order ->
                        sendSerialized(order)
                        acks += receiveDeserialized<Ack>()
                    }

                    send(Frame.Binary(true, byteArrayOf(3, 5, 7, 11)))
                    batchReply = (incoming.receive() as Frame.Binary).readBytes()

                    send(Frame.Text("bye"))
                    for (frame: Frame in incoming) {
                        framesAfterFarewell += frame
                    }
                    closeReason = this.closeReason.await()
                }
            }
        }

        assertEquals("welcome desk-7", greeting)
        assertEquals(
            listOf(Ack("bolt-m6", 40, 40), Ack("bolt-m6", -15, 25), Ack("washer-m6", 200, 225)),
            acks,
        )
        assertArrayEquals(byteArrayOf(4, 26), batchReply)
        assertTrue(framesAfterFarewell.isEmpty(), "no data frames after the farewell: $framesAfterFarewell")
        assertEquals(CloseReason(CloseReason.Codes.NORMAL, "desk desk-7 settled at 225"), closeReason)
        assertEquals(
            listOf(
                "open desk-7",
                "order bolt-m6 40",
                "order bolt-m6 -15",
                "order washer-m6 200",
                "batch 4",
                "bye",
            ),
            serverSeen,
        )
    }

    private companion object {
        const val ORDER_COUNT: Int = 3
    }
}
