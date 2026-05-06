/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_apache5_jvm

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.engine.apache5.Apache5EngineConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.withCharset
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

public class KtorClientApache5JvmTest {
    @Test
    fun sendsGetRequestsWithHeadersQueryParametersAndDefaultRequestUrl(): Unit = runBlocking {
        LocalTestServer().use { server: LocalTestServer ->
            server.handle("/inspect") { exchange: HttpExchange ->
                val body: String = buildString {
                    appendLine("method=${exchange.requestMethod}")
                    appendLine("path=${exchange.requestURI.path}")
                    appendLine("query=${exchange.requestURI.rawQuery}")
                    appendLine("defaultHeader=${exchange.requestHeaders.getFirst("X-Default-Header")}")
                    appendLine("customHeader=${exchange.requestHeaders.getFirst("X-Custom-Header")}")
                    append("protocol=${exchange.protocol}")
                }
                exchange.respond(
                    statusCode = HttpStatusCode.Accepted.value,
                    body = body,
                    headers = mapOf("X-Apache5-Test" to "metadata"),
                )
            }
            server.start()

            withApacheClient(
                clientConfig = {
                    defaultRequest {
                        url {
                            protocol = URLProtocol.HTTP
                            host = LocalTestServer.host
                            port = server.port
                        }
                        header("X-Default-Header", "from-default-request")
                    }
                },
            ) { client: HttpClient ->
                val response: HttpResponse = client.get("/inspect?source=test") {
                    parameter("added", "true")
                    header("X-Custom-Header", "from-request")
                }

                assertThat(response.status).isEqualTo(HttpStatusCode.Accepted)
                assertThat(response.headers["X-Apache5-Test"]).isEqualTo("metadata")
                assertThat(response.bodyAsText()).contains(
                    "method=GET",
                    "path=/inspect",
                    "source=test",
                    "added=true",
                    "defaultHeader=from-default-request",
                    "customHeader=from-request",
                    "protocol=HTTP/1.1",
                )
            }
        }
    }

    @Test
    fun postsTextBodiesAndPreservesContentHeaders(): Unit = runBlocking {
        LocalTestServer().use { server: LocalTestServer ->
            server.handle("/echo") { exchange: HttpExchange ->
                val requestBody: String = exchange.readRequestBody()
                val body: String = buildString {
                    appendLine("method=${exchange.requestMethod}")
                    appendLine("body=$requestBody")
                    appendLine("contentType=${exchange.requestHeaders.getFirst(HttpHeaders.ContentType)}")
                    append("requestId=${exchange.requestHeaders.getFirst("X-Request-Id")}")
                }
                exchange.respond(
                    statusCode = HttpStatusCode.Created.value,
                    body = body,
                    headers = mapOf("X-Body-Length" to requestBody.length.toString()),
                )
            }
            server.start()

            withApacheClient { client: HttpClient ->
                val payload: String = "apache5 payload ✓"
                val response: HttpResponse = client.post(server.url("/echo")) {
                    contentType(ContentType.Text.Plain.withCharset(UTF_8))
                    header("X-Request-Id", "apache5-post")
                    setBody(payload)
                }

                assertThat(response.status).isEqualTo(HttpStatusCode.Created)
                assertThat(response.headers["X-Body-Length"]).isEqualTo(payload.length.toString())
                assertThat(response.bodyAsText()).contains(
                    "method=POST",
                    "body=$payload",
                    "contentType=text/plain; charset=UTF-8",
                    "requestId=apache5-post",
                )
            }
        }
    }

