/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_content_negotiation_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE as ClientSSE
import io.ktor.client.plugins.sse.deserialize
import io.ktor.client.plugins.sse.sse
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
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.send
import io.ktor.server.sse.sse
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Server-Sent Event streams served by an application that negotiates JSON bodies on its other routes.
 *
 * A parcel-tracking service accepts depot scans as negotiated JSON request bodies, answers
 * parcel snapshots as negotiated JSON responses, and replays a parcel's scans on a
 * `text/event-stream` feed whose event data is encoded with the same `Json` instance the
 * converter uses. The test checks that the plugin decodes and encodes the JSON routes, that it
 * leaves the event stream untouched even when the client also accepts `application/json`, that
 * a Ktor SSE client decodes the replayed scans back into the posted values, and that a client
 * accepting only event streams on the snapshot route still receives the JSON snapshot, because
 * the plugin falls back to its registered converter when no `Accept` item matches.
 */
class ParcelTrackingEventStreamTest {

    @Serializable
    data class Scan(val depot: String, val status: String, val weightGrams: Int)

    @Serializable
    data class ScanReceipt(val parcel: String, val sequence: Int)

    @Serializable
    data class ParcelSnapshot(val parcel: String, val scans: List<Scan>)

    private val trackingJson: Json = Json { encodeDefaults = true }
    private val scansByParcel: ConcurrentHashMap<String, CopyOnWriteArrayList<Scan>> = ConcurrentHashMap()

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private lateinit var client: HttpClient
    private var port: Int = 0

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(ContentNegotiation) {
                json(trackingJson)
            }
            install(SSE)
            routing {
                post("/parcels/{parcel}/scans") {
                    val parcel: String = call.parameters["parcel"]!!
                    val scan: Scan = call.receive()
                    val scans: CopyOnWriteArrayList<Scan> = scansByParcel.computeIfAbsent(parcel) { CopyOnWriteArrayList() }
                    scans.add(scan)
                    call.respond(HttpStatusCode.Created, ScanReceipt(parcel, scans.size))
                }
                get("/parcels/{parcel}") {
                    val parcel: String = call.parameters["parcel"]!!
                    call.respond(ParcelSnapshot(parcel, scansByParcel[parcel].orEmpty().toList()))
                }
                sse(
                    "/parcels/{parcel}/feed",
                    serialize = { typeInfo, value -> trackingJson.encodeToString(serializer(typeInfo.kotlinType!!), value) },
                ) {
                    val parcel: String = call.parameters["parcel"]!!
                    scansByParcel[parcel].orEmpty().forEachIndexed { index: Int, scan: Scan ->
                        send(scan, event = "scan", id = "$parcel-${index + 1}")
                    }
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
        client = HttpClient(CIO) {
            install(ClientContentNegotiation) {
                json(trackingJson)
            }
            install(ClientSSE)
        }
    }

    @AfterEach
    fun stop() {
        client.close()
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    private suspend fun postScan(parcel: String, scan: Scan): ScanReceipt {
        val response: HttpResponse = client.post("http://127.0.0.1:$port/parcels/$parcel/scans") {
            contentType(ContentType.Application.Json)
            setBody(scan)
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.headers[HttpHeaders.ContentType]!!.startsWith("application/json"))
        return response.body()
    }

    @Test
    fun postedScansAreNegotiatedIntoTheSnapshot() = runBlocking {
        assertEquals(ScanReceipt("PX-1", 1), postScan("PX-1", Scan("Rotterdam", "received", 1200)))
        assertEquals(ScanReceipt("PX-1", 2), postScan("PX-1", Scan("Utrecht", "sorted", 1200)))

        val snapshot: HttpResponse = client.get("http://127.0.0.1:$port/parcels/PX-1") {
            header(HttpHeaders.Accept, "application/json")
        }
        assertEquals(HttpStatusCode.OK, snapshot.status)
        assertTrue(snapshot.headers[HttpHeaders.ContentType]!!.startsWith("application/json"))
        assertEquals(
            ParcelSnapshot("PX-1", listOf(Scan("Rotterdam", "received", 1200), Scan("Utrecht", "sorted", 1200))),
            snapshot.body<ParcelSnapshot>(),
        )
    }

    @Test
    fun eventStreamBypassesTheJsonConverter() = runBlocking {
        postScan("PX-2", Scan("Antwerp", "received", 800))
        postScan("PX-2", Scan("Ghent", "out-for-delivery", 800))

        val feed: HttpResponse = client.get("http://127.0.0.1:$port/parcels/PX-2/feed") {
            header(HttpHeaders.Accept, "application/json, text/event-stream")
        }
        assertEquals(HttpStatusCode.OK, feed.status)
        assertTrue(feed.headers[HttpHeaders.ContentType]!!.startsWith("text/event-stream"))

        val text: String = feed.bodyAsText()
        assertFalse(text.trimStart().startsWith("{") || text.trimStart().startsWith("["), text)
        val events: List<Map<String, String>> = text.split("\r\n\r\n", "\n\n")
            .filter { it.isNotBlank() }
            .map { block: String ->
                block.lines().filter { it.contains(':') }.associate { line: String ->
                    line.substringBefore(':') to line.substringAfter(':').trimStart()
                }
            }
        assertEquals(listOf("PX-2-1", "PX-2-2"), events.map { it["id"] })
        assertEquals(listOf("scan", "scan"), events.map { it["event"] })
        assertEquals(
            listOf(Scan("Antwerp", "received", 800), Scan("Ghent", "out-for-delivery", 800)),
            events.map { trackingJson.decodeFromString<Scan>(it["data"]!!) },
        )
    }

    @Test
    fun sseClientDecodesReplayedScans() = runBlocking {
        val posted: List<Scan> = listOf(
            Scan("Lyon", "received", 2500),
            Scan("Dijon", "sorted", 2500),
            Scan("Paris", "delivered", 2500),
        )
        posted.forEach { postScan("PX-3", it) }

        val received: MutableList<Scan> = mutableListOf()
        val ids: MutableList<String?> = mutableListOf()
        client.sse(
            "http://127.0.0.1:$port/parcels/PX-3/feed",
            deserialize = { typeInfo, data -> trackingJson.decodeFromString(serializer(typeInfo.kotlinType!!), data) },
        ) {
            incoming.collect { event ->
                ids.add(event.id)
                received.add(deserialize<Scan>(event.data)!!)
            }
        }

        assertEquals(posted, received)
        assertEquals(listOf("PX-3-1", "PX-3-2", "PX-3-3"), ids)
    }

    @Test
    fun eventStreamOnlyClientFallsBackToJsonSnapshot() = runBlocking {
        postScan("PX-4", Scan("Bremen", "received", 400))

        val response: HttpResponse = client.get("http://127.0.0.1:$port/parcels/PX-4") {
            header(HttpHeaders.Accept, "text/event-stream")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers[HttpHeaders.ContentType]!!.startsWith("application/json"))
        assertEquals(
            ParcelSnapshot("PX-4", listOf(Scan("Bremen", "received", 400))),
            trackingJson.decodeFromString<ParcelSnapshot>(response.bodyAsText()),
        )
    }
}
