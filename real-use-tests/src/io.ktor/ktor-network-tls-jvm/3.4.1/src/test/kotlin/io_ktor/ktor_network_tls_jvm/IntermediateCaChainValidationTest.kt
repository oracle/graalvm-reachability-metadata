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
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Collections
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

/**
 * Certificate chain validation through an intermediate CA: an application that trusts only its
 * company's offline root reaches a payments gateway whose certificate an intermediate CA issued,
 * and refuses a gateway that omits the intermediate and one whose look-alike hierarchy hangs off
 * a root it never trusted.
 */
class IntermediateCaChainValidationTest {
    @Test
    fun clientBuildsThePathFromTheGatewayCertificateThroughTheIntermediateToTheTrustedRoot(): Unit {
        val corporate: Hierarchy = Hierarchy.issue()
        val lookAlike: Hierarchy = Hierarchy.issue()
        val trust = RecordingTrustManager(trustManagerFor(corporate.rootTrustStore()))

        GatewayServer(corporate.gatewayKeyStore(withIntermediate = true)).use { complete ->
            GatewayServer(corporate.gatewayKeyStore(withIntermediate = false)).use { leafOnly ->
                GatewayServer(lookAlike.gatewayKeyStore(withIntermediate = true)).use { foreign ->
                    val authorised: String = charge(complete.port, trust, "order-1001")
                    val completeChain: List<String> = trust.lastChain()

                    val missingIntermediate: Throwable = chargeFailure(leafOnly.port, trust)
                    assertInstanceOf(CertificateException::class.java, missingIntermediate)
                    assertEquals(listOf(GATEWAY_SUBJECT), trust.lastChain())

                    val untrustedRoot: Throwable = chargeFailure(foreign.port, trust)
                    assertInstanceOf(CertificateException::class.java, untrustedRoot)
                    assertEquals(listOf(GATEWAY_SUBJECT, INTERMEDIATE_SUBJECT), trust.lastChain())

                    val authorisedAgain: String = charge(complete.port, trust, "order-1002")

                    assertEquals("order-1001 authorised", authorised)
                    assertEquals("order-1002 authorised", authorisedAgain)
                    assertEquals(listOf(GATEWAY_SUBJECT, INTERMEDIATE_SUBJECT), completeChain)
                    complete.close()
                    leafOnly.close()
                    foreign.close()
                    assertEquals(listOf("order-1001", "order-1002"), complete.orders.toList())
                    assertEquals(emptyList<String>(), leafOnly.orders.toList())
                    assertEquals(emptyList<String>(), foreign.orders.toList())
                }
            }
        }
    }

    /** Opens a verified TLS connection to the gateway at [port] and submits one order. */
    private fun charge(port: Int, trust: X509TrustManager, order: String): String = runBlocking {
        withTimeout(TIMEOUT_MILLIS) {
            SelectorManager(Dispatchers.IO).use { selector ->
                val plain = aSocket(selector).tcp().connect("127.0.0.1", port)
                val secure = plain.tls(Dispatchers.IO) {
                    trustManager = trust
                    serverName = GATEWAY_HOST
                }
                secure.use { socket ->
                    val input = socket.openReadChannel()
                    val output = socket.openWriteChannel(autoFlush = true)
                    output.writeStringUtf8("$order\n")
                    checkNotNull(input.readLine()) { "gateway closed before answering $order" }
                }
            }
        }
    }

    /** Attempts the handshake that [charge] performs and returns why it was refused. */
    private fun chargeFailure(port: Int, trust: X509TrustManager): Throwable {
        val outcome: Result<String> = runCatching { charge(port, trust, "order-0") }
        return checkNotNull(outcome.exceptionOrNull()) { "handshake with port $port unexpectedly succeeded" }
    }

    /**
     * Delegates every decision to the platform trust manager and remembers the subjects of each
     * chain the server presented, as an application that audits its peers would.
     */
    private class RecordingTrustManager(private val delegate: X509TrustManager) : X509TrustManager {
        private val chains: MutableList<List<String>> = Collections.synchronizedList(mutableListOf())