    @Test
    fun streamsWriteChannelRequestContentThroughApache5(): Unit = runBlocking {
        LocalTestServer().use { server: LocalTestServer ->
            server.handle("/stream") { exchange: HttpExchange ->
                val requestBody: String = exchange.readRequestBody()
                exchange.respond(
                    statusCode = HttpStatusCode.OK.value,
                    body = "streamed=$requestBody",
                    headers = mapOf("X-Observed-Method" to exchange.requestMethod),
                )
            }
            server.start()

            withApacheClient { client: HttpClient ->
                val chunks: List<String> = listOf("alpha", "-beta", "-gamma")
                val response: HttpResponse = client.post(server.url("/stream")) {
                    setBody(
                        object : OutgoingContent.WriteChannelContent() {
                            override val contentType: ContentType = ContentType.Text.Plain.withCharset(UTF_8)

                            override suspend fun writeTo(channel: ByteWriteChannel): Unit {
                                for (chunk: String in chunks) {
                                    channel.writeStringUtf8(chunk)
                                    channel.flush()
                                }
                            }
                        },
                    )
                }

                assertThat(response.status).isEqualTo(HttpStatusCode.OK)
                assertThat(response.headers["X-Observed-Method"]).isEqualTo("POST")
                assertThat(response.bodyAsText()).isEqualTo("streamed=alpha-beta-gamma")
            }
        }
    }

    @Test
    fun readsStatusHeadersAndBinaryResponseBodies(): Unit = runBlocking {
        LocalTestServer().use { server: LocalTestServer ->
            val payload: ByteArray = byteArrayOf(0, 1, 2, 3, 5, 8, 13, 21, -1)
            server.handle("/binary") { exchange: HttpExchange ->
                exchange.respond(
                    statusCode = HttpStatusCode.PartialContent.value,
                    body = payload,
                    contentType = "application/octet-stream",
                    headers = mapOf("X-Payload-Kind" to "fibonacci-bytes"),
                )
            }
            server.start()

            withApacheClient { client: HttpClient ->
                val response: HttpResponse = client.get(server.url("/binary"))
                val bytes: ByteArray = response.bodyAsBytes()

                assertThat(response.status).isEqualTo(HttpStatusCode.PartialContent)
                assertThat(response.headers["X-Payload-Kind"]).isEqualTo("fibonacci-bytes")
                assertThat(bytes).isEqualTo(payload)
            }
        }
    }

    @Test
    fun apacheEngineConfigurationControlsRedirectHandling(): Unit = runBlocking {
        LocalTestServer().use { server: LocalTestServer ->
            server.handle("/redirect") { exchange: HttpExchange ->
                exchange.respond(
                    statusCode = HttpStatusCode.Found.value,
                    body = "redirecting",
                    headers = mapOf(HttpHeaders.Location to server.url("/final")),
                )
            }
            server.handle("/final") { exchange: HttpExchange ->
                exchange.respond(statusCode = HttpStatusCode.OK.value, body = "arrived through ${exchange.requestMethod}")
            }
            server.start()

            withApacheClient(
                clientConfig = { followRedirects = false },
            ) { client: HttpClient ->
                val response: HttpResponse = client.get(server.url("/redirect"))

                assertThat(response.status).isEqualTo(HttpStatusCode.Found)
                assertThat(response.headers[HttpHeaders.Location]).isEqualTo(server.url("/final"))
                assertThat(response.bodyAsText()).isEqualTo("redirecting")
            }

            withApacheClient(
                engineConfig = { followRedirects = true },
                clientConfig = { followRedirects = false },
            ) { client: HttpClient ->
                val response: HttpResponse = client.get(server.url("/redirect"))

                assertThat(response.status).isEqualTo(HttpStatusCode.OK)
                assertThat(response.bodyAsText()).isEqualTo("arrived through GET")
            }
        }
    }

