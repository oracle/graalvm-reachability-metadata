/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_network_tls_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.http
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.network.tls.CIOCipherSuites
import io.ktor.network.tls.certificates.KeyType
import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.network.tls.extensions.HashAlgorithm
import io.ktor.network.tls.extensions.SignatureAlgorithm
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Base64
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * HTTPS through an authenticating HTTP `CONNECT` proxy: a CIO client behind a corporate egress
 * proxy tunnels pinned TLS sessions to an order API, so the proxy relays only encrypted records,
 * and a request carrying the wrong proxy credentials is refused before any TLS handshake starts.
 */
class HttpsThroughConnectProxyTest {
    @Test
    fun clientTunnelsPinnedTlsThroughAnAuthenticatingConnectProxy(): Unit {
        val keyStore: KeyStore = buildKeyStore {
            certificate(ALIAS) {
                password = PASSWORD
                hash = HashAlgorithm.SHA256
                sign = SignatureAlgorithm.RSA
                keyType = KeyType.Server
                keySizeInBits = 2048
                domains = listOf("localhost")
                ipAddresses = listOf(InetAddress.getLoopbackAddress())
            }
        }

        OrderApiServer(keyStore).use { api ->
            EgressProxy(allowedTarget = "localhost:${api.port}").use { proxy ->
                val baseUrl = "https://localhost:${api.port}"
                val client = HttpClient(CIO) {
                    expectSuccess = false
                    engine {
                        this.proxy = ProxyBuilder.http("http://127.0.0.1:${proxy.port}")
                        https {
                            trustManager = trustManagerFor(keyStore)
                            cipherSuites = listOf(CIOCipherSuites.ECDHE_RSA_AES128_SHA256)
                        }
                    }
                }

                val outcome: Pair<List<Pair<HttpStatusCode, String>>, Throwable?> = runBlocking {
                    withTimeout(TIMEOUT_MILLIS) {
                        client.use { http ->
                            val fetched: HttpResponse = http.get("$baseUrl/orders/17") {
                                header(HttpHeaders.ProxyAuthorization, basic(PROXY_USER, PROXY_SECRET))
                            }
                            val placed: HttpResponse = http.post("$baseUrl/orders") {
                                header(HttpHeaders.ProxyAuthorization, basic(PROXY_USER, PROXY_SECRET))
                                contentType(ContentType.Application.Json)
                                setBody("""{"sku":"$SECRET_SKU","quantity":3}""")
                            }
                            val answers = listOf(fetched, placed).map { it.status to it.bodyAsText() }
                            val refusal: Throwable? = runCatching {
                                http.get("$baseUrl/orders/18") {
                                    header(HttpHeaders.ProxyAuthorization, basic(PROXY_USER, "guessed"))
                                }
                            }.exceptionOrNull()
                            answers to refusal
                        }
                    }
                }

                assertEquals(
                    listOf(
                        HttpStatusCode.OK to """{"id":17,"status":"shipped"}""",
                        HttpStatusCode.Created to """{"id":18,"sku":"$SECRET_SKU","quantity":3}""",
                    ),
                    outcome.first,
                )
                val refusal: Throwable = checkNotNull(outcome.second) { "wrong proxy credentials were accepted" }
                assertTrue(refusal is IOException, "unexpected refusal type: $refusal")

                val tunnels: List<Tunnel> = List(3) {
                    checkNotNull(proxy.tunnels.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) { "tunnel ${it + 1} not seen" }
                }
                assertEquals(List(3) { "CONNECT localhost:${api.port} HTTP/1.1" }, tunnels.map { it.connectLine })
                assertEquals(List(3) { "localhost:${api.port}" }, tunnels.map { it.hostHeader })
                assertEquals(listOf(true, true, false), tunnels.map { it.authorized })
                tunnels.take(2).forEach { tunnel ->
                    val upstream: ByteArray = tunnel.clientBytes
                    assertEquals(TLS_HANDSHAKE_RECORD, upstream.first(), "tunnel did not open with a TLS handshake")
                    val relayed = String(upstream, StandardCharsets.ISO_8859_1)
                    assertFalse(relayed.contains("/orders"), "proxy saw the request path in plaintext")
                    assertFalse(relayed.contains(SECRET_SKU), "proxy saw the request body in plaintext")
                    assertTrue(tunnel.serverByteCount > 0, "no bytes relayed back from the API")
                }
                assertEquals(0, tunnels[2].clientBytes.size)

                val requests: List<ObservedRequest> = List(2) {
                    checkNotNull(api.requests.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) { "request ${it + 1} not seen" }
                }
                assertEquals(listOf("GET /orders/17", "POST /orders"), requests.map { it.requestLine })
                assertEquals("""{"sku":"$SECRET_SKU","quantity":3}""", requests[1].body)
                requests.forEach { request ->
                    assertEquals("localhost:${api.port}", request.host)
                    assertEquals("TLSv1.2", request.protocol)
                    assertEquals(CIOCipherSuites.ECDHE_RSA_AES128_SHA256.name, request.cipherSuite)
                }
                assertEquals(null, api.requests.poll())
            }
        }
    }

