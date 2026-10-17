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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.cert.CertificateException
import java.util.Collections
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Server identity verification during the TLS handshake: an application that pins its
 * inventory service's certificate reaches it under a wildcard host name, and refuses both a
 * host name the certificate does not list and an impostor whose certificate it never trusted.
 */
class ServerIdentityVerificationTest {
    @Test
    fun clientAcceptsOnlyTheTrustedServerUnderAHostNameItsCertificateLists(): Unit {
        val genuineKeyStore: KeyStore = serviceKeyStore()
        val impostorKeyStore: KeyStore = serviceKeyStore()
        val pinned: X509TrustManager = trustManagerFor(genuineKeyStore)

        StockServer(genuineKeyStore).use { genuine ->
            StockServer(impostorKeyStore).use { impostor ->
                val firstAnswer: String = queryStock(genuine.port, "inventory.services.internal", pinned, "sku-17")

                val wrongHost: Throwable = queryFailure(genuine.port, "inventory.services.example", pinned)
                assertInstanceOf(TlsException::class.java, wrongHost)
                assertTrue(
                    wrongHost.message.orEmpty().contains("inventory.services.example"),
                    "rejection names the requested host: ${wrongHost.message}",
                )

                val untrusted: Throwable = queryFailure(impostor.port, "inventory.services.internal", pinned)
                assertInstanceOf(CertificateException::class.java, untrusted)

                val secondAnswer: String = queryStock(genuine.port, "localhost", pinned, "sku-42")

                assertEquals("sku-17 in stock: 17", firstAnswer)
                assertEquals("sku-42 in stock: 42", secondAnswer)
                genuine.close()
                impostor.close()
                assertEquals(listOf("sku-17", "sku-42"), genuine.queries.toList())
                assertEquals(emptyList<String>(), impostor.queries.toList())
            }
        }
    }

    /** Opens a verified TLS connection to `serverName` at [port] and asks for one SKU. */
    private fun queryStock(port: Int, serverName: String, trust: X509TrustManager, sku: String): String =
        runBlocking {
            withTimeout(TIMEOUT_MILLIS) {
                SelectorManager(Dispatchers.IO).use { selector ->
                    val plain = aSocket(selector).tcp().connect("127.0.0.1", port)
                    val secure = plain.tls(Dispatchers.IO) {
                        trustManager = trust
                        this.serverName = serverName
                    }
                    secure.use { socket ->
                        val input = socket.openReadChannel()
                        val output = socket.openWriteChannel(autoFlush = true)
                        output.writeStringUtf8("$sku\n")
                        checkNotNull(input.readLine()) { "server closed before answering $sku" }
                    }
                }
            }
        }

    /** Attempts the handshake that [queryStock] performs and returns why it was refused. */
    private fun queryFailure(port: Int, serverName: String, trust: X509TrustManager): Throwable {
        val outcome: Result<String> = runCatching { queryStock(port, serverName, trust, "sku-0") }
        return checkNotNull(outcome.exceptionOrNull()) { "handshake with $serverName unexpectedly succeeded" }
    }

    /**
     * A JDK TLS 1.2 stock service: each connection sends one SKU line and receives its stock
     * level; the SKUs it served are recorded so the test can tell which requests got through.
     */
    private class StockServer(keyStore: KeyStore) : AutoCloseable {
        private val serverSocket: SSLServerSocket
        private val worker: Thread
        val queries: MutableList<String> = Collections.synchronizedList(mutableListOf())
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
            worker = Thread({ serve() }, "stock-server").apply { start() }
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
                val reader = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
                val sku: String = reader.readLine() ?: return
                queries += sku
                val level: String = sku.substringAfter("sku-")
                connection.outputStream.apply {
                    write("$sku in stock: $level\n".toByteArray(StandardCharsets.UTF_8))
                    flush()
                }
            } catch (refused: IOException) {
                // The client aborted the handshake; nothing was served on this connection.
            }
        }

        override fun close(): Unit {
            serverSocket.close()
            worker.join(TIMEOUT_MILLIS)
        }
    }

    private companion object {
        const val ALIAS: String = "inventory-service"
        const val PASSWORD: String = "changeit"
        const val TIMEOUT_MILLIS: Long = 30_000

        /** A fresh self-signed identity for the inventory service's host names. */
        fun serviceKeyStore(): KeyStore = buildKeyStore {
            certificate(ALIAS) {
                password = PASSWORD
                hash = HashAlgorithm.SHA256
                sign = SignatureAlgorithm.RSA
                keyType = KeyType.Server
                keySizeInBits = 2048
                domains = listOf("*.services.internal", "localhost")
                ipAddresses = listOf(InetAddress.getLoopbackAddress())
            }
        }

        /** Trusts exactly the certificates held in [keyStore], as a pinned client would. */
        fun trustManagerFor(keyStore: KeyStore): X509TrustManager =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply { init(keyStore) }
                .trustManagers
                .filterIsInstance<X509TrustManager>()
                .single()
    }
}
