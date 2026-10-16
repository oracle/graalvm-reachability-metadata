/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_http_cio_jvm

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.cio.Request
import io.ktor.http.cio.RequestResponseBuilder
import io.ktor.http.cio.Response
import io.ktor.http.cio.decodeChunked
import io.ktor.http.cio.parseHttpBody
import io.ktor.http.cio.parseRequest
import io.ktor.http.cio.parseResponse
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.WriterJob
import io.ktor.utils.io.readLine
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writePacket
import io.ktor.utils.io.writeStringUtf8
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.Source
import kotlinx.io.readByteArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers incremental chunked transfer decoding of a long-lived event stream with the
 * `decodeChunked` writer job: each event is decoded and handled before the origin sends the next,
 * the terminating chunk leaves the keep-alive connection ready for the next exchange, and a stream
 * cut off inside a chunk fails the decoder instead of yielding a short event.
 */
class ChunkedEventStreamTest {

    /** One server-sent event as the dashboard sees it. */
    private data class Event(val name: String, val data: String)

    /**
     * A price feed origin: `/feed` streams chunked events, releasing each one only after the
     * subscriber granted a permit, and `/status` answers with a fixed-length body.
     */
    private class FeedOrigin(private val server: ServerSocket, scope: CoroutineScope) {

        /** One permit per event the subscriber is ready to receive. */
        val permits: Channel<Unit> = Channel(Channel.UNLIMITED)

        /** Every request line served, in order, across all connections. */
        val served: Channel<String> = Channel(Channel.UNLIMITED)

        val port: Int = (server.localAddress as InetSocketAddress).port

        private val acceptor: Job = scope.launch {
            while (true) {
                val socket: Socket = server.accept()
                launch { serve(socket) }
            }
        }

        fun stop() {
            acceptor.cancel()
            server.close()
        }

        private suspend fun serve(socket: Socket) {
            val input: ByteReadChannel = socket.openReadChannel()
            val output: ByteWriteChannel = socket.openWriteChannel(autoFlush = false)
            try {
                while (true) {
                    val request: Request = parseRequest(input) ?: break
                    val path: String = request.use { it.uri.toString() }
                    served.send("GET $path")
                    when (path) {
                        "/feed" -> streamFeed(output, truncated = false)
                        "/feed?outage=true" -> {
                            streamFeed(output, truncated = true)
                            break
                        }
                        else -> writeStatus(output)
                    }
                }
            } finally {
                output.flushAndClose()
                socket.close()
            }
        }

        private suspend fun streamFeed(output: ByteWriteChannel, truncated: Boolean) {
            writeHead(
                output,
                HttpHeaders.ContentType to "text/event-stream",
                HttpHeaders.CacheControl to "no-cache",
                HttpHeaders.TransferEncoding to "chunked",
            )
            output.flush()
            permits.receive()
            writeChunk(output, "event: quote\ndata: ACME 101.25\n\n")
            output.flush()

            permits.receive()
            if (truncated) {
                output.writeStringUtf8("40\r\nevent: quote\ndata: AC")
                output.flush()
                return
            }
            // One event split across two chunks: the subscriber must reassemble it.
            writeChunk(output, "event: quote\nda")
            writeChunk(output, "ta: ACME 101.40\n\n")
            output.flush()

            permits.receive()
            // Chunk extensions carry no payload; the decoder must skip them.
            writeChunk(output, "event: halt\ndata: market closed\n\n", extension = ";reason=\"close; eod\"")
            output.writeStringUtf8("0\r\n\r\n")
            output.flush()
        }

        private suspend fun writeStatus(output: ByteWriteChannel) {
            val body: ByteArray = "feed healthy".encodeToByteArray()
            writeHead(
                output,
                HttpHeaders.ContentType to "text/plain",
                HttpHeaders.ContentLength to body.size.toString(),
            )
            output.writeFully(body)
            output.flush()
        }

        private suspend fun writeHead(output: ByteWriteChannel, vararg headers: Pair<String, String>) {
            val head = RequestResponseBuilder()
            try {
                head.responseLine("HTTP/1.1", HttpStatusCode.OK.value, HttpStatusCode.OK.description)
                head.headerLine(HttpHeaders.Server, "feed-origin/2.0")
                for ((name: String, value: String) in headers) {
                    head.headerLine(name, value)
                }
                head.emptyLine()
                val packet: Source = head.build()
                output.writePacket(packet)
            } finally {
                head.release()
            }
        }

        private suspend fun writeChunk(output: ByteWriteChannel, text: String, extension: String = "") {
            val bytes: ByteArray = text.encodeToByteArray()
            output.writeStringUtf8("${bytes.size.toString(16)}$extension\r\n")
            output.writeStringUtf8(text)
            output.writeStringUtf8("\r\n")
        }
    }

    /** A trading dashboard that subscribes to the feed over one keep-alive connection. */
    private class Dashboard(private val socket: Socket, private val permits: Channel<Unit>) {

