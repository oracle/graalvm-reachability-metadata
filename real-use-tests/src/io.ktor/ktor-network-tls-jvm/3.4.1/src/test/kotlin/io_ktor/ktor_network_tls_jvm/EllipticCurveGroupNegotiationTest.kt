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
import io.ktor.network.tls.TLSAlertLevel
import io.ktor.network.tls.TLSAlertType
import io.ktor.network.tls.TlsException
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
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Collections
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Elliptic-curve group negotiation for the ECDHE key exchange: a telemetry agent's client hello
 * offers Ktor's supported named groups, so collectors restricted to `secp256r1` or to `secp384r1`
 * each complete an ephemeral key agreement on their own curve, while a collector that only
 * accepts `x25519` and `secp521r1` refuses the agent with a `handshake_failure` alert.
 */
class EllipticCurveGroupNegotiationTest {
    @Test
    fun clientAgreesOnTheCollectorsCurveAndIsRefusedWhenNoGroupIsShared(): Unit {
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
        val trust: X509TrustManager = trustManagerFor(keyStore)
        val suites: List<CipherSuite> = listOf(
            CIOCipherSuites.ECDHE_RSA_AES256_SHA384,
            CIOCipherSuites.ECDHE_RSA_AES128_SHA256,
        )

        TelemetryCollector(keyStore, listOf("secp256r1")).use { p256Collector ->
            TelemetryCollector(keyStore, listOf("secp384r1")).use { p384Collector ->
                TelemetryCollector(keyStore, listOf("x25519", "secp521r1")).use { modernOnlyCollector ->
                    val p256Ack: String = report(p256Collector.port, trust, suites, "cpu=41 host=web-1")
                    val p384Ack: String = report(p384Collector.port, trust, suites, "mem=73 host=web-2")
                    val refusal: Throwable = checkNotNull(
                        runCatching { report(modernOnlyCollector.port, trust, suites, "disk=12 host=web-3") }
                            .exceptionOrNull(),
                    ) { "a collector sharing no named group with the agent accepted it" }

                    assertEquals("stored cpu=41 host=web-1", p256Ack)
                    assertEquals("stored mem=73 host=web-2", p384Ack)
                    assertInstanceOf(TlsException::class.java, refusal)
                    assertEquals(
                        "Received alert during handshake. Level: ${TLSAlertLevel.FATAL}, " +
                            "code: ${TLSAlertType.HandshakeFailure}",
                        refusal.message,
                    )

                    p256Collector.close()
                    p384Collector.close()
                    modernOnlyCollector.close()
                    val negotiated = "via ${CIOCipherSuites.ECDHE_RSA_AES256_SHA384.name}"
                    assertEquals(listOf("cpu=41 host=web-1 $negotiated"), p256Collector.outcomes.toList())
                    assertEquals(listOf("mem=73 host=web-2 $negotiated"), p384Collector.outcomes.toList())
                    assertEquals(listOf("refused"), modernOnlyCollector.outcomes.toList())
                }
            }
        }
    }

    /** Opens a TLS connection offering [suites], sends one [reading] line and returns the acknowledgement. */
    private fun report(port: Int, trust: X509TrustManager, suites: List<CipherSuite>, reading: String): String =
        runBlocking {
            withTimeout(TIMEOUT_MILLIS) {
                SelectorManager(Dispatchers.IO).use { selector ->
                    val plain = aSocket(selector).tcp().connect("127.0.0.1", port)
                    try {
                        val secure = plain.tls(Dispatchers.IO) {
                            trustManager = trust
                            serverName = "localhost"
                            cipherSuites = suites
                        }
                        secure.use { socket ->
                            val input = socket.openReadChannel()
                            val output = socket.openWriteChannel(autoFlush = true)
                            output.writeStringUtf8("$reading\n")
                            checkNotNull(input.readLine()) { "collector closed before acknowledging '$reading'" }
                        }
                    } finally {
                        plain.close()
                    }
                }
            }
        }

    /**
     * A JDK TLS 1.2 telemetry collector that only accepts ECDHE key agreement on [namedGroups];
     * each connection carries one reading line and gets one acknowledgement. It records, per
     * connection, either the reading it stored with the suite it negotiated or that it refused
     * the handshake.
     */
    private class TelemetryCollector(keyStore: KeyStore, namedGroups: List<String>) : AutoCloseable {
        private val serverSocket: SSLServerSocket
        private val worker: Thread
        val outcomes: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val port: Int

        init {
            val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                .apply { init(keyStore, PASSWORD.toCharArray()) }
                .keyManagers
            val context = SSLContext.getInstance("TLSv1.2").apply { init(keyManagers, null, null) }
            serverSocket = context.serverSocketFactory
                .createServerSocket(0, 4, InetAddress.getLoopbackAddress()) as SSLServerSocket
            serverSocket.sslParameters = serverSocket.sslParameters.apply {
                protocols = arrayOf("TLSv1.2")
                this.namedGroups = namedGroups.toTypedArray()
            }
            port = serverSocket.localPort
            worker = Thread({ serve() }, "telemetry-collector-${namedGroups.joinToString("+")}").apply { start() }
        }

        private fun serve(): Unit {
            while (true) {
                val connection: SSLSocket = try {
                    serverSocket.accept() as SSLSocket
                } catch (closed: SocketException) {
                    return
                }
                connection.use { answer(it) }
            }
        }

        private fun answer(connection: SSLSocket): Unit {
            connection.soTimeout = TIMEOUT_MILLIS.toInt()
            try {
                connection.startHandshake()
            } catch (refused: SSLHandshakeException) {
                outcomes += "refused"
                return
            }
            try {
                val reader = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
                val reading: String = reader.readLine() ?: return
                outcomes += "$reading via ${connection.session.cipherSuite}"
                connection.outputStream.apply {
                    write("stored $reading\n".toByteArray(StandardCharsets.UTF_8))
                    flush()
                }
            } catch (dropped: IOException) {
                outcomes += "dropped: ${dropped.message}"
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
