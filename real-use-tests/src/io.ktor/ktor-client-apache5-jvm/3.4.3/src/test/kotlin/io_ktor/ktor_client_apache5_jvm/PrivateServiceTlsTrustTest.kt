/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_apache5_jvm

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsExchange
import com.sun.net.httpserver.HttpsServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.network.tls.certificates.trustManagers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.InetSocketAddress
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException

/**
 * HTTPS with an application-supplied `SSLContext` on the `Apache5` client engine.
 *
 * A billing client calls an internal service that presents a self-signed certificate issued for
 * `localhost`. The application pins that certificate by handing the engine an `SSLContext` whose
 * trust managers hold only it, and observes that calls succeed over TLS 1.3, that a client left
 * on the default JDK trust store refuses the handshake, and that the engine's hostname
 * verification rejects the same certificate when the service is addressed by IP.
 */
class PrivateServiceTlsTrustTest {

    /** The internal billing service: an HTTPS endpoint that reports the negotiated TLS session. */
    class BillingService(private val keyStore: KeyStore, private val password: CharArray) {
        private lateinit var server: HttpsServer

        val port: Int
            get() = server.address.port

        fun start() {
            val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            keyManagers.init(keyStore, password)
            val tls: SSLContext = SSLContext.getInstance("TLS")
            tls.init(keyManagers.keyManagers, null, null)

            server = HttpsServer.create(InetSocketAddress(0), 0)
            server.httpsConfigurator = HttpsConfigurator(tls)
            server.createContext("/invoices/latest") { exchange: HttpExchange ->
                val protocol: String = (exchange as HttpsExchange).sslSession.protocol
                val tenant: String = exchange.requestHeaders.getFirst("X-Tenant") ?: "unknown"
                val body: ByteArray = """{"tenant":"$tenant","invoice":"INV-2041","total":"129.90"}"""
                    .toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.responseHeaders.add("X-Tls-Protocol", protocol)
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            server.start()
        }

        fun stop() {
            server.stop(0)
        }
    }

    private val keyPassword: String = "billing-secret"
    private val serviceKeyStore: KeyStore = buildKeyStore {
        certificate("billing") {
            password = keyPassword
            domains = listOf("localhost")
            ipAddresses = emptyList()
        }
    }
    private val service: BillingService = BillingService(serviceKeyStore, keyPassword.toCharArray())

    @BeforeEach
    fun startService() {
        service.start()
    }

    @AfterEach
    fun stopService() {
        service.stop()
    }

    /** Builds the client-side `SSLContext` that trusts only the billing service certificate. */
    private fun pinnedTrustContext(): SSLContext {
        val certificate = serviceKeyStore.getCertificate("billing") as X509Certificate
        val trusted: KeyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        trusted.load(null, null)
        trusted.setCertificateEntry("billing", certificate)

        val context: SSLContext = SSLContext.getInstance("TLS")
        context.init(null, trusted.trustManagers.toTypedArray(), null)
        return context
    }

    private fun billingClient(sslContext: SSLContext?): HttpClient = HttpClient(Apache5) {
        expectSuccess = true
        engine {
            connectTimeout = 5_000
            socketTimeout = 10_000
            if (sslContext != null) {
                this.sslContext = sslContext
            }
        }
    }

    @Test
    fun callsPrivateServiceThroughPinnedCertificate() {
        billingClient(pinnedTrustContext()).use { client ->
            runBlocking {
                repeat(2) { attempt ->
                    val response: HttpResponse = client.get("https://localhost:${service.port}/invoices/latest") {
                        header("X-Tenant", "acme-$attempt")
                    }
                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals("TLSv1.3", response.headers["X-Tls-Protocol"])
                    assertEquals(
                        """{"tenant":"acme-$attempt","invoice":"INV-2041","total":"129.90"}""",
                        response.bodyAsText(),
                    )
                }
            }
        }
    }

    @Test
    fun defaultTrustStoreRejectsSelfSignedService() {
        billingClient(sslContext = null).use { client ->
            val failure: Exception = assertThrows {
                runBlocking { client.get("https://localhost:${service.port}/invoices/latest") }
            }
            assertTrue(hasSslCause(failure), "expected a TLS failure, got $failure")
        }
    }

    @Test
    fun hostnameVerificationRejectsServiceAddressedByIp() {
        billingClient(pinnedTrustContext()).use { client ->
            val failure: Exception = assertThrows {
                runBlocking { client.get("https://127.0.0.1:${service.port}/invoices/latest") }
            }
            assertTrue(hasSslCause(failure), "expected a TLS failure, got $failure")
        }
    }

    private fun hasSslCause(failure: Throwable): Boolean =
        generateSequence(failure) { it.cause }.any { it is SSLException }
}
