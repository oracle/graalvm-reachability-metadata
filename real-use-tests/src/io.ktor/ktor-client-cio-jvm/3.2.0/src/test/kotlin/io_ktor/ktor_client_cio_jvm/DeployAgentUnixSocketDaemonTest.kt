/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.unixSocket
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.cio.unixConnector
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

/**
 * HTTP over Unix domain sockets with the CIO client engine (`unixSocket(path)` on a request).
 *
 * A deployment agent drives a local container daemon that, like the Docker daemon, exposes its
 * HTTP API only on a Unix socket file rather than a TCP port. The agent addresses the daemon with
 * an ordinary URL whose host never resolves and routes each request to the socket file, so the
 * daemon sees the agent's requests and the agent reads the daemon's answers as plain HTTP.
 */
class DeployAgentUnixSocketDaemonTest {

    private lateinit var socketDirectory: Path
    private lateinit var socketPath: String
    private lateinit var daemon: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>

    /** Containers the daemon runs, by name, with the image each one was created from. */
    private val containers: ConcurrentHashMap<String, String> = ConcurrentHashMap()

    /** `Host` header of every request the daemon received, in arrival order. */
    private val hostHeaders: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()

    @BeforeEach
    fun startDaemon() {
        // Socket paths are capped near 100 bytes, so the file lives in a short temporary directory.
        socketDirectory = Files.createTempDirectory("deployd")
        socketPath = socketDirectory.resolve("daemon.sock").toString()
        containers["registry-cache"] = "registry:2"
        daemon = embeddedServer(ServerCIO, configure = { unixConnector(socketPath) }) {
            routing {
                get("/containers") {
                    hostHeaders += call.request.headers["Host"].orEmpty()
                    val listing: String = containers.entries
                        .sortedBy { it.key }
                        .joinToString("\n") { "${it.key} ${it.value}" }
                    call.respondText(listing)
                }
                post("/containers/{name}") {
                    hostHeaders += call.request.headers["Host"].orEmpty()
                    val name: String = call.parameters["name"]!!
                    val image: String = call.receiveText()
                    if (containers.putIfAbsent(name, image) == null) {
                        call.respondText("created $name from $image", status = HttpStatusCode.Created)
                    } else {
                        call.respondText("$name already exists", status = HttpStatusCode.Conflict)
                    }
                }
            }
        }.start(wait = false)
        // Returns once the connector is bound, so the socket file exists before the agent connects.
        runBlocking { daemon.engine.resolvedConnectors() }
    }

    @AfterEach
    fun stopDaemon() {
        daemon.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
        Files.deleteIfExists(Path.of(socketPath))
        Files.deleteIfExists(socketDirectory)
    }

    @Test
    fun agentDrivesTheDaemonThroughItsUnixSocket() {
        assertTrue(Files.exists(Path.of(socketPath)))

        HttpClient(ClientCIO).use { agent ->
            runBlocking {
                val created: HttpResponse = agent.post("$DAEMON_URL/containers/web") {
                    unixSocket(socketPath)
                    contentType(ContentType.Text.Plain)
                    setBody("nginx:1.27")
                }
                assertEquals(HttpStatusCode.Created, created.status)
                assertEquals("created web from nginx:1.27", created.bodyAsText())

                val duplicate: HttpResponse = agent.post("$DAEMON_URL/containers/web") {
                    unixSocket(socketPath)
                    contentType(ContentType.Text.Plain)
                    setBody("nginx:1.28")
                }
                assertEquals(HttpStatusCode.Conflict, duplicate.status)
                assertEquals("web already exists", duplicate.bodyAsText())

                val listing: HttpResponse = agent.get("$DAEMON_URL/containers") {
                    unixSocket(socketPath)
                }
                assertEquals(HttpStatusCode.OK, listing.status)
                assertEquals("registry-cache registry:2\nweb nginx:1.27", listing.bodyAsText())
            }
        }

        assertEquals(mapOf("registry-cache" to "registry:2", "web" to "nginx:1.27"), containers.toMap())
        assertEquals(List(3) { DAEMON_HOST }, hostHeaders.toList())
    }

    private companion object {
        /** Host the agent names in its URLs; `.invalid` never resolves, so only the socket can reach it. */
        const val DAEMON_HOST: String = "deployd.invalid"
        const val DAEMON_URL: String = "http://$DAEMON_HOST"
    }
}