        fun lastChain(): List<String> = chains.last()

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String): Unit {
            chains += chain.map { it.subjectX500Principal.name }
            delegate.checkServerTrusted(chain, authType)
        }

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String): Unit =
            delegate.checkClientTrusted(chain, authType)

        override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers
    }

    /** A root CA, an intermediate CA it signed, and a gateway certificate the intermediate signed. */
    private class Hierarchy(
        val root: X509Certificate,
        val intermediate: X509Certificate,
        val gateway: X509Certificate,
        val gatewayKey: PrivateKey,
    ) {
        /** A trust store that anchors only the root, as the client's configuration ships. */
        fun rootTrustStore(): KeyStore = emptyKeyStore().apply { setCertificateEntry("root", root) }

        /** The gateway's identity, sent with or without the intermediate that issued it. */
        fun gatewayKeyStore(withIntermediate: Boolean): KeyStore {
            val chain: Array<X509Certificate> =
                if (withIntermediate) arrayOf(gateway, intermediate) else arrayOf(gateway)
            return emptyKeyStore().apply { setKeyEntry(GATEWAY, gatewayKey, PASSWORD.toCharArray(), chain) }
        }

        companion object {
            fun issue(): Hierarchy {
                val rootStore: KeyStore = buildKeyStore {
                    certificate(ROOT) {
                        password = PASSWORD
                        hash = HashAlgorithm.SHA256
                        sign = SignatureAlgorithm.RSA
                        keyType = KeyType.CA
                        keySizeInBits = 2048
                        subject = X500Principal(ROOT_SUBJECT)
                    }
                }
                val rootCertificate = rootStore.getCertificate(ROOT) as X509Certificate
                val intermediateStore: KeyStore = buildKeyStore {
                    certificate(INTERMEDIATE) {
                        password = PASSWORD
                        hash = HashAlgorithm.SHA256
                        sign = SignatureAlgorithm.RSA
                        keyType = KeyType.CA
                        keySizeInBits = 2048
                        subject = X500Principal(INTERMEDIATE_SUBJECT)
                        signWith(keyPairOf(rootStore, ROOT), rootCertificate)
                    }
                }
                val intermediateCertificate = intermediateStore.getCertificate(INTERMEDIATE) as X509Certificate
                val gatewayStore: KeyStore = buildKeyStore {
                    certificate(GATEWAY) {
                        password = PASSWORD
                        hash = HashAlgorithm.SHA256
                        sign = SignatureAlgorithm.RSA
                        keyType = KeyType.Server
                        keySizeInBits = 2048
                        subject = X500Principal(GATEWAY_SUBJECT)
                        domains = listOf(GATEWAY_HOST)
                        signWith(keyPairOf(intermediateStore, INTERMEDIATE), intermediateCertificate)
                    }
                }
                return Hierarchy(
                    root = rootCertificate,
                    intermediate = intermediateCertificate,
                    gateway = gatewayStore.getCertificate(GATEWAY) as X509Certificate,
                    gatewayKey = gatewayStore.getKey(GATEWAY, PASSWORD.toCharArray()) as PrivateKey,
                )
            }

            private fun keyPairOf(store: KeyStore, alias: String): KeyPair = KeyPair(
                store.getCertificate(alias).publicKey,
                store.getKey(alias, PASSWORD.toCharArray()) as PrivateKey,
            )

            private fun emptyKeyStore(): KeyStore = KeyStore.getInstance("PKCS12").apply { load(null, null) }
        }
    }

    /**
     * A JDK TLS 1.2 payments gateway: each connection submits one order line and receives its
     * authorisation; the orders it authorised are recorded so the test can tell which got through.
     */
    private class GatewayServer(keyStore: KeyStore) : AutoCloseable {
        private val serverSocket: SSLServerSocket
        private val worker: Thread
        val orders: MutableList<String> = Collections.synchronizedList(mutableListOf())
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
            worker = Thread({ serve() }, "payments-gateway").apply { start() }
        }

        private fun serve(): Unit {
            while (true) {
                val connection: SSLSocket = try {
                    serverSocket.accept() as SSLSocket
                } catch (closed: SocketException) {
                    return
                }
                connection.use { authorise(it) }
            }
        }

        private fun authorise(connection: SSLSocket): Unit {
            connection.soTimeout = TIMEOUT_MILLIS.toInt()
            try {
                val reader = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
                val order: String = reader.readLine() ?: return
                orders += order
                connection.outputStream.apply {
                    write("$order authorised\n".toByteArray(StandardCharsets.UTF_8))
                    flush()
                }
            } catch (refused: IOException) {
                // The client aborted the handshake; no order was taken on this connection.
            }
        }

        override fun close(): Unit {
            serverSocket.close()
            worker.join(TIMEOUT_MILLIS)
        }
    }

    private companion object {
        const val ROOT: String = "root-ca"
        const val INTERMEDIATE: String = "issuing-ca"
        const val GATEWAY: String = "payments-gateway"
        const val GATEWAY_HOST: String = "payments.corp.internal"
        const val ROOT_SUBJECT: String = "CN=Corp Offline Root CA,O=Corp"
        const val INTERMEDIATE_SUBJECT: String = "CN=Corp Issuing CA 1,O=Corp"
        const val GATEWAY_SUBJECT: String = "CN=payments.corp.internal,O=Corp"
        const val PASSWORD: String = "changeit"
        const val TIMEOUT_MILLIS: Long = 30_000

        /** The platform PKIX trust manager over the anchors held in [keyStore]. */
        fun trustManagerFor(keyStore: KeyStore): X509TrustManager =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply { init(keyStore) }
                .trustManagers
                .filterIsInstance<X509TrustManager>()
                .single()
    }
}
