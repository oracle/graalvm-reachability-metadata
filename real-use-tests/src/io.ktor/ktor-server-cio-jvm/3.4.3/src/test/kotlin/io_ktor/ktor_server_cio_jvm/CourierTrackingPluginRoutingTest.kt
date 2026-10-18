/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationPlugin
import io.ktor.server.application.RouteScopedPlugin
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.hooks.CallFailed
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.server.application.install
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.host
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.accept
import io.ktor.server.routing.get
import io.ktor.server.routing.header
import io.ktor.server.routing.host
import io.ktor.server.routing.param
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.AttributeKey
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

/**
 * The CIO engine's call interception and conditional routing: custom application and
 * route-scoped plugins built from `onCall`, `onCallReceive`, `onCallRespond`, `CallFailed`
 * and `ResponseSent` hooks with typed configuration and call attributes, and routes selected
 * by host, header, `Accept`, query parameter, regex, optional and tailcard path segments.
 *
 * A courier company runs one tracking API for several merchants, each on its own subdomain.
 * A tenant plugin resolves the merchant from the `Host` header and refuses unknown ones,
 * decodes plain-text parcel manifests on receive, stamps the tenant on every response, turns
 * domain failures into `503` answers, and records what was sent. A route-scoped plugin
 * guards the dispatch routes with an access scope. The test checks which route answered,
 * the decoded parameters, the stamped headers, the refusals and the sent-response log.
 */
class CourierTrackingPluginRoutingTest {

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0
    private val parcels: MutableMap<String, Parcel> = ConcurrentHashMap()
    private val dispatchHandled: MutableList<String> = CopyOnWriteArrayList()
    private val sent: LinkedBlockingQueue<String> = LinkedBlockingQueue()

    /** One parcel of a merchant's manifest, decoded from a `id;weight;destination` line. */
    data class Parcel(val tenant: String, val id: String, val grams: Int, val destination: String)

    /** Thrown when a parcel's carrier hub is offline. */
    class HubOfflineException(val hub: String) : RuntimeException("hub $hub offline")

    /** Configuration of [TenantResolver]. */
    class TenantConfig {
        var domain: String = ""
        var tenants: Set<String> = emptySet()
        var sentLog: LinkedBlockingQueue<String>? = null
    }

