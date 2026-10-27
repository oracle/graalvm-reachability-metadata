/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_network_tls_jvm

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.tls.CIOCipherSuites
import io.ktor.network.tls.CipherSuite
import io.ktor.network.tls.certificates.KeyType
import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.network.tls.extensions.HashAlgorithm
import io.ktor.network.tls.extensions.SignatureAlgorithm
import io.ktor.network.tls.tls
import io.ktor.utils.io.readLine
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.Dispatchers
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
import java.security.cert.X509Certificate
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * ECDSA server authentication with the ECDHE_ECDSA cipher suites: an application ships
 * telemetry batches to a collector whose elliptic-curve certificate it generated with Ktor,
 * once over AES-128-GCM/SHA-256 and once over AES-256-GCM/SHA-384.
 */
class EcdsaServerAuthenticationTest {
    @Test
    fun clientVerifiesAnEllipticCurveServerUnderEachEcdsaSuite(): Unit {
        val keyStore: KeyStore = buildKeyStore {
            certificate(ALIAS) {
                password = PASSWORD
                hash = HashAlgorithm.SHA256
                sign = SignatureAlgorithm.ECDSA
                keyType = KeyType.Server
                keySizeInBits = 256
                domains = listOf("localhost")
                ipAddresses = listOf(InetAddress.getLoopbackAddress())
            }
        }
        val serverCertificate = keyStore.getCertificate(ALIAS) as X509Certificate
        assertEquals("EC", serverCertificate.publicKey.algorithm)

        val suites: List<CipherSuite> = listOf(
            CIOCipherSuites.ECDHE_ECDSA_AES128_SHA256,
            CIOCipherSuites.ECDHE_ECDSA_AES256_SHA384,
        )
        val batches: List<List<String>> = listOf(
            listOf("cpu host=web-1 value=0.42", "mem host=web-1 value=0.71"),
            listOf("cpu host=web-2 value=0.13", "disk host=web-2 value=0.88", "net host=web-2 value=1024"),
        )

        TelemetryCollector(keyStore).use { collector ->
            val acknowledgements: List<String> = runBlocking {
                withTimeout(TIMEOUT_MILLIS) {
                    SelectorManager(Dispatchers.IO).use { selector ->
                        suites.zip(batches).map { (suite, batch) ->
                            val plain = aSocket(selector).tcp().connect("127.0.0.1", collector.port)
                            val secure = plain.tls(Dispatchers.IO) {
                                trustManager = trustManagerFor(keyStore)
                                serverName = "localhost"
                                cipherSuites = listOf(suite)
                            }
                            secure.use { socket ->
                                val input = socket.openReadChannel()
                                val output = socket.openWriteChannel(autoFlush = true)
                                batch.forEach { metric -> output.writeStringUtf8("$metric\n") }
                                output.writeStringUtf8("$END_OF_BATCH\n")
                                checkNotNull(input.readLine()) { "collector closed before acknowledging ${suite.name}" }
                            }
                        }
                    }
                }
            }

            assertEquals(listOf("ACK 2 metrics", "ACK 3 metrics"), acknowledgements)

            val received: List<ReceivedBatch> = List(suites.size) {
                checkNotNull(collector.batches.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    "batch ${it + 1} not seen"
                }
            }
            assertEquals(batches, received.map { it.metrics })
            assertEquals(suites.map { it.name }, received.map { it.cipherSuite })
            received.forEach { batch -> assertEquals("TLSv1.2", batch.protocol) }
        }
    }

    /** One batch as the collector saw it, with the session parameters it arrived under. */
    private data class ReceivedBatch(val metrics: List<String>, val protocol: String, val cipherSuite: String)

    /**
     * A JDK TLS 1.2 collector holding the EC identity from [keyStore]; it reads metric lines
     * until the end marker and acknowledges how many it stored, one batch per connection.
     */
    private class TelemetryCollector(keyStore: KeyStore) : AutoCloseable {
        private val serverSocket: SSLServerSocket
        private val worker: Thread
        val batches: LinkedBlockingQueue<ReceivedBatch> = LinkedBlockingQueue()
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
            worker = Thread({ serve() }, "telemetry-collector").apply { start() }
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
            val metrics: List<String> = generateSequence { reader.readLine() }
                .takeWhile { it != END_OF_BATCH }
                .toList()
            batches.add(ReceivedBatch(metrics, connection.session.protocol, connection.session.cipherSuite))
            connection.outputStream.apply {
                write("ACK ${metrics.size} metrics\n".toByteArray(StandardCharsets.UTF_8))
                flush()
            }
        }

        override fun close(): Unit {
            serverSocket.close()
            worker.join(TIMEOUT_MILLIS)
        }
    }

    private companion object {
        const val ALIAS: String = "telemetry-collector"
        const val PASSWORD: String = "changeit"
        const val END_OF_BATCH: String = "."
        const val TIMEOUT_MILLIS: Long = 30_000

        /** Trusts exactly the certificates held in [keyStore], as a pinned peer would. */
        fun trustManagerFor(keyStore: KeyStore): X509TrustManager =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply { init(keyStore) }
                .trustManagers
                .filterIsInstance<X509TrustManager>()
                .single()
    }
}
