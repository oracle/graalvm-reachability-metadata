/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_cio_jvm

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.CIOEngineConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.forms.submitForm
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
import io.ktor.http.Parameters
import io.ktor.http.URLProtocol
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.withCharset
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeStringUtf8
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class Ktor_client_cio_jvmTest {
    @Test
    public fun `cio engine sends get requests with default headers query parameters and reads response metadata`(): Unit =
        runBlocking {
            withTimeout(TEST_TIMEOUT_MILLIS) {
                LocalHttpServer().use { server: LocalHttpServer ->
                    server.handle("/inspect") { exchange: HttpExchange ->
                        val responseText: String = buildString {
                            appendLine("method=${exchange.requestMethod}")
                            appendLine("query=${exchange.requestURI.rawQuery}")
                            appendLine("defaultHeader=${exchange.requestHeaders.getFirst("X-Default-Header")}")
                            appendLine("callHeader=${exchange.requestHeaders.getFirst("X-Call-Header")}")
                            append("protocol=${exchange.protocol}")
                        }
                        exchange.respond(
                            statusCode = HttpStatusCode.Accepted.value,
                            body = responseText,
                            contentType = "text/plain; charset=utf-8",
                            headers = mapOf("X-CIO-Engine" to "observed"),
                        )
                    }

                    withCioClient(
                        config = {
                            defaultRequest {
                                url {
                                    protocol = URLProtocol.HTTP
                                    host = server.host
                                    port = server.port
                                }
                                header("X-Default-Header", "from-default-request")
                            }
                        },
                    ) { client: HttpClient ->
                        val response: HttpResponse = client.get("/inspect") {
                            parameter("alpha", "one")
                            parameter("encoded", "a b")
                            header("X-Call-Header", "from-call")
                        }
                        val body: String = response.bodyAsText()

                        assertThat(response.status).isEqualTo(HttpStatusCode.Accepted)
                        assertThat(response.headers["X-CIO-Engine"]).isEqualTo("observed")
                        assertThat(body)
                            .contains("method=GET")
                            .contains("alpha=one")
                            .contains("encoded=")
                            .contains("defaultHeader=from-default-request")
                            .contains("callHeader=from-call")
                            .contains("protocol=HTTP/1.1")
                    }
                }
            }
        }

    @Test
    public fun `cio engine posts text bodies and preserves content headers`(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            LocalHttpServer().use { server: LocalHttpServer ->
                server.handle("/echo") { exchange: HttpExchange ->
                    val requestBody: String = exchange.readRequestBody()
                    val responseText: String = buildString {
                        appendLine("method=${exchange.requestMethod}")
                        appendLine("contentType=${exchange.requestHeaders.getFirst(HttpHeaders.ContentType)}")
                        appendLine("length=${requestBody.toByteArray(StandardCharsets.UTF_8).size}")
                        append("body=$requestBody")
                    }
                    exchange.respond(
                        statusCode = HttpStatusCode.Created.value,
                        body = responseText,
                        contentType = "text/plain; charset=utf-8",
                    )
                }

                withCioClient { client: HttpClient ->
                    val payload: String = "hello from Ktor CIO ✓"
                    val response: HttpResponse = client.post(server.url("/echo")) {
                        contentType(ContentType.Text.Plain.withCharset(StandardCharsets.UTF_8))
                        header("X-Request-Id", "post-text")
                        setBody(payload)
                    }

                    assertThat(response.status).isEqualTo(HttpStatusCode.Created)
                    assertThat(response.bodyAsText())
                        .contains("method=POST")
                        .contains("contentType=text/plain; charset=UTF-8")
                        .contains("length=${payload.toByteArray(StandardCharsets.UTF_8).size}")
                        .contains("body=$payload")
                }
            }
        }
    }

    @Test
    public fun `cio engine streams write channel request content with chunked transfer`(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            LocalHttpServer().use { server: LocalHttpServer ->
                server.handle("/stream") { exchange: HttpExchange ->
                    val requestBody: String = exchange.readRequestBody()
                    val responseText: String = buildString {
                        appendLine("method=${exchange.requestMethod}")
                        appendLine("contentType=${exchange.requestHeaders.getFirst(HttpHeaders.ContentType)}")
                        val contentLength: String =
                            exchange.requestHeaders.getFirst(HttpHeaders.ContentLength) ?: "missing"
                        val transferEncoding: String? = exchange.requestHeaders.getFirst(HttpHeaders.TransferEncoding)
                        appendLine("contentLength=$contentLength")
                        appendLine("transferEncoding=$transferEncoding")
                        append("body=$requestBody")
                    }
                    exchange.respond(HttpStatusCode.OK.value, responseText, "text/plain; charset=utf-8")
                }

                withCioClient { client: HttpClient ->
                    val chunks: List<String> = listOf("alpha", "-beta", "-gamma")
                    val response: HttpResponse = client.post(server.url("/stream")) {
                        setBody(
                            object : OutgoingContent.WriteChannelContent() {
                                override val contentType: ContentType =
                                    ContentType.Text.Plain.withCharset(StandardCharsets.UTF_8)

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
                    assertThat(response.bodyAsText())
                        .contains("method=POST")
                        .contains("contentType=text/plain; charset=UTF-8")
                        .contains("contentLength=missing")
                        .contains("transferEncoding=chunked")
                        .contains("body=alpha-beta-gamma")
                }
            }
        }
    }

    @Test
    public fun `cio engine submits url encoded forms and stores response cookies`(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            LocalHttpServer().use { server: LocalHttpServer ->
                server.handle("/login") { exchange: HttpExchange ->
                    val requestBody: String = exchange.readRequestBody()
                    exchange.respond(
                        statusCode = HttpStatusCode.OK.value,
                        body = "contentType=${exchange.requestHeaders.getFirst(HttpHeaders.ContentType)}\nbody=$requestBody",
                        contentType = "text/plain; charset=utf-8",
                        headers = mapOf(HttpHeaders.SetCookie to "sessionId=cio-cookie; Path=/; HttpOnly"),
                    )
                }
                server.handle("/profile") { exchange: HttpExchange ->
                    val cookieHeader: String = exchange.requestHeaders.getFirst(HttpHeaders.Cookie) ?: "missing"
                    exchange.respond(HttpStatusCode.OK.value, cookieHeader, "text/plain; charset=utf-8")
                }

                withCioClient({ install(HttpCookies) }) { client: HttpClient ->
                    val loginBody: String = client.submitForm(
                        url = server.url("/login"),
                        formParameters = Parameters.build {
                            append("username", "native image")
                            append("engine", "CIO")
                        },
                    ).bodyAsText()
                    val cookieHeader: String = client.get(server.url("/profile")).bodyAsText()

                    assertThat(loginBody)
                        .contains("contentType=application/x-www-form-urlencoded")
                        .contains("username=native+image")
                        .contains("engine=CIO")
                    assertThat(cookieHeader).contains("sessionId=cio-cookie")
                }
            }
        }
    }

    @Test
    public fun `cio engine reads binary response bodies and leaves redirects visible when disabled`(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            LocalHttpServer().use { server: LocalHttpServer ->
                val payload: ByteArray = byteArrayOf(0, 1, 2, 3, 5, 8, 13, 21, -1)
                server.handle("/binary") { exchange: HttpExchange ->
                    exchange.respond(
                        statusCode = HttpStatusCode.OK.value,
                        body = payload,
                        contentType = "application/octet-stream",
                        headers = mapOf("X-Payload-Kind" to "fibonacci-bytes"),
                    )
                }
                server.handle("/redirect") { exchange: HttpExchange ->
                    exchange.respond(
                        statusCode = HttpStatusCode.Found.value,
                        body = "redirecting",
                        contentType = "text/plain; charset=utf-8",
                        headers = mapOf(HttpHeaders.Location to server.url("/binary")),
                    )
                }

                withCioClient({ followRedirects = false }) { client: HttpClient ->
                    val binaryResponse: HttpResponse = client.get(server.url("/binary"))
                    val redirectResponse: HttpResponse = client.get(server.url("/redirect"))

                    assertThat(binaryResponse.status).isEqualTo(HttpStatusCode.OK)
                    assertThat(binaryResponse.headers["X-Payload-Kind"]).isEqualTo("fibonacci-bytes")
                    assertThat(binaryResponse.bodyAsBytes()).isEqualTo(payload)
                    assertThat(redirectResponse.status).isEqualTo(HttpStatusCode.Found)
                    assertThat(redirectResponse.headers[HttpHeaders.Location]).isEqualTo(server.url("/binary"))
                    assertThat(redirectResponse.bodyAsText()).isEqualTo("redirecting")
                }
            }
        }
    }

    @Test
    public fun `cio engine handles concurrent requests with bounded connection configuration`(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            LocalHttpServer().use { server: LocalHttpServer ->
                server.handle("/sum") { exchange: HttpExchange ->
                    val query: Map<String, String> = exchange.requestURI.rawQuery
                        .split('&')
                        .map { parameter: String -> parameter.substringBefore('=') to parameter.substringAfter('=') }
                        .toMap()
                    val left: Int = query.getValue("left").toInt()
                    val right: Int = query.getValue("right").toInt()
                    exchange.respond(HttpStatusCode.OK.value, (left + right).toString(), "text/plain; charset=utf-8")
                }

                withCioClient({
                    engine {
                        maxConnectionsCount = 4
                    }
                }) { client: HttpClient ->
                    val responses: List<String> = (1..8).map { index: Int ->
                        async {
                            client.get(server.url("/sum?left=$index&right=${index * 2}")).bodyAsText()
                        }
                    }.map { deferred -> deferred.await() }

                    assertThat(responses).containsExactlyElementsOf((1..8).map { index: Int -> (index * 3).toString() })
                }
            }
        }
    }

    @Test
    public fun `cio websocket session sends and receives text frames`(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            LocalWebSocketServer { message: String -> "echo:$message" }.use { server: LocalWebSocketServer ->
                server.start()

                withCioClient({ install(WebSockets) }) { client: HttpClient ->
                    client.webSocket(urlString = server.url("/chat")) {
                        send(Frame.Text("native-image websocket over CIO"))

                        val frame: Frame = withTimeout(5_000) { incoming.receive() }
                        assertThat(frame).isInstanceOf(Frame.Text::class.java)
                        assertThat((frame as Frame.Text).readText()).isEqualTo("echo:native-image websocket over CIO")
                    }
                }

                server.awaitHandled()
            }
        }
    }

    private suspend fun <T> withCioClient(
        config: HttpClientConfig<CIOEngineConfig>.() -> Unit = {},
        block: suspend (HttpClient) -> T,
    ): T {
        val client: HttpClient = HttpClient(CIO) {
            install(HttpTimeout) {
                connectTimeoutMillis = 2_000
                socketTimeoutMillis = 5_000
                requestTimeoutMillis = 5_000
            }
            config()
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

    private class LocalHttpServer : AutoCloseable {
        private val threadCounter: AtomicInteger = AtomicInteger()
        private val executor: ExecutorService = Executors.newCachedThreadPool { runnable: Runnable ->
            Thread(runnable, "ktor-cio-http-server-${threadCounter.incrementAndGet()}").apply {
                isDaemon = true
            }
        }
        private val server: HttpServer = HttpServer.create(
            InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
            0,
        )

        val host: String = server.address.address.hostAddress

        val port: Int
            get() = server.address.port

        init {
            server.executor = executor
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
            executor.awaitTermination(10, TimeUnit.SECONDS)
        }
    }

    private class LocalWebSocketServer(
        private val responseFor: (String) -> String,
    ) : AutoCloseable {
        private val serverSocket: ServerSocket = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable: Runnable ->
            Thread(runnable, "ktor-cio-websocket-server").apply {
                isDaemon = true
            }
        }
        private var handling: Future<*>? = null

        fun start(): Unit {
            handling = executor.submit {
                serverSocket.accept().use { socket: Socket ->
                    socket.soTimeout = 5_000
                    val input: BufferedInputStream = BufferedInputStream(socket.getInputStream())
                    val requestHeaders: List<String> = readHttpHeaders(input)
                    val key: String = requestHeaders
                        .first { header: String -> header.startsWith("Sec-WebSocket-Key:", ignoreCase = true) }
                        .substringAfter(':')
                        .trim()

                    val response: String = buildString {
                        append("HTTP/1.1 101 Switching Protocols\r\n")
                        append("Upgrade: websocket\r\n")
                        append("Connection: Upgrade\r\n")
                        append("Sec-WebSocket-Accept: ${webSocketAccept(key)}\r\n")
                        append("\r\n")
                    }
                    socket.getOutputStream().write(response.toByteArray(StandardCharsets.ISO_8859_1))
                    socket.getOutputStream().flush()

                    val receivedText: String = readClientTextFrame(input)
                    writeServerTextFrame(socket, responseFor(receivedText))
                    writeServerCloseFrame(socket)
                }
            }
        }

        fun url(path: String): String = "ws://${serverSocket.inetAddress.hostAddress}:${serverSocket.localPort}$path"

        fun awaitHandled(): Unit {
            handling?.get(10, TimeUnit.SECONDS)
        }

        override fun close(): Unit {
            serverSocket.close()
            executor.shutdownNow()
            executor.awaitTermination(10, TimeUnit.SECONDS)
        }
    }

    private companion object {
        private const val TEST_TIMEOUT_MILLIS: Long = 15_000
        private const val WEB_SOCKET_GUID: String = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

        private fun HttpExchange.readRequestBody(): String =
            requestBody.readBytes().toString(StandardCharsets.UTF_8)

        private fun HttpExchange.respond(
            statusCode: Int,
            body: String,
            contentType: String,
            headers: Map<String, String> = emptyMap(),
        ): Unit = respond(statusCode, body.toByteArray(StandardCharsets.UTF_8), contentType, headers)

        private fun HttpExchange.respond(
            statusCode: Int,
            body: ByteArray,
            contentType: String,
            headers: Map<String, String> = emptyMap(),
        ): Unit {
            for ((name: String, value: String) in headers) {
                responseHeaders.add(name, value)
            }
            responseHeaders.add(HttpHeaders.ContentType, contentType)
            sendResponseHeaders(statusCode, body.size.toLong())
            responseBody.use { output -> output.write(body) }
        }

        private fun readHttpHeaders(input: InputStream): List<String> {
            val bytes: ByteArrayOutputStream = ByteArrayOutputStream()
            var current: Int
            var matched: Int = 0
            while (true) {
                current = input.read()
                if (current == -1) {
                    error("WebSocket client closed before completing the handshake")
                }
                bytes.write(current)
                matched = when {
                    matched == 0 && current == '\r'.code -> 1
                    matched == 1 && current == '\n'.code -> 2
                    matched == 2 && current == '\r'.code -> 3
                    matched == 3 && current == '\n'.code -> 4
                    current == '\r'.code -> 1
                    else -> 0
                }
                if (matched == 4) {
                    break
                }
            }
            return bytes.toString(StandardCharsets.ISO_8859_1)
                .lineSequence()
                .filter { line: String -> line.isNotBlank() }
                .toList()
        }

        private fun webSocketAccept(key: String): String {
            val digest: ByteArray = MessageDigest.getInstance("SHA-1")
                .digest((key + WEB_SOCKET_GUID).toByteArray(StandardCharsets.ISO_8859_1))
            return Base64.getEncoder().encodeToString(digest)
        }

        private fun readClientTextFrame(input: InputStream): String {
            val firstByte: Int = input.readRequiredByte()
            val secondByte: Int = input.readRequiredByte()
            val opcode: Int = firstByte and 0x0F
            val masked: Boolean = (secondByte and 0x80) != 0
            val lengthCode: Int = secondByte and 0x7F
            val length: Int = when (lengthCode) {
                in 0..125 -> lengthCode
                126 -> (input.readRequiredByte() shl 8) or input.readRequiredByte()
                else -> error("Unexpected long WebSocket frame in test: $lengthCode")
            }
            assertThat(opcode).isEqualTo(0x1)
            assertThat(masked).isTrue()

            val mask: ByteArray = input.readRequiredBytes(4)
            val payload: ByteArray = input.readRequiredBytes(length)
            for (index: Int in payload.indices) {
                payload[index] = (payload[index].toInt() xor mask[index % mask.size].toInt()).toByte()
            }
            return payload.toString(StandardCharsets.UTF_8)
        }

        private fun writeServerTextFrame(socket: Socket, text: String): Unit {
            val payload: ByteArray = text.toByteArray(StandardCharsets.UTF_8)
            assertThat(payload.size).isLessThan(126)
            socket.getOutputStream().write(byteArrayOf(0x81.toByte(), payload.size.toByte()))
            socket.getOutputStream().write(payload)
            socket.getOutputStream().flush()
        }

        private fun writeServerCloseFrame(socket: Socket): Unit {
            socket.getOutputStream().write(byteArrayOf(0x88.toByte(), 0x00))
            socket.getOutputStream().flush()
        }

        private fun InputStream.readRequiredByte(): Int {
            val value: Int = read()
            if (value == -1) {
                error("Unexpected end of stream")
            }
            return value
        }

        private fun InputStream.readRequiredBytes(length: Int): ByteArray {
            val buffer: ByteArray = ByteArray(length)
            var offset: Int = 0
            while (offset < length) {
                val read: Int = read(buffer, offset, length - offset)
                if (read == -1) {
                    error("Unexpected end of stream")
                }
                offset += read
            }
            return buffer
        }
    }
}
