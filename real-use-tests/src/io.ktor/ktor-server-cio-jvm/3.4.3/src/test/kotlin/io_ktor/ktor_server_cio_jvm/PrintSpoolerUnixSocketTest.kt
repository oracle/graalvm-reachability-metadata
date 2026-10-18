/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.UnixSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.cio.unixConnector
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readByteArray
import io.ktor.utils.io.readLineStrict
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

/**
 * The CIO engine's Unix domain socket connector: one application served on a TCP connector
 * and a Unix domain socket connector at once, with keep-alive and `Connection: close`
 * handling on the socket.
 *
 * A print spooler daemon publishes a read-only status page on TCP and its control API on a
 * local socket file, the way container daemons do. An operator tool connects to the socket,
 * submits two jobs and lists the queue over one kept-alive connection, then cancels a job on
 * a second connection that asks to be closed. The test checks the raw HTTP/1.1 exchanges on
 * the socket, the `Connection: close` acknowledgement, that the TCP connector refuses control
 * calls, and that both connectors see the same queue.
 */
class PrintSpoolerUnixSocketTest {

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private lateinit var socketDirectory: Path
    private lateinit var socketPath: String
    private var port: Int = 0
    private val queue: MutableMap<Int, String> = ConcurrentHashMap()
    private val nextJobId: AtomicInteger = AtomicInteger(100)

    @BeforeEach
    fun start() {
        socketDirectory = Files.createTempDirectory("spool")
        socketPath = socketDirectory.resolve("spooler.sock").toString()
        server = embeddedServer(
            ServerCIO,
            configure = {
                connector {
                    host = "127.0.0.1"
                    port = 0
                }
                unixConnector(socketPath)
                connectionIdleTimeoutSeconds = 30
            },
            module = { spoolerModule() },
        ).start(wait = false)
        port = runBlocking {
            server.engine.resolvedConnectors().first { connector -> connector.host == "127.0.0.1" }.port
        }
    }

