/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_cio_jvm

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.contentLength
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * The CIO engine's HTTP/1.1 connection handling: pipelined requests answered in order on one
 * connection, `Expect: 100-continue` negotiation, idle-timeout closing of quiet keep-alive
 * connections, and graceful shutdown that lets an in-flight request finish.
 *
 * A photo lab's scan station talks to the lab server over plain sockets, the way embedded
 * scanner firmware does. It pipelines status queries without waiting for each answer, asks
 * permission before uploading a scan so oversized scans are refused before their bytes are
 * sent, and leaves its connection idle between jobs. At closing time the operator stops the
 * server while a print job is still rendering, and the job's answer must still arrive.
 */
class PhotoLabConnectionHandlingTest {

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0
    private var stopped: Boolean = false
    private val scans: MutableMap<String, Int> = ConcurrentHashMap()
    private val queueReleased: CompletableDeferred<Unit> = CompletableDeferred()
    private val printStarted: CountDownLatch = CountDownLatch(1)
    private val printMayFinish: CompletableDeferred<Unit> = CompletableDeferred()

    @BeforeEach
    fun start() {
        server = embeddedServer(
            CIO,
            configure = {
                connector {
                    host = "127.0.0.1"
                    port = 0
                }
                connectionIdleTimeoutSeconds = IDLE_TIMEOUT_SECONDS
            },
            module = { labModule() },
        ).start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().single().port }
    }

    @AfterEach
    fun stop() {
        if (!stopped) {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
        }
    }

    @Test
    fun pipelinedQueriesAreAnsweredInRequestOrder() {
        StationConnection(port).use { station: StationConnection ->
            station.send(
                """
                GET /queue/wait HTTP/1.1
                Host: lab

                GET /queue/release HTTP/1.1
                Host: lab

                GET /queue/depth HTTP/1.1
                Host: lab


                """.trimIndent().replace("\n", "\r\n"),
            )

            val waited: RawResponse = station.readResponse()
            assertEquals(200, waited.status)
            assertEquals("queue drained", waited.body)

            val released: RawResponse = station.readResponse()
            assertEquals(200, released.status)
            assertEquals("queue released", released.body)

            val depth: RawResponse = station.readResponse()
            assertEquals(200, depth.status)
            assertEquals("0 scans waiting", depth.body)
        }
    }

    @Test
    fun scanUploadsNegotiateExpectContinue() {
        StationConnection(port).use { station: StationConnection ->
            val scan = "film-roll-12 frame-07 ".repeat(20)
            station.send(
                """
                POST /scans/roll-12 HTTP/1.1
                Host: lab
                Content-Type: application/octet-stream
                Content-Length: ${scan.length}
                Expect: 100-continue


                """.trimIndent().replace("\n", "\r\n"),
            )
            val permission: RawResponse = station.readInterim()
            assertEquals(100, permission.status)

            station.send(scan)
            val stored: RawResponse = station.readResponse()
            assertEquals(201, stored.status)
            assertEquals("stored roll-12 (${scan.length} bytes)", stored.body)
            assertNull(stored.headers["connection"])

            station.send(
                """
                POST /scans/roll-13 HTTP/1.1
                Host: lab
                Content-Type: application/octet-stream
                Content-Length: 4
                Expect: scan-format/tiff


                """.trimIndent().replace("\n", "\r\n"),
            )
            val unsupported: RawResponse = station.readResponse()
            assertEquals(417, unsupported.status)
        }

        StationConnection(port).use { station: StationConnection ->
            station.send(
                """
                POST /scans/roll-14 HTTP/1.1
                Host: lab
                Content-Type: application/octet-stream
                Content-Length: ${MAX_SCAN_BYTES + 1}
                Expect: 100-continue
                Connection: close


                """.trimIndent().replace("\n", "\r\n"),
            )
            val refused: RawResponse = station.readResponse()
            assertEquals(413, refused.status)
            assertEquals("scan exceeds $MAX_SCAN_BYTES bytes", refused.body)
        }

        assertEquals(mapOf("roll-12" to 440), scans.toMap())
    }

    @Test
    fun quietKeepAliveConnectionsAreClosedAfterTheIdleTimeout() {
        StationConnection(port).use { station: StationConnection ->
            station.send("GET /queue/depth HTTP/1.1\r\nHost: lab\r\n\r\n")
            val depth: RawResponse = station.readResponse()
            assertEquals(200, depth.status)
            assertNull(depth.headers["connection"])

            assertEquals(-1, station.awaitEndOfStream(), "server closes the idle connection")
        }
    }

    @Test
    fun gracefulShutdownLetsAnInFlightPrintJobFinish() {
        val printResponse: AtomicReference<RawResponse> = AtomicReference()
        val printFailure: AtomicReference<Throwable> = AtomicReference()
        val printer: Thread = thread(name = "print-client") {
            try {
                StationConnection(port).use { station: StationConnection ->
                    station.send("GET /prints/roll-12 HTTP/1.1\r\nHost: lab\r\n\r\n")
                    printResponse.set(station.readResponse())
                }
            } catch (failure: Throwable) {
                printFailure.set(failure)
            }
        }
        assertTrue(printStarted.await(30, TimeUnit.SECONDS), "print job started")

        val stopper: Thread = thread(name = "operator-stop") {
            server.stop(gracePeriodMillis = 20_000, timeoutMillis = 30_000)
        }
        stopped = true
        assertTrue(awaitRefusedConnections(), "server stops accepting new stations")

        printMayFinish.complete(Unit)
        printer.join(30_000)
        stopper.join(30_000)

        assertNull(printFailure.get())
        val print: RawResponse = printResponse.get()
        assertEquals(200, print.status)
        assertEquals("print of roll-12 ready", print.body)
        assertTrue(!stopper.isAlive, "server stopped")
    }

    private fun Application.labModule() {
        routing {
            get("/queue/wait") {
                withTimeout(30_000) { queueReleased.await() }
                call.respondText("queue drained", ContentType.Text.Plain)
            }
            get("/queue/release") {
                queueReleased.complete(Unit)
                call.respondText("queue released", ContentType.Text.Plain)
            }
            get("/queue/depth") {
                call.respondText("${scans.size} scans waiting", ContentType.Text.Plain)
            }
            post("/scans/{roll}") {
                val roll: String = call.parameters["roll"]!!
                val declared: Long = call.request.contentLength() ?: 0
                if (declared > MAX_SCAN_BYTES) {
                    return@post call.respondText(
                        "scan exceeds $MAX_SCAN_BYTES bytes",
                        status = HttpStatusCode.PayloadTooLarge,
                    )
                }
                // A refused expectation has already been answered with 417 and yields no body.
                val scan: ByteArray = call.receive<ByteArray>()
                if (scan.isEmpty()) {
                    return@post call.respondText("empty scan", status = HttpStatusCode.BadRequest)
                }
                scans[roll] = scan.size
                call.respondText("stored $roll (${scan.size} bytes)", status = HttpStatusCode.Created)
            }
            get("/prints/{roll}") {
                printStarted.countDown()
                withTimeout(30_000) { printMayFinish.await() }
                call.respondText("print of ${call.parameters["roll"]} ready", ContentType.Text.Plain)
            }
        }
    }

    /** Polls, within a bound, until the stopping server refuses new connections. */
    private fun awaitRefusedConnections(): Boolean {
        val deadline: Long = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (System.nanoTime() < deadline) {
            try {
                Socket().use { probe: Socket -> probe.connect(InetSocketAddress("127.0.0.1", port), 1_000) }
            } catch (refused: ConnectException) {
                return true
            }
            Thread.sleep(50)
        }
        return false
    }

    /** A status line, lower-cased headers and a `Content-Length` delimited body. */
    private class RawResponse(val status: Int, val headers: Map<String, String>, val body: String)

    /** A scan station speaking HTTP/1.1 by hand over one blocking socket. */
    private class StationConnection(port: Int) : AutoCloseable {
        private val socket: Socket = Socket()
        private val input: InputStream
        private val output: OutputStream

        init {
            socket.connect(InetSocketAddress("127.0.0.1", port), 10_000)
            socket.soTimeout = 30_000
            input = BufferedInputStream(socket.getInputStream())
            output = socket.getOutputStream()
        }

        fun send(text: String) {
            output.write(text.toByteArray(Charsets.US_ASCII))
            output.flush()
        }

        /** Reads an interim `1xx` response, which carries headers but never a body. */
        fun readInterim(): RawResponse {
            val (status: Int, headers: Map<String, String>) = readHead()
            return RawResponse(status, headers, "")
        }

        fun readResponse(): RawResponse {
            val (status: Int, headers: Map<String, String>) = readHead()
            val length: Int = headers["content-length"]?.toInt() ?: 0
            val body = ByteArray(length)
            var read = 0
            while (read < length) {
                val count: Int = input.read(body, read, length - read)
                assertTrue(count > 0, "response body truncated")
                read += count
            }
            return RawResponse(status, headers, String(body, Charsets.UTF_8))
        }

        /** Blocks until the server closes the connection; the socket timeout bounds the wait. */
        fun awaitEndOfStream(): Int = input.read()

        private fun readHead(): Pair<Int, Map<String, String>> {
            val statusLine: String = readLine()
            assertTrue(statusLine.startsWith("HTTP/1.1 "), statusLine)
            val status: Int = statusLine.split(' ')[1].toInt()
            val headers: MutableMap<String, String> = LinkedHashMap()
            while (true) {
                val line: String = readLine()
                if (line.isEmpty()) {
                    break
                }
                val separator: Int = line.indexOf(':')
                headers[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
            }
            return status to headers
        }

        private fun readLine(): String {
            val line = ByteArrayOutputStream()
            while (true) {
                val next: Int = input.read()
                assertTrue(next >= 0, "connection closed mid-response")
                if (next == '\n'.code) {
                    break
                }
                if (next != '\r'.code) {
                    line.write(next)
                }
            }
            return line.toString(Charsets.US_ASCII)
        }

        override fun close() {
            socket.close()
        }
    }

    private companion object {
        const val IDLE_TIMEOUT_SECONDS: Int = 2
        const val MAX_SCAN_BYTES: Long = 64 * 1024
    }
}
