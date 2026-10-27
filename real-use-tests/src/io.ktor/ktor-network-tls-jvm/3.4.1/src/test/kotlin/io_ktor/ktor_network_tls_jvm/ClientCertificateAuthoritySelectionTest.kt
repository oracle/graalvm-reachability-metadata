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
import io.ktor.network.tls.addCertificateChain
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
import java.security.cert.X509Certificate
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

/**
 * Client certificate selection by the certificate authorities a server names in its
 * `CertificateRequest`: an integration hub holding client identities from two partners' CAs in one
 * [TLSConfig] presents to each partner the identity that partner's CA issued, and answers a
 * partner whose CA issued none of them with an empty certificate list, which an optional-auth
 * directory serves as a guest and a mandatory-auth order desk refuses.
 */
class ClientCertificateAuthoritySelectionTest {
    @Test
    fun hubPresentsToEachPartnerTheIdentityIssuedByTheAuthorityThatPartnerRequests(): Unit {
        val acme: PartnerAuthority = PartnerAuthority.issue("acme", ACME_CA_SUBJECT)
        val globex: PartnerAuthority = PartnerAuthority.issue("globex", GLOBEX_CA_SUBJECT)
        val initech: PartnerAuthority = PartnerAuthority.issue("initech", INITECH_CA_SUBJECT)
        val acmeIdentity: ClientIdentity = acme.issueClientIdentity(HUB_AT_ACME_SUBJECT)
        val globexIdentity: ClientIdentity = globex.issueClientIdentity(HUB_AT_GLOBEX_SUBJECT)
        val serverStore: KeyStore = partnerServerKeyStore()

        val hubConfig: TLSConfig = TLSConfigBuilder().apply {
            trustManager = trustManagerFor(serverStore)
            serverName = "localhost"
            addCertificateChain(arrayOf(globexIdentity.certificate), globexIdentity.key)
            addCertificateChain(arrayOf(acmeIdentity.certificate), acmeIdentity.key)
        }.build()

        PartnerServer("acme-orders", serverStore, acme.certificate, ClientAuth.REQUIRED).use { acmeOrders ->
            PartnerServer("globex-orders", serverStore, globex.certificate, ClientAuth.REQUIRED).use { globexOrders ->
                PartnerServer("initech-directory", serverStore, initech.certificate, ClientAuth.OPTIONAL)
                    .use { initechDirectory ->
                        PartnerServer("initech-orders", serverStore, initech.certificate, ClientAuth.REQUIRED)
                            .use { initechOrders ->
                                val acmeReply: String = call(hubConfig, acmeOrders.port, "PLACE widget x12")
                                val globexReply: String = call(hubConfig, globexOrders.port, "PLACE gizmo x3")
                                val directoryReply: String = call(hubConfig, initechDirectory.port, "LOOKUP staplers")
                                val refusal: Throwable = checkNotNull(
                                    runCatching { call(hubConfig, initechOrders.port, "PLACE stapler x1") }
                                        .exceptionOrNull(),
                                ) { "initech order desk accepted a hub without an Initech identity" }

                                assertEquals("acme-orders accepted PLACE widget x12 from $HUB_AT_ACME_SUBJECT", acmeReply)
                                assertEquals("globex-orders accepted PLACE gizmo x3 from $HUB_AT_GLOBEX_SUBJECT", globexReply)
                                assertEquals("initech-directory answered LOOKUP staplers for guest", directoryReply)
                                assertInstanceOf(IOException::class.java, refusal)

                                assertEquals(
                                    Visit.Authenticated(HUB_AT_ACME_SUBJECT, ACME_CA_SUBJECT, "PLACE widget x12"),
                                    acmeOrders.nextVisit(),
                                )
                                assertEquals(
                                    Visit.Authenticated(HUB_AT_GLOBEX_SUBJECT, GLOBEX_CA_SUBJECT, "PLACE gizmo x3"),
                                    globexOrders.nextVisit(),
                                )
                                assertEquals(Visit.Guest("LOOKUP staplers"), initechDirectory.nextVisit())
                                val refused: Visit = initechOrders.nextVisit()
                                assertInstanceOf(Visit.Refused::class.java, refused)
                                assertEquals(
                                    "(handshake_failure) Empty client certificate chain",
                                    (refused as Visit.Refused).reason,
                                )
                            }
                    }
            }
        }
    }

    /** Opens a TLS session with [config] to the partner at [port], sends [request], reads the reply. */
    private fun call(config: TLSConfig, port: Int, request: String): String = runBlocking {
        withTimeout(TIMEOUT_MILLIS) {
            SelectorManager(Dispatchers.IO).use { selector ->
                val plain = aSocket(selector).tcp().connect("127.0.0.1", port)
                plain.tls(Dispatchers.IO, config).use { socket ->
                    val input = socket.openReadChannel()
                    val output = socket.openWriteChannel(autoFlush = true)
                    output.writeStringUtf8("$request\n")
                    checkNotNull(input.readLine()) { "partner on port $port closed before answering" }
                }
            }
        }
    }

    /** A client identity: a leaf certificate a partner CA issued, and its private key. */
    private class ClientIdentity(val certificate: X509Certificate, val key: PrivateKey)

    /** A partner's private certificate authority that issues client identities to the hub. */
    private class PartnerAuthority(private val alias: String, private val store: KeyStore) {
        val certificate: X509Certificate = store.getCertificate(alias) as X509Certificate

