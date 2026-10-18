/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_okhttp_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.CertificatePinner
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * HTTPS calls through an OkHttp engine built on a preconfigured `OkHttpClient` with private trust
 * and certificate pinning, negotiating HTTP/2 over TLS.
 *
 * A bookkeeping app talks to a ledger service that presents a certificate issued by the
 * company's private root. The app hands Ktor an `OkHttpClient` that trusts only that root and
 * pins the issuing root through the engine's `config` block; the calls run over HTTP/2, which
 * the app reads back from the Ktor response. A second build of the app pinned to a retired root
 * refuses to talk to the same service.
 */
class LedgerHttpsPinningTest {

    private val companyRoot: HeldCertificate = HeldCertificate.Builder()
        .commonName("Company Root CA")
        .certificateAuthority(0)
        .build()

    private val ledgerCertificate: HeldCertificate = HeldCertificate.Builder()
        .commonName("ledger")
        .addSubjectAlternativeName("localhost")
        .signedBy(companyRoot)
        .build()

    private lateinit var ledger: MockWebServer

    @BeforeEach
    fun start() {
        val serverCertificates: HandshakeCertificates = HandshakeCertificates.Builder()
            .heldCertificate(ledgerCertificate, companyRoot.certificate)
            .build()
        ledger = MockWebServer()
        ledger.useHttps(serverCertificates.sslSocketFactory())
        ledger.protocols = listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)
        ledger.start()
    }

    @AfterEach
    fun stop() {
        ledger.close()
    }

    @Test
    fun appPostsAndReadsEntriesOverPinnedHttp2() {
        ledger.enqueue(
            MockResponse.Builder()
                .code(201)
                .addHeader("Content-Type", "application/json")
                .addHeader("Location", "/entries/42")
                .body("""{"id":42,"status":"booked"}""")
                .build(),
        )
        ledger.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "application/json")
                .body("""{"balance":"1250.00","currency":"EUR"}""")
                .build(),
        )

        val client: HttpClient = ledgerClient(CertificatePinner.pin(companyRoot.certificate))
        lateinit var booked: HttpResponse
        lateinit var bookedBody: String
        lateinit var balance: HttpResponse
        lateinit var balanceBody: String
        client.use {
            runBlocking {
                booked = it.post(ledger.url("/entries").toString()) {
                    contentType(ContentType.Application.Json)
                    header("X-Ledger-Book", "2026-Q4")
                    setBody("""{"account":"4000","amount":"-75.00"}""")
                }
                bookedBody = booked.bodyAsText()
                balance = it.get(ledger.url("/accounts/4000/balance").toString())
                balanceBody = balance.bodyAsText()
            }
        }

        assertEquals(HttpStatusCode.Created, booked.status)
        assertEquals(HttpProtocolVersion.HTTP_2_0, booked.version)
        assertEquals("/entries/42", booked.headers["Location"])
        assertEquals("""{"id":42,"status":"booked"}""", bookedBody)
        assertEquals(HttpStatusCode.OK, balance.status)
        assertEquals(HttpProtocolVersion.HTTP_2_0, balance.version)
        assertEquals("""{"balance":"1250.00","currency":"EUR"}""", balanceBody)

        val postRequest: RecordedRequest = takeRecorded()
        assertEquals("POST", postRequest.method)
        assertEquals("/entries", postRequest.url.encodedPath)
        assertEquals("HTTP/2", postRequest.version)
        assertEquals("2026-Q4", postRequest.headers["X-Ledger-Book"])
        assertTrue(postRequest.headers["Content-Type"]!!.startsWith("application/json"))
        assertEquals("""{"account":"4000","amount":"-75.00"}""", postRequest.body!!.utf8())
        assertNotNull(postRequest.handshake, "the ledger saw a TLS handshake")

        val getRequest: RecordedRequest = takeRecorded()
        assertEquals("GET", getRequest.method)
        assertEquals("/accounts/4000/balance", getRequest.url.encodedPath)
        assertEquals(0, getRequest.connectionIndex, "both calls share one pinned HTTP/2 connection")
    }

    @Test
    fun appPinnedToARetiredRootRefusesTheLedger() {
        val retiredRoot: HeldCertificate = HeldCertificate.Builder()
            .commonName("Retired Root CA")
            .certificateAuthority(0)
            .build()
        ledger.enqueue(MockResponse.Builder().body("never sent").build())

        val client: HttpClient = ledgerClient(CertificatePinner.pin(retiredRoot.certificate))
        val failure: SSLPeerUnverifiedException = client.use {
            assertThrows<SSLPeerUnverifiedException> {
                runBlocking { it.get(ledger.url("/accounts/4000/balance").toString()) }
            }
        }

        assertTrue(
            failure.message!!.startsWith("Certificate pinning failure!"),
            "pinning failure is reported: ${failure.message}",
        )
        assertTrue(failure.message!!.contains(CertificatePinner.pin(companyRoot.certificate)))
        assertEquals(0, ledger.requestCount, "no request reached the ledger")
    }

    /** Builds the app's ledger client: private trust on the base `OkHttpClient`, the pin on top. */
    private fun ledgerClient(rootPin: String): HttpClient {
        val clientCertificates: HandshakeCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(companyRoot.certificate)
            .build()
        val companyHttp: OkHttpClient = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .build()
        return HttpClient(OkHttp) {
            engine {
                preconfigured = companyHttp
                config {
                    // Dial only the address the ledger listens on, so a refused `::1` cannot win.
                    dns(Dns { listOf(ledger.socketAddress.address) })
                    certificatePinner(CertificatePinner.Builder().add(ledger.hostName, rootPin).build())
                }
            }
        }
    }

    private fun takeRecorded(): RecordedRequest {
        val recorded: RecordedRequest? = ledger.takeRequest(10, TimeUnit.SECONDS)
        assertNotNull(recorded, "the ledger recorded a request")
        return recorded!!
    }
}
