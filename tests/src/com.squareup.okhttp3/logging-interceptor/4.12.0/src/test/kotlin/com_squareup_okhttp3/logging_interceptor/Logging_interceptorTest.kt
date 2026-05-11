/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.logging_interceptor

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.LoggingEventListener
import okio.BufferedSink
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream

public class Logging_interceptorTest {
    @Test
    fun bodyLevelLogsRequestAndResponseBodiesWithoutConsumingResponse() {
        val logs = mutableListOf<String>()
        val interceptor = HttpLoggingInterceptor { message: String -> logs += message }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = httpClient {
            addInterceptor(interceptor)
        }
        val responseBody = "{\"status\":\"created\",\"id\":7}"

        OneShotHttpServer(
            statusLine = "HTTP/1.1 201 Created",
            headers = listOf("Content-Type" to "application/json; charset=utf-8", "X-Trace-Id" to "trace-123"),
            body = responseBody.toByteArray(StandardCharsets.UTF_8),
        ).use { server ->
            server.start()
            try {
                val request = Request.Builder()
                    .url(server.url("/submit?source=test"))
                    .header("X-Request-Id", "request-123")
                    .post("request-body=hello".toRequestBody())
                    .build()

                client.newCall(request).execute().use { response ->
                    assertThat(response.code).isEqualTo(201)
                    assertThat(response.body!!.string()).isEqualTo(responseBody)
                }
            } finally {
                client.closeResources()
            }
        }

        val transcript = logs.joinToString("\n")
        assertThat(interceptor.level).isEqualTo(HttpLoggingInterceptor.Level.BODY)
        assertThat(transcript).contains("--> POST", "/submit?source=test")
        assertThat(transcript).contains("X-Request-Id: request-123")
        assertThat(transcript).contains("request-body=hello")
        assertThat(transcript).contains("<-- 201 Created")
        assertThat(transcript).contains("Content-Type: application/json; charset=utf-8")
        assertThat(transcript).contains("X-Trace-Id: trace-123")
        assertThat(transcript).contains(responseBody)
        assertThat(transcript).contains("<-- END HTTP")
    }

    @Test
    fun headersLevelRedactsConfiguredRequestAndResponseHeadersAndOmitsBodies() {
        val logs = mutableListOf<String>()
        val interceptor = HttpLoggingInterceptor { message: String -> logs += message }.apply {
            level = HttpLoggingInterceptor.Level.HEADERS
            redactHeader("Authorization")
            redactHeader("X-Secret")
        }
        val client = httpClient {
            addInterceptor(interceptor)
        }
        val responseBody = "this response body must not be logged"

        OneShotHttpServer(
            headers = listOf(
                "Content-Type" to "text/plain; charset=utf-8",
                "Authorization" to "Bearer response-token",
                "X-Secret" to "response-secret",
            ),
            body = responseBody.toByteArray(StandardCharsets.UTF_8),
        ).use { server ->
            server.start()
            try {
                val request = Request.Builder()
                    .url(server.url("/headers"))
                    .header("Authorization", "Bearer request-token")
                    .header("X-Secret", "request-secret")
                    .build()

                client.newCall(request).execute().use { response ->
                    assertThat(response.code).isEqualTo(200)
                    assertThat(response.body!!.string()).isEqualTo(responseBody)
                }
            } finally {
                client.closeResources()
            }
        }

        val transcript = logs.joinToString("\n")
        assertThat(transcript).contains("--> GET", "/headers")
        assertThat(transcript).contains("Authorization: ██")
        assertThat(transcript).contains("X-Secret: ██")
        assertThat(transcript).contains("<-- 200 OK")
        assertThat(transcript).contains("Content-Type: text/plain; charset=utf-8")
        assertThat(transcript).doesNotContain("request-token", "request-secret", "response-token", "response-secret")
        assertThat(transcript).doesNotContain(responseBody)
    }

    @Test
    fun basicLevelLogsOnlyRequestAndResponseLines() {
        val logs = mutableListOf<String>()
        val interceptor = HttpLoggingInterceptor { message: String -> logs += message }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = httpClient {
            addInterceptor(interceptor)
        }

        OneShotHttpServer(
            statusLine = "HTTP/1.1 204 No Content",
            headers = listOf("X-Server" to "one-shot"),
        ).use { server ->
            server.start()
            try {
                val request = Request.Builder()
                    .url(server.url("/empty"))
                    .header("X-Client", "basic-test")
                    .build()

                client.newCall(request).execute().use { response ->
                    assertThat(response.code).isEqualTo(204)
                }
            } finally {
                client.closeResources()
            }
        }

        val transcript = logs.joinToString("\n")
        assertThat(transcript).contains("--> GET", "/empty")
        assertThat(transcript).contains("<-- 204 No Content")
        assertThat(transcript).doesNotContain("X-Client: basic-test", "X-Server: one-shot")
    }