    /** One `CONNECT` tunnel as the proxy handled it, with every byte the client sent through it. */
    private class Tunnel(
        val connectLine: String,
        val hostHeader: String?,
        val authorized: Boolean,
        val clientBytes: ByteArray,
        val serverByteCount: Long,
    )

    /** One HTTP request as the API decoded it, with the TLS parameters of its connection. */
    private data class ObservedRequest(
        val requestLine: String,
        val host: String?,
        val body: String,
        val protocol: String,
        val cipherSuite: String,
    )

    /**
     * A plain-socket HTTP forward proxy that only opens `CONNECT` tunnels to one destination for
     * callers presenting the right Basic credentials, and relays the tunnel bytes verbatim.
     */
    private class EgressProxy(private val allowedTarget: String) : AutoCloseable {
        private val serverSocket: ServerSocket = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
        private val worker: Thread
        val tunnels: LinkedBlockingQueue<Tunnel> = LinkedBlockingQueue()
        val port: Int = serverSocket.localPort

        init {
            worker = Thread({ serve() }, "egress-proxy").apply { start() }
        }

        private fun serve(): Unit {
            while (true) {
                val client: Socket = try {
                    serverSocket.accept()
                } catch (closed: SocketException) {
                    return
                }
                client.use { handle(it) }
            }
        }

        private fun handle(client: Socket): Unit {
            val input: InputStream = client.getInputStream()
            val output: OutputStream = client.getOutputStream()
            val head: List<String> = readHead(input)
            val connectLine: String = head.first()
            val headers: Map<String, String> = head.drop(1)
                .associate { line -> line.substringBefore(':').trim().lowercase() to line.substringAfter(':').trim() }
            val authorized: Boolean = headers["proxy-authorization"] == basic(PROXY_USER, PROXY_SECRET)
            val target: String = connectLine.split(' ')[1]
            if (!authorized || target != allowedTarget) {
                output.write(
                    (
                        "HTTP/1.1 407 Proxy Authentication Required\r\n" +
                            "Proxy-Authenticate: Basic realm=\"egress\"\r\n" +
                            "Content-Length: 0\r\n" +
                            "Connection: close\r\n\r\n"
                        ).toByteArray(StandardCharsets.US_ASCII),
                )
                output.flush()
                tunnels.add(Tunnel(connectLine, headers["host"], false, ByteArray(0), 0))
                return
            }

            Socket(InetAddress.getLoopbackAddress(), target.substringAfterLast(':').toInt()).use { upstream ->
                output.write("HTTP/1.1 200 Connection established\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
                output.flush()
                val fromClient = ByteArrayOutputStream()
                var fromServer: Long = 0
                val downstream = Thread({
                    fromServer = pump(upstream.getInputStream(), output, null)
                    client.close()
                    upstream.close()
                }, "egress-proxy-downstream").apply { start() }
                pump(input, upstream.getOutputStream(), fromClient)
                downstream.join(TIMEOUT_MILLIS)
                val captured: ByteArray = synchronized(fromClient) { fromClient.toByteArray() }
                tunnels.add(Tunnel(connectLine, headers["host"], true, captured, fromServer))
            }
        }

        /** Copies [from] into [to] until either side closes, recording the bytes in [copy]. */
        private fun pump(from: InputStream, to: OutputStream, copy: ByteArrayOutputStream?): Long {
            val buffer = ByteArray(4096)
            var total: Long = 0
            try {
                while (true) {
                    val count: Int = from.read(buffer)
                    if (count < 0) {
                        break
                    }
                    copy?.let { synchronized(it) { it.write(buffer, 0, count) } }
                    to.write(buffer, 0, count)
                    to.flush()
                    total += count
                }
            } catch (closed: IOException) {
                // The other direction ended the tunnel.
            }
            return total
        }

        /** Reads the request head byte by byte so no tunnel byte is consumed with it. */
        private fun readHead(input: InputStream): List<String> {
            val head = ByteArrayOutputStream()
            while (!head.toString(StandardCharsets.US_ASCII).endsWith("\r\n\r\n")) {
                val next: Int = input.read()
                check(next >= 0) { "client closed before finishing the CONNECT request" }
                head.write(next)
            }
            return head.toString(StandardCharsets.US_ASCII).trimEnd().split("\r\n")
        }

        override fun close(): Unit {
            serverSocket.close()
            worker.join(TIMEOUT_MILLIS)
        }
    }

