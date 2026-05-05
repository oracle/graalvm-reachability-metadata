/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_cio_jvm

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

public class Ktor_server_cio_jvmTest {
    @Test
    fun `cio engine serves routed text responses with parameters headers and status`() {
        withCioServer(
            module = {
                routing {
                    get("/hello/{name}") {
                        val name: String = call.parameters["name"] ?: "missing"
                        call.response.headers.append("X-Test-Engine", "cio")
                        call.respondText(
                            text = "Hello, $name!",
                            contentType = ContentType.Text.Plain,
                            status = HttpStatusCode.Accepted,
                        )
                    }
                }
            },
        ) { baseUri: URI ->
            val executor: ExecutorService = Executors.newSingleThreadExecutor()
            val client: HttpClient = newClient(executor)
            try {
                val response: HttpResponse<String> = client.send(
                    request(baseUri.resolve("/hello/native-image")).GET().build(),
                    HttpResponse.BodyHandlers.ofString(),
                )

                assertThat(response.statusCode()).isEqualTo(HttpStatusCode.Accepted.value)
                assertThat(response.headers().firstValue("X-Test-Engine")).hasValue("cio")
                assertThat(response.headers().firstValue(HttpHeaders.ContentType))
                    .hasValueSatisfying { value: String ->
                        assertThat(value).startsWith(ContentType.Text.Plain.toString())
                    }
                assertThat(response.body()).isEqualTo("Hello, native-image!")
            } finally {
                client.close()
                executor.shutdownNow()
                assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue()
            }
        }
    }

    @Test
    fun `cio engine receives request bodies and returns binary payloads`() {
        withCioServer(
            module = {
                routing {
                    post("/echo") {
                        val mode: String = call.request.header("X-Echo-Mode") ?: "plain"
                        val body: String = call.receiveText()

                        call.respondText(
                            text = "$mode:$body",
                            contentType = ContentType.Text.Plain,
                        )
                    }
                    get("/bytes") {
                        call.respondBytes(
                            bytes = byteArrayOf(0, 1, 2, 3, 127, -1),
                            contentType = ContentType.Application.OctetStream,
                            status = HttpStatusCode.OK,
                        )
                    }
                }
            },
        ) { baseUri: URI ->
            val executor: ExecutorService = Executors.newSingleThreadExecutor()
            val client: HttpClient = newClient(executor)
            try {
                val echoResponse: HttpResponse<String> = client.send(
                    request(baseUri.resolve("/echo"))
                        .header("X-Echo-Mode", "upper")
                        .POST(HttpRequest.BodyPublishers.ofString("ktor-cio"))
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
                val bytesResponse: HttpResponse<ByteArray> = client.send(
                    request(baseUri.resolve("/bytes")).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray(),
                )

                assertThat(echoResponse.statusCode()).isEqualTo(HttpStatusCode.OK.value)
                assertThat(echoResponse.body()).isEqualTo("upper:ktor-cio")
                assertThat(bytesResponse.statusCode()).isEqualTo(HttpStatusCode.OK.value)
                assertThat(bytesResponse.headers().firstValue(HttpHeaders.ContentType))
                    .hasValueSatisfying { value: String ->
                        assertThat(value).isEqualTo(ContentType.Application.OctetStream.toString())
                    }
                assertThat(bytesResponse.body()).containsExactly(*byteArrayOf(0, 1, 2, 3, 127, -1))
            } finally {
                client.close()
                executor.shutdownNow()
                assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue()
            }
        }
    }

    @Test
    fun `cio engine streams text writer responses`() {
        withCioServer(
            module = {
                routing {
                    get("/stream") {
                        call.respondTextWriter(contentType = ContentType.Text.Plain) {
                            appendLine("first")
                            flush()
                            appendLine("second")
                        }
                    }
                }
            },
        ) { baseUri: URI ->
            val executor: ExecutorService = Executors.newSingleThreadExecutor()
            val client: HttpClient = newClient(executor)
            try {
                val response: HttpResponse<String> = client.send(
                    request(baseUri.resolve("/stream")).GET().build(),
                    HttpResponse.BodyHandlers.ofString(),
                )

                assertThat(response.statusCode()).isEqualTo(HttpStatusCode.OK.value)
                assertThat(
                    response.body().lineSequence().filter { line: String -> line.isNotBlank() }.toList(),
                )
                    .containsExactly("first", "second")
            } finally {
                client.close()
                executor.shutdownNow()
                assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue()
            }
        }
    }

    @Test
    fun `cio engine handles concurrent requests on the same connector`() {
        withCioServer(
            module = {
                routing {
                    get("/sum/{left}/{right}") {
                        val left: Int = call.parameters["left"]?.toInt() ?: 0
                        val right: Int = call.parameters["right"]?.toInt() ?: 0

                        call.respondText((left + right).toString(), ContentType.Text.Plain)
                    }
                }
            },
        ) { baseUri: URI ->
            val clientExecutor: ExecutorService = Executors.newSingleThreadExecutor()
            val workerExecutor: ExecutorService = Executors.newFixedThreadPool(4)
            val client: HttpClient = newClient(clientExecutor)
            try {
                val tasks: List<Callable<Pair<Int, String>>> = (1..8).map { index: Int ->
                    Callable {
                        val response: HttpResponse<String> = client.send(
                            request(baseUri.resolve("/sum/$index/${index * 2}")).GET().build(),
                            HttpResponse.BodyHandlers.ofString(),
                        )
                        response.statusCode() to response.body()
                    }
                }

                val results: List<Pair<Int, String>> = workerExecutor
                    .invokeAll(tasks, 10, TimeUnit.SECONDS)
                    .map { future -> future.get(10, TimeUnit.SECONDS) }

                assertThat(results).containsExactlyElementsOf(
                    (1..8).map { index: Int -> HttpStatusCode.OK.value to (index * 3).toString() },
                )
            } finally {
                client.close()
                workerExecutor.shutdownNow()
                clientExecutor.shutdownNow()
                assertThat(workerExecutor.awaitTermination(10, TimeUnit.SECONDS)).isTrue()
                assertThat(clientExecutor.awaitTermination(10, TimeUnit.SECONDS)).isTrue()
            }
        }
    }

    private fun withCioServer(module: Application.() -> Unit, test: (URI) -> Unit) {
        val port: Int = findAvailablePort()
        val server = embeddedServer(CIO, host = LOOPBACK_HOST, port = port, module = module)

        server.start(wait = false)
        try {
            test(URI("http://$LOOPBACK_HOST:$port"))
        } finally {
            server.stop(0L, 5_000L)
        }
    }

    private fun newClient(executor: ExecutorService): HttpClient = HttpClient.newBuilder()
        .connectTimeout(REQUEST_TIMEOUT)
        .executor(executor)
        .version(HttpClient.Version.HTTP_1_1)
        .build()

    private fun request(uri: URI): HttpRequest.Builder = HttpRequest.newBuilder(uri)
        .timeout(REQUEST_TIMEOUT)

    private fun findAvailablePort(): Int = ServerSocket().use { socket: ServerSocket ->
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(InetAddress.getByName(LOOPBACK_HOST), 0))
        socket.localPort
    }

    private companion object {
        private const val LOOPBACK_HOST: String = "127.0.0.1"
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