    @Test
    fun noneLevelPerformsRequestWithoutWritingLogs() {
        val logs = mutableListOf<String>()
        val interceptor = HttpLoggingInterceptor { message: String -> logs += message }.apply {
            level = HttpLoggingInterceptor.Level.NONE
        }
        val client = httpClient {
            addInterceptor(interceptor)
        }
        val responseBody = "network still executed"

        OneShotHttpServer(
            body = responseBody.toByteArray(StandardCharsets.UTF_8),
        ).use { server ->
            server.start()
            try {
                val request = Request.Builder()
                    .url(server.url("/silent"))
                    .build()

                client.newCall(request).execute().use { response ->
                    assertThat(response.code).isEqualTo(200)
                    assertThat(response.body!!.string()).isEqualTo(responseBody)
                }
            } finally {
                client.closeResources()
            }
        }

        assertThat(logs).isEmpty()
    }

    @Test
    fun bodyLevelOmitsBinaryRequestAndResponseBodies() {
        val logs = mutableListOf<String>()
        val interceptor = HttpLoggingInterceptor { message: String -> logs += message }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = httpClient {
            addInterceptor(interceptor)
        }
        val binaryRequest = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04)
        val binaryResponse = byteArrayOf(0x05, 0x06, 0x07, 0x08, 0x09)

        OneShotHttpServer(
            body = binaryResponse,
        ).use { server ->
            server.start()
            try {
                val request = Request.Builder()
                    .url(server.url("/binary"))
                    .post(binaryRequest.toRequestBody())
                    .build()

                client.newCall(request).execute().use { response ->
                    assertThat(response.code).isEqualTo(200)
                    assertThat(response.body!!.bytes()).isEqualTo(binaryResponse)
                }
            } finally {
                client.closeResources()
            }
        }

