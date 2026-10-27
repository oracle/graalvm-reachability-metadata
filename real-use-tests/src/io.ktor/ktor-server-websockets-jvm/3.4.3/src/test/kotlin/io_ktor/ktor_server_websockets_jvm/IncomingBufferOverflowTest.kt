/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_websockets_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.cio.webSocketRaw
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.ChannelOverflow
import io.ktor.websocket.ChannelOverflowException
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Covers bounded incoming buffers configured through the plugin's `channels` block: a sensor
 * ingest endpoint caps each session at a few unread readings with
 * `incoming = bounded(capacity, ChannelOverflow.CLOSE)`, so a burst that fits the buffer is
 * stored in full once a slow store catches up, while a device that overruns the cap has its
 * oldest unread reading dropped and the handler, after draining what was still buffered, sees
 * a `ChannelOverflowException` and closes the session with `VIOLATED_POLICY`.
 */
class IncomingBufferOverflowTest {

    /**
     * A store whose writes suspend until [open] completes, standing in for a slow database.
     * The handler is its only writer; read [written] once [expectedWritten] or the session ended.
     */
    private class ReadingStore(private val expectedCount: Int) {

        val written: MutableList<String> = mutableListOf()

        val firstWriteStarted: CompletableDeferred<Unit> = CompletableDeferred()

        val open: CompletableDeferred<Unit> = CompletableDeferred()

        val expectedWritten: CompletableDeferred<Unit> = CompletableDeferred()

        suspend fun write(reading: String) {
            firstWriteStarted.complete(Unit)
            open.await()
            written += reading
            if (written.size == expectedCount) {
                expectedWritten.complete(Unit)
            }
        }
    }

    /** How the ingest handler's session ended; read it once [closeReason] completes. */
    private class SessionLog {

        var overflowMessage: String? = null

        val closeReason: CompletableDeferred<CloseReason?> = CompletableDeferred()
    }

    @Test
    fun storesABurstThatFitsTheBuffer(): Unit = withIngestEndpoint(expectedCount = CAPACITY + 1) {
            port: Int, store: ReadingStore, log: SessionLog ->
        val client: HttpClient = HttpClient(ClientCIO) {
            install(ClientWebSockets)
        }
        var clientCloseReason: CloseReason? = null

        client.webSocket(host = "127.0.0.1", port = port, path = "/ingest") {
            send(Frame.Text("r1"))
            withTimeout(AWAIT_MILLIS) { store.firstWriteStarted.await() }
            for (index: Int in 2..CAPACITY + 1) {
                send(Frame.Text("r$index"))
            }
            store.open.complete(Unit)
            withTimeout(AWAIT_MILLIS) { store.expectedWritten.await() }
            close(CloseReason(CloseReason.Codes.NORMAL, "burst delivered"))
            clientCloseReason = closeReason.await()
        }
        client.close()

        val serverCloseReason: CloseReason? = withTimeout(AWAIT_MILLIS) { log.closeReason.await() }
        assertEquals(listOf("r1", "r2", "r3", "r4", "r5"), store.written)
        assertNull(log.overflowMessage)
        assertEquals(CloseReason.Codes.NORMAL, serverCloseReason?.knownReason)
        assertEquals("burst delivered", serverCloseReason?.message)
        assertEquals(CloseReason.Codes.NORMAL, clientCloseReason?.knownReason)
    }

    @Test
    fun cutsOffADeviceThatOverrunsTheBuffer(): Unit = withIngestEndpoint(expectedCount = OVERRUN_SIZE) {
            port: Int, store: ReadingStore, log: SessionLog ->
        val client: HttpClient = HttpClient(ClientCIO) {
            install(ClientWebSockets)
        }
        val receipts: MutableList<String> = mutableListOf()
        var serverClose: CloseReason? = null

        client.webSocketRaw(host = "127.0.0.1", port = port, path = "/ingest") {
            send(Frame.Text("r1"))
            withTimeout(AWAIT_MILLIS) { store.firstWriteStarted.await() }
            // Each pong confirms the session took the reading off the socket, so the reader's own
            // buffer, which `channels` bounds as well, never overruns before the handler's does.
            for (index: Int in 2..OVERRUN_SIZE) {
                send(Frame.Text("r$index"))
                send(Frame.Ping("r$index".encodeToByteArray()))
                val receipt: Frame = incoming.receive()
                assertTrue(receipt is Frame.Pong, "expected a pong receipt, got $receipt")
                receipts += receipt.data.decodeToString()
            }
            store.open.complete(Unit)
            for (frame: Frame in incoming) {
                if (frame is Frame.Close) {
                    serverClose = frame.readReason()
                    send(Frame.Close(CloseReason(CloseReason.Codes.NORMAL, "device disconnecting")))
                    break
                }
            }
        }
        client.close()

        withTimeout(AWAIT_MILLIS) { log.closeReason.await() }
        assertEquals(listOf("r2", "r3", "r4", "r5", "r6"), receipts)
        assertEquals("Channel overflowed", log.overflowMessage)
        assertEquals(listOf("r1", "r3", "r4", "r5", "r6"), store.written)
        assertEquals(CloseReason.Codes.VIOLATED_POLICY, serverClose?.knownReason)
        assertEquals("too many unread readings", serverClose?.message)
    }

    /** Starts the ingest endpoint on an ephemeral port, runs [block] against it, and stops it. */
    private fun withIngestEndpoint(expectedCount: Int, block: suspend (Int, ReadingStore, SessionLog) -> Unit) {
        val store = ReadingStore(expectedCount)
        val log = SessionLog()
        val server: EmbeddedServer<*, *> = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            ingestEndpoint(store, log)
        }
        server.start(wait = false)
        try {
            runBlocking {
                val port: Int = server.engine.resolvedConnectors().first().port
                block(port, store, log)
            }
        } finally {
            server.stop(gracePeriodMillis = 0, timeoutMillis = STOP_TIMEOUT_MILLIS)
        }
    }

    private fun Application.ingestEndpoint(store: ReadingStore, log: SessionLog) {
        install(WebSockets) {
            channels {
                incoming = bounded(CAPACITY, ChannelOverflow.CLOSE)
            }
        }
        routing {
            webSocket("/ingest") {
                try {
                    for (frame: Frame in incoming) {
                        store.write((frame as Frame.Text).readText())
                    }
                } catch (cause: ChannelOverflowException) {
                    log.overflowMessage = cause.message
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "too many unread readings"))
                } finally {
                    log.closeReason.complete(closeReason.await())
                }
            }
        }
    }

    private companion object {

        const val CAPACITY: Int = 4

        /** One reading held by the store, [CAPACITY] buffered, and one more that overruns them. */
        const val OVERRUN_SIZE: Int = CAPACITY + 2

        const val AWAIT_MILLIS: Long = 10_000L

        const val STOP_TIMEOUT_MILLIS: Long = 5_000L
    }
}
