/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_websockets_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.cio.webSocketRaw
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocketRaw
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.FrameType
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Covers raw sessions opened with `webSocketRaw`: a telemetry gateway receives every control
 * frame itself, so it answers pings with pongs by hand, reassembles fragmented readings from
 * their `fin` flags, and completes the close handshake by echoing the client's close frame.
 */
class RawSessionControlFrameTest {

    /** What the gateway handler saw during one raw session; read it once [finished] completes. */
    private class GatewayLog {

        val frameTypes: MutableList<FrameType> = mutableListOf()

        val readings: MutableList<String> = mutableListOf()

        var clientCloseReason: CloseReason? = null

        val finished: CompletableDeferred<Unit> = CompletableDeferred()
    }

    @Test
    fun answersPingsAndEchoesTheCloseHandshakeByHand(): Unit = withGateway { port: Int, log: GatewayLog ->
        val client: HttpClient = rawClient()
        var pongPayload: ByteArray? = null
        var reply: String? = null
        var echoedClose: CloseReason? = null

        client.webSocketRaw(host = "127.0.0.1", port = port, path = "/telemetry") {
            send(Frame.Ping("heartbeat-7".encodeToByteArray()))
            val pong: Frame = incoming.receive()
            assertEquals(FrameType.PONG, pong.frameType)
            pongPayload = pong.data

            send(Frame.Text("temperature=21.5"))
            reply = (incoming.receive() as Frame.Text).readText()

            send(Frame.Close(CloseReason(CloseReason.Codes.GOING_AWAY, "sensor sleeping")))
            echoedClose = (incoming.receive() as Frame.Close).readReason()
        }
        client.close()

        assertArrayEquals("heartbeat-7".encodeToByteArray(), pongPayload)
        assertEquals("stored temperature=21.5", reply)
        assertEquals(CloseReason.Codes.GOING_AWAY, echoedClose?.knownReason)
        assertEquals("sensor sleeping", echoedClose?.message)
        withTimeout(AWAIT_MILLIS) { log.finished.await() }
        assertEquals(listOf(FrameType.PING, FrameType.TEXT, FrameType.CLOSE), log.frameTypes)
        assertEquals(listOf("temperature=21.5"), log.readings)
        assertEquals("sensor sleeping", log.clientCloseReason?.message)
    }

    @Test
    fun reassemblesFragmentedReadingsFromTheirFinFlags(): Unit = withGateway { port: Int, log: GatewayLog ->
        val client: HttpClient = rawClient()
        val replies: MutableList<String> = mutableListOf()
        val replyFinFlags: MutableList<Boolean> = mutableListOf()

        client.webSocketRaw(host = "127.0.0.1", port = port, path = "/telemetry") {
            send(Frame.Text(false, "humidity=".encodeToByteArray()))
            send(Frame.Text(false, "48".encodeToByteArray()))
            send(Frame.Text(true, "%".encodeToByteArray()))
            val fragmentedReply: Frame = incoming.receive()
            replyFinFlags += fragmentedReply.fin
            replies += (fragmentedReply as Frame.Text).readText()

            send(Frame.Text("pressure=1013"))
            val wholeReply: Frame = incoming.receive()
            replyFinFlags += wholeReply.fin
            replies += (wholeReply as Frame.Text).readText()

            send(Frame.Close(CloseReason(CloseReason.Codes.NORMAL, "batch done")))
            assertTrue(incoming.receive() is Frame.Close)
        }
        client.close()

        assertEquals(listOf("stored humidity=48% from 3 fragments", "stored pressure=1013"), replies)
        assertEquals(listOf(true, true), replyFinFlags)
        withTimeout(AWAIT_MILLIS) { log.finished.await() }
        assertEquals(listOf("humidity=48%", "pressure=1013"), log.readings)
        assertFalse(FrameType.PING in log.frameTypes)
        assertEquals(FrameType.CLOSE, log.frameTypes.last())
    }

    private fun rawClient(): HttpClient = HttpClient(ClientCIO) {
        install(ClientWebSockets)
    }

    /** Starts the gateway on an ephemeral port, runs [block] against it, and stops it. */
    private fun withGateway(block: suspend (Int, GatewayLog) -> Unit) {
        val log = GatewayLog()
        val server: EmbeddedServer<*, *> = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            telemetryGateway(log)
        }
        server.start(wait = false)
        try {
            runBlocking {
                val port: Int = server.engine.resolvedConnectors().first().port
                block(port, log)
            }
        } finally {
            server.stop(gracePeriodMillis = 0, timeoutMillis = STOP_TIMEOUT_MILLIS)
        }
    }

    private fun Application.telemetryGateway(log: GatewayLog) {
        install(WebSockets)
        routing {
            webSocketRaw("/telemetry") {
                val pending = StringBuilder()
                var fragments = 0
                try {
                    for (frame: Frame in incoming) {
                        log.frameTypes += frame.frameType
                        when (frame) {
                            is Frame.Ping -> send(Frame.Pong(frame.data))
                            is Frame.Text, is Frame.Binary -> {
                                pending.append(frame.data.decodeToString())
                                fragments++
                                if (frame.fin) {
                                    val reading: String = pending.toString()
                                    log.readings += reading
                                    val suffix: String = if (fragments > 1) " from $fragments fragments" else ""
                                    send(Frame.Text("stored $reading$suffix"))
                                    pending.clear()
                                    fragments = 0
                                }
                            }
                            is Frame.Close -> {
                                val reason: CloseReason? = frame.readReason()
                                log.clientCloseReason = reason
                                send(Frame.Close(reason ?: CloseReason(CloseReason.Codes.NORMAL, "")))
                                break
                            }
                            else -> Unit
                        }
                    }
                } finally {
                    log.finished.complete(Unit)
                }
            }
        }
    }

    private companion object {

        const val AWAIT_MILLIS: Long = 10_000L

        const val STOP_TIMEOUT_MILLIS: Long = 5_000L
    }
}