    /** Configuration of [RequireScope]. */
    class ScopeConfig {
        var scope: String = ""
    }

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(TenantResolver) {
                domain = DOMAIN
                tenants = setOf("acme", "globex")
                sentLog = sent
            }
            routing {
                host("status.$DOMAIN") {
                    get("/health") { call.respondText("courier api up", ContentType.Text.Plain) }
                }
                route("/parcels") {
                    post {
                        val parcel: Parcel = call.receive()
                        parcels[parcel.id] = parcel
                        call.respondText("accepted ${parcel.id} for ${parcel.tenant}", status = HttpStatusCode.Created)
                    }
                    get(Regex("""/(?<id>[A-Z]{2}\d{6})""")) {
                        val parcel: Parcel? = parcels[call.parameters["id"]!!]
                        if (parcel == null || parcel.tenant != call.tenant) {
                            call.respond(HttpStatusCode.NotFound)
                            return@get
                        }
                        if (parcel.destination == "OFFLINE") throw HubOfflineException("north")
                        call.respondText("${parcel.id} ${parcel.grams}g to ${parcel.destination}")
                    }
                    route("/{id}/label/{format?}") {
                        header("X-Api-Version", "2") {
                            get { call.respondText("label v2 ${call.parameters["id"]} ${call.parameters["format"] ?: "pdf"}") }
                        }
                        get { call.respondText("label v1 ${call.parameters["id"]} ${call.parameters["format"] ?: "pdf"}") }
                    }
                    accept(ContentType.Text.CSV) {
                        get { call.respondText(manifestCsv(call.tenant), ContentType.Text.CSV) }
                    }
                    param("destination") {
                        get { call.respondText(idsTo(call.tenant, call.parameters["destination"]!!)) }
                    }
                    get { call.respondText(idsTo(call.tenant, null)) }
                }
                route("/dispatch") {
                    install(RequireScope) { scope = "dispatch" }
                    get("/hubs/{path...}") {
                        val path: List<String> = call.parameters.getAll("path").orEmpty()
                        dispatchHandled.add(path.joinToString("/"))
                        call.respondText("hub route ${path.joinToString(" > ")}")
                    }
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
    fun merchantsTrackParcelsThroughPluginsAndSelectedRoutes() {
        HttpClient(ClientCIO).use { client: HttpClient ->
            runBlocking {
                val base = "http://127.0.0.1:$port"

                val health: HttpResponse = client.get("$base/health") { header(HttpHeaders.Host, "status.$DOMAIN") }
                assertEquals(HttpStatusCode.OK, health.status)
                assertEquals("courier api up", health.bodyAsText())
                assertNull(health.headers[TENANT_HEADER])

                val unknown: HttpResponse = client.get("$base/parcels") { header(HttpHeaders.Host, "initech.$DOMAIN") }
                assertEquals(HttpStatusCode.NotFound, unknown.status)
                assertEquals("unknown merchant initech", unknown.bodyAsText())

                val manifest: List<String> = listOf("AB123456;1200;Lyon", "AB654321;300;Oslo", "CD000001;50;Lyon")
                for (line: String in manifest) {
                    val created: HttpResponse = client.post("$base/parcels") {
                        header(HttpHeaders.Host, "acme.$DOMAIN")
                        contentType(ContentType.Text.Plain)
                        setBody(line)
                    }
                    assertEquals(HttpStatusCode.Created, created.status)
                    assertEquals("accepted ${line.substringBefore(';')} for acme", created.bodyAsText())
                    assertEquals("acme", created.headers[TENANT_HEADER])
                }
                val globex: HttpResponse = client.post("$base/parcels") {
                    header(HttpHeaders.Host, "globex.$DOMAIN")
                    contentType(ContentType.Text.Plain)
                    setBody("GX777777;900;OFFLINE")
                }
                assertEquals(HttpStatusCode.Created, globex.status)
                assertEquals("globex", globex.headers[TENANT_HEADER])

                val tracked: HttpResponse = client.get("$base/parcels/AB123456") { header(HttpHeaders.Host, "acme.$DOMAIN") }
                assertEquals(HttpStatusCode.OK, tracked.status)
                assertEquals("AB123456 1200g to Lyon", tracked.bodyAsText())

                val foreign: HttpResponse = client.get("$base/parcels/GX777777") { header(HttpHeaders.Host, "acme.$DOMAIN") }
                assertEquals(HttpStatusCode.NotFound, foreign.status)
                assertEquals("acme", foreign.headers[TENANT_HEADER])

                val offline: HttpResponse = client.get("$base/parcels/GX777777") { header(HttpHeaders.Host, "globex.$DOMAIN") }
                assertEquals(HttpStatusCode.ServiceUnavailable, offline.status)
                assertEquals("hub north offline, retry later", offline.bodyAsText())
                assertEquals("globex", offline.headers[TENANT_HEADER])

                val labelV1: HttpResponse = client.get("$base/parcels/AB654321/label") { header(HttpHeaders.Host, "acme.$DOMAIN") }
                assertEquals("label v1 AB654321 pdf", labelV1.bodyAsText())
                val labelV2: HttpResponse = client.get("$base/parcels/AB654321/label/zpl") {
                    header(HttpHeaders.Host, "acme.$DOMAIN")
                    header("X-Api-Version", "2")
                }
                assertEquals("label v2 AB654321 zpl", labelV2.bodyAsText())

                val csv: HttpResponse = client.get("$base/parcels") {
                    header(HttpHeaders.Host, "acme.$DOMAIN")
                    header(HttpHeaders.Accept, "text/csv")
                }
                assertEquals(ContentType.Text.CSV, csv.contentType()?.withoutParameters())
                assertEquals("id,grams,destination\nAB123456,1200,Lyon\nAB654321,300,Oslo\nCD000001,50,Lyon", csv.bodyAsText())

                val toLyon: HttpResponse = client.get("$base/parcels?destination=Lyon") {
                    header(HttpHeaders.Host, "acme.$DOMAIN")
                    header(HttpHeaders.Accept, "text/plain")
                }
                assertEquals("AB123456,CD000001", toLyon.bodyAsText())
                val all: HttpResponse = client.get("$base/parcels") {
                    header(HttpHeaders.Host, "acme.$DOMAIN")
                    header(HttpHeaders.Accept, "text/plain")
                }
                assertEquals("AB123456,AB654321,CD000001", all.bodyAsText())

                val badId: HttpResponse = client.get("$base/parcels/ab12") { header(HttpHeaders.Host, "acme.$DOMAIN") }
                assertEquals(HttpStatusCode.NotFound, badId.status)

                val noScope: HttpResponse = client.get("$base/dispatch/hubs/eu/lyon") { header(HttpHeaders.Host, "acme.$DOMAIN") }
                assertEquals(HttpStatusCode.Forbidden, noScope.status)
                assertEquals("missing scope dispatch", noScope.bodyAsText())
                assertEquals("acme", noScope.headers[TENANT_HEADER])
                val dispatched: HttpResponse = client.get("$base/dispatch/hubs/eu/fr/lyon") {
                    header(HttpHeaders.Host, "acme.$DOMAIN")
                    header(SCOPE_HEADER, "track, dispatch")
                }
                assertEquals(HttpStatusCode.OK, dispatched.status)
                assertEquals("hub route eu > fr > lyon", dispatched.bodyAsText())
            }
        }

        assertEquals(listOf("eu/fr/lyon"), dispatchHandled.toList())
        assertEquals(Parcel("acme", "CD000001", 50, "Lyon"), parcels["CD000001"])
        val expectedLog: List<String> = listOf(
            "- /health 200",
            "- /parcels 404",
            "acme /parcels 201",
            "acme /parcels 201",
            "acme /parcels 201",
            "globex /parcels 201",
            "acme /parcels/AB123456 200",
            "acme /parcels/GX777777 404",
            "globex /parcels/GX777777 503",
            "acme /parcels/AB654321/label 200",
            "acme /parcels/AB654321/label/zpl 200",
            "acme /parcels 200",
            "acme /parcels?destination=Lyon 200",
            "acme /parcels 200",
            "acme /parcels/ab12 404",
            "acme /dispatch/hubs/eu/lyon 403",
            "acme /dispatch/hubs/eu/fr/lyon 200",
        )
        val log: List<String> = expectedLog.map { sent.poll(10, TimeUnit.SECONDS) ?: "missing" }
        assertEquals(expectedLog.sorted(), log.sorted())
    }

    private fun manifestCsv(tenant: String): String =
        (listOf("id,grams,destination") + parcelsOf(tenant).map { "${it.id},${it.grams},${it.destination}" })
            .joinToString("\n")

    private fun idsTo(tenant: String, destination: String?): String =
        parcelsOf(tenant).filter { destination == null || it.destination == destination }.joinToString(",") { it.id }

    private fun parcelsOf(tenant: String): List<Parcel> = parcels.values.filter { it.tenant == tenant }.sortedBy { it.id }

    private companion object {
        const val DOMAIN: String = "parcels.example.com"
        const val TENANT_HEADER: String = "X-Tenant"
        const val SCOPE_HEADER: String = "X-Scope"
        val TenantKey: AttributeKey<String> = AttributeKey("courier.tenant")

        val ApplicationCall.tenant: String
            get() = attributes[TenantKey]

        val TenantResolver: ApplicationPlugin<TenantConfig> = createApplicationPlugin("TenantResolver", ::TenantConfig) {
            val suffix = ".${pluginConfig.domain}"
            val tenants: Set<String> = pluginConfig.tenants
            val sentLog: LinkedBlockingQueue<String> = pluginConfig.sentLog!!

            onCall { call ->
                val subdomain: String = call.request.host().removeSuffix(suffix)
                when {
                    subdomain == "status" -> Unit
                    subdomain in tenants -> call.attributes.put(TenantKey, subdomain)
                    else -> call.respondText("unknown merchant $subdomain", status = HttpStatusCode.NotFound)
                }
            }
            onCallReceive { call ->
                transformBody { body: ByteReadChannel ->
                    if (requestedType?.type != Parcel::class) return@transformBody body
                    val fields: List<String> = body.toByteArray().decodeToString().trim().split(';')
                    Parcel(call.tenant, fields[0], fields[1].toInt(), fields[2])
                }
            }
            onCallRespond { call, _ ->
                call.attributes.getOrNull(TenantKey)?.let { call.response.headers.append(TENANT_HEADER, it) }
            }
            on(CallFailed) { call, cause ->
                if (cause is HubOfflineException) {
                    call.respondText("hub ${cause.hub} offline, retry later", status = HttpStatusCode.ServiceUnavailable)
                }
            }
            on(ResponseSent) { call ->
                val who: String = call.attributes.getOrNull(TenantKey) ?: "-"
                sentLog.add("$who ${call.request.uri} ${call.response.status()?.value}")
            }
        }

        val RequireScope: RouteScopedPlugin<ScopeConfig> = createRouteScopedPlugin("RequireScope", ::ScopeConfig) {
            val scope: String = pluginConfig.scope
            onCall { call ->
                val granted: List<String> = call.request.headers[SCOPE_HEADER].orEmpty().split(',').map { it.trim() }
                if (scope !in granted) call.respondText("missing scope $scope", status = HttpStatusCode.Forbidden)
            }
        }
    }
}
