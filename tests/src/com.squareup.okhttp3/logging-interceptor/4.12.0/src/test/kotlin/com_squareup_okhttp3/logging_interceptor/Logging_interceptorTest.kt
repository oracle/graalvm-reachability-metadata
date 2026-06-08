/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.logging_interceptor

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

public class HttpLoggingInterceptorTest {
    @Test
    fun bodyLevelLogsPlainTextRequestAndResponseWithoutConsumingResponse(): Unit = withServer { server: TestServer ->
        server.createContext("/plain") { exchange: HttpExchange ->
            val requestBody: String = exchange.readRequestBody()
            exchange.responseHeaders.add("X-Response-Header", "visible-response-header")
            exchange.respond(201, "server received: $requestBody", "text/plain; charset=utf-8")
        }
        val logs: MutableList<String> = mutableListOf()
        val interceptor: HttpLoggingInterceptor = loggingInterceptor(logs, HttpLoggingInterceptor.Level.BODY)
        interceptor.redactHeader("Authorization")
        val client: OkHttpClient = newClient(interceptor)

        try {
            val request: Request = Request.Builder()
                .url(server.url("/plain"))
                .header("Authorization", "Bearer secret-token")
                .header("X-Request-Header", "visible-request-header")
                .post("hello from okhttp".toRequestBody("text/plain; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response: Response ->
                assertThat(response.code).isEqualTo(201)
                assertThat(response.header("X-Response-Header")).isEqualTo("visible-response-header")
                assertThat(checkNotNull(response.body).string()).isEqualTo("server received: hello from okhttp")
            }
        } finally {
            client.close()
        }

        assertThat(logs.hasLineContaining("--> POST", "/plain")).isTrue()
        assertThat(logs).contains("Authorization: ██")
        assertThat(logs).contains("X-Request-Header: visible-request-header")
        assertThat(logs).contains("hello from okhttp")
        assertThat(logs.hasLineContaining("<-- 201", "/plain")).isTrue()
        assertThat(logs.hasLineContaining("visible-response-header")).isTrue()
        assertThat(logs).contains("server received: hello from okhttp")
        assertThat(logs.none { line: String -> line.contains("secret-token") }).isTrue()
    }

    @Test
    fun headersLevelLogsMetadataAndOmitsBodies(): Unit = withServer { server: TestServer ->
        server.createContext("/headers") { exchange: HttpExchange ->
            exchange.readRequestBody()
            exchange.responseHeaders.add("X-Trace-Id", "trace-123")
            exchange.respond(202, "response body is hidden", "text/plain; charset=utf-8")
        }
        val logs: MutableList<String> = mutableListOf()
        val client: OkHttpClient = newClient(loggingInterceptor(logs, HttpLoggingInterceptor.Level.HEADERS))

        try {
            val request: Request = Request.Builder()
                .url(server.url("/headers"))
                .header("X-Request-Id", "request-123")
                .put("request body is hidden".toRequestBody("text/plain; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response: Response ->
                assertThat(response.code).isEqualTo(202)
                assertThat(checkNotNull(response.body).string()).isEqualTo("response body is hidden")
            }
        } finally {
            client.close()
        }

        assertThat(logs.hasLineContaining("--> PUT", "/headers")).isTrue()
        assertThat(logs).contains("X-Request-Id: request-123")
        assertThat(logs.hasLineContaining("<-- 202", "/headers")).isTrue()
        assertThat(logs.hasLineContaining("trace-123")).isTrue()
        assertThat(logs.none { line: String -> line == "request body is hidden" }).isTrue()
        assertThat(logs.none { line: String -> line == "response body is hidden" }).isTrue()
    }

    @Test
    fun basicLevelLogsOnlyRequestAndResponseSummaries(): Unit = withServer { server: TestServer ->
        server.createContext("/summary") { exchange: HttpExchange ->
            exchange.responseHeaders.add("X-Should-Not-Be-Logged", "hidden-header")
            exchange.respond(204, ByteArray(0), "text/plain; charset=utf-8")
        }
        val logs: MutableList<String> = mutableListOf()
        val client: OkHttpClient = newClient(loggingInterceptor(logs, HttpLoggingInterceptor.Level.BASIC))

        try {
            val request: Request = Request.Builder()
                .url(server.url("/summary"))
                .header("X-Should-Not-Be-Logged", "hidden-request-header")
                .get()
                .build()

            client.newCall(request).execute().use { response: Response ->
                assertThat(response.code).isEqualTo(204)
            }
        } finally {
            client.close()
        }

        assertThat(logs).hasSize(2)
        assertThat(logs.hasLineContaining("--> GET", "/summary")).isTrue()
        assertThat(logs.hasLineContaining("<-- 204", "/summary")).isTrue()
        assertThat(logs.none { line: String -> line.contains("X-Should-Not-Be-Logged") }).isTrue()
    }

    @Test
    fun noneLevelDoesNotCallLogger(): Unit = withServer { server: TestServer ->
        server.createContext("/none") { exchange: HttpExchange ->
            exchange.respond(200, "not logged", "text/plain; charset=utf-8")
        }
        val logs: MutableList<String> = mutableListOf()
        val client: OkHttpClient = newClient(loggingInterceptor(logs, HttpLoggingInterceptor.Level.NONE))

        try {
            val request: Request = Request.Builder()
                .url(server.url("/none"))
                .get()
                .build()

            client.newCall(request).execute().use { response: Response ->
                assertThat(response.code).isEqualTo(200)
                assertThat(checkNotNull(response.body).string()).isEqualTo("not logged")
            }
        } finally {
            client.close()
        }

        assertThat(logs).isEmpty()
    }

    @Test
    fun bodyLevelOmitsBinaryRequestAndResponseBodies(): Unit = withServer { server: TestServer ->
        val binaryResponse: ByteArray = byteArrayOf(0, 1, 2, 3, -1)
        server.createContext("/binary") { exchange: HttpExchange ->
            exchange.readRequestBody()
            exchange.respond(200, binaryResponse, "application/octet-stream")
        }
        val logs: MutableList<String> = mutableListOf()
        val client: OkHttpClient = newClient(loggingInterceptor(logs, HttpLoggingInterceptor.Level.BODY))

        try {
            val request: Request = Request.Builder()
                .url(server.url("/binary"))
                .post(byteArrayOf(0, 1, 2, -1).toRequestBody("application/octet-stream".toMediaType()))
                .build()

            client.newCall(request).execute().use { response: Response ->
                assertThat(response.code).isEqualTo(200)
                assertThat(checkNotNull(response.body).bytes()).isEqualTo(binaryResponse)
            }
        } finally {
            client.close()
        }

        assertThat(logs.hasLineContaining("--> END POST", "binary", "body omitted")).isTrue()
        assertThat(logs.hasLineContaining("<-- END HTTP", "binary", "body omitted")).isTrue()
    }

    private fun withServer(block: (TestServer) -> Unit): Unit {
        TestServer().use(block)
    }

    private fun loggingInterceptor(
        logs: MutableList<String>,
        level: HttpLoggingInterceptor.Level,
    ): HttpLoggingInterceptor {
        val interceptor: HttpLoggingInterceptor = HttpLoggingInterceptor { message: String -> logs.add(message) }
        interceptor.level = level
        return interceptor
    }

    private fun newClient(interceptor: HttpLoggingInterceptor): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(interceptor)
        .callTimeout(5, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

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
    responseHeaders.add("Content-Type", contentType)
    sendResponseHeaders(statusCode, body.size.toLong())
    responseBody.use { output ->
        output.write(body)
    }
}

private fun OkHttpClient.close(): Unit {
    dispatcher.executorService.shutdownNow()
    connectionPool.evictAll()
    cache?.close()
}

private fun List<String>.hasLineContaining(vararg fragments: String): Boolean = any { line: String ->
    fragments.all { fragment: String -> line.contains(fragment) }
}
