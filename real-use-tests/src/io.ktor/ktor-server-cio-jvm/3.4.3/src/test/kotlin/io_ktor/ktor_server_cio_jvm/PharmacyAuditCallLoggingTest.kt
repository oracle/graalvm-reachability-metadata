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
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticFiles
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.processingTimeMillis
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.uri
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
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

/**
 * The CIO engine's request logging with CallLogging: one access-log line per completed call
 * in a custom format on a named logger, request filters, skipped static content, and
 * processing time measured on an injected clock.
 *
 * A pharmacy chain runs its dispensing API on an embedded CIO server and keeps an audit
 * trail of every prescription call, naming the pharmacist and store that sent it. Each
 * dispense spends time on the pharmacy clock checking drug interactions. Health probes and
 * the static patient leaflets stay out of the trail. The test checks the receipts, the
 * served leaflet, and the exact audit lines with their statuses, methods, URIs, headers and
 * the processing time CallLogging measured between the start and the end of each call.
 */
class PharmacyAuditCallLoggingTest {

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0
    private val pharmacyClock: AtomicLong = AtomicLong(1_760_000_000_000L)
    private val auditTrail: LinkedBlockingQueue<String> = LinkedBlockingQueue()
    private val refillsLeft: MutableMap<String, Int> = ConcurrentHashMap(mapOf("rx-1001" to 2, "rx-1002" to 0))

    @TempDir
    lateinit var leafletDirectory: Path

    @BeforeEach
    fun start() {
        Files.writeString(leafletDirectory.resolve("amoxicillin.txt"), AMOXICILLIN_LEAFLET)
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(CallLogging) {
                logger = LoggerFactory.getLogger("pharmacy.audit")
                level = Level.INFO
                clock { pharmacyClock.get() }
                filter { call -> call.request.path().startsWith("/rx/") }
                disableForStaticContent()
                disableDefaultColors()
                format { call ->
                    val line: String = listOf(
                        call.response.status().toString(),
                        call.request.httpMethod.value,
                        call.request.uri,
                        "pharmacist=${call.request.headers[PHARMACIST_HEADER] ?: "-"}",
                        "store=${call.request.headers[STORE_HEADER] ?: "-"}",
                        "took=${call.processingTimeMillis { pharmacyClock.get() }}ms",
                    ).joinToString(" ")
                    auditTrail.add(line)
                    line
                }
            }
            routing {
                get("/health") {
                    call.respondText("up", ContentType.Text.Plain)
                }
                staticFiles("/rx/leaflets", leafletDirectory.toFile())
                post("/rx/{id}/dispense") {
                    val id: String = call.parameters["id"]!!
                    val refills: Int? = refillsLeft[id]
                    if (refills == null || refills == 0) {
                        call.respondText("$id has no refills left", ContentType.Text.Plain, HttpStatusCode.Conflict)
                        return@post
                    }
                    pharmacyClock.addAndGet(INTERACTION_CHECK_MILLIS)
                    refillsLeft[id] = refills - 1
                    val pharmacist: String? = call.request.headers[PHARMACIST_HEADER]
                    val store: String? = call.request.headers[STORE_HEADER]
                    call.respondText(
                        "$id dispensed by $pharmacist at $store, ${refills - 1} refills left",
                        ContentType.Text.Plain,
                    )
                }
                get("/rx/{id}") {
                    val id: String = call.parameters["id"]!!
                    val refills: Int? = refillsLeft[id]
                    if (refills == null) {
                        call.respondText("no prescription $id", ContentType.Text.Plain, HttpStatusCode.NotFound)
                    } else {
                        call.respondText("$id: $refills refills left", ContentType.Text.Plain)
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
    fun prescriptionCallsLeaveAnAuditTrail() {
        HttpClient(ClientCIO).use { client: HttpClient ->
            runBlocking {
                val base = "http://127.0.0.1:$port"

                val health: HttpResponse = client.get("$base/health")
                assertEquals(HttpStatusCode.OK, health.status)
                assertEquals("up", health.bodyAsText())

                val dispensed: HttpResponse = client.post("$base/rx/rx-1001/dispense") {
                    header(PHARMACIST_HEADER, "p.okafor")
                    header(STORE_HEADER, "store-17")
                }
                assertEquals(HttpStatusCode.OK, dispensed.status)
                assertEquals("rx-1001 dispensed by p.okafor at store-17, 1 refills left", dispensed.bodyAsText())
                assertEquals(
                    "200 OK POST /rx/rx-1001/dispense pharmacist=p.okafor store=store-17 took=250ms",
                    nextAuditLine(),
                )

                val leaflet: HttpResponse = client.get("$base/rx/leaflets/amoxicillin.txt") {
                    header(PHARMACIST_HEADER, "p.okafor")
                }
                assertEquals(HttpStatusCode.OK, leaflet.status)
                assertEquals(AMOXICILLIN_LEAFLET, leaflet.bodyAsText())

                val unknown: HttpResponse = client.get("$base/rx/rx-9999?store=store-17")
                assertEquals(HttpStatusCode.NotFound, unknown.status)
                assertEquals("no prescription rx-9999", unknown.bodyAsText())
                assertEquals(
                    "404 Not Found GET /rx/rx-9999?store=store-17 pharmacist=- store=- took=0ms",
                    nextAuditLine(),
                )

                val exhausted: HttpResponse = client.post("$base/rx/rx-1002/dispense") {
                    header(PHARMACIST_HEADER, "j.lindqvist")
                    header(STORE_HEADER, "store-04")
                }
                assertEquals(HttpStatusCode.Conflict, exhausted.status)
                assertEquals("rx-1002 has no refills left", exhausted.bodyAsText())
                assertEquals(
                    "409 Conflict POST /rx/rx-1002/dispense pharmacist=j.lindqvist store=store-04 took=0ms",
                    nextAuditLine(),
                )

                val secondFill: HttpResponse = client.post("$base/rx/rx-1001/dispense") {
                    header(PHARMACIST_HEADER, "j.lindqvist")
                    header(STORE_HEADER, "store-04")
                }
                assertEquals("rx-1001 dispensed by j.lindqvist at store-04, 0 refills left", secondFill.bodyAsText())
                assertEquals(
                    "200 OK POST /rx/rx-1001/dispense pharmacist=j.lindqvist store=store-04 took=250ms",
                    nextAuditLine(),
                )

                val status: HttpResponse = client.get("$base/rx/rx-1001") {
                    header(STORE_HEADER, "store-04")
                }
                assertEquals("rx-1001: 0 refills left", status.bodyAsText())
                assertEquals(
                    "200 OK GET /rx/rx-1001 pharmacist=- store=store-04 took=0ms",
                    nextAuditLine(),
                )
            }
        }
        assertTrue(auditTrail.isEmpty(), "health probes and leaflets stay out of the trail: $auditTrail")
        assertEquals(1_760_000_000_500L, pharmacyClock.get())
    }

    /** Waits for the line CallLogging writes once the response has been sent. */
    private fun nextAuditLine(): String? = auditTrail.poll(10, TimeUnit.SECONDS)

    private companion object {
        const val PHARMACIST_HEADER: String = "X-Pharmacist"
        const val STORE_HEADER: String = "X-Store"
        const val INTERACTION_CHECK_MILLIS: Long = 250L
        const val AMOXICILLIN_LEAFLET: String = "Amoxicillin 500 mg: take one capsule every 8 hours with water.\n"
    }
}