    @AfterEach
    fun stop() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
        Files.deleteIfExists(Path.of(socketPath))
        Files.deleteIfExists(socketDirectory)
    }

    @Test
    fun operatorControlsTheSpoolerOverItsSocketWhileTcpServesStatus() {
        assertTrue(Files.exists(Path.of(socketPath)), "socket file $socketPath")

        runBlocking {
            withTimeout(30_000) {
                SelectorManager(Dispatchers.IO).use { selector: SelectorManager ->
                    aSocket(selector).tcp().connect(UnixSocketAddress(socketPath)).use { socket: Socket ->
                        val operator = OperatorConnection(socket)

                        val first: RawResponse = operator.exchange("POST", "/jobs", "quarterly-report.pdf")
                        assertEquals(201, first.status)
                        assertEquals("job 100 queued", first.body)
                        assertEquals("100", first.headers["location-job"])
                        assertNull(first.headers["connection"])

                        val second: RawResponse = operator.exchange("POST", "/jobs", "shipping-labels.pdf")
                        assertEquals(201, second.status)
                        assertEquals("job 101 queued", second.body)

                        val listing: RawResponse = operator.exchange("GET", "/jobs", null)
                        assertEquals(200, listing.status)
                        assertEquals("100 quarterly-report.pdf\n101 shipping-labels.pdf", listing.body)
                        assertTrue(listing.headers["content-type"]!!.startsWith("text/plain"))

                        val empty: RawResponse = operator.exchange("POST", "/jobs", "")
                        assertEquals(400, empty.status)
                        assertEquals("document name required", empty.body)
                    }

                    aSocket(selector).tcp().connect(UnixSocketAddress(socketPath)).use { socket: Socket ->
                        val operator = OperatorConnection(socket)
                        val cancelled: RawResponse = operator.exchange("DELETE", "/jobs/100", null, close = true)
                        assertEquals(200, cancelled.status)
                        assertEquals("job 100 cancelled", cancelled.body)
                        assertEquals("close", cancelled.headers["connection"]?.lowercase())
                    }
                }
            }
        }

        HttpClient(ClientCIO).use { client: HttpClient ->
            runBlocking {
                val status: HttpResponse = client.get("http://127.0.0.1:$port/status")
                assertEquals(HttpStatusCode.OK, status.status)
                assertEquals("spooler online, 1 job(s) waiting: shipping-labels.pdf", status.bodyAsText())

                val refused: HttpResponse = client.post("http://127.0.0.1:$port/jobs") {
                    setBody("intruder.pdf")
                }
                assertEquals(HttpStatusCode.Forbidden, refused.status)
                assertEquals("control API is only available on the local socket", refused.bodyAsText())
            }
        }

        assertEquals(mapOf(101 to "shipping-labels.pdf"), queue.toMap())
    }

    private fun Application.spoolerModule() {
        routing {
            get("/status") {
                val waiting: String = queue.toSortedMap().values.joinToString(", ")
                call.respondText("spooler online, ${queue.size} job(s) waiting: $waiting", ContentType.Text.Plain)
            }
            post("/jobs") {
                if (!call.onControlSocket()) {
                    return@post call.refuseControl()
                }
                val document: String = call.receiveText().trim()
                if (document.isEmpty()) {
                    return@post call.respondText("document name required", status = HttpStatusCode.BadRequest)
                }
                val id: Int = nextJobId.getAndIncrement()
                queue[id] = document
                call.response.headers.append("Location-Job", id.toString())
                call.respondText("job $id queued", ContentType.Text.Plain, HttpStatusCode.Created)
            }
            get("/jobs") {
                if (!call.onControlSocket()) {
                    return@get call.refuseControl()
                }
                val listing: String = queue.toSortedMap().entries.joinToString("\n") { (id, name) -> "$id $name" }
                call.respondText(listing, ContentType.Text.Plain)
            }
            delete("/jobs/{id}") {
                if (!call.onControlSocket()) {
                    return@delete call.refuseControl()
                }
                val id: Int = call.parameters["id"]!!.toInt()
                val removed: String? = queue.remove(id)
                if (removed == null) {
                    call.respondText("no job $id", status = HttpStatusCode.NotFound)
                } else {
                    call.respondText("job $id cancelled", ContentType.Text.Plain)
                }
            }
        }
    }

    /** Calls on the socket connector do not arrive on the TCP connector's local port. */
    private fun ApplicationCall.onControlSocket(): Boolean = request.local.localPort != port

    private suspend fun ApplicationCall.refuseControl() {
        respondText("control API is only available on the local socket", status = HttpStatusCode.Forbidden)
    }

    /** A status line, lower-cased headers and a `Content-Length` delimited body. */
    private class RawResponse(val status: Int, val headers: Map<String, String>, val body: String)

    /** An operator tool speaking HTTP/1.1 by hand over one socket connection. */
    private class OperatorConnection(socket: Socket) {
        private val input: ByteReadChannel = socket.openReadChannel()
        private val output: ByteWriteChannel = socket.openWriteChannel(autoFlush = true)

        suspend fun exchange(method: String, path: String, body: String?, close: Boolean = false): RawResponse {
            val payload: ByteArray = body?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
            val request = StringBuilder()
                .append("$method $path HTTP/1.1\r\n")
                .append("Host: spooler\r\n")
                .append("User-Agent: spoolctl/1.0\r\n")
            if (body != null) {
                request.append("Content-Type: text/plain; charset=UTF-8\r\n")
                request.append("Content-Length: ${payload.size}\r\n")
            }
            if (close) {
                request.append("Connection: close\r\n")
            }
            request.append("\r\n")
            output.writeStringUtf8(request.toString())
            if (body != null) {
                output.writeStringUtf8(body)
            }
            output.flush()
            return readResponse()
        }

        private suspend fun readResponse(): RawResponse {
            val statusLine: String = input.readLineStrict()!!
            assertTrue(statusLine.startsWith("HTTP/1.1 "), statusLine)
            val status: Int = statusLine.split(' ')[1].toInt()
            val headers: MutableMap<String, String> = LinkedHashMap()
            while (true) {
                val line: String = input.readLineStrict()!!
                if (line.isEmpty()) {
                    break
                }
                val separator: Int = line.indexOf(':')
                headers[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
            }
            val length: Int = headers["content-length"]!!.toInt()
            val body = String(input.readByteArray(length), Charsets.UTF_8)
            return RawResponse(status, headers, body)
        }
    }
}
