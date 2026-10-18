/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_core_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.ClientPluginInstance
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.plugin
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.util.AttributeKey
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import io.ktor.server.cio.CIO as ServerCIO

/**
 * An application-defined client plugin built with `createClientPlugin` and its request,
 * send, response and body-transformation hooks.
 *
 * A currency trading desk talks to an FX gateway through its own `FxGateway` plugin. The
 * plugin stamps every request with the tenant and a request id kept in call attributes,
 * refreshes an expired bearer token by re-sending the request from the `Send` hook, writes
 * outgoing `FxOrder` objects in the gateway's line format, and decodes quote responses into
 * `FxQuote` objects. The test checks what the desk observes: decoded quotes, a single token
 * refresh, the order echo with the tenant, and the per-call journal the plugin keeps.
 */
class FxDeskCustomClientPluginTest {

    /** A quote for one currency pair, decoded from the gateway's line format. */
    data class FxQuote(val pair: String, val bid: String, val ask: String)

    /** An order the desk places, encoded by the plugin into the gateway's line format. */
    data class FxOrder(val side: String, val amount: Int, val pair: String)

    /** Configuration of the desk's `FxGateway` plugin. */
    class FxGatewayConfig {
        var tenant: String = "unset"
        var initialToken: String = "unset"
        var refreshToken: () -> String = { "unset" }
        var journal: MutableList<String> = mutableListOf()
    }

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0
    private val quoteRequests: AtomicInteger = AtomicInteger()

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/quotes/{base}/{quote}") {
                    quoteRequests.incrementAndGet()
                    if (call.request.headers[HttpHeaders.Authorization] != "Bearer $VALID_TOKEN") {
                        call.respondText("token expired", status = HttpStatusCode.Unauthorized)
                        return@get
                    }
                    val pair: String = "${call.parameters["base"]}/${call.parameters["quote"]}"
                    call.response.header(SERVED_BY_HEADER, "fx-node-3")
                    call.respondText("$pair 1.0841 1.0843", QUOTE_TYPE)
                }
                post("/orders") {
                    val tenant: String = call.request.headers[TENANT_HEADER] ?: "none"
                    val type: String = call.request.headers[HttpHeaders.ContentType] ?: "none"
                    val line: String = call.receiveText()
                    call.respondText("accepted [$line] tenant=$tenant type=$type", status = HttpStatusCode.Created)
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stop() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun deskPluginStampsRefreshesAndTranslatesGatewayTraffic() {
        val base: String = "http://127.0.0.1:$port"
        val refreshes: AtomicInteger = AtomicInteger()
        val journal: MutableList<String> = CopyOnWriteArrayList()
        val client = HttpClient(CIO) {
            install(FxGateway) {
                tenant = "acme-treasury"
                initialToken = "stale-token"
                this.journal = journal
                refreshToken = {
                    refreshes.incrementAndGet()
                    VALID_TOKEN
                }
            }
        }

        client.use {
            runBlocking {
                val installed: ClientPluginInstance<FxGatewayConfig> = it.plugin(FxGateway)
                assertNull(it.pluginOrNull(OtherPlugin))

                val eurUsd: FxQuote = it.get("$base/quotes/EUR/USD").body()
                assertEquals(FxQuote("EUR/USD", "1.0841", "1.0843"), eurUsd)
                assertEquals(1, refreshes.get())
                assertEquals(2, quoteRequests.get())

                val quoted: HttpResponse = it.get("$base/quotes/GBP/JPY")
                assertEquals("fx-node-3", quoted.headers[SERVED_BY_HEADER])
                assertEquals("req-2", quoted.call.attributes[RequestIdKey])
                assertEquals(FxQuote("GBP/JPY", "1.0841", "1.0843"), quoted.body<FxQuote>())
                assertEquals(1, refreshes.get())
                assertEquals(3, quoteRequests.get())

                val order: HttpResponse = it.post("$base/orders") {
                    setBody(FxOrder(side = "BUY", amount = 250_000, pair = "EUR/USD"))
                }
                assertEquals(HttpStatusCode.Created, order.status)
                assertEquals(
                    "accepted [BUY 250000 EUR/USD] tenant=acme-treasury type=$ORDER_TYPE",
                    order.bodyAsText()
                )

                assertSame(installed, it.plugin(FxGateway))
                assertEquals(
                    listOf(
                        "req-1 GET /quotes/EUR/USD -> 401",
                        "req-1 GET /quotes/EUR/USD -> 200",
                        "req-2 GET /quotes/GBP/JPY -> 200",
                        "req-3 POST /orders -> 201"
                    ),
                    journal
                )
            }
        }
    }

    private companion object {
        const val VALID_TOKEN: String = "fresh-token-7"
        const val TENANT_HEADER: String = "X-Tenant"
        const val REQUEST_ID_HEADER: String = "X-Request-Id"
        const val SERVED_BY_HEADER: String = "X-Served-By"
        const val ORDER_TYPE: String = "application/vnd.fx.order"
        val QUOTE_TYPE: ContentType = ContentType.parse("application/vnd.fx.quote")

        val RequestIdKey: AttributeKey<String> = AttributeKey("FxRequestId")

        /** The desk's gateway plugin: tenant stamping, token refresh, journal and FX body codecs. */
        val FxGateway: ClientPlugin<FxGatewayConfig> = createClientPlugin("FxGateway", ::FxGatewayConfig) {
            val config: FxGatewayConfig = pluginConfig
            val sequence: AtomicInteger = AtomicInteger()
            var token: String = config.initialToken

            onRequest { request, _ ->
                val requestId: String = "req-${sequence.incrementAndGet()}"
                request.attributes.put(RequestIdKey, requestId)
                request.headers.append(TENANT_HEADER, config.tenant)
                request.headers.append(REQUEST_ID_HEADER, requestId)
                request.headers[HttpHeaders.Authorization] = "Bearer $token"
            }

            transformRequestBody { _, content, _ ->
                if (content is FxOrder) {
                    TextContent(
                        "${content.side} ${content.amount} ${content.pair}",
                        ContentType.parse(ORDER_TYPE)
                    )
                } else {
                    null
                }
            }

            on(Send) { request ->
                val first = proceed(request)
                if (first.response.status != HttpStatusCode.Unauthorized) {
                    return@on first
                }
                token = config.refreshToken()
                request.headers[HttpHeaders.Authorization] = "Bearer $token"
                proceed(request)
            }

            onResponse { response ->
                val requestId: String = response.call.attributes[RequestIdKey]
                val request = response.call.request
                config.journal.add(
                    "$requestId ${request.method.value} ${request.url.encodedPath} -> ${response.status.value}"
                )
            }

            transformResponseBody { response, content, requestedType ->
                if (requestedType.type != FxQuote::class || response.contentType()?.match(QUOTE_TYPE) != true) {
                    return@transformResponseBody null
                }
                val (pair: String, bid: String, ask: String) = content.toByteArray().decodeToString().split(" ")
                FxQuote(pair, bid, ask)
            }
        }

        /** A plugin the desk never installs, used to check plugin lookup. */
        val OtherPlugin: ClientPlugin<Unit> = createClientPlugin("Other") {}
    }
}