        val transcript = logs.joinToString("\n")
        assertThat(transcript).contains("--> POST", "/binary")
        assertThat(transcript).contains("--> END POST (binary ${binaryRequest.size}-byte body omitted)")
        assertThat(transcript).contains("<-- 200 OK")
        assertThat(transcript).contains("<-- END HTTP (binary ${binaryResponse.size}-byte body omitted)")
    }

    @Test
    fun bodyLevelOmitsOneShotRequestBodyWithoutConsumingIt() {
        val logs = mutableListOf<String>()
        val interceptor = HttpLoggingInterceptor { message: String -> logs += message }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = httpClient {
            addInterceptor(interceptor)
        }
        val payload = "single-use request payload"
        val payloadBytes = payload.toByteArray(StandardCharsets.UTF_8)
        val responseBody = "accepted"
        val requestBody = object : RequestBody() {
            override fun contentType(): MediaType = "text/plain; charset=utf-8".toMediaType()

            override fun contentLength(): Long = payloadBytes.size.toLong()

            override fun isOneShot(): Boolean = true

            override fun writeTo(sink: BufferedSink) {
                sink.write(payloadBytes)
            }
        }

        OneShotHttpServer(
            body = responseBody.toByteArray(StandardCharsets.UTF_8),
        ).use { server ->
            server.start()
            try {
                val request = Request.Builder()
                    .url(server.url("/one-shot"))
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    assertThat(response.code).isEqualTo(200)
                    assertThat(response.body!!.string()).isEqualTo(responseBody)
                }

                assertThat(server.recordedRequestBody).isEqualTo(payloadBytes)
            } finally {
                client.closeResources()
            }
        }

        val transcript = logs.joinToString("\n")
        assertThat(transcript).contains("--> POST", "/one-shot")
        assertThat(transcript).contains("--> END POST (one-shot body omitted)")
        assertThat(transcript).doesNotContain(payload)
        assertThat(transcript).contains("<-- 200 OK")
    }

    @Test
    fun networkBodyLoggerReportsGzipEncodedResponseBodySizes() {
        val logs = mutableListOf<String>()
        val interceptor = HttpLoggingInterceptor { message: String -> logs += message }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = httpClient {
            addNetworkInterceptor(interceptor)
        }
        val plainBody = "compressed response payload"

        OneShotHttpServer(
            headers = listOf("Content-Type" to "text/plain; charset=utf-8", "Content-Encoding" to "gzip"),
            body = gzip(plainBody),
        ).use { server ->
            server.start()
            try {
                val request = Request.Builder()
                    .url(server.url("/gzip"))
                    .build()

                client.newCall(request).execute().use { response ->
                    assertThat(response.code).isEqualTo(200)
                    assertThat(response.body!!.string()).isEqualTo(plainBody)
                }
            } finally {
                client.closeResources()
            }
        }

        val transcript = logs.joinToString("\n")
        assertThat(transcript).contains("Content-Encoding: gzip")
        assertThat(transcript).contains(plainBody)
        assertThat(transcript).contains("gzipped-byte body)")
    }

    @Test
    fun eventListenerFactoryLogsConnectionRequestResponseAndCallEvents() {
        val logs = mutableListOf<String>()
        val client = httpClient {
            eventListenerFactory(LoggingEventListener.Factory { message: String -> logs += message })
        }
        val responseBody = "events"

        OneShotHttpServer(
            body = responseBody.toByteArray(StandardCharsets.UTF_8),
        ).use { server ->
            server.start()
            try {
                val request = Request.Builder()
                    .url(server.url("/events"))
                    .build()

                client.newCall(request).execute().use { response ->
                    assertThat(response.code).isEqualTo(200)
                    assertThat(response.body!!.string()).isEqualTo(responseBody)
                }
            } finally {
                client.closeResources()
            }
        }

        val transcript = logs.joinToString("\n")
        assertThat(transcript).contains("callStart: Request{method=GET")
        assertThat(transcript).contains("connectStart:")
        assertThat(transcript).contains("connectionAcquired:")
        assertThat(transcript).contains("requestHeadersStart")
        assertThat(transcript).contains("requestHeadersEnd")
        assertThat(transcript).contains("responseHeadersStart")
        assertThat(transcript).contains("responseHeadersEnd: Response{protocol=http/1.1, code=200")
        assertThat(transcript).contains("responseBodyStart")
        assertThat(transcript).contains("responseBodyEnd: byteCount=6")
        assertThat(transcript).contains("callEnd")
    }

    private fun httpClient(configure: OkHttpClient.Builder.() -> Unit): OkHttpClient =
        OkHttpClient.Builder()
            .callTimeout(10, TimeUnit.SECONDS)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .apply(configure)
            .build()

    private fun OkHttpClient.closeResources() {
        dispatcher.executorService.shutdown()
        connectionPool.evictAll()
        cache?.close()
    }

    private fun gzip(content: String): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { gzip ->
            gzip.write(content.toByteArray(StandardCharsets.UTF_8))
        }
        return output.toByteArray()
    }

    private class OneShotHttpServer(
        private val statusLine: String = "HTTP/1.1 200 OK",
        private val headers: List<Pair<String, String>> = emptyList(),
        private val body: ByteArray = ByteArray(0),
    ) : Closeable {
        private val loopbackAddress: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
        private val serverSocket: ServerSocket = ServerSocket(0, 1, loopbackAddress).apply {
            soTimeout = 5_000
        }
        private val executor = Executors.newSingleThreadExecutor()
        private val handled = CountDownLatch(1)
        @Volatile
        private var requestBody = ByteArray(0)

        val recordedRequestBody: ByteArray
            get() = requestBody.copyOf()

        fun start() {
            executor.execute {
                try {
                    serverSocket.accept().use { socket: Socket ->
                        socket.soTimeout = 5_000
                        readRequest(socket.getInputStream())
                        socket.getOutputStream().use { output ->
                            output.write(responseHeaders())
                            output.write(body)
                            output.flush()
                        }
                    }
                } finally {
                    handled.countDown()
                }
            }
        }

        fun url(path: String): String = "http://${serverSocket.inetAddress.hostAddress}:${serverSocket.localPort}$path"

        override fun close() {
            serverSocket.close()
            handled.await(5, TimeUnit.SECONDS)
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }

        private fun responseHeaders(): ByteArray {
            val allHeaders = headers + listOf("Content-Length" to body.size.toString(), "Connection" to "close")
            val response = buildString {
                append(statusLine).append("\r\n")
                allHeaders.forEach { (name: String, value: String) ->
                    append(name).append(": ").append(value).append("\r\n")
                }
                append("\r\n")
            }
            return response.toByteArray(StandardCharsets.ISO_8859_1)
        }

        private fun readRequest(input: InputStream) {
            val headerBytes = ByteArrayOutputStream()
            val delimiter = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
            var matched = 0
            while (matched < delimiter.size) {
                val next = input.read()
                if (next == -1) {
                    return
                }
                headerBytes.write(next)
                matched = if (next.toByte() == delimiter[matched]) matched + 1 else 0
                check(headerBytes.size() <= MAX_HEADER_BYTES) { "request headers exceeded $MAX_HEADER_BYTES bytes" }
            }

            val headerText = String(headerBytes.toByteArray(), StandardCharsets.ISO_8859_1)
            val contentLength = CONTENT_LENGTH.find(headerText)?.groupValues?.get(1)?.toInt() ?: 0
            val bodyBytes = ByteArrayOutputStream()
            val buffer = ByteArray(8_192)
            var remaining = contentLength
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (read == -1) {
                    return
                }
                bodyBytes.write(buffer, 0, read)
                remaining -= read
            }
            requestBody = bodyBytes.toByteArray()
        }

        private companion object {
            private const val MAX_HEADER_BYTES = 65_536
            private val CONTENT_LENGTH = Regex("(?im)^Content-Length:\\s*(\\d+)\\s*$")
        }
    }
}
