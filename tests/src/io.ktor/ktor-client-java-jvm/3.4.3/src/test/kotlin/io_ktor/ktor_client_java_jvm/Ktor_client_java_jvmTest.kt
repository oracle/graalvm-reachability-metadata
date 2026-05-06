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
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.withCharset
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeStringUtf8
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URL
import java.net.http.HttpClient as JdkHttpClient
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
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
    fun `post request streams write channel content through java engine`(): Unit = runBlocking {
        LocalTestServer().use { server: LocalTestServer ->
            server.handle("/stream") { exchange: HttpExchange ->
                val requestBody: String = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
                exchange.respond(
                    statusCode = 200,
                    body = "streamed=$requestBody",
                    headers = mapOf("X-Observed-Method" to exchange.requestMethod),
                )
            }
            server.start()

            withJavaClient { client: HttpClient ->
                val chunks: List<String> = listOf("alpha", "-beta", "-gamma")
                val response = client.post(server.url("/stream")) {
                    setBody(
                        object : OutgoingContent.WriteChannelContent() {
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

    @Test
    fun `websocket session sends and receives text frames through java engine`(): Unit = runBlocking {
        LocalWebSocketServer { message: String -> "echo:$message" }.use { server: LocalWebSocketServer ->
            server.start()

            withJavaClient(clientConfig = { install(WebSockets) }) { client: HttpClient ->
                client.webSocket(urlString = server.url("/chat")) {
                    send(Frame.Text("native-image websocket"))

                    val frame: Frame = withTimeout(5_000) {
                        incoming.receive()
                    }
                    assertThat(frame).isInstanceOf(Frame.Text::class.java)
                    assertThat((frame as Frame.Text).readText()).isEqualTo("echo:native-image websocket")
                }
            }

            server.awaitHandled()
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

    private class LocalWebSocketServer(
        private val responseFor: (String) -> String,
    ) : AutoCloseable {
        private val serverSocket: ServerSocket = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable: Runnable ->
            Thread(runnable, "ktor-java-test-websocket-server").apply {
                isDaemon = true
            }
        }
        private var handler: Future<*>? = null

        fun start(): Unit {
            handler = executor.submit {
                serverSocket.use { listeningSocket: ServerSocket ->
                    val socket: Socket = listeningSocket.accept()
                    socket.soTimeout = 5_000
                    socket.use { acceptedSocket: Socket ->
                        handleConnection(acceptedSocket)
                    }
                }
            }
        }

        fun url(path: String): String {
            val hostAddress: String = serverSocket.inetAddress.hostAddress
            val host: String = if (hostAddress.contains(':')) "[$hostAddress]" else hostAddress
            return "ws://$host:${serverSocket.localPort}$path"
        }

        fun awaitHandled(): Unit {
            handler?.get(10, TimeUnit.SECONDS)
        }

        override fun close(): Unit {
            serverSocket.close()
            executor.shutdownNow()
        }

        private fun handleConnection(socket: Socket): Unit {
            val input: BufferedInputStream = BufferedInputStream(socket.getInputStream())
            val output: OutputStream = socket.getOutputStream()
            val requestLine: String = input.readHttpLine()
            check(requestLine.startsWith("GET ")) { "Expected WebSocket upgrade request" }

            val headers: MutableMap<String, String> = mutableMapOf()
            while (true) {
                val line: String = input.readHttpLine()
                if (line.isEmpty()) {
                    break
                }
                val separatorIndex: Int = line.indexOf(':')
                if (separatorIndex != -1) {
                    headers[line.substring(0, separatorIndex).lowercase()] = line.substring(separatorIndex + 1).trim()
                }
            }

            val webSocketKey: String = checkNotNull(headers["sec-websocket-key"]) {
                "Missing WebSocket key"
            }
            writeHandshakeResponse(output, webSocketKey)

            val incomingFrame: WebSocketFrameData = input.readWebSocketFrame()
            check(incomingFrame.opcode == TEXT_OPCODE) { "Expected text frame" }
            output.writeTextFrame(responseFor(incomingFrame.payload.toString(StandardCharsets.UTF_8)))

            val closeFrame: WebSocketFrameData = input.readWebSocketFrame()
            check(closeFrame.opcode == CLOSE_OPCODE) { "Expected close frame" }
            output.writeCloseFrame()
        }

        private fun writeHandshakeResponse(output: OutputStream, webSocketKey: String): Unit {
            val acceptBytes: ByteArray = MessageDigest.getInstance("SHA-1").digest(
                (webSocketKey + WEB_SOCKET_ACCEPT_GUID).toByteArray(StandardCharsets.US_ASCII),
            )
            val acceptHeader: String = Base64.getEncoder().encodeToString(acceptBytes)
            val response: String = "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: $acceptHeader\r\n" +
                "\r\n"
            output.write(response.toByteArray(StandardCharsets.US_ASCII))
            output.flush()
        }
    }
}

private const val TEXT_OPCODE: Int = 0x1
private const val CLOSE_OPCODE: Int = 0x8
private const val WEB_SOCKET_ACCEPT_GUID: String = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

private data class WebSocketFrameData(
    val opcode: Int,
    val payload: ByteArray,
)

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

private fun BufferedInputStream.readHttpLine(): String {
    val bytes: ByteArrayOutputStream = ByteArrayOutputStream()
    while (true) {
        val nextByte: Int = read()
        check(nextByte != -1) { "Unexpected end of HTTP headers" }
        if (nextByte == '\n'.code) {
            return bytes.toString(StandardCharsets.US_ASCII).removeSuffix("\r")
        }
        bytes.write(nextByte)
    }
}

private fun BufferedInputStream.readWebSocketFrame(): WebSocketFrameData {
    val firstByte: Int = readRequiredByte()
    val secondByte: Int = readRequiredByte()
    val opcode: Int = firstByte and 0x0f
    val masked: Boolean = (secondByte and 0x80) != 0
    val payloadLength: Int = when (val initialLength: Int = secondByte and 0x7f) {
        126 -> readUnsignedShort()
        127 -> readLongPayloadLength()
        else -> initialLength
    }
    val mask: ByteArray = if (masked) readRequiredBytes(4) else ByteArray(0)
    val payload: ByteArray = readRequiredBytes(payloadLength)
    if (masked) {
        for (index: Int in payload.indices) {
            payload[index] = (payload[index].toInt() xor mask[index % mask.size].toInt()).toByte()
        }
    }
    return WebSocketFrameData(opcode = opcode, payload = payload)
}

private fun BufferedInputStream.readRequiredByte(): Int {
    val value: Int = read()
    check(value != -1) { "Unexpected end of WebSocket frame" }
    return value
}

private fun BufferedInputStream.readRequiredBytes(size: Int): ByteArray {
    val bytes: ByteArray = readNBytes(size)
    check(bytes.size == size) { "Unexpected end of WebSocket frame" }
    return bytes
}

private fun BufferedInputStream.readUnsignedShort(): Int {
    val bytes: ByteArray = readRequiredBytes(2)
    return ((bytes[0].toInt() and 0xff) shl 8) or (bytes[1].toInt() and 0xff)
}

private fun BufferedInputStream.readLongPayloadLength(): Int {
    val bytes: ByteArray = readRequiredBytes(8)
    var value: Long = 0
    for (byte: Byte in bytes) {
        value = (value shl 8) or (byte.toLong() and 0xff)
    }
    check(value <= Int.MAX_VALUE) { "WebSocket frame is too large" }
    return value.toInt()
}

private fun OutputStream.writeTextFrame(text: String): Unit {
    writeWebSocketFrame(opcode = TEXT_OPCODE, payload = text.toByteArray(StandardCharsets.UTF_8))
}

private fun OutputStream.writeCloseFrame(): Unit {
    writeWebSocketFrame(opcode = CLOSE_OPCODE, payload = ByteArray(0))
}

private fun OutputStream.writeWebSocketFrame(opcode: Int, payload: ByteArray): Unit {
    write(0x80 or opcode)
    when {
        payload.size < 126 -> write(payload.size)
        payload.size <= 65_535 -> {
            write(126)
            write((payload.size ushr 8) and 0xff)
            write(payload.size and 0xff)
        }
        else -> error("Test WebSocket payload is too large")
    }
    write(payload)
    flush()
}
