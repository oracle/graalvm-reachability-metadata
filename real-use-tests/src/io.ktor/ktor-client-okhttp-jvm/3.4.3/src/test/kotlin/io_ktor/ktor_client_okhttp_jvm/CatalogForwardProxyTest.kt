/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_okhttp_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.http
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Response
import okhttp3.Route
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Plain HTTP calls that the OkHttp engine routes through a forward proxy configured with Ktor's
 * `ProxyBuilder`, answering the proxy's authentication challenges with an OkHttp proxy authenticator.
 *
 * A catalog client inside a corporate network reaches `catalog.example` only through the egress
 * proxy, which demands Basic credentials. The proxy here is a MockWebServer that records what the
 * engine sends it. The test checks that the engine sends absolute-form requests for the remote
 * host to the proxy without resolving that host itself, that each `407` challenge is answered by
 * replaying the request (body included) with `Proxy-Authorization`, and that a client without
 * credentials receives the proxy's `407` as an ordinary response.
 */
class CatalogForwardProxyTest {

    /** One proxy challenge as the authenticator saw it: the route's proxy and the challenge scheme. */
    data class Challenge(val proxyPort: Int, val scheme: String, val realm: String?)

    private lateinit var egressProxy: MockWebServer

    @BeforeEach
    fun start() {
        egressProxy = MockWebServer()
        egressProxy.start()
    }

    @AfterEach
    fun stop() {
        egressProxy.close()
    }

    @Test
    fun catalogCallsTravelThroughTheAuthenticatingProxy() {
        egressProxy.enqueue(challenge())
        egressProxy.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "application/json")
                .body("""[{"sku":"lamp-01","price":"39.90"},{"sku":"desk-07","price":"249.00"}]""")
                .build(),
        )
        egressProxy.enqueue(challenge())
        egressProxy.enqueue(
            MockResponse.Builder()
                .code(201)
                .addHeader("Content-Type", "application/json")
                .addHeader("Location", "/orders/9001")
                .body("""{"order":9001,"status":"accepted"}""")
                .build(),
        )

        val challenges: MutableList<Challenge> = CopyOnWriteArrayList()
        val client: HttpClient = catalogClient(
            Authenticator { route: Route?, response: Response ->
                if (response.request.header(HttpHeaders.ProxyAuthorization) != null) {
                    return@Authenticator null
                }
                val challenge = response.challenges().first()
                val proxyAddress = route?.proxy?.address() as InetSocketAddress
                challenges += Challenge(proxyAddress.port, challenge.scheme, challenge.realm)
                response.request.newBuilder()
                    .header(HttpHeaders.ProxyAuthorization, Credentials.basic(PROXY_USER, PROXY_PASSWORD))
                    .build()
            },
        )

        lateinit var listing: HttpResponse
        lateinit var listingBody: String
        lateinit var order: HttpResponse
        lateinit var orderBody: String
        client.use {
            runBlocking {
                listing = it.get("http://catalog.example/products?page=2")
                listingBody = listing.bodyAsText()
                order = it.post("http://catalog.example/orders") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"sku":"lamp-01","quantity":3}""")
                }
                orderBody = order.bodyAsText()
            }
        }

        assertEquals(HttpStatusCode.OK, listing.status)
        assertEquals("""[{"sku":"lamp-01","price":"39.90"},{"sku":"desk-07","price":"249.00"}]""", listingBody)
        assertEquals(HttpStatusCode.Created, order.status)
        assertEquals("/orders/9001", order.headers[HttpHeaders.Location])
        assertEquals("""{"order":9001,"status":"accepted"}""", orderBody)

        assertEquals(
            listOf(
                Challenge(egressProxy.port, "Basic", "corp-egress"),
                Challenge(egressProxy.port, "Basic", "corp-egress"),
            ),
            challenges,
        )

        val firstListing: RecordedRequest = takeRecorded()
        val authorizedListing: RecordedRequest = takeRecorded()
        val firstOrder: RecordedRequest = takeRecorded()
        val authorizedOrder: RecordedRequest = takeRecorded()

        assertEquals("GET http://catalog.example/products?page=2 HTTP/1.1", firstListing.requestLine)
        assertEquals("catalog.example", firstListing.headers[HttpHeaders.Host])
        assertNull(firstListing.headers[HttpHeaders.ProxyAuthorization])

        assertEquals("GET http://catalog.example/products?page=2 HTTP/1.1", authorizedListing.requestLine)
        assertEquals(EXPECTED_CREDENTIALS, authorizedListing.headers[HttpHeaders.ProxyAuthorization])

        assertEquals("POST http://catalog.example/orders HTTP/1.1", firstOrder.requestLine)
        assertNull(firstOrder.headers[HttpHeaders.ProxyAuthorization])
        assertEquals("POST http://catalog.example/orders HTTP/1.1", authorizedOrder.requestLine)
        assertEquals(EXPECTED_CREDENTIALS, authorizedOrder.headers[HttpHeaders.ProxyAuthorization])
        assertEquals("""{"sku":"lamp-01","quantity":3}""", authorizedOrder.body?.utf8())
        assertEquals(
            "application/json",
            authorizedOrder.headers[HttpHeaders.ContentType].orEmpty().substringBefore(';'),
        )
    }

    @Test
    fun clientWithoutCredentialsSeesTheProxyRefusal() {
        egressProxy.enqueue(challenge())

        val refusedChallenges: MutableList<String> = CopyOnWriteArrayList()
        val client: HttpClient = catalogClient(
            Authenticator { _: Route?, response: Response ->
                refusedChallenges += response.challenges().joinToString { it.scheme }
                null
            },
        )

        lateinit var refused: HttpResponse
        lateinit var refusedBody: String
        client.use {
            runBlocking {
                refused = it.get("http://catalog.example/products")
                refusedBody = refused.bodyAsText()
            }
        }

        assertEquals(HttpStatusCode.ProxyAuthenticationRequired, refused.status)
        assertEquals("""Basic realm="corp-egress"""", refused.headers[HttpHeaders.ProxyAuthenticate])
        assertEquals("proxy credentials required", refusedBody)
        assertEquals(listOf("Basic"), refusedChallenges)

        val recorded: RecordedRequest = takeRecorded()
        assertEquals("GET http://catalog.example/products HTTP/1.1", recorded.requestLine)
        assertNull(recorded.headers[HttpHeaders.ProxyAuthorization])
        assertEquals(1, egressProxy.requestCount)
    }

    /** Builds the catalog client the way the application does: proxy from Ktor, auth from OkHttp. */
    private fun catalogClient(proxyAuthenticator: Authenticator): HttpClient = HttpClient(OkHttp) {
        engine {
            proxy = ProxyBuilder.http("http://127.0.0.1:${egressProxy.port}")
            config {
                proxyAuthenticator(proxyAuthenticator)
            }
        }
    }

    private fun challenge(): MockResponse = MockResponse.Builder()
        .code(407)
        .addHeader(HttpHeaders.ProxyAuthenticate, """Basic realm="corp-egress"""")
        .addHeader("Content-Type", "text/plain")
        .body("proxy credentials required")
        .build()

    private fun takeRecorded(): RecordedRequest =
        checkNotNull(egressProxy.takeRequest(10, TimeUnit.SECONDS)) { "the proxy received no request" }

    private companion object {
        const val PROXY_USER: String = "catalog-svc"
        const val PROXY_PASSWORD: String = "egress-2026"
        val EXPECTED_CREDENTIALS: String = Credentials.basic(PROXY_USER, PROXY_PASSWORD)
    }
}