        fun issueClientIdentity(subjectName: String): ClientIdentity {
            val issuerKeys = KeyPair(certificate.publicKey, store.getKey(alias, PASSWORD.toCharArray()) as PrivateKey)
            val identityStore: KeyStore = buildKeyStore {
                certificate(CLIENT_ALIAS) {
                    password = PASSWORD
                    hash = HashAlgorithm.SHA256
                    sign = SignatureAlgorithm.RSA
                    keyType = KeyType.Client
                    keySizeInBits = 2048
                    subject = X500Principal(subjectName)
                    signWith(issuerKeys, certificate)
                }
            }
            return ClientIdentity(
                identityStore.getCertificate(CLIENT_ALIAS) as X509Certificate,
                identityStore.getKey(CLIENT_ALIAS, PASSWORD.toCharArray()) as PrivateKey,
            )
        }

        companion object {
            fun issue(alias: String, subjectName: String): PartnerAuthority = PartnerAuthority(
                alias,
                buildKeyStore {
                    certificate(alias) {
                        password = PASSWORD
                        hash = HashAlgorithm.SHA256
                        sign = SignatureAlgorithm.RSA
                        keyType = KeyType.CA
                        keySizeInBits = 2048
                        subject = X500Principal(subjectName)
                    }
                },
            )
        }
    }

    /** How a partner server treats clients that present no certificate it trusts. */
    private enum class ClientAuth { OPTIONAL, REQUIRED }

    /** What one partner server observed on one connection. */
    private sealed interface Visit {
        data class Authenticated(val subject: String, val issuer: String, val request: String) : Visit

        data class Guest(val request: String) : Visit

        data class Refused(val reason: String) : Visit
    }

    /**
     * A JDK TLS 1.2 partner server that requests client certificates issued by [clientCa] (its
     * `CertificateRequest` names that CA) and records who each connection authenticated as.
     */
    private class PartnerServer(
        private val name: String,
        keyStore: KeyStore,
        clientCa: X509Certificate,
        clientAuth: ClientAuth,
    ) : AutoCloseable {
        private val serverSocket: SSLServerSocket
        private val worker: Thread
        private val visits: LinkedBlockingQueue<Visit> = LinkedBlockingQueue()
        val port: Int

        init {
            val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                .apply { init(keyStore, PASSWORD.toCharArray()) }
                .keyManagers
            val clientTrust: KeyStore = emptyKeyStore().apply { setCertificateEntry("client-ca", clientCa) }
            val context = SSLContext.getInstance("TLSv1.2")
                .apply { init(keyManagers, arrayOf(trustManagerFor(clientTrust)), null) }
            serverSocket = context.serverSocketFactory
                .createServerSocket(0, 8, InetAddress.getLoopbackAddress()) as SSLServerSocket
            serverSocket.enabledProtocols = arrayOf("TLSv1.2")
            when (clientAuth) {
                ClientAuth.OPTIONAL -> serverSocket.wantClientAuth = true
                ClientAuth.REQUIRED -> serverSocket.needClientAuth = true
            }
            port = serverSocket.localPort
            worker = Thread({ serve() }, name).apply { start() }
        }

        fun nextVisit(): Visit =
            checkNotNull(visits.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) { "$name saw no connection" }

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
            connection.soTimeout = TIMEOUT_MILLIS.toInt()
            try {
                connection.startHandshake()
            } catch (refused: IOException) {
                visits += Visit.Refused(refused.message.orEmpty())
                return
            }
            val reader = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
            val request: String = reader.readLine() ?: return
            val peer: X509Certificate? = try {
                connection.session.peerCertificates.first() as X509Certificate
            } catch (anonymous: SSLPeerUnverifiedException) {
                null
            }
            val reply: String = if (peer == null) {
                visits += Visit.Guest(request)
                "$name answered $request for guest"
            } else {
                val subject: String = peer.subjectX500Principal.name
                visits += Visit.Authenticated(subject, peer.issuerX500Principal.name, request)
                "$name accepted $request from $subject"
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
        const val CLIENT_ALIAS: String = "hub"
        const val SERVER_ALIAS: String = "partner-gateway"
        const val ACME_CA_SUBJECT: String = "CN=Acme Partner CA,O=Acme"
        const val GLOBEX_CA_SUBJECT: String = "CN=Globex Partner CA,O=Globex"
        const val INITECH_CA_SUBJECT: String = "CN=Initech Partner CA,O=Initech"
        const val HUB_AT_ACME_SUBJECT: String = "CN=integration-hub,OU=Acme Suppliers,O=Example"
        const val HUB_AT_GLOBEX_SUBJECT: String = "CN=integration-hub,OU=Globex Vendors,O=Example"
        const val PASSWORD: String = "changeit"
        const val TIMEOUT_MILLIS: Long = 30_000

        /** The gateway identity every partner server presents for `localhost`. */
        fun partnerServerKeyStore(): KeyStore = buildKeyStore {
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

        fun emptyKeyStore(): KeyStore = KeyStore.getInstance("PKCS12").apply { load(null, null) }

        /** The platform PKIX trust manager over the anchors held in [keyStore]. */
        fun trustManagerFor(keyStore: KeyStore): X509TrustManager =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply { init(keyStore) }
                .trustManagers
                .filterIsInstance<X509TrustManager>()
                .single()
    }
}
