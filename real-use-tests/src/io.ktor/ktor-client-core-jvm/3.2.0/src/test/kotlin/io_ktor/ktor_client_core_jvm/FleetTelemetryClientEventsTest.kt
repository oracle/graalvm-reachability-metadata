/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_core_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.observer.ResponseObserver
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.utils.HttpRequestCreated
import io.ktor.client.utils.HttpRequestIsReadyForSending
import io.ktor.client.utils.HttpResponseReceiveFail
import io.ktor.client.utils.HttpResponseReceiveFailed
import io.ktor.client.utils.HttpResponseReceived
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Client lifecycle events raised on `HttpClient.monitor` and the `ResponseObserver` plugin.
 *
 * A fleet telemetry dashboard talks to a shipment service. It stamps a trace id on every
 * request when `HttpRequestCreated` fires, logs the final outgoing content when
 * `HttpRequestIsReadyForSending` fires, counts statuses on `HttpResponseReceived`, and
 * records decoding failures on `HttpResponseReceiveFailed`. A filtered `ResponseObserver`
 * keeps an audit trail of shipment responses, reading each body from its own copy while the
 * caller still decodes the original. The test checks the trace ids the server saw, the event
 * journal, the decoded shipments, and the audit trail.
 */
class FleetTelemetryClientEventsTest {

    /** A shipment as the service reports it. */
    @Serializable
    data class Shipment(val id: String, val truck: String, val status: String)

    /** A checkpoint scan the dashboard reports for a shipment. */
    @Serializable
    data class Checkpoint(val shipmentId: String, val depot: String)

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0
    private val seenTraceIds: MutableList<String> = CopyOnWriteArrayList()

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/health") {
                    seenTraceIds.add(call.request.headers[TRACE_HEADER] ?: "none")
                    call.respondText("up")
                }
                get("/shipments/{id}") {
                    val trace: String = call.request.headers[TRACE_HEADER] ?: "none"
                    seenTraceIds.add(trace)
                    call.response.header(TRACE_HEADER, trace)
                    val id: String = call.parameters["id"] ?: "unknown"
                    if (id == "SHP-9") {
                        call.respondText("{\"id\": \"SHP-9\", \"truck\": ", ContentType.Application.Json)
                        return@get
                    }
                    call.respondText(
                        "{\"id\":\"$id\",\"truck\":\"TRK-12\",\"status\":\"in-transit\"}",
                        ContentType.Application.Json,
                    )
                }
                post("/checkpoints") {
                    seenTraceIds.add(call.request.headers[TRACE_HEADER] ?: "none")
                    val body: String = call.receiveText()
                    call.respondText(
                        "{\"id\":\"SHP-1\",\"truck\":\"TRK-12\",\"status\":\"scanned:${body.length}\"}",
                        ContentType.Application.Json,
                        HttpStatusCode.Created,
                    )
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
    fun dashboardTracesJournalsAndAuditsShipmentTraffic() {
        val base: String = "http://127.0.0.1:$port"
        val audit: MutableList<String> = CopyOnWriteArrayList()
        val audited: CountDownLatch = CountDownLatch(3)
        val client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json()
            }
            install(ResponseObserver) {
                onResponse { response: HttpResponse ->
                    val text: String = response.bodyAsText()
                    audit.add("${response.status.value} ${response.call.request.url.encodedPath} $text")
                    audited.countDown()
                }
                filter { call: HttpClientCall -> call.request.url.encodedPath.startsWith("/shipments") }
            }
        }
        val traceCounter: AtomicInteger = AtomicInteger()
        val journal: MutableList<String> = CopyOnWriteArrayList()
        val statuses: MutableList<Int> = CopyOnWriteArrayList()
        val failures: MutableList<HttpResponseReceiveFail> = CopyOnWriteArrayList()
        client.monitor.subscribe(HttpRequestCreated) { builder: HttpRequestBuilder ->
            builder.headers.append(TRACE_HEADER, "trace-${traceCounter.incrementAndGet()}")
        }
        client.monitor.subscribe(HttpRequestIsReadyForSending) { builder: HttpRequestBuilder ->
            val content: OutgoingContent = builder.body as OutgoingContent
            journal.add("${builder.method.value} ${builder.url.encodedPath} ${content.contentType ?: "no-body"}")
        }
        client.monitor.subscribe(HttpResponseReceived) { response: HttpResponse ->
            statuses.add(response.status.value)
        }
        client.monitor.subscribe(HttpResponseReceiveFailed) { fail: HttpResponseReceiveFail ->
            failures.add(fail)
        }

        client.use {
            runBlocking {
                assertEquals("up", client.get("$base/health").bodyAsText())

                val shipment: Shipment = client.get("$base/shipments/SHP-1").body()
                assertEquals(Shipment("SHP-1", "TRK-12", "in-transit"), shipment)

                val scanned: HttpResponse = client.post("$base/checkpoints") {
                    contentType(ContentType.Application.Json)
                    setBody(Checkpoint("SHP-1", "Rotterdam"))
                }
                assertEquals(HttpStatusCode.Created, scanned.status)
                val scannedShipment: Shipment = scanned.body()
                assertEquals("scanned:42", scannedShipment.status)

                val second: HttpResponse = client.get("$base/shipments/SHP-2")
                assertEquals("trace-4", second.headers[TRACE_HEADER])
                assertEquals("TRK-12", second.body<Shipment>().truck)

                val broken: HttpResponse = client.get("$base/shipments/SHP-9")
                assertThrows(Exception::class.java) {
                    runBlocking { broken.body<Shipment>() }
                }
                assertEquals(1, failures.size)
                assertSame(broken, failures[0].response)
                assertEquals("/shipments/SHP-9", failures[0].response.call.request.url.encodedPath)
            }
            assertTrue(audited.await(10, TimeUnit.SECONDS), "observer did not see every shipment response")
        }

        assertEquals(listOf("trace-1", "trace-2", "trace-3", "trace-4", "trace-5"), seenTraceIds)
        assertEquals(
            listOf(
                "GET /health no-body",
                "GET /shipments/SHP-1 no-body",
                "POST /checkpoints application/json",
                "GET /shipments/SHP-2 no-body",
                "GET /shipments/SHP-9 no-body",
            ),
            journal,
        )
        assertEquals(listOf(200, 200, 201, 200, 200), statuses)
        assertEquals(
            setOf(
                "200 /shipments/SHP-1 {\"id\":\"SHP-1\",\"truck\":\"TRK-12\",\"status\":\"in-transit\"}",
                "200 /shipments/SHP-2 {\"id\":\"SHP-2\",\"truck\":\"TRK-12\",\"status\":\"in-transit\"}",
                "200 /shipments/SHP-9 {\"id\": \"SHP-9\", \"truck\": ",
            ),
            audit.toSet(),
        )
        assertEquals(3, audit.size)
    }

    private companion object {
        const val TRACE_HEADER: String = "X-Trace-Id"
    }
}
