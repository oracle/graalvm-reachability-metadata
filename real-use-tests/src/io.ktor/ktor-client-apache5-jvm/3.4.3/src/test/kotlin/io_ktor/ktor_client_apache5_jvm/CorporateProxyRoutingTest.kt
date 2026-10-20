/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_apache5_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.engine.http
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.Proxy
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Outbound HTTP proxy routing configured through the `Apache5` engine's `proxy` setting.
 *
 * A treasury client inside a locked-down network reaches an exchange-rate service only through
 * the corporate forward proxy: it names the proxy with `ProxyBuilder.http`, sends proxy
 * credentials on every call, and observes that the proxy saw absolute-form request targets
 * and stamped the forwarded traffic. Calls without credentials get the proxy's `407`, a
 * client configured for a direct connection bypasses the proxy, and a SOCKS proxy is refused
 * because the engine only speaks to HTTP proxies.
 */
class CorporateProxyRoutingTest {

    /** One request line the proxy received, with the proxy credentials it carried, if any. */
    data class ProxiedRequest(val method: String, val target: String, val credentials: String?)

    /** One request the exchange-rate service received, with the hop headers it carried. */
    data class OriginRequest(val path: String, val via: String?, val proxyAuthorization: String?)

    private lateinit var origin: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private lateinit var proxy: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private lateinit var upstream: HttpClient
    private var originPort: Int = 0
    private var proxyPort: Int = 0
    private val proxyLog: MutableList<ProxiedRequest> = CopyOnWriteArrayList()
    private val originLog: MutableList<OriginRequest> = CopyOnWriteArrayList()
    private val proxyCredentials: String =
        "Basic " + Base64.getEncoder().encodeToString("treasury:s3cret".toByteArray())

