/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.ServerReady
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.cio.EngineMain
import io.ktor.server.config.property
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import io.ktor.client.engine.cio.CIO as ClientCIO

/**
 * Configuration-driven CIO engine startup: `EngineMain` booting a server from a HOCON file with
 * command-line overrides, loading the application module by its configured name, decoding typed
 * configuration, serving under a configured root path, and raising lifecycle events.
 *
 * A harbor museum deploys its ticket office the way a packaged application does: an
 * `application.conf` names the module, the deployment host, port, root path and CIO idle
 * timeout, and carries the museum's own settings, while the launcher overrides the currency on
 * the command line. The test checks the engine settings taken from the file, the quotes and
 * exhibition data the module derives from configuration, that nothing is served outside the
 * root path, and that start and stop raise the application lifecycle events in order.
 */
class MuseumTicketingConfigBootTest {

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0
    private var stopped: Boolean = false
    private val lifecycle: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val serverReady: CountDownLatch = CountDownLatch(1)

    @BeforeEach
    fun start(@TempDir configDirectory: Path) {
        val configFile: Path = configDirectory.resolve("application.conf")
        Files.writeString(configFile, applicationConf())
        server = EngineMain.createServer(arrayOf("-config=$configFile", "-P:museum.currency=CHF"))
        server.monitor.subscribe(ApplicationStarted) { lifecycle.add("started") }
        server.monitor.subscribe(ServerReady) {
            lifecycle.add("ready")
            serverReady.countDown()
        }
        server.monitor.subscribe(ApplicationStopping) { lifecycle.add("stopping") }
        server.monitor.subscribe(ApplicationStopped) { lifecycle.add("stopped") }
        server.start(wait = false)
        assertTrue(serverReady.await(10, TimeUnit.SECONDS), "server never reported ready")
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stop() {
        if (!stopped) {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
        }
    }

    /** The deployment section of the file configures the CIO engine and the application. */
    @Test
    fun engineTakesDeploymentSettingsFromTheConfigFile() {
        val connector = runBlocking { server.engine.resolvedConnectors().single() }
        assertEquals("127.0.0.1", connector.host)
        assertTrue(connector.port > 0)
        assertEquals(45, server.engineConfig.connectionIdleTimeoutSeconds)
        assertEquals("/tickets", server.application.rootPath)
        assertEquals(false, server.application.developmentMode)
        assertEquals(listOf(MODULE_REFERENCE), server.environment.config.property("ktor.application.modules").getList())
    }

    /** The module reads plain, substituted, list and typed values plus the command-line override. */
    @Test
    fun moduleServesSettingsDecodedFromConfiguration() {
        runBlocking {
            HttpClient(ClientCIO).use { client: HttpClient ->
                val info: HttpResponse = client.get("http://127.0.0.1:$port/tickets/info")
                assertEquals(HttpStatusCode.OK, info.status)
                val museum: MuseumInfo = Json.decodeFromString(info.bodyAsText())
                assertEquals("Harbor Museum", museum.name)
                assertEquals("Welcome to Harbor Museum", museum.greeting)
                assertEquals("CHF", museum.currency)
                assertEquals(listOf("TUE", "WED", "THU", "FRI", "SAT"), museum.openDays)
                assertEquals("none", museum.renovationNotice)
                assertEquals(listOf("tides", "lenses"), museum.exhibitions.map { exhibition -> exhibition.id })

                val family: Quote = Json.decodeFromString(
                    client.get("http://127.0.0.1:$port/tickets/quote?adults=2&children=3").bodyAsText(),
                )
                assertEquals(Quote(adults = 2, children = 3, total = 63, currency = "CHF", discounted = false), family)

                val school: Quote = Json.decodeFromString(
                    client.get("http://127.0.0.1:$port/tickets/quote?adults=2&children=10").bodyAsText(),
                )
                assertEquals(Quote(adults = 2, children = 10, total = 107, currency = "CHF", discounted = true), school)

                val lenses: HttpResponse = client.get("http://127.0.0.1:$port/tickets/exhibitions/lenses")
                assertEquals(HttpStatusCode.OK, lenses.status)
                assertEquals("Lighthouse Lenses: 25 places", lenses.bodyAsText())

                val unknown: HttpResponse = client.get("http://127.0.0.1:$port/tickets/exhibitions/whales")
                assertEquals(HttpStatusCode.NotFound, unknown.status)

                val outsideRoot: HttpResponse = client.get("http://127.0.0.1:$port/info")
                assertEquals(HttpStatusCode.NotFound, outsideRoot.status)
            }
        }
    }

    /** Starting and stopping the server raises the lifecycle events an application hooks into. */
    @Test
    fun startAndStopRaiseLifecycleEventsInOrder() {
        assertEquals(listOf("started", "ready"), lifecycle.toList())
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
        stopped = true
        assertEquals(listOf("started", "ready", "stopping", "stopped"), lifecycle.toList())
    }

    /** Museum pricing, decoded from the `museum.pricing` section as a whole. */
    @Serializable
    data class Pricing(val adult: Int, val child: Int, val groupSize: Int, val groupDiscountPercent: Int)

    @Serializable
    data class Exhibition(val id: String, val title: String, val capacity: Int)

    @Serializable
    data class MuseumInfo(
        val name: String,
        val greeting: String,
        val currency: String,
        val openDays: List<String>,
        val renovationNotice: String,
        val exhibitions: List<Exhibition>,
    )

    @Serializable
    data class Quote(val adults: Int, val children: Int, val total: Int, val currency: String, val discounted: Boolean)

    /** The application module `ktor.application.modules` names; the engine loads it by that name. */
    object TicketOffice {

        fun Application.ticketing() {
            val museum = environment.config.config("museum")
            val name: String = property("museum.name")
            val pricing: Pricing = property("museum.pricing")
            val currency: String = museum.property("currency").getString()
            val exhibitions: List<Exhibition> = museum.configList("exhibitions").map { entry ->
                Exhibition(
                    id = entry.property("id").getString(),
                    title = entry.property("title").getString(),
                    capacity = entry.property("capacity").getString().toInt(),
                )
            }
            val info = MuseumInfo(
                name = name,
                greeting = museum.property("greeting").getString(),
                currency = currency,
                openDays = museum.property("openDays").getList(),
                renovationNotice = museum.propertyOrNull("renovationNotice")?.getString() ?: "none",
                exhibitions = exhibitions,
            )
            log.info("Ticket office for {} configured with {} exhibitions", name, exhibitions.size)

            install(ContentNegotiation) {
                json()
            }
            routing {
                get("/info") {
                    call.respond(info)
                }
                get("/quote") {
                    val adults: Int = call.request.queryParameters["adults"]?.toInt() ?: 0
                    val children: Int = call.request.queryParameters["children"]?.toInt() ?: 0
                    val fullPrice: Int = adults * pricing.adult + children * pricing.child
                    val discounted: Boolean = adults + children >= pricing.groupSize
                    val total: Int = if (discounted) {
                        fullPrice * (100 - pricing.groupDiscountPercent) / 100
                    } else {
                        fullPrice
                    }
                    call.respond(Quote(adults, children, total, currency, discounted))
                }
                get("/exhibitions/{id}") {
                    val exhibition: Exhibition? = exhibitions.firstOrNull { it.id == call.parameters["id"] }
                    if (exhibition == null) {
                        call.respondText("No such exhibition", status = HttpStatusCode.NotFound)
                    } else {
                        call.respondText("${exhibition.title}: ${exhibition.capacity} places")
                    }
                }
            }
        }
    }

    private companion object {

        val MODULE_REFERENCE: String = "${TicketOffice::class.java.name}.ticketing"

        fun applicationConf(): String = """
            ktor {
              deployment {
                host = "127.0.0.1"
                port = 0
                rootPath = "/tickets"
                connectionIdleTimeoutSeconds = 45
              }
              application {
                modules = [ "$MODULE_REFERENCE" ]
              }
            }
            museum {
              name = "Harbor Museum"
              greeting = "Welcome to "${'$'}{museum.name}
              currency = "EUR"
              openDays = [ "TUE", "WED", "THU", "FRI", "SAT" ]
              pricing {
                adult = 18
                child = 9
                groupSize = 10
                groupDiscountPercent = 15
              }
              exhibitions = [
                { id = "tides", title = "Tides of the North Sea", capacity = 40 }
                { id = "lenses", title = "Lighthouse Lenses", capacity = 25 }
              ]
            }
        """.trimIndent()
    }
}
