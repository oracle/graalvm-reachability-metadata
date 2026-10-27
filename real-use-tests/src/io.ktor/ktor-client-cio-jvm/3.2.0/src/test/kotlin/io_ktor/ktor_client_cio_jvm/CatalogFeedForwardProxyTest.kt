/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
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
import io.ktor.server.application.ApplicationPlugin
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

/**
 * HTTP forward proxying in the CIO client engine (`engine { proxy = ProxyBuilder.http(...) }`).
 *
 * A catalog-feed importer runs inside a network where outbound traffic must pass a corporate
 * HTTP proxy that demands `Proxy-Authorization`. The importer points its CIO engine at the
 * proxy, so the engine connects to the proxy instead of the catalog service and sends the
 * absolute-form request target with the catalog's `Host`. The proxy refuses the importer with
 * `407` until it presents credentials, then relays reads and uploads to the catalog service; a
 * client without the proxy setting reaches the service directly and never touches the proxy.
 */
class CatalogFeedForwardProxyTest {

    private lateinit var catalog: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private lateinit var proxy: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private lateinit var relayClient: HttpClient
    private var catalogPort: Int = 0
    private var proxyPort: Int = 0

    /** Request target, `Host` header and method of every request the proxy received. */
    private val proxied: ConcurrentLinkedQueue<ProxiedRequest> = ConcurrentLinkedQueue()

    /** `Via` header of every request the catalog service received, `direct` when absent. */
    private val catalogVia: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()

    /** Uploads the catalog service accepted. */
    private val uploads: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()

    /** One request as the proxy saw it on the wire. */
    private data class ProxiedRequest(val target: String, val host: String, val method: String)

    @BeforeEach
    fun startServers() {
        catalog = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/catalog/{sku}") {
                    catalogVia += call.request.headers[HttpHeaders.Via] ?: "direct"
                    val sku: String = call.parameters["sku"]!!
                    call.respondText("""{"sku":"$sku","title":"Item $sku"}""", ContentType.Application.Json)
                }
                post("/catalog/uploads") {
                    catalogVia += call.request.headers[HttpHeaders.Via] ?: "direct"
                    uploads += call.receiveText()
                    call.respondText("accepted:${uploads.size}", status = HttpStatusCode.Created)
                }
            }
        }.start(wait = false)
        catalogPort = runBlocking { catalog.engine.resolvedConnectors().first().port }

        relayClient = HttpClient(ClientCIO)
        val forwardProxy: ApplicationPlugin<Unit> = createApplicationPlugin("ForwardProxy") {
            onCall { call ->
                val target: String = call.request.uri
                val method: HttpMethod = call.request.httpMethod
                proxied += ProxiedRequest(target, call.request.headers[HttpHeaders.Host]!!, method.value)
                if (call.request.headers[HttpHeaders.ProxyAuthorization] != PROXY_CREDENTIALS) {
                    call.response.headers.append(HttpHeaders.ProxyAuthenticate, "Basic realm=\"corp\"")
                    call.respondText("proxy login required", status = HttpStatusCode.ProxyAuthenticationRequired)
                    return@onCall
                }
                val upstream: HttpResponse = relayClient.request(target) {
                    this.method = method
                    header(HttpHeaders.Via, PROXY_VIA)
                    if (method == HttpMethod.Post) {
                        contentType(ContentType.Text.Plain)
                        setBody(call.receiveText())
                    }
                }
                call.respondText(upstream.bodyAsText(), upstream.contentType(), upstream.status)
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
        relayClient.close()
        catalog.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun importerReachesTheCatalogOnlyThroughTheAuthenticatedProxy() {
        val importer = HttpClient(ClientCIO) {
            engine {
                proxy = ProxyBuilder.http(Url("http://127.0.0.1:$proxyPort"))
            }
        }
        val catalogBase = "http://127.0.0.1:$catalogPort"

        importer.use { client ->
            runBlocking {
                val refused: HttpResponse = client.get("$catalogBase/catalog/SKU-1")
                assertEquals(HttpStatusCode.ProxyAuthenticationRequired, refused.status)
                assertEquals("Basic realm=\"corp\"", refused.headers[HttpHeaders.ProxyAuthenticate])
                assertEquals("proxy login required", refused.bodyAsText())

                val item: HttpResponse = client.get("$catalogBase/catalog/SKU-7") {
                    header(HttpHeaders.ProxyAuthorization, PROXY_CREDENTIALS)
                }
                assertEquals(HttpStatusCode.OK, item.status)
                assertEquals(ContentType.Application.Json, item.contentType()?.withoutParameters())
                assertEquals("""{"sku":"SKU-7","title":"Item SKU-7"}""", item.bodyAsText())

                val upload: HttpResponse = client.post("$catalogBase/catalog/uploads?batch=42") {
                    header(HttpHeaders.ProxyAuthorization, PROXY_CREDENTIALS)
                    contentType(ContentType.Text.Plain)
                    setBody("SKU-7;SKU-8;SKU-9")
                }
                assertEquals(HttpStatusCode.Created, upload.status)
                assertEquals("accepted:1", upload.bodyAsText())
            }
        }

        val catalogHost = "127.0.0.1:$catalogPort"
        assertEquals(
            listOf(
                ProxiedRequest("$catalogBase/catalog/SKU-1", catalogHost, "GET"),
                ProxiedRequest("$catalogBase/catalog/SKU-7", catalogHost, "GET"),
                ProxiedRequest("$catalogBase/catalog/uploads?batch=42", catalogHost, "POST"),
            ),
            proxied.toList(),
        )
        assertEquals(listOf(PROXY_VIA, PROXY_VIA), catalogVia.toList())
        assertEquals(listOf("SKU-7;SKU-8;SKU-9"), uploads.toList())
    }

    @Test
    fun clientWithoutProxySettingBypassesTheProxy() {
        val answer: String = HttpClient(ClientCIO).use { client ->
            runBlocking { client.get("http://127.0.0.1:$catalogPort/catalog/SKU-3").bodyAsText() }
        }

        assertEquals("""{"sku":"SKU-3","title":"Item SKU-3"}""", answer)
        assertEquals(emptyList<ProxiedRequest>(), proxied.toList())
        assertEquals(listOf("direct"), catalogVia.toList())
    }

    private companion object {
        const val PROXY_CREDENTIALS: String = "Basic aW1wb3J0ZXI6czNjcmV0"
        const val PROXY_VIA: String = "1.1 corp-proxy"
    }
}