    @BeforeEach
    fun startServers() {
        origin = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/rates") {
                    originLog.add(
                        OriginRequest(
                            call.request.uri,
                            call.request.headers[HttpHeaders.Via],
                            call.request.headers[HttpHeaders.ProxyAuthorization],
                        ),
                    )
                    val currency: String = call.request.queryParameters["currency"]!!
                    call.respondText("$currency/USD=1.0842")
                }
                post("/transfers") {
                    originLog.add(
                        OriginRequest(
                            call.request.uri,
                            call.request.headers[HttpHeaders.Via],
                            call.request.headers[HttpHeaders.ProxyAuthorization],
                        ),
                    )
                    val transfer: String = call.receiveText()
                    call.respondText("accepted: $transfer", status = HttpStatusCode.Created)
                }
            }
        }.start(wait = false)
        originPort = runBlocking { origin.engine.resolvedConnectors().first().port }

        upstream = HttpClient(Apache5) {
            followRedirects = false
        }
        val forwardProxy = createApplicationPlugin("ForwardProxy") {
            onCall { call ->
                val target: String = call.request.uri
                val credentials: String? = call.request.headers[HttpHeaders.ProxyAuthorization]
                proxyLog.add(ProxiedRequest(call.request.httpMethod.value, target, credentials))
                if (credentials != proxyCredentials) {
                    call.response.header(HttpHeaders.ProxyAuthenticate, "Basic realm=\"corp-proxy\"")
                    call.respondText("proxy authentication required", status = HttpStatusCode.ProxyAuthenticationRequired)
                    return@onCall
                }
                val incoming: HttpMethod = call.request.httpMethod
                val requestBody: String = call.receiveText()
                val forwarded: HttpResponse = upstream.request(target) {
                    method = incoming
                    header(HttpHeaders.Via, "1.1 corp-proxy")
                    if (incoming == HttpMethod.Post) {
                        contentType(ContentType.Text.Plain)
                        setBody(requestBody)
                    }
                }
                call.response.header("X-Proxied-By", "corp-proxy")
                call.respondText(forwarded.bodyAsText(), status = forwarded.status)
            }
        }
        proxy = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(forwardProxy)
        }.start(wait = false)
        proxyPort = runBlocking { proxy.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stopServers() {
        proxy.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
        upstream.close()
        origin.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun authenticatedCallsAreForwardedThroughTheCorporateProxy() = runBlocking {
        val exchange: String = "http://127.0.0.1:$originPort"
        HttpClient(Apache5) {
            engine {
                proxy = ProxyBuilder.http("http://127.0.0.1:$proxyPort")
            }
            defaultRequest {
                header(HttpHeaders.ProxyAuthorization, proxyCredentials)
            }
        }.use { treasury: HttpClient ->
            val rate: HttpResponse = treasury.get("$exchange/rates?currency=EUR")
            assertEquals(HttpStatusCode.OK, rate.status)
            assertEquals("EUR/USD=1.0842", rate.bodyAsText())
            assertEquals("corp-proxy", rate.headers["X-Proxied-By"])

            val transfer: HttpResponse = treasury.post("$exchange/transfers") {
                contentType(ContentType.Text.Plain)
                setBody("EUR 250000 to ACME-GMBH")
            }
            assertEquals(HttpStatusCode.Created, transfer.status)
            assertEquals("accepted: EUR 250000 to ACME-GMBH", transfer.bodyAsText())
            assertEquals("corp-proxy", transfer.headers["X-Proxied-By"])
        }

        assertEquals(
            listOf(
                ProxiedRequest("GET", "$exchange/rates?currency=EUR", proxyCredentials),
                ProxiedRequest("POST", "$exchange/transfers", proxyCredentials),
            ),
            proxyLog,
        )
        assertEquals(
            listOf(
                OriginRequest("/rates?currency=EUR", "1.1 corp-proxy", null),
                OriginRequest("/transfers", "1.1 corp-proxy", null),
            ),
            originLog,
        )
    }

    @Test
    fun callWithoutProxyCredentialsIsStoppedAtTheProxy() = runBlocking {
        val exchange: String = "http://127.0.0.1:$originPort"
        HttpClient(Apache5) {
            engine {
                proxy = ProxyBuilder.http("http://127.0.0.1:$proxyPort")
            }
        }.use { unauthenticated: HttpClient ->
            val refused: HttpResponse = unauthenticated.get("$exchange/rates?currency=GBP")

            assertEquals(HttpStatusCode.ProxyAuthenticationRequired, refused.status)
            assertEquals("Basic realm=\"corp-proxy\"", refused.headers[HttpHeaders.ProxyAuthenticate])
            assertEquals("proxy authentication required", refused.bodyAsText())
        }

        assertEquals(listOf(ProxiedRequest("GET", "$exchange/rates?currency=GBP", null)), proxyLog)
        assertTrue(originLog.isEmpty())
    }

    @Test
    fun directConnectionBypassesTheProxy() = runBlocking {
        HttpClient(Apache5) {
            engine {
                proxy = Proxy.NO_PROXY
            }
        }.use { onPremises: HttpClient ->
            val rate: HttpResponse = onPremises.get("http://127.0.0.1:$originPort/rates?currency=CHF")

            assertEquals(HttpStatusCode.OK, rate.status)
            assertEquals("CHF/USD=1.0842", rate.bodyAsText())
            assertEquals(null, rate.headers["X-Proxied-By"])
        }

        assertTrue(proxyLog.isEmpty())
        assertEquals(listOf(OriginRequest("/rates?currency=CHF", null, null)), originLog)
    }

    @Test
    fun socksProxyIsRefusedByTheEngine() {
        HttpClient(Apache5) {
            engine {
                proxy = ProxyBuilder.socks("127.0.0.1", proxyPort)
            }
        }.use { misconfigured: HttpClient ->
            val failure: IllegalStateException = assertThrows {
                runBlocking { misconfigured.get("http://127.0.0.1:$originPort/rates?currency=JPY") }
            }

            assertEquals("Only HTTP proxy is supported for Apache engine, but configured SOCKS.", failure.message)
        }

        assertTrue(proxyLog.isEmpty())
        assertTrue(originLog.isEmpty())
    }
}
