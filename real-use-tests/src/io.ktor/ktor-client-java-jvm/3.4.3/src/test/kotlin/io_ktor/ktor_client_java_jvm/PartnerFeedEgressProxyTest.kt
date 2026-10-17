/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_java_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.http
import io.ktor.client.engine.java.Java
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
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.hostWithPortIfSpecified
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

/**
 * HTTP proxy routing through the Java engine's proxy configuration, as a pricing service inside a
 * locked-down network reaches a partner's exchange-rate feed only through the corporate egress proxy.
 */
public class PartnerFeedEgressProxyTest {

    /** One embedded CIO server on a free loopback port. */
    abstract class LoopbackServer : AutoCloseable {
        protected abstract val server: EmbeddedServer<*, *>

        val port: Int by lazy {
            server.start(wait = false)
            runBlocking { server.engine.resolvedConnectors().first().port }
        }

        override fun close() {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 2_000)
        }
    }

    /** The partner's exchange-rate feed; it echoes the host it was addressed as. */
    class PartnerFeed : LoopbackServer() {
        override val server: EmbeddedServer<*, *> = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/rates") {
                    val currency: String = call.request.queryParameters["currency"] ?: "USD"
                    val host: String? = call.request.headers[HttpHeaders.XForwardedHost] ?: call.request.headers[HttpHeaders.Host]
                    call.respondText("$currency=1.0842;host=$host")
                }
                post("/orders") {
                    val order: String = call.receiveText()
                    call.respondText("""{"accepted":$order}""", ContentType.Application.Json, HttpStatusCode.Created)
                }
            }
        }
    }

    /**
     * The corporate egress proxy: it resolves partner host names only it knows, forwards requests
     * to them, and, when [credentials] are set, demands Basic `Proxy-Authorization` first.
     */
    class EgressProxy(private val partners: Map<String, Int>, private val credentials: String? = null) : LoopbackServer() {
        /** The request-target of every request the proxy received, in arrival order. */
        val requestTargets: MutableList<String> = CopyOnWriteArrayList()

        /** The `Proxy-Authorization` header of every request, or `-` when it had none. */
        val proxyAuthorizations: MutableList<String> = CopyOnWriteArrayList()

        private val upstream: HttpClient = HttpClient(Java) { expectSuccess = false }

        override val server: EmbeddedServer<*, *> = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            intercept(ApplicationCallPipeline.Call) {
                val target: String = call.request.uri
                requestTargets += target
                val authorization: String? = call.request.headers[HttpHeaders.ProxyAuthorization]
                proxyAuthorizations += authorization ?: "-"
                val expected: String? = credentials?.let { "Basic " + Base64.getEncoder().encodeToString(it.toByteArray()) }
                if (expected != null && authorization != expected) {
                    call.response.header(HttpHeaders.ProxyAuthenticate, "Basic realm=\"egress\"")
                    call.respondText("proxy authentication required", status = HttpStatusCode.ProxyAuthenticationRequired)
                    return@intercept finish()
                }
                val url: Url = Url(target)
                val partnerPort: Int? = partners[url.host]
                if (partnerPort == null) {
                    call.respondText("egress to ${url.host} is not allowed", status = HttpStatusCode.Forbidden)
                    return@intercept finish()
                }
                val body: String = call.receiveText()
                val forwarded: HttpResponse = upstream.request("http://127.0.0.1:$partnerPort${url.encodedPathAndQuery}") {
                    method = call.request.httpMethod
                    header(HttpHeaders.XForwardedHost, url.hostWithPortIfSpecified)
                    if (method == HttpMethod.Post) {
                        contentType(ContentType.parse(call.request.headers[HttpHeaders.ContentType]!!))
                        setBody(body)
                    }
                }
                call.response.header(HttpHeaders.Via, "1.1 egress-proxy")
                call.respondText(
                    forwarded.bodyAsText(),
                    forwarded.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) },
                    forwarded.status,
                )
                finish()
            }
        }

        override fun close() {
            upstream.close()
            super.close()
        }
    }

    @Test
    fun reachesPartnerHostsThatOnlyTheProxyResolves(): Unit = runBlocking {
        PartnerFeed().use { feed: PartnerFeed ->
            EgressProxy(mapOf("rates.partner.internal" to feed.port)).use { proxy: EgressProxy ->
                val client: HttpClient = HttpClient(Java) {
                    engine {
                        this.proxy = ProxyBuilder.http("http://127.0.0.1:${proxy.port}/")
                    }
                }
                client.use {
                    val rates: HttpResponse = client.get("http://rates.partner.internal/rates?currency=EUR")
                    assertThat(rates.status).isEqualTo(HttpStatusCode.OK)
                    assertThat(rates.headers[HttpHeaders.Via]).isEqualTo("1.1 egress-proxy")
                    assertThat(rates.bodyAsText()).isEqualTo("EUR=1.0842;host=rates.partner.internal")

                    val order: HttpResponse = client.post("http://rates.partner.internal:8080/orders") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"pair":"EURUSD","amount":250000}""")
                    }
                    assertThat(order.status).isEqualTo(HttpStatusCode.Created)
                    assertThat(order.bodyAsText()).isEqualTo("""{"accepted":{"pair":"EURUSD","amount":250000}}""")

                    val blocked: HttpResponse = client.get("http://exfiltrate.example/upload")
                    assertThat(blocked.status).isEqualTo(HttpStatusCode.Forbidden)
                    assertThat(blocked.bodyAsText()).isEqualTo("egress to exfiltrate.example is not allowed")
                }
                // The client never resolved the partner names itself: it sent absolute-form targets.
                assertThat(proxy.requestTargets).containsExactly(
                    "http://rates.partner.internal/rates?currency=EUR",
                    "http://rates.partner.internal:8080/orders",
                    "http://exfiltrate.example/upload",
                )
            }
        }
    }

    @Test
    fun answersTheProxyAuthenticationChallengeWithTheBuilderAuthenticator(): Unit = runBlocking {
        PartnerFeed().use { feed: PartnerFeed ->
            EgressProxy(mapOf("rates.partner.internal" to feed.port), credentials = "pricing-svc:s3cret").use { proxy ->
                val proxyAddress: Proxy = ProxyBuilder.http("http://127.0.0.1:${proxy.port}/")

                val anonymous: HttpClient = HttpClient(Java) {
                    engine { this.proxy = proxyAddress }
                }
                anonymous.use {
                    val denied: HttpResponse = anonymous.get("http://rates.partner.internal/rates")
                    assertThat(denied.status).isEqualTo(HttpStatusCode.ProxyAuthenticationRequired)
                    assertThat(denied.bodyAsText()).isEqualTo("proxy authentication required")
                }

                val prompts: MutableList<String> = CopyOnWriteArrayList()
                val authenticated: HttpClient = HttpClient(Java) {
                    engine {
                        this.proxy = proxyAddress
                        config {
                            authenticator(object : Authenticator() {
                                override fun getPasswordAuthentication(): PasswordAuthentication? {
                                    if (requestorType != RequestorType.PROXY) {
                                        return null
                                    }
                                    prompts += "$requestingScheme $requestingPrompt"
                                    return PasswordAuthentication("pricing-svc", "s3cret".toCharArray())
                                }
                            })
                        }
                    }
                }
                authenticated.use {
                    val rates: HttpResponse = authenticated.get("http://rates.partner.internal/rates?currency=GBP")
                    assertThat(rates.status).isEqualTo(HttpStatusCode.OK)
                    assertThat(rates.bodyAsText()).isEqualTo("GBP=1.0842;host=rates.partner.internal")
                }
                assertThat(prompts).containsExactly("basic egress")
                assertThat(proxy.proxyAuthorizations).containsExactly(
                    "-",
                    "-",
                    "Basic " + Base64.getEncoder().encodeToString("pricing-svc:s3cret".toByteArray()),
                )
            }
        }
    }

    @Test
    fun routesOnlyPartnerHostsThroughTheProxySelectorOfTheBuilder(): Unit = runBlocking {
        PartnerFeed().use { feed: PartnerFeed ->
            EgressProxy(mapOf("rates.partner.internal" to feed.port)).use { proxy: EgressProxy ->
                val egress: Proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", proxy.port))
                val selector: ProxySelector = object : ProxySelector() {
                    override fun select(uri: URI): List<Proxy> =
                        if (uri.host.endsWith(".partner.internal")) listOf(egress) else listOf(Proxy.NO_PROXY)

                    override fun connectFailed(uri: URI, address: SocketAddress, failure: IOException) {
                        throw failure
                    }
                }
                val client: HttpClient = HttpClient(Java) {
                    engine {
                        config { proxy(selector) }
                    }
                }
                client.use {
                    val proxied: HttpResponse = client.get("http://rates.partner.internal/rates?currency=CHF")
                    assertThat(proxied.headers[HttpHeaders.Via]).isEqualTo("1.1 egress-proxy")
                    assertThat(proxied.bodyAsText()).isEqualTo("CHF=1.0842;host=rates.partner.internal")

                    val direct: HttpResponse = client.get("http://127.0.0.1:${feed.port}/rates?currency=JPY")
                    assertThat(direct.headers[HttpHeaders.Via]).isNull()
                    assertThat(direct.bodyAsText()).isEqualTo("JPY=1.0842;host=127.0.0.1:${feed.port}")
                }
                assertThat(proxy.requestTargets).containsExactly("http://rates.partner.internal/rates?currency=CHF")
            }
        }
    }

    @Test
    fun rejectsASocksProxyBecauseTheJavaEngineOnlySpeaksHttpProxies(): Unit = runBlocking {
        val client: HttpClient = HttpClient(Java) {
            engine {
                proxy = ProxyBuilder.socks("127.0.0.1", 1080)
            }
        }
        client.use {
            val failure: IllegalStateException = assertThrows<IllegalStateException> {
                client.get("http://rates.partner.internal/rates")
            }
            assertThat(failure.message).isEqualTo("Only HTTP proxy is supported for Java HTTP engine, but configured SOCKS.")
        }
    }
}
