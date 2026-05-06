/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_okhttp_jvm

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.engine.okhttp.OkHttpConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

public class KtorClientOkhttpJvmTest {
    @Test
    fun sendsPostRequestsWithDefaultRequestHeadersAndQueryParameters(): Unit = withServer { server: TestServer ->
        server.createContext("/request-info") { exchange: HttpExchange ->
            val requestBody: String = exchange.readRequestBody()
            val response: String = buildString {
                appendLine("method=${exchange.requestMethod}")
                appendLine("path=${exchange.requestURI.path}")
                appendLine("query=${exchange.requestURI.rawQuery}")
                appendLine("defaultHeader=${exchange.requestHeaders.getFirst("X-Default-Header")}")
                appendLine("customHeader=${exchange.requestHeaders.getFirst("X-Custom-Header")}")
                appendLine("contentType=${exchange.requestHeaders.getFirst(HttpHeaders.ContentType)}")
                append("body=$requestBody")
            }
            exchange.respond(HttpStatusCode.Created.value, response, "text/plain; charset=utf-8")
        }

        val client: HttpClient = HttpClient(OkHttp) {
            installShortTimeouts()
            defaultRequest {
                url {
                    protocol = URLProtocol.HTTP
                    host = TestServer.host
                    port = server.port
                }
                header("X-Default-Header", "from-default-request")
            }
        }

        try {
            val responseText: String = runBlocking {
                client.post("/request-info?source=test") {
                    parameter("added", "true")
                    header("X-Custom-Header", "from-request")
                    contentType(ContentType.Text.Plain)
                    setBody("hello from ktor")
                }.bodyAsText()
            }

            assertThat(responseText).contains(
                "method=POST",
                "path=/request-info",
                "source=test",
                "added=true",
                "defaultHeader=from-default-request",
                "customHeader=from-request",
                "contentType=text/plain",
                "body=hello from ktor",
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun readsStatusHeadersAndBinaryResponseBodies(): Unit = withServer { server: TestServer ->
        val payload: ByteArray = byteArrayOf(0, 1, 2, 3, 5, 8, 13, 21, -1)
        server.createContext("/binary") { exchange: HttpExchange ->
            exchange.responseHeaders.add("X-Payload-Kind", "fibonacci-bytes")
            exchange.respond(HttpStatusCode.Accepted.value, payload, "application/octet-stream")
        }

        val client: HttpClient = HttpClient(OkHttp) {
            installShortTimeouts()
        }
        try {
            val response: io.ktor.client.statement.HttpResponse = runBlocking {
                client.get(server.url("/binary"))
            }
            val bytes: ByteArray = runBlocking { response.bodyAsBytes() }

            assertThat(response.status).isEqualTo(HttpStatusCode.Accepted)
            assertThat(response.headers["X-Payload-Kind"]).isEqualTo("fibonacci-bytes")
            assertThat(bytes).isEqualTo(payload)
        } finally {
            client.close()
        }
    }

    @Test
    fun submitsUrlEncodedFormsThroughOkHttpEngine(): Unit = withServer { server: TestServer ->
        server.createContext("/form") { exchange: HttpExchange ->
            val requestBody: String = exchange.readRequestBody()
            val contentType: String = exchange.requestHeaders.getFirst(HttpHeaders.ContentType)
            val response: String = "contentType=$contentType\nbody=$requestBody"
            exchange.respond(HttpStatusCode.OK.value, response, "text/plain; charset=utf-8")
        }

        val client: HttpClient = HttpClient(OkHttp) {
            installShortTimeouts()
        }
        try {
            val responseText: String = runBlocking {
                client.submitForm(
                    url = server.url("/form"),
                    formParameters = Parameters.build {
                        append("name", "Ktor OkHttp")
                        append("feature", "url encoded forms")
                    },
                ).bodyAsText()
            }

            assertThat(responseText).contains(
                "contentType=application/x-www-form-urlencoded",
                "name=Ktor+OkHttp",
                "feature=url+encoded+forms",
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun submitsMultipartFormDataWithTextAndBinaryParts(): Unit = withServer { server: TestServer ->
        server.createContext("/upload") { exchange: HttpExchange ->
            val contentType: String = exchange.requestHeaders.getFirst(HttpHeaders.ContentType)
            val requestBody: String = exchange.readRequestBody()
            val response: String = buildString {
                appendLine("contentType=$contentType")
                appendLine("hasDescription=${requestBody.contains("native multipart upload")}")
                appendLine("hasFileName=${requestBody.contains("filename=\"greeting.txt\"")}")
                appendLine("hasPayload=${requestBody.contains("hello from a multipart file")}")
                append("hasPayloadContentType=${requestBody.contains("Content-Type: text/plain")}")
            }
            exchange.respond(HttpStatusCode.OK.value, response, "text/plain; charset=utf-8")
        }

        val client: HttpClient = HttpClient(OkHttp) {
            installShortTimeouts()
        }
        try {
            val responseText: String = runBlocking {
                client.submitFormWithBinaryData(
                    url = server.url("/upload"),
                    formData = formData {
                        append("description", "native multipart upload")
                        append(
                            "payload",
                            "hello from a multipart file".toByteArray(UTF_8),
                            Headers.build {
                                append(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
                                append(HttpHeaders.ContentDisposition, "filename=\"greeting.txt\"")
                            },
                        )
                    },
                ).bodyAsText()
            }

            assertThat(responseText).contains(
                "contentType=multipart/form-data",
                "hasDescription=true",
                "hasFileName=true",
                "hasPayload=true",
                "hasPayloadContentType=true",
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun storesAndSendsCookiesAcrossRequests(): Unit = withServer { server: TestServer ->
        server.createContext("/login") { exchange: HttpExchange ->
            exchange.responseHeaders.add(HttpHeaders.SetCookie, "sessionId=native-cookie; Path=/; HttpOnly")
            exchange.respond(HttpStatusCode.OK.value, "cookie stored", "text/plain; charset=utf-8")
        }
        server.createContext("/profile") { exchange: HttpExchange ->
            val cookieHeader: String = exchange.requestHeaders.getFirst(HttpHeaders.Cookie) ?: "missing"
            exchange.respond(HttpStatusCode.OK.value, cookieHeader, "text/plain; charset=utf-8")
        }

        val client: HttpClient = HttpClient(OkHttp) {
            installShortTimeouts()
            install(HttpCookies)
        }
        try {
            val cookieHeader: String = runBlocking {
                client.get(server.url("/login")).bodyAsText()
                client.get(server.url("/profile")).bodyAsText()
            }

            assertThat(cookieHeader).contains("sessionId=native-cookie")
        } finally {
            client.close()
        }
    }

    @Test
    fun appliesOkHttpBuilderConfigurationForInterceptorsAndRedirects(): Unit = withServer { server: TestServer ->
        server.createContext("/intercepted") { exchange: HttpExchange ->
            val headerValue: String = exchange.requestHeaders.getFirst("X-Engine-Interceptor") ?: "missing"
            exchange.respond(HttpStatusCode.OK.value, headerValue, "text/plain; charset=utf-8")
        }
        server.createContext("/redirect") { exchange: HttpExchange ->
            exchange.responseHeaders.add(HttpHeaders.Location, server.url("/intercepted"))
            exchange.respond(HttpStatusCode.Found.value, "redirecting", "text/plain; charset=utf-8")
        }

        val client: HttpClient = HttpClient(OkHttp) {
            installShortTimeouts()
            expectSuccess = false
            followRedirects = false
            engine {
                config {
                    followRedirects(false)
                    addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header("X-Engine-Interceptor", "configured-by-okhttp")
                            .build()
                        chain.proceed(request)
                    }
                }
            }
        }

        try {
            val interceptedText: String = runBlocking {
                client.get(server.url("/intercepted")).bodyAsText()
            }
            val redirectResponse: io.ktor.client.statement.HttpResponse = runBlocking {
                client.get(server.url("/redirect"))
            }
            val redirectBody: String = runBlocking { redirectResponse.bodyAsText() }

            assertThat(interceptedText).isEqualTo("configured-by-okhttp")
            assertThat(redirectResponse.status).isEqualTo(HttpStatusCode.Found)
            assertThat(redirectResponse.headers[HttpHeaders.Location]).isEqualTo(server.url("/intercepted"))
            assertThat(redirectBody).isEqualTo("redirecting")
        } finally {
            client.close()
        }
    }

    private fun withServer(block: (TestServer) -> Unit): Unit {
        TestServer().use(block)
    }

    private fun HttpClientConfig<OkHttpConfig>.installShortTimeouts(): Unit {
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 5_000
            socketTimeoutMillis = 5_000
        }
    }

    private class TestServer : AutoCloseable {
        private val executor: ExecutorService = Executors.newCachedThreadPool()
        private val server: HttpServer = HttpServer.create(InetSocketAddress(InetAddress.getByName(host), 0), 0)

        val port: Int
            get() = server.address.port

        init {
            server.executor = executor
            server.start()
        }

        fun createContext(path: String, handler: (HttpExchange) -> Unit): Unit {
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

private fun HttpExchange.respond(statusCode: Int, text: String, contentType: String): Unit {
    respond(statusCode, text.toByteArray(UTF_8), contentType)
}

private fun HttpExchange.respond(statusCode: Int, body: ByteArray, contentType: String): Unit {
    responseHeaders.add(HttpHeaders.ContentType, contentType)
    sendResponseHeaders(statusCode, body.size.toLong())
    responseBody.use { output ->
        output.write(body)
    }
}
