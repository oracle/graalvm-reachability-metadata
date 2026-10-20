/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_cio_jvm

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsExchange
import com.sun.net.httpserver.HttpsServer
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.network.tls.certificates.trustManagers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.KeyStore
import java.security.cert.CertPathBuilderException
import java.util.concurrent.ConcurrentLinkedQueue
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import io.ktor.client.engine.cio.CIO as ClientCIO

/**
 * HTTPS of the CIO client engine through its own TLS stack, configured with `engine { https { ... } }`.
 *
 * An internal billing gateway serves HTTPS with a self-signed certificate. The invoicing client
 * pins trust in exactly that certificate through the CIO engine's TLS trust manager and submits
 * invoices over the encrypted connection; the gateway records the TLS protocol each call arrived
 * on. A client that relies on the platform trust store instead refuses to talk to the gateway, so
 * no invoice reaches it.
 */
class BillingGatewayPinnedTlsTest {

    private val keyStore: KeyStore = buildKeyStore {
        certificate(ALIAS) {
            password = KEY_PASSWORD
            domains = listOf("localhost")
            ipAddresses = listOf(InetAddress.getByName(HOST))
        }
    }

    private lateinit var server: HttpsServer

    /** Invoice bodies the gateway accepted, with the TLS protocol of the call that carried them. */
    private val accepted: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()

    @BeforeEach
    fun startGateway() {
        val keyManagers: KeyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagers.init(keyStore, KEY_PASSWORD.toCharArray())
        val tls: SSLContext = SSLContext.getInstance("TLS")
        tls.init(keyManagers.keyManagers, null, null)

        server = HttpsServer.create(InetSocketAddress(HOST, 0), 0)
        server.httpsConfigurator = HttpsConfigurator(tls)
        server.createContext("/invoices") { exchange: HttpExchange ->
            exchange.use {
                val protocol: String = (it as HttpsExchange).sslSession.protocol
                val invoice: String = it.requestBody.readBytes().decodeToString()
                accepted += "$protocol $invoice"
                val reply: ByteArray = "{\"accepted\":${accepted.size},\"tenant\":\"${it.requestHeaders.getFirst("X-Tenant")}\"}"
                    .toByteArray()
                it.responseHeaders.add("Content-Type", "application/json")
                it.sendResponseHeaders(HttpStatusCode.Created.value, reply.size.toLong())
                it.responseBody.write(reply)
            }
        }
        server.start()
    }

    @AfterEach
    fun stopGateway() {
        server.stop(0)
    }

    @Test
    fun pinnedClientSubmitsInvoicesAndPlatformTrustIsRefused() {
        val pinned = HttpClient(ClientCIO) {
            engine {
                https {
                    trustManager = keyStore.trustManagers.first()
                }
            }
        }

        pinned.use {
            runBlocking {
                assertEquals("{\"accepted\":1,\"tenant\":\"acme\"}", submit(it, "{\"invoice\":\"INV-1\",\"cents\":12500}"))
                assertEquals("{\"accepted\":2,\"tenant\":\"acme\"}", submit(it, "{\"invoice\":\"INV-2\",\"cents\":990}"))
            }
        }

        val platformTrust = HttpClient(ClientCIO)
        val refusal: Throwable? = platformTrust.use {
            runBlocking { runCatching { submit(it, "{\"invoice\":\"INV-3\",\"cents\":1}") }.exceptionOrNull() }
        }

        val untrusted: CertPathBuilderException = assertInstanceOf(CertPathBuilderException::class.java, refusal)
        assertTrue(
            untrusted.message!!.contains("certification path"),
            "the platform trust store finds no path to the self-signed certificate: ${untrusted.message}",
        )
        assertEquals(
            listOf(
                "TLSv1.2 {\"invoice\":\"INV-1\",\"cents\":12500}",
                "TLSv1.2 {\"invoice\":\"INV-2\",\"cents\":990}",
            ),
            accepted.toList(),
        )
    }

    private suspend fun submit(client: HttpClient, invoice: String): String {
        val response: HttpResponse = client.post("https://$HOST:${server.address.port}/invoices") {
            header("X-Tenant", "acme")
            contentType(ContentType.Application.Json)
            setBody(invoice)
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return response.bodyAsText()
    }

    private companion object {
        const val HOST: String = "127.0.0.1"
        const val ALIAS: String = "billing-gateway"
        const val KEY_PASSWORD: String = "changeit"
    }
}