        private val input: ByteReadChannel = socket.openReadChannel()

        private val output: ByteWriteChannel = socket.openWriteChannel(autoFlush = false)

        /** Every event handled, each recorded before the next permit is granted. */
        val handled: MutableList<Event> = mutableListOf()

        suspend fun subscribe(path: String): WriterJob = coroutineScope {
            val response: Response = get(path)
            val (contentType: String?, transferEncoding: String?) = response.use {
                assertEquals(HttpStatusCode.OK.value, it.status)
                it.headers[HttpHeaders.ContentType]?.toString() to it.headers[HttpHeaders.TransferEncoding]?.toString()
            }
            assertEquals("text/event-stream", contentType)
            assertEquals("chunked", transferEncoding)

            // The length is unknown up front: the stream ends at the terminating chunk.
            val decoder: WriterJob = decodeChunked(input, -1L)
            permits.send(Unit)
            try {
                while (true) {
                    val event: Event = readEvent(decoder.channel) ?: break
                    handled += event
                    if (event.name == "halt") {
                        break
                    }
                    permits.send(Unit)
                }
            } catch (_: IOException) {
                // A broken stream may surface here or as an early end; the decoder keeps the cause.
            }
            decoder.job.join()
            decoder
        }

        suspend fun status(): String {
            val response: Response = get("/status")
            return response.use {
                val sink = ByteChannel()
                parseHttpBody(it.headers, input, sink)
                sink.flushAndClose()
                sink.readRemaining().readByteArray().decodeToString()
            }
        }

        fun close() {
            socket.close()
        }

        private suspend fun get(path: String): Response {
            val head = RequestResponseBuilder()
            try {
                head.requestLine(HttpMethod.Get, path, "HTTP/1.1")
                head.headerLine(HttpHeaders.Host, "feed.example")
                head.headerLine(HttpHeaders.Accept, "text/event-stream")
                head.emptyLine()
                val packet: Source = head.build()
                output.writePacket(packet)
            } finally {
                head.release()
            }
            output.flush()
            return requireNotNull(parseResponse(input)) { "connection closed before a response" }
        }

        /** Reads `field: value` lines up to the blank line that ends one event. */
        private suspend fun readEvent(stream: ByteReadChannel): Event? {
            var name: String? = null
            val data: StringBuilder = StringBuilder()
            while (true) {
                val line: String = stream.readLine() ?: return null
                if (line.isEmpty()) {
                    return Event(requireNotNull(name) { "event without a name" }, data.toString())
                }
                val field: String = line.substringBefore(": ")
                val value: String = line.substringAfter(": ")
                when (field) {
                    "event" -> name = value
                    "data" -> data.append(value)
                }
            }
        }
    }

    @Test
    fun eventsAreDecodedOneChunkAtATimeAndTheConnectionStaysUsable(): Unit = runBlocking {
        withFeedOrigin { origin: FeedOrigin, selector: SelectorManager ->
            val dashboard: Dashboard = connect(selector, origin)
            val decoder: WriterJob = dashboard.subscribe("/feed")

            assertEquals(
                listOf(
                    Event("quote", "ACME 101.25"),
                    Event("quote", "ACME 101.40"),
                    Event("halt", "market closed"),
                ),
                dashboard.handled,
            )
            assertTrue(decoder.channel.isClosedForRead)
            assertTrue(decoder.job.isCompleted)
            assertFalse(decoder.job.isCancelled)
            assertNull(decoder.channel.closedCause)

            assertEquals("feed healthy", dashboard.status())
            dashboard.close()
            assertEquals("GET /feed", origin.served.receive())
            assertEquals("GET /status", origin.served.receive())
        }
    }

    @Test
    fun aStreamCutInsideAChunkFailsTheDecoder(): Unit = runBlocking {
        withFeedOrigin { origin: FeedOrigin, selector: SelectorManager ->
            val dashboard: Dashboard = connect(selector, origin)
            val decoder: WriterJob = dashboard.subscribe("/feed?outage=true")
            dashboard.close()

            assertEquals(listOf(Event("quote", "ACME 101.25")), dashboard.handled)
            assertTrue(decoder.channel.isClosedForRead)
            assertInstanceOf(IOException::class.java, decoder.channel.closedCause)
            assertEquals("GET /feed?outage=true", origin.served.receive())
        }
    }

    private companion object {

        suspend fun connect(selector: SelectorManager, origin: FeedOrigin): Dashboard =
            Dashboard(aSocket(selector).tcp().connect("127.0.0.1", origin.port), origin.permits)

        suspend fun withFeedOrigin(block: suspend (FeedOrigin, SelectorManager) -> Unit) {
            val selector = SelectorManager(Dispatchers.IO)
            try {
                withTimeout(30_000) {
                    val origin = FeedOrigin(aSocket(selector).tcp().bind("127.0.0.1", 0), this)
                    try {
                        block(origin, selector)
                    } finally {
                        origin.stop()
                    }
                }
            } finally {
                selector.close()
            }
        }
    }
}