    @Test
    fun routesRequestsThroughConfiguredHttpProxy(): Unit = runBlocking {
        LocalTestServer().use { proxyServer: LocalTestServer ->
            proxyServer.handle("/") { exchange: HttpExchange ->
                val body: String = buildString {
                    appendLine("method=${exchange.requestMethod}")
                    appendLine("uri=${exchange.requestURI}")
                    append("host=${exchange.requestHeaders.getFirst(HttpHeaders.Host)}")
                }
                exchange.respond(
                    statusCode = HttpStatusCode.OK.value,
                    body = body,
                    headers = mapOf("X-Proxy-Handled" to "apache5"),
                )
            }
            proxyServer.start()

            withApacheClient(
                engineConfig = {
                    proxy = Proxy(
                        Proxy.Type.HTTP,
                        InetSocketAddress(LocalTestServer.host, proxyServer.port),
                    )
                },
            ) { client: HttpClient ->
                val response: HttpResponse = client.get("http://example.invalid/proxied?via=apache5")

                assertThat(response.status).isEqualTo(HttpStatusCode.OK)
                assertThat(response.headers["X-Proxy-Handled"]).isEqualTo("apache5")
                assertThat(response.bodyAsText()).contains(
                    "method=GET",
                    "example.invalid",
                    "/proxied",
                    "via=apache5",
                )
            }
        }
    }

    @Test
    fun appliesApacheConnectionManagerConfiguration(): Unit = runBlocking {
        LocalTestServer().use { server: LocalTestServer ->
            server.handle("/configured") { exchange: HttpExchange ->
                exchange.respond(statusCode = HttpStatusCode.OK.value, body = "connection manager configured")
            }
            server.start()

            withApacheClient(
                engineConfig = {
                    configureConnectionManager {
                        setMaxConnTotal(4)
                        setMaxConnPerRoute(4)
                    }
                },
            ) { client: HttpClient ->
                val response: HttpResponse = client.get(server.url("/configured"))

                assertThat(response.status).isEqualTo(HttpStatusCode.OK)
                assertThat(response.bodyAsText()).isEqualTo("connection manager configured")
            }
        }
    }

    private suspend fun <T> withApacheClient(
        engineConfig: Apache5EngineConfig.() -> Unit = {},
        clientConfig: HttpClientConfig<Apache5EngineConfig>.() -> Unit = {},
        block: suspend (HttpClient) -> T,
    ): T {
        val client: HttpClient = HttpClient(Apache5) {
            install(HttpTimeout) {
                connectTimeoutMillis = 2_000
                socketTimeoutMillis = 5_000
                requestTimeoutMillis = 5_000
            }
            engine {
                connectTimeout = 2_000
                socketTimeout = 5_000
                connectionRequestTimeout = 2_000
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
        private val executor: ExecutorService = Executors.newCachedThreadPool()
        private val server: HttpServer = HttpServer.create(InetSocketAddress(InetAddress.getByName(host), 0), 0)

        val port: Int
            get() = server.address.port

        init {
            server.executor = executor
        }

        fun start(): Unit {
            server.start()
        }

        fun handle(path: String, handler: (HttpExchange) -> Unit): Unit {
            server.createContext(path) { exchange: HttpExchange ->
                try {
                    handler(exchange)
                } finally {
                    exchange.close()
                }
            }
        }

        fun url(path: String): String = "http://$host:$port$path"

        override fun close(): Unit {
            server.stop(0)
            executor.shutdownNow()
        }

        companion object {
            const val host: String = "127.0.0.1"
        }
    }
}

private fun HttpExchange.readRequestBody(): String = requestBody.use { input ->
    String(input.readBytes(), UTF_8)
}

private fun HttpExchange.respond(
    statusCode: Int,
    body: String,
    contentType: String = "text/plain; charset=utf-8",
    headers: Map<String, String> = emptyMap(),
): Unit {
    respond(statusCode, body.toByteArray(UTF_8), contentType, headers)
}

private fun HttpExchange.respond(
    statusCode: Int,
    body: ByteArray,
    contentType: String,
    headers: Map<String, String> = emptyMap(),
): Unit {
    responseHeaders.add(HttpHeaders.ContentType, contentType)
    for ((name: String, value: String) in headers) {
        responseHeaders.add(name, value)
    }
    sendResponseHeaders(statusCode, body.size.toLong())
    responseBody.use { output ->
        output.write(body)
    }
}
