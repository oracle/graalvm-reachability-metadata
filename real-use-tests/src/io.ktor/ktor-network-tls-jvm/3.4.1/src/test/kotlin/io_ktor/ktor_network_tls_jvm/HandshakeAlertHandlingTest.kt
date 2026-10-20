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
 * Fatal TLS alert handling during the handshake: a payment client whose offer a gateway cannot
 * accept receives the gateway's `handshake_failure` or `protocol_version` alert as a
 * [TlsException], then falls back to a cipher suite the gateway supports and completes the payment.
 */
class HandshakeAlertHandlingTest {
    @Test
    fun clientSurfacesFatalHandshakeAlertsAndRecoversWithACompatibleSuite(): Unit {
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

        PaymentGateway(keyStore, "TLSv1.2").use { legacyGateway ->
            PaymentGateway(keyStore, "TLSv1.3").use { modernGateway ->
                val noCommonSuite: Throwable = paymentFailure(
                    legacyGateway.port,
                    trust,
                    listOf(CIOCipherSuites.ECDHE_ECDSA_AES128_SHA256, CIOCipherSuites.ECDHE_ECDSA_AES256_SHA384),
                )
                val versionTooOld: Throwable = paymentFailure(
                    modernGateway.port,
                    trust,
                    listOf(CIOCipherSuites.ECDHE_RSA_AES256_SHA384),
                )
                val receipt: String = pay(
                    legacyGateway.port,
                    trust,
                    listOf(CIOCipherSuites.ECDHE_RSA_AES256_SHA384),
                    "order-7 EUR 12.50",
                )

                assertInstanceOf(TlsException::class.java, noCommonSuite)
                assertEquals(alertMessage(TLSAlertType.HandshakeFailure), noCommonSuite.message)
                assertInstanceOf(TlsException::class.java, versionTooOld)
                assertEquals(alertMessage(TLSAlertType.ProtocolVersion), versionTooOld.message)
                assertEquals("accepted order-7 EUR 12.50", receipt)

                legacyGateway.close()
                modernGateway.close()
                assertEquals(
                    listOf("refused", "order-7 EUR 12.50 via ${CIOCipherSuites.ECDHE_RSA_AES256_SHA384.name}"),
                    legacyGateway.outcomes.toList(),
                )
                assertEquals(listOf("refused"), modernGateway.outcomes.toList())
            }
        }
    }

    /** Opens a TLS connection offering [suites], submits one [order] line and returns the receipt. */
    private fun pay(port: Int, trust: X509TrustManager, suites: List<CipherSuite>, order: String): String =
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
                            output.writeStringUtf8("$order\n")
                            checkNotNull(input.readLine()) { "gateway closed before answering '$order'" }
                        }
                    } finally {
                        plain.close()
                    }
                }
            }
        }

    /** Attempts the payment that [pay] performs and returns why the handshake was refused. */
    private fun paymentFailure(port: Int, trust: X509TrustManager, suites: List<CipherSuite>): Throwable {
        val outcome: Result<String> = runCatching { pay(port, trust, suites, "order-0 EUR 0.00") }
        return checkNotNull(outcome.exceptionOrNull()) { "handshake with port $port unexpectedly succeeded" }
    }

    /**
     * A JDK TLS payment gateway restricted to one protocol version and an RSA certificate; each
     * connection carries one order line and gets one receipt. It records, per connection, either
     * the order it accepted with the suite it negotiated or that it refused the handshake.
     */
    private class PaymentGateway(keyStore: KeyStore, protocol: String) : AutoCloseable {
        private val serverSocket: SSLServerSocket
        private val worker: Thread
        val outcomes: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val port: Int

        init {
            val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                .apply { init(keyStore, PASSWORD.toCharArray()) }
                .keyManagers
            val context = SSLContext.getInstance("TLS").apply { init(keyManagers, null, null) }
            serverSocket = context.serverSocketFactory
                .createServerSocket(0, 8, InetAddress.getLoopbackAddress()) as SSLServerSocket
            serverSocket.enabledProtocols = arrayOf(protocol)
            port = serverSocket.localPort
            worker = Thread({ serve() }, "payment-gateway-$protocol").apply { start() }
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
                val order: String = reader.readLine() ?: return
                outcomes += "$order via ${connection.session.cipherSuite}"
                connection.outputStream.apply {
                    write("accepted $order\n".toByteArray(StandardCharsets.UTF_8))
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
        const val ALIAS: String = "payment-gateway"
        const val PASSWORD: String = "changeit"
        const val TIMEOUT_MILLIS: Long = 30_000

        /** The message Ktor raises when the peer answers the handshake with a fatal [type] alert. */
        fun alertMessage(type: TLSAlertType): String =
            "Received alert during handshake. Level: ${TLSAlertLevel.FATAL}, code: $type"

        /** Trusts exactly the certificates held in [keyStore], as a pinned client would. */
        fun trustManagerFor(keyStore: KeyStore): X509TrustManager =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply { init(keyStore) }
                .trustManagers
                .filterIsInstance<X509TrustManager>()
                .single()
    }
}
