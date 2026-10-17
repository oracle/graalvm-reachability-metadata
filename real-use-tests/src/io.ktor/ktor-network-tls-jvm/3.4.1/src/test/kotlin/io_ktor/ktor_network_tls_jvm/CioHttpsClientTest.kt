/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_network_tls_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
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
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * HTTPS requests through the CIO client engine's TLS configuration: an application's Ktor HTTP
 * client pins a self-signed server certificate and a cipher suite in `engine { https { } }` and
 * calls a JSON API over `https://`.
 */
class CioHttpsClientTest {
    @Test
    fun httpClientCallsAnHttpsApiThroughThePinnedTlsConfiguration(): Unit {
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

        TodoApiServer(keyStore).use { server ->
            val baseUrl = "https://localhost:${server.port}"
            val client = HttpClient(CIO) {
                expectSuccess = false
                engine {
                    https {
                        trustManager = trustManagerFor(keyStore)
                        cipherSuites = listOf(CIOCipherSuites.ECDHE_RSA_AES128_SHA256)
                    }
                }
            }

            val outcomes: List<Pair<HttpStatusCode, String>> = runBlocking {
                withTimeout(TIMEOUT_MILLIS) {
                    client.use { http ->
                        val listed: HttpResponse = http.get("$baseUrl/todos") {
                            header("X-Request-Id", "list-1")
                        }
                        val created: HttpResponse = http.post("$baseUrl/todos") {
                            contentType(ContentType.Application.Json)
                            setBody("""{"title":"renew certificate"}""")
                        }
                        val missing: HttpResponse = http.get("$baseUrl/todos/42")
                        listOf(listed, created, missing).map { it.status to it.bodyAsText() }
                    }
                }
            }

            assertEquals(
                listOf(
                    HttpStatusCode.OK to """[{"id":1,"title":"rotate keys"}]""",
                    HttpStatusCode.Created to """{"id":2,"title":"renew certificate"}""",
                    HttpStatusCode.NotFound to """{"error":"no todo 42"}""",
                ),
                outcomes,
            )

            val requests: List<ObservedRequest> = List(3) {
                checkNotNull(server.requests.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) { "request ${it + 1} not seen" }
            }
            assertEquals(listOf("GET /todos", "POST /todos", "GET /todos/42"), requests.map { it.requestLine })
            assertEquals("list-1", requests[0].headers["x-request-id"])
            assertEquals("""{"title":"renew certificate"}""", requests[1].body)
            requests.forEach { request ->
                assertEquals("TLSv1.2", request.protocol)
                assertEquals(CIOCipherSuites.ECDHE_RSA_AES128_SHA256.name, request.cipherSuite)
            }
        }
    }

    /** One HTTP request as the server decoded it, with the TLS parameters of its connection. */
    private data class ObservedRequest(
        val requestLine: String,
        val headers: Map<String, String>,
        val body: String,
        val protocol: String,
        val cipherSuite: String,
    )

    /**
     * A JDK TLS 1.2 server speaking a tiny JSON todo API over HTTP/1.1, one request per
     * connection, that records every request it decodes.
     */
    private class TodoApiServer(keyStore: KeyStore) : AutoCloseable {
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
            worker = Thread({ serve() }, "todo-api-server").apply { start() }
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

            val (method: String, path: String) = requestLine.split(' ')
            requests.add(
                ObservedRequest(
                    "$method $path",
                    headers,
                    body,
                    connection.session.protocol,
                    connection.session.cipherSuite,
                ),
            )
            val (status: String, payload: String) = when ("$method $path") {
                "GET /todos" -> "200 OK" to """[{"id":1,"title":"rotate keys"}]"""
                "POST /todos" -> "201 Created" to """{"id":2,${body.removePrefix("{")}"""
                else -> "404 Not Found" to """{"error":"no todo ${path.substringAfterLast('/')}"}"""
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
        const val ALIAS: String = "todo-api"
        const val PASSWORD: String = "changeit"
        const val TIMEOUT_MILLIS: Long = 30_000

        /** Trusts exactly the certificates held in [keyStore], as a pinned client would. */
        fun trustManagerFor(keyStore: KeyStore): X509TrustManager =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply { init(keyStore) }
                .trustManagers
                .filterIsInstance<X509TrustManager>()
                .single()
    }
}