    /**
     * A JDK TLS 1.2 server speaking a tiny JSON order API over HTTP/1.1, one request per
     * connection, that records every request it decodes.
     */
    private class OrderApiServer(keyStore: KeyStore) : AutoCloseable {
        private val serverSocket: SSLServerSocket
        private val worker: Thread
        val requests: LinkedBlockingQueue<ObservedRequest> = LinkedBlockingQueue()
        val port: Int

        init {
            val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                .apply { init(keyStore, PASSWORD.toCharArray()) }
                .keyManagers
            val context = SSLContext.getInstance("TLSv1.2").apply { init(keyManagers, null, null) }
            serverSocket = context.serverSocketFactory
                .createServerSocket(0, 8, InetAddress.getLoopbackAddress()) as SSLServerSocket
            serverSocket.enabledProtocols = arrayOf("TLSv1.2")
            port = serverSocket.localPort
            worker = Thread({ serve() }, "order-api-server").apply { start() }
        }

        private fun serve(): Unit {
            while (true) {
                val connection: SSLSocket = try {
                    serverSocket.accept() as SSLSocket
                } catch (closed: SocketException) {
                    return
                }
                connection.use { handle(it) }
            }
        }

        private fun handle(connection: SSLSocket): Unit {
            val reader = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
            val requestLine: String = reader.readLine() ?: return
            val headers: Map<String, String> = generateSequence { reader.readLine() }
                .takeWhile { it.isNotEmpty() }
                .associate { line -> line.substringBefore(':').trim().lowercase() to line.substringAfter(':').trim() }
            val contentLength: Int = headers["content-length"]?.toInt() ?: 0
            val body: String = CharArray(contentLength).also { buffer ->
                var read = 0
                while (read < contentLength) {
                    val count: Int = reader.read(buffer, read, contentLength - read)
                    check(count >= 0) { "body ended after $read of $contentLength chars" }
                    read += count
                }
            }.concatToString()

            val (method: String, target: String) = requestLine.split(' ')
            // Servers must accept the absolute form a client behind a proxy may send.
            val path: String = if (target.startsWith("https://")) {
                "/" + target.removePrefix("https://").substringAfter('/', "")
            } else {
                target
            }
            requests.add(
                ObservedRequest(
                    "$method $path",
                    headers["host"],
                    body,
                    connection.session.protocol,
                    connection.session.cipherSuite,
                ),
            )
            val (status: String, payload: String) = when ("$method $path") {
                "GET /orders/17" -> "200 OK" to """{"id":17,"status":"shipped"}"""
                "POST /orders" -> "201 Created" to """{"id":18,${body.removePrefix("{")}"""
                else -> "404 Not Found" to """{"error":"no route $method $path"}"""
            }
            val bytes: ByteArray = payload.toByteArray(StandardCharsets.UTF_8)
            val head = "HTTP/1.1 $status\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
            connection.outputStream.apply {
                write(head.toByteArray(StandardCharsets.US_ASCII))
                write(bytes)
                flush()
            }
        }

        override fun close(): Unit {
            serverSocket.close()
            worker.join(TIMEOUT_MILLIS)
        }
    }

    private companion object {
        const val ALIAS: String = "order-api"
        const val PASSWORD: String = "changeit"
        const val PROXY_USER: String = "build-agent"
        const val PROXY_SECRET: String = "egress-2026"
        const val SECRET_SKU: String = "SKU-CONFIDENTIAL-4411"
        const val TLS_HANDSHAKE_RECORD: Byte = 22
        const val TIMEOUT_MILLIS: Long = 30_000

        /** The `Proxy-Authorization` value for HTTP Basic credentials. */
        fun basic(user: String, secret: String): String =
            "Basic " + Base64.getEncoder().encodeToString("$user:$secret".toByteArray(StandardCharsets.UTF_8))

        /** Trusts exactly the certificates held in [keyStore], as a pinned client would. */
        fun trustManagerFor(keyStore: KeyStore): X509TrustManager =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply { init(keyStore) }
                .trustManagers
                .filterIsInstance<X509TrustManager>()
                .single()
    }
}
