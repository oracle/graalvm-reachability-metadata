/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.server.application.install
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.websocket.WebSockets as ServerWebSockets

/**
 * The CIO engine's WebSocket upgrade: HTTP/1.1 connections switched to long-lived
 * WebSocket sessions that exchange text, binary, and close frames.
 *
 * A courier dispatch room runs on an embedded CIO server. Dispatchers join a room over
 * a WebSocket, get a roster on arrival, and see every message posted to the room. A
 * courier's handheld also uploads a binary GPS fix, which the server acknowledges with
 * the bytes in reverse order, and leaves with a quit command that the server answers
 * with a normal close frame. The test checks the roster, the broadcasts on both
 * sessions, the binary round trip, the close reason, and the departure notice.
 */
class DispatchRoomWebSocketTest {

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0
    private val members: MutableMap<String, DefaultWebSocketServerSession> = ConcurrentHashMap()
    private val upgradedRooms: MutableList<String> = CopyOnWriteArrayList()

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(ServerWebSockets) {
                maxFrameSize = 64 * 1024
            }
            routing {
                webSocket("/rooms/{room}") {
                    val room: String = call.parameters["room"]!!
                    val name: String = call.request.queryParameters["name"]!!
                    upgradedRooms += room
                    members[name] = this
                    send(Frame.Text("welcome $name to $room; on duty: ${members.keys.sorted().joinToString()}"))
                    broadcast("$name joined", except = name)
                    try {
                        for (frame: Frame in incoming) {
                            when (frame) {
                                is Frame.Text -> {
                                    val text: String = frame.readText()
                                    if (text == "/quit") {
                                        close(CloseReason(CloseReason.Codes.NORMAL, "off duty: $name"))
                                    } else {
                                        broadcast("$name: $text", except = null)
                                    }
                                }
                                is Frame.Binary -> send(Frame.Binary(true, frame.readBytes().reversedArray()))
                                else -> Unit
                            }
                        }
                    } finally {
                        members.remove(name)
                        broadcast("$name left", except = name)
                    }
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
    fun dispatchersShareARoomOverCioWebSockets() {
        val client = HttpClient(ClientCIO) {
            install(ClientWebSockets)
        }

        client.use {
            runBlocking {
                withTimeout(30_000) {
                    val alice: DefaultClientWebSocketSession =
                        it.webSocketSession("ws://127.0.0.1:$port/rooms/north?name=alice")
                    assertEquals("welcome alice to north; on duty: alice", alice.nextText())

                    val bob: DefaultClientWebSocketSession =
                        it.webSocketSession("ws://127.0.0.1:$port/rooms/north?name=bob")
                    assertEquals("welcome bob to north; on duty: alice, bob", bob.nextText())
                    assertEquals("bob joined", alice.nextText())

                    alice.send(Frame.Text("parcel 4471 ready at dock 3"))
                    assertEquals("alice: parcel 4471 ready at dock 3", alice.nextText())
                    assertEquals("alice: parcel 4471 ready at dock 3", bob.nextText())

                    val fix: ByteArray = byteArrayOf(52, 49, 46, 57, 44, 49, 50, 46, 53)
                    bob.send(Frame.Binary(true, fix))
                    val ack: Frame = bob.incoming.receive()
                    assertEquals(Frame.Binary::class, ack::class)
                    assertArrayEquals(fix.reversedArray(), ack.readBytes())

                    bob.send(Frame.Text("/quit"))
                    val reason: CloseReason? = bob.closeReason.await()
                    assertEquals(CloseReason(CloseReason.Codes.NORMAL, "off duty: bob"), reason)
                    assertEquals("bob left", alice.nextText())

                    alice.close(CloseReason(CloseReason.Codes.GOING_AWAY, "shift over"))
                    assertEquals(CloseReason.Codes.GOING_AWAY.code, alice.closeReason.await()?.code)
                }
            }
        }

        assertEquals(listOf("north", "north"), upgradedRooms)
    }

    private suspend fun broadcast(message: String, except: String?) {
        members.filterKeys { it != except }.values.forEach { session: DefaultWebSocketServerSession ->
            session.send(Frame.Text(message))
        }
    }

    private suspend fun DefaultClientWebSocketSession.nextText(): String {
        val frame: Frame = incoming.receive()
        assertEquals(Frame.Text::class, frame::class, "unexpected $frame")
        return (frame as Frame.Text).readText()
    }
}
