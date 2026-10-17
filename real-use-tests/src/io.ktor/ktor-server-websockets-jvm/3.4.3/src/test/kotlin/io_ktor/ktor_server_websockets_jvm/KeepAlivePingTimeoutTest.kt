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
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Covers keep-alive pinging in the plugin's default sessions: a presence service configured with
 * `pingPeriod` and `timeout` pings every connected device, keeps a device that answers its pings
 * online, and drops a device that stays silent past a per-session `timeoutMillis` with a
 * `Ping timeout` close.
 */
class KeepAlivePingTimeoutTest {

    /** What the presence service saw for one device; read it once [departed] completes. */
    private class PresenceLog {

        val statuses: MutableList<String> = mutableListOf()

        val departed: CompletableDeferred<CloseReason?> = CompletableDeferred()
    }

    @Test
    fun keepsADeviceThatAnswersEveryPingOnline(): Unit = withPresenceService { port: Int, log: PresenceLog ->
        val client: HttpClient = rawClient()
        val pingPayloads: MutableList<String> = mutableListOf()
        var acknowledgement: String? = null
        var serverClose: CloseReason? = null

        client.webSocketRaw(host = "127.0.0.1", port = port, path = "/presence") {
            while (pingPayloads.size < ANSWERED_PINGS) {
                val frame: Frame = incoming.receive()
                assertTrue(frame is Frame.Ping, "expected only pings before the first status, got $frame")
                pingPayloads += frame.data.decodeToString()
                send(Frame.Pong(frame.data))
            }
            send(Frame.Text("status=online"))
            while (acknowledgement == null) {
                when (val frame: Frame = incoming.receive()) {
                    is Frame.Ping -> send(Frame.Pong(frame.data))
                    is Frame.Text -> acknowledgement = frame.readText()
                    else -> Unit
                }
            }
            send(Frame.Close(CloseReason(CloseReason.Codes.NORMAL, "device shutting down")))
            for (frame: Frame in incoming) {
                when (frame) {
                    is Frame.Ping -> send(Frame.Pong(frame.data))
                    is Frame.Close -> {
                        serverClose = frame.readReason()
                        break
                    }
                    else -> Unit
                }
            }
        }
        client.close()

        assertEquals(ANSWERED_PINGS, pingPayloads.size)
        assertTrue(pingPayloads.all { payload: String -> payload.startsWith("[ping ") }, "$pingPayloads")
        assertEquals(ANSWERED_PINGS, pingPayloads.toSet().size)
        assertEquals("ack status=online", acknowledgement)
        assertEquals(CloseReason.Codes.NORMAL, serverClose?.knownReason)
        val departure: CloseReason? = withTimeout(AWAIT_MILLIS) { log.departed.await() }
        assertEquals(CloseReason.Codes.NORMAL, departure?.knownReason)
        assertEquals("device shutting down", departure?.message)
        assertEquals(listOf("status=online"), log.statuses)
    }

    @Test
    fun dropsADeviceThatNeverAnswersAPing(): Unit = withPresenceService { port: Int, log: PresenceLog ->
        val client: HttpClient = rawClient()
        var unansweredPings = 0
        var serverClose: CloseReason? = null

        client.webSocketRaw(host = "127.0.0.1", port = port, path = "/presence/strict") {
            for (frame: Frame in incoming) {
                when (frame) {
                    is Frame.Ping -> unansweredPings++
                    is Frame.Close -> {
                        serverClose = frame.readReason()
                        break
                    }
                    else -> Unit
                }
            }
        }
        client.close()

        assertTrue(unansweredPings >= 1, "the service must ping before dropping the device")
        assertEquals(CloseReason.Codes.INTERNAL_ERROR, serverClose?.knownReason)
        assertEquals("Ping timeout", serverClose?.message)
        val departure: CloseReason? = withTimeout(AWAIT_MILLIS) { log.departed.await() }
        assertEquals(CloseReason.Codes.INTERNAL_ERROR, departure?.knownReason)
        assertEquals("Ping timeout", departure?.message)
        assertEquals(emptyList<String>(), log.statuses)
    }

    private fun rawClient(): HttpClient = HttpClient(ClientCIO) {
        install(ClientWebSockets)
    }

    /** Starts the presence service on an ephemeral port, runs [block] against it, and stops it. */
    private fun withPresenceService(block: suspend (Int, PresenceLog) -> Unit) {
        val log = PresenceLog()
        val server: EmbeddedServer<*, *> = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            presenceService(log)
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

    private fun Application.presenceService(log: PresenceLog) {
        install(WebSockets) {
            pingPeriod = PING_PERIOD_MILLIS.milliseconds
            timeout = 30.seconds
        }
        routing {
            webSocket("/presence") {
                try {
                    for (frame: Frame in incoming) {
                        if (frame is Frame.Text) {
                            val status: String = frame.readText()
                            log.statuses += status
                            send(Frame.Text("ack $status"))
                        }
                    }
                } finally {
                    log.departed.complete(closeReason.await())
                }
            }
            webSocket("/presence/strict") {
                timeoutMillis = STRICT_TIMEOUT_MILLIS
                try {
                    for (frame: Frame in incoming) {
                        if (frame is Frame.Text) {
                            log.statuses += frame.readText()
                        }
                    }
                } finally {
                    log.departed.complete(closeReason.await())
                }
            }
        }
    }

    private companion object {

        const val ANSWERED_PINGS: Int = 3

        const val PING_PERIOD_MILLIS: Long = 100L

        const val STRICT_TIMEOUT_MILLIS: Long = 300L

        const val AWAIT_MILLIS: Long = 10_000L

        const val STOP_TIMEOUT_MILLIS: Long = 5_000L
    }
}
