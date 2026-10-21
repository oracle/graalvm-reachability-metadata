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
import io.ktor.network.tls.TLSConfig
import io.ktor.network.tls.TLSConfigBuilder
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
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Collections
import javax.net.ssl.ExtendedSSLSession
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Server Name Indication during the TLS handshake: a client reaching two tenants hosted on one
 * address sends each tenant's host name as SNI through one reusable [TLSConfig] per tenant, and
 * the shared server answers each connection with the certificate and content of that tenant.
 */
class ServerNameIndicationTest {
    @Test
    fun eachTenantIsServedWithItsOwnCertificateSelectedBySni(): Unit {
        val keyStore: KeyStore = buildKeyStore {
            for (host in listOf(SHOP_HOST, BLOG_HOST)) {
                certificate(host) {
                    password = PASSWORD
                    hash = HashAlgorithm.SHA256
                    sign = SignatureAlgorithm.RSA
                    keyType = KeyType.Server
                    keySizeInBits = 2048
                    domains = listOf(host)
                }
            }
        }
        val trust: X509TrustManager = trustManagerFor(keyStore)
        val shopConfig: TLSConfig = tenantConfig(SHOP_HOST, trust)
        val blogConfig: TLSConfig = tenantConfig(BLOG_HOST, trust)

        VirtualHostServer(keyStore).use { server ->
            val replies: List<String> = listOf(
                fetch(server.port, shopConfig, "/cart"),
                fetch(server.port, blogConfig, "/posts/1"),
                fetch(server.port, shopConfig, "/checkout"),
                fetch(server.port, blogConfig, "/about"),
            )

            assertEquals(SHOP_HOST, shopConfig.serverName)
            assertEquals(BLOG_HOST, blogConfig.serverName)
            assertEquals(
                listOf(
                    "$SHOP_HOST served /cart",
                    "$BLOG_HOST served /posts/1",
                    "$SHOP_HOST served /checkout",
                    "$BLOG_HOST served /about",
                ),
                replies,
            )

            server.close()
            assertEquals(
                listOf(
                    "sni=$SHOP_HOST cert=$SHOP_HOST path=/cart",
                    "sni=$BLOG_HOST cert=$BLOG_HOST path=/posts/1",
                    "sni=$SHOP_HOST cert=$SHOP_HOST path=/checkout",
                    "sni=$BLOG_HOST cert=$BLOG_HOST path=/about",
                ),
                server.visits.toList(),
            )
        }
    }

    /** Builds the TLS settings the client keeps for one tenant and reuses for every connection. */
    private fun tenantConfig(host: String, trust: X509TrustManager): TLSConfig =
        TLSConfigBuilder().apply {
            trustManager = trust
            serverName = host
        }.build()

    /** Opens a TLS connection with [config] to the shared address and requests one [path]. */
    private fun fetch(port: Int, config: TLSConfig, path: String): String = runBlocking {
        withTimeout(TIMEOUT_MILLIS) {
            SelectorManager(Dispatchers.IO).use { selector ->
                val plain = aSocket(selector).tcp().connect("127.0.0.1", port)
                try {
                    plain.tls(Dispatchers.IO, config).use { socket ->
                        val input = socket.openReadChannel()
                        val output = socket.openWriteChannel(autoFlush = true)
                        output.writeStringUtf8("$path\n")
                        checkNotNull(input.readLine()) { "server closed before answering $path" }
                    }
                } finally {
                    plain.close()
                }
            }
        }
    }

    /**
     * A JDK TLS 1.2 server hosting several tenants on one port: the key manager picks the
     * certificate that matches the requested SNI host name, and each connection carries one path
     * line answered on behalf of that tenant. It records the SNI, certificate and path it saw.
     */
    private class VirtualHostServer(keyStore: KeyStore) : AutoCloseable {
        private val serverSocket: SSLServerSocket
        private val worker: Thread
        val visits: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val port: Int

        init {
            val keyManagers = KeyManagerFactory.getInstance("PKIX")
                .apply { init(keyStore, PASSWORD.toCharArray()) }
                .keyManagers
            val context = SSLContext.getInstance("TLS").apply { init(keyManagers, null, null) }
            serverSocket = context.serverSocketFactory
                .createServerSocket(0, 8, InetAddress.getLoopbackAddress()) as SSLServerSocket
            serverSocket.sslParameters = serverSocket.sslParameters.apply {
                protocols = arrayOf("TLSv1.2")
                endpointIdentificationAlgorithm = "HTTPS"
            }
            port = serverSocket.localPort
            worker = Thread({ serve() }, "virtual-host-server").apply { start() }
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
                val session = connection.session as ExtendedSSLSession
                val sni: String = session.requestedServerNames
                    .filterIsInstance<SNIHostName>()
                    .joinToString(",") { it.asciiName }
                val certificate = session.localCertificates.first() as X509Certificate
                val certHosts: String = certificate.subjectAlternativeNames
                    .filter { it[0] == DNS_NAME }
                    .joinToString(",") { it[1] as String }
                val reader = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
                val path: String = reader.readLine() ?: return
                visits += "sni=$sni cert=$certHosts path=$path"
                connection.outputStream.apply {
                    write("$certHosts served $path\n".toByteArray(StandardCharsets.UTF_8))
                    flush()
                }
            } catch (failed: IOException) {
                visits += "failed: ${failed.message}"
            }
        }

        override fun close(): Unit {
            serverSocket.close()
            worker.join(TIMEOUT_MILLIS)
        }
    }

    private companion object {
        const val SHOP_HOST: String = "shop.tenants.test"
        const val BLOG_HOST: String = "blog.tenants.test"
        const val PASSWORD: String = "changeit"
        const val TIMEOUT_MILLIS: Long = 30_000

        /** The `dNSName` entry type in an X.509 subject alternative name. */
        const val DNS_NAME: Int = 2

        /** Trusts exactly the certificates held in [keyStore], as a pinned client would. */
        fun trustManagerFor(keyStore: KeyStore): X509TrustManager =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply { init(keyStore) }
                .trustManagers
                .filterIsInstance<X509TrustManager>()
                .single()
    }
}
