/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_websockets_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.FrameTooBigException
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Covers frame-size enforcement in the plugin's sessions: a chunk-upload endpoint configured
 * with `maxFrameSize` accepts chunks within the limit, while an oversized chunk surfaces to the
 * handler as a `FrameTooBigException` and reaches the client as a `TOO_BIG` close frame.
 */
class FrameSizeLimitTest {

    /** What the server endpoint saw during one upload session; read it once [closeReason] completes. */
    private class UploadLog {

        val acceptedChunks: MutableList<Int> = mutableListOf()

        var rejectedFrameSize: Long? = null

        val closeReason: CompletableDeferred<CloseReason?> = CompletableDeferred()
    }

    @Test
    fun acceptsChunksWithinTheLimit(): Unit = withUploadEndpoint { port: Int, log: UploadLog ->
        val client: HttpClient = HttpClient(ClientCIO) {
            install(ClientWebSockets)
        }
        val acks: MutableList<String> = mutableListOf()
        var clientCloseReason: CloseReason? = null

        client.webSocket(host = "127.0.0.1", port = port, path = "/upload") {
            for (size: Int in listOf(16, 512, MAX_FRAME_SIZE)) {
                send(Frame.Binary(true, ByteArray(size) { index: Int -> index.toByte() }))
                acks += (incoming.receive() as Frame.Text).readText()
            }
            close(CloseReason(CloseReason.Codes.NORMAL, "upload complete"))
            clientCloseReason = closeReason.await()
        }
        client.close()

        assertEquals(listOf("ack 16", "ack 512", "ack $MAX_FRAME_SIZE"), acks)
        val serverCloseReason: CloseReason? = withTimeout(AWAIT_MILLIS) { log.closeReason.await() }
        assertEquals(listOf(16, 512, MAX_FRAME_SIZE), log.acceptedChunks)
        assertEquals(CloseReason.Codes.NORMAL, serverCloseReason?.knownReason)
        assertEquals("upload complete", serverCloseReason?.message)
        assertEquals(CloseReason.Codes.NORMAL, clientCloseReason?.knownReason)
        assertNull(log.rejectedFrameSize)
    }

    @Test
    fun closesTheSessionWithTooBigOnAnOversizedChunk(): Unit = withUploadEndpoint { port: Int, log: UploadLog ->
        val client: HttpClient = HttpClient(ClientCIO) {
            install(ClientWebSockets)
        }
        val acks: MutableList<String> = mutableListOf()
        var clientCloseReason: CloseReason? = null

        client.webSocket(host = "127.0.0.1", port = port, path = "/upload") {
            send(Frame.Binary(true, ByteArray(64)))
            acks += (incoming.receive() as Frame.Text).readText()
            send(Frame.Binary(true, ByteArray(MAX_FRAME_SIZE * 4)))
            for (frame: Frame in incoming) {
                acks += "unexpected ${frame.frameType}"
            }
            clientCloseReason = closeReason.await()
        }
        client.close()

        assertEquals(listOf("ack 64"), acks)
        assertEquals(CloseReason.Codes.TOO_BIG, clientCloseReason?.knownReason)
        withTimeout(AWAIT_MILLIS) { log.closeReason.await() }
        assertEquals(listOf(64), log.acceptedChunks)
        assertEquals(MAX_FRAME_SIZE * 4L, log.rejectedFrameSize)
    }

    /** Starts the upload endpoint on an ephemeral port, runs [block] against it, and stops it. */
    private fun withUploadEndpoint(block: suspend (Int, UploadLog) -> Unit) {
        val log = UploadLog()
        val server: EmbeddedServer<*, *> = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            uploadEndpoint(log)
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

    private fun Application.uploadEndpoint(log: UploadLog) {
        install(WebSockets) {
            maxFrameSize = MAX_FRAME_SIZE.toLong()
        }
        routing {
            webSocket("/upload") {
                try {
                    for (frame: Frame in incoming) {
                        val chunk: ByteArray = (frame as Frame.Binary).readBytes()
                        log.acceptedChunks += chunk.size
                        send(Frame.Text("ack ${chunk.size}"))
                    }
                } catch (cause: FrameTooBigException) {
                    log.rejectedFrameSize = cause.frameSize
                } finally {
                    log.closeReason.complete(closeReason.await())
                }
            }
        }
    }

    private companion object {

        const val MAX_FRAME_SIZE: Int = 1024

        const val AWAIT_MILLIS: Long = 10_000L

        const val STOP_TIMEOUT_MILLIS: Long = 5_000L
    }
}
