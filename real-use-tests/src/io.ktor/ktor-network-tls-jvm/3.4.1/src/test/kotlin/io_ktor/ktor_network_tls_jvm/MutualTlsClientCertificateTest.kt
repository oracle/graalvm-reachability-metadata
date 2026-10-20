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
import io.ktor.network.tls.addKeyStore
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
import javax.security.auth.x500.X500Principal

/**
 * Mutual TLS client certificate authentication: an application holding several client
 * identities in one keystore picks one per connection with `addKeyStore`, and a server that
 * requires client certificates authorizes each request by the certificate subject it received.
 */
class MutualTlsClientCertificateTest {
    @Test
    fun clientAuthenticatesWithTheIdentitySelectedFromItsKeyStore(): Unit {
        val serverKeyStore: KeyStore = buildKeyStore {
            certificate(SERVER_ALIAS) {
                password = PASSWORD
                hash = HashAlgorithm.SHA256
                sign = SignatureAlgorithm.RSA
                keyType = KeyType.Server
                keySizeInBits = 2048
                domains = listOf("localhost")
                ipAddresses = listOf(InetAddress.getLoopbackAddress())
            }
        }
        val clientKeyStore: KeyStore = buildKeyStore {
            listOf(BILLING_ALIAS, REPORTS_ALIAS).forEach { alias ->
                certificate(alias) {
                    password = PASSWORD
                    hash = HashAlgorithm.SHA256
                    sign = SignatureAlgorithm.RSA
                    keyType = KeyType.Client
                    keySizeInBits = 2048
                    subject = X500Principal("CN=$alias-service, OU=payments, O=Example")
                }
            }
        }

        AccountsServer(serverKeyStore, clientKeyStore).use { server ->
            val replies: List<String> = runBlocking {
                withTimeout(TIMEOUT_MILLIS) {
                    SelectorManager(Dispatchers.IO).use { selector ->
                        listOf(BILLING_ALIAS to "INVOICE 1001", REPORTS_ALIAS to "INVOICE 1001").map { (alias, command) ->
                            val plain = aSocket(selector).tcp().connect("127.0.0.1", server.port)
                            val secure = plain.tls(Dispatchers.IO) {
                                trustManager = trustManagerFor(serverKeyStore)
                                serverName = "localhost"
                                cipherSuites = listOf(CIOCipherSuites.ECDHE_RSA_AES128_SHA256)
                                addKeyStore(clientKeyStore, PASSWORD.toCharArray(), alias)
                            }
                            secure.use { socket ->
                                val input = socket.openReadChannel()
                                val output = socket.openWriteChannel(autoFlush = true)
                                output.writeStringUtf8("$command\n")
                                checkNotNull(input.readLine()) { "server closed before answering $alias" }
                            }
                        }
                    }
                }
            }

            assertEquals(
                listOf(
                    "OK billing-service may read INVOICE 1001",
                    "DENIED reports-service may not read INVOICE 1001",
                ),
                replies,
            )

            val connections: List<AuthenticatedConnection> = List(2) {
                checkNotNull(server.connections.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    "connection ${it + 1} not seen"
                }
            }
            val expectedSubjects: List<String> = listOf(BILLING_ALIAS, REPORTS_ALIAS).map { alias ->
                (clientKeyStore.getCertificate(alias) as X509Certificate).subjectX500Principal.name
            }
            assertEquals(expectedSubjects, connections.map { it.peerSubject })
            assertEquals(listOf("INVOICE 1001", "INVOICE 1001"), connections.map { it.command })
            connections.forEach { connection ->
                assertEquals("TLSv1.2", connection.protocol)
                assertEquals(CIOCipherSuites.ECDHE_RSA_AES128_SHA256.name, connection.cipherSuite)
            }
        }
    }

    /** One connection as the server saw it: the authenticated client subject and its command. */
    private data class AuthenticatedConnection(
        val peerSubject: String,
        val command: String,
        val protocol: String,
        val cipherSuite: String,
    )

    /**
     * A JDK TLS 1.2 server that requires a client certificate trusted by [clientTrustStore] and
     * lets only the billing service read invoices, one command per connection.
     */
    private class AccountsServer(keyStore: KeyStore, clientTrustStore: KeyStore) : AutoCloseable {
        private val serverSocket: SSLServerSocket
        private val worker: Thread
        val connections: LinkedBlockingQueue<AuthenticatedConnection> = LinkedBlockingQueue()
        val port: Int

        init {
            val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                .apply { init(keyStore, PASSWORD.toCharArray()) }
                .keyManagers
            val trustManagers = arrayOf(trustManagerFor(clientTrustStore))
            val context = SSLContext.getInstance("TLSv1.2").apply { init(keyManagers, trustManagers, null) }
            serverSocket = context.serverSocketFactory
                .createServerSocket(0, 8, InetAddress.getLoopbackAddress()) as SSLServerSocket
            serverSocket.enabledProtocols = arrayOf("TLSv1.2")
            serverSocket.needClientAuth = true
            port = serverSocket.localPort
            worker = Thread({ serve() }, "accounts-server").apply { start() }
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
            val command: String = reader.readLine() ?: return
            val peer = connection.session.peerCertificates.first() as X509Certificate
            val subject: String = peer.subjectX500Principal.name
            connections.add(
                AuthenticatedConnection(subject, command, connection.session.protocol, connection.session.cipherSuite),
            )
            val commonName: String = subject.substringAfter("CN=").substringBefore(',')
            val reply: String = if (commonName == "billing-service") {
                "OK $commonName may read $command"
            } else {
                "DENIED $commonName may not read $command"
            }
            connection.outputStream.apply {
                write("$reply\n".toByteArray(StandardCharsets.UTF_8))
                flush()
            }
        }

        override fun close(): Unit {
            serverSocket.close()
            worker.join(TIMEOUT_MILLIS)
        }
    }

    private companion object {
        const val SERVER_ALIAS: String = "accounts-server"
        const val BILLING_ALIAS: String = "billing"
        const val REPORTS_ALIAS: String = "reports"
        const val PASSWORD: String = "changeit"
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
