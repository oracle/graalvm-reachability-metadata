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
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * TLS client handshake and encrypted record exchange: an application upgrades a raw Ktor TCP
 * socket to TLS 1.2 against a server whose certificate it generated with Ktor, then talks a
 * line protocol over the encrypted channel.
 */
class TlsClientHandshakeTest {
    @Test
    fun clientNegotiatesTlsAndExchangesLinesWithAnEchoServer(): Unit {
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

        UppercaseEchoServer(keyStore).use { server ->
            val replies: List<String> = runBlocking {
                withTimeout(TIMEOUT_MILLIS) {
                    SelectorManager(Dispatchers.IO).use { selector ->
                        val plain = aSocket(selector).tcp().connect("127.0.0.1", server.port)
                        val secure = plain.tls(Dispatchers.IO) {
                            trustManager = trustManagerFor(keyStore)
                            serverName = "localhost"
                            cipherSuites = listOf(CIOCipherSuites.ECDHE_RSA_AES128_SHA256)
                        }
                        secure.use { socket ->
                            val input = socket.openReadChannel()
                            val output = socket.openWriteChannel(autoFlush = true)
                            listOf("hello", "ktor tls", "bye").map { line ->
                                output.writeStringUtf8("$line\n")
                                checkNotNull(input.readLine()) { "server closed before replying to '$line'" }
                            }
                        }
                    }
                }
            }

            assertEquals(listOf("HELLO", "KTOR TLS", "BYE"), replies)
            val session: NegotiatedSession = server.session.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            assertEquals("TLSv1.2", session.protocol)
            assertEquals(CIOCipherSuites.ECDHE_RSA_AES128_SHA256.name, session.cipherSuite)
            assertEquals(listOf("hello", "ktor tls", "bye"), session.received)
        }
    }

    /** What the server observed on the single connection it accepted. */
    private data class NegotiatedSession(val protocol: String, val cipherSuite: String, val received: List<String>)

    /**
     * A JDK TLS 1.2 server that accepts one connection, answers each line in upper case, and
     * ends the conversation after `bye`.
     */
    private class UppercaseEchoServer(keyStore: KeyStore) : AutoCloseable {
        private val serverSocket: SSLServerSocket
        private val worker: Thread
        val session: CompletableFuture<NegotiatedSession> = CompletableFuture()
        val port: Int

        init {
            val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                .apply { init(keyStore, PASSWORD.toCharArray()) }
                .keyManagers
            val context = SSLContext.getInstance("TLSv1.2").apply { init(keyManagers, null, null) }
            serverSocket = context.serverSocketFactory
                .createServerSocket(0, 1, InetAddress.getLoopbackAddress()) as SSLServerSocket
            serverSocket.enabledProtocols = arrayOf("TLSv1.2")
            port = serverSocket.localPort
            worker = Thread({ serve() }, "uppercase-echo-server").apply { start() }
        }

        private fun serve(): Unit {
            try {
                (serverSocket.accept() as SSLSocket).use { client ->
                    val reader = BufferedReader(InputStreamReader(client.inputStream, StandardCharsets.UTF_8))
                    val writer = PrintWriter(OutputStreamWriter(client.outputStream, StandardCharsets.UTF_8), true)
                    val received = mutableListOf<String>()
                    while (true) {
                        val line: String = reader.readLine() ?: break
                        received += line
                        writer.print(line.uppercase() + "\n")
                        writer.flush()
                        if (line == "bye") {
                            break
                        }
                    }
                    session.complete(NegotiatedSession(client.session.protocol, client.session.cipherSuite, received))
                }
            } catch (failure: Exception) {
                session.completeExceptionally(failure)
            }
        }

        override fun close(): Unit {
            serverSocket.close()
            worker.join(TIMEOUT_MILLIS)
        }
    }

    private companion object {
        const val ALIAS: String = "echo-server"
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
