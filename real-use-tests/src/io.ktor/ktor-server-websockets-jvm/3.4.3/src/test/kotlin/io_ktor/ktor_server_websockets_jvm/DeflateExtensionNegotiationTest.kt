/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_websockets_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketDeflateExtension
import io.ktor.websocket.close
import io.ktor.websocket.extensionOrNull
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.zip.Deflater
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Covers permessage-deflate extension negotiation in the plugin's upgrade: a log-shipping
 * endpoint accepts the deflate extension a client offers, exchanges large compressible batches
 * through it, and still serves a client that offers no extension with plain frames.
 */
class DeflateExtensionNegotiationTest {

    /** Builds the repetitive log batches an agent ships; they compress well. */
    private object LogBatch {

        fun of(host: String, lines: Int): String = (1..lines).joinToString("\n") { index: Int ->
            val second: String = (index % 60).toString().padStart(2, '0')
            "2026-10-17T12:00:${second}Z $host INFO request handled id=$index"
        }
    }

    @Test
    fun negotiatesDeflateAndExchangesLargeBatches(): Unit = withLogEndpoint { port: Int ->
        val client: HttpClient = HttpClient(ClientCIO) {
            install(ClientWebSockets) {
                extensions {
                    install(WebSocketDeflateExtension) {
                        compressionLevel = Deflater.BEST_COMPRESSION
                        serverNoContextTakeOver = true
                        compressIfBiggerThan(COMPRESSION_THRESHOLD)
                    }
                }
            }
        }
        val batch: String = LogBatch.of("api-1", BATCH_LINES)
        var negotiatedHeader: String? = null
        var clientHasDeflate = false
        val replies: MutableList<String> = mutableListOf()

        client.webSocket(host = "127.0.0.1", port = port, path = "/logs") {
            negotiatedHeader = call.response.headers[HttpHeaders.SecWebSocketExtensions]
            clientHasDeflate = extensionOrNull(WebSocketDeflateExtension) != null
            send(Frame.Text(batch))
            send(Frame.Text("flush"))
            repeat(REPLIES_PER_SESSION) {
                replies += (incoming.receive() as Frame.Text).readText()
            }
            close(CloseReason(CloseReason.Codes.NORMAL, "agent stopped"))
        }
        client.close()

        assertNotNull(negotiatedHeader, "the upgrade should answer the deflate offer")
        val header: String = negotiatedHeader!!
        assertTrue(header.contains("permessage-deflate"), header)
        assertTrue(header.contains("server_no_context_takeover"), "offered parameter accepted: $header")
        assertTrue(clientHasDeflate, "the client session should carry the negotiated extension")
        assertEquals(
            listOf(
                "deflate=true",
                "stored ${batch.length} chars in $BATCH_LINES lines",
                "stored 5 chars in 1 lines",
                LogBatch.of("collector", BATCH_LINES),
            ),
            replies,
        )
    }

    @Test
    fun servesPlainFramesWhenTheClientOffersNoExtension(): Unit = withLogEndpoint { port: Int ->
        val client: HttpClient = HttpClient(ClientCIO) {
            install(ClientWebSockets)
        }
        var negotiatedHeader: String? = "unset"
        var clientHasDeflate = true
        val replies: MutableList<String> = mutableListOf()

        client.webSocket(host = "127.0.0.1", port = port, path = "/logs") {
            negotiatedHeader = call.response.headers[HttpHeaders.SecWebSocketExtensions]
            clientHasDeflate = extensionOrNull(WebSocketDeflateExtension) != null
            send(Frame.Text("one line"))
            send(Frame.Text("flush"))
            repeat(REPLIES_PER_SESSION) {
                replies += (incoming.receive() as Frame.Text).readText()
            }
            close(CloseReason(CloseReason.Codes.NORMAL, "agent stopped"))
        }
        client.close()

        assertNull(negotiatedHeader, "no extension should be negotiated without an offer")
        assertFalse(clientHasDeflate, "the client session should carry no deflate extension")
        assertEquals(
            listOf(
                "deflate=false",
                "stored 8 chars in 1 lines",
                "stored 5 chars in 1 lines",
                LogBatch.of("collector", BATCH_LINES),
            ),
            replies,
        )
    }

    /** Starts the log endpoint on an ephemeral port, runs [block] against it, and stops it. */
    private fun withLogEndpoint(block: suspend (Int) -> Unit) {
        val server: EmbeddedServer<*, *> = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            logEndpoint()
        }
        server.start(wait = false)
        try {
            runBlocking {
                val port: Int = server.engine.resolvedConnectors().first().port
                block(port)
            }
        } finally {
            server.stop(gracePeriodMillis = 0, timeoutMillis = STOP_TIMEOUT_MILLIS)
        }
    }

    private fun Application.logEndpoint() {
        install(WebSockets) {
            extensions {
                install(WebSocketDeflateExtension) {
                    compressIfBiggerThan(COMPRESSION_THRESHOLD)
                }
            }
        }
        routing {
            webSocket("/logs") {
                send(Frame.Text("deflate=${extensionOrNull(WebSocketDeflateExtension) != null}"))
                for (frame: Frame in incoming) {
                    val text: String = (frame as Frame.Text).readText()
                    send(Frame.Text("stored ${text.length} chars in ${text.lines().size} lines"))
                    if (text == "flush") {
                        send(Frame.Text(LogBatch.of("collector", BATCH_LINES)))
                    }
                }
            }
        }
    }

    private companion object {

        const val COMPRESSION_THRESHOLD: Int = 256

        const val BATCH_LINES: Int = 400

        const val REPLIES_PER_SESSION: Int = 4

        const val STOP_TIMEOUT_MILLIS: Long = 5_000L
    }
}
