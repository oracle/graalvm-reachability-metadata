/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_java_jvm

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.java.Java
import io.ktor.client.engine.java.JavaHttpConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.withCharset
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.URL
import java.net.http.HttpClient as JdkHttpClient
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

public class Ktor_client_java_jvmTest {
    @Test
    fun `get request sends headers query parameters and reads response metadata`(): Unit = runBlocking {
        LocalTestServer().use { server: LocalTestServer ->
            server.handle("/inspect") { exchange: HttpExchange ->
                val body: String = buildString {
                    appendLine("method=${exchange.requestMethod}")
                    appendLine("query=${exchange.requestURI.rawQuery}")
                    appendLine("header=${exchange.requestHeaders.getFirst("X-Test-Header")}")
                    appendLine("protocol=${exchange.protocol}")
                }
                exchange.respond(
                    statusCode = 200,
                    body = body,
                    headers = mapOf("X-Engine-Test" to "received"),
                )
            }
            server.start()

            withJavaClient { client: HttpClient ->
                val response = client.get(server.url("/inspect")) {
                    parameter("alpha", "one")
                    parameter("encoded", "a b")
                    header("X-Test-Header", "present")
                }

                assertThat(response.status).isEqualTo(HttpStatusCode.OK)
                assertThat(response.headers["X-Engine-Test"]).isEqualTo("received")
                assertThat(response.bodyAsText())
                    .contains("method=GET")
                    .contains("alpha=one")
                    .contains("encoded=")
                    .contains("header=present")
                    .contains("protocol=HTTP/1.1")
            }
        }
    }

    @Test
    fun `post request publishes text body and preserves content headers`(): Unit = runBlocking {
        LocalTestServer().use { server: LocalTestServer ->
            server.handle("/echo") { exchange: HttpExchange ->
                val requestBody: String = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
                val body: String = buildString {
                    appendLine("method=${exchange.requestMethod}")
                    appendLine("body=$requestBody")
                    appendLine("contentType=${exchange.requestHeaders.getFirst(HttpHeaders.ContentType)}")
                    appendLine("requestId=${exchange.requestHeaders.getFirst("X-Request-Id")}")
                }
                exchange.respond(
                    statusCode = 201,
                    body = body,
                    headers = mapOf("X-Body-Length" to requestBody.length.toString()),
                )
            }
            server.start()

            withJavaClient { client: HttpClient ->
                val payload: String = "payload-✓"
                val response = client.post(server.url("/echo")) {
                    contentType(ContentType.Text.Plain.withCharset(Charsets.UTF_8))
                    header("X-Request-Id", "abc-123")
                    setBody(payload)
                }

                assertThat(response.status).isEqualTo(HttpStatusCode.Created)
                assertThat(response.headers["X-Body-Length"]).isEqualTo(payload.length.toString())
                assertThat(response.bodyAsText())
                    .contains("method=POST")
                    .contains("body=$payload")
                    .contains("contentType=text/plain; charset=UTF-8")
                    .contains("requestId=abc-123")
            }
        }
    }

    @Test
    fun `java engine builder controls redirects when ktor redirect plugin is disabled`(): Unit = runBlocking {
        LocalTestServer().use { server: LocalTestServer ->
            server.handle("/redirect") { exchange: HttpExchange ->
                exchange.respond(
                    statusCode = 302,
                    body = "",
                    headers = mapOf(HttpHeaders.Location to server.url("/final").toString()),
                )
            }
            server.handle("/final") { exchange: HttpExchange ->
                exchange.respond(statusCode = 200, body = "arrived through ${exchange.requestMethod}")
            }
            server.start()

            withJavaClient(clientConfig = { followRedirects = false }) { client: HttpClient ->
                val response = client.get(server.url("/redirect"))

                assertThat(response.status).isEqualTo(HttpStatusCode.Found)
                assertThat(response.headers[HttpHeaders.Location]).isEqualTo(server.url("/final").toString())
            }

            withJavaClient(
                engineConfig = {
                    config {
                        followRedirects(JdkHttpClient.Redirect.ALWAYS)
                    }
                },
                clientConfig = { followRedirects = false },
            ) { client: HttpClient ->
                val response = client.get(server.url("/redirect"))

                assertThat(response.status).isEqualTo(HttpStatusCode.OK)
                assertThat(response.bodyAsText()).isEqualTo("arrived through GET")
            }
        }
    }

    private suspend fun <T> withJavaClient(
        engineConfig: JavaHttpConfig.() -> Unit = {},
        clientConfig: HttpClientConfig<JavaHttpConfig>.() -> Unit = {},
        block: suspend (HttpClient) -> T,
    ): T {
        val client: HttpClient = HttpClient(Java) {
            install(HttpTimeout) {
                connectTimeoutMillis = 2_000
                socketTimeoutMillis = 5_000
                requestTimeoutMillis = 5_000
            }
            engine {
                protocolVersion = JdkHttpClient.Version.HTTP_1_1
                engineConfig()
            }
            clientConfig()
        }
        return try {
            block(client)
        } finally {
            client.close()
            withTimeout(10_000) {
                client.coroutineContext.job.join()
            }
        }
    }

    private class LocalTestServer : AutoCloseable {
        private val server: HttpServer = HttpServer.create(
            InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
            0,
        )
        private val threadCounter: AtomicInteger = AtomicInteger()
        private val executor: ExecutorService = Executors.newCachedThreadPool { runnable: Runnable ->
            Thread(runnable, "ktor-java-test-server-${threadCounter.incrementAndGet()}").apply {
                isDaemon = true
            }
        }

        private val baseUrl: String
            get() = "http://${server.address.hostString}:${server.address.port}"

        fun handle(path: String, handler: (HttpExchange) -> Unit): Unit {
            server.createContext(path) { exchange: HttpExchange ->
                try {
                    handler(exchange)
                } finally {
                    exchange.close()
                }
            }
        }

        fun start(): Unit {
            server.executor = executor
            server.start()
        }

        fun url(path: String): URL = URI.create("$baseUrl$path").toURL()

        override fun close(): Unit {
            server.stop(0)
            executor.shutdownNow()
        }
    }
}

private fun HttpExchange.respond(
    statusCode: Int,
    body: String,
    headers: Map<String, String> = emptyMap(),
): Unit {
    for ((name: String, value: String) in headers) {
        responseHeaders.set(name, value)
    }
    if (body.isNotEmpty() && !responseHeaders.containsKey(HttpHeaders.ContentType)) {
        responseHeaders.set(HttpHeaders.ContentType, "text/plain; charset=utf-8")
    }

    val bodyBytes: ByteArray = body.toByteArray(Charsets.UTF_8)
    sendResponseHeaders(statusCode, bodyBytes.size.toLong())
    responseBody.use { output ->
        output.write(bodyBytes)
    }
}
