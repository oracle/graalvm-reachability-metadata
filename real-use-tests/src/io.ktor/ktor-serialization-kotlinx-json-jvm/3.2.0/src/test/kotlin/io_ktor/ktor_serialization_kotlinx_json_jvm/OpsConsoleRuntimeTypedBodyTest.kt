/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_serialization_kotlinx_json_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.DefaultJson
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/**
 * Infers JSON serializers at runtime for bodies whose static type is `Any`, as the kotlinx
 * converter that `json()` registers does on server and client content negotiation.
 *
 * An operations console serves its reports from a registry of producers typed `() -> Any`, so
 * the converter has to pick a serializer from each value it is handed: a serializable class,
 * lists, sets and maps of them, maps of lists, lists with null entries, empty lists, a bare number and
 * contextual UUIDs. A list mixing two report types is refused. A metrics agent pushes batches
 * it keeps as `Any` the same way from the client side.
 */
public class OpsConsoleRuntimeTypedBodyTest {

    /** Cluster-wide headline numbers. */
    @Serializable
    private data class ClusterSummary(val cluster: String, val nodes: Int, val healthy: Boolean)

    /** One node's health. */
    @Serializable
    private data class NodeStatus(val host: String, val load: Double, val zone: String = "eu-1a")

    /** A region the cluster spans. */
    @Serializable
    private data class Region(val code: String, val primary: Boolean)

    /** Free and total capacity of one resource pool. */
    @Serializable
    private data class Capacity(val used: Int, val total: Int)

    /** A planned maintenance slot. */
    @Serializable
    private data class MaintenanceWindow(val start: String, val minutes: Int)

    /** Who carries the pager for one rotation slot. */
    @Serializable
    private data class OnCall(val engineer: String, val rotation: String)

    /** One metric sample the agent pushes. */
    @Serializable
    private data class Metric(val name: String, val value: Double)

    /** The console's acknowledgement of a pushed batch. */
    @Serializable
    private data class IngestAck(val accepted: Int, val names: List<String>)

    /** Writes incident ids as their canonical string form. */
    private object UuidAsString : KSerializer<UUID> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("ops.IncidentId", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: UUID): Unit = encoder.encodeString(value.toString())

        override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
    }

    /** Report producers the console looks up by name; their results are only known as `Any`. */
    private class ReportCatalog(private val producers: Map<String, () -> Any>) {
        fun produce(name: String): Any? = producers[name]?.invoke()
    }

    private val incidentA: UUID = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e")
    private val incidentB: UUID = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7")

    private val catalog: ReportCatalog = ReportCatalog(
        mapOf(
            "summary" to { ClusterSummary(cluster = "prod-eu", nodes = 3, healthy = true) },
            "nodes" to {
                listOf(
                    NodeStatus(host = "node-1", load = 0.42),
                    NodeStatus(host = "node-2", load = 0.87, zone = "eu-1b"),
                )
            },
            "regions" to { setOf(Region(code = "eu-west", primary = true), Region(code = "eu-north", primary = false)) },
            "capacity" to { mapOf("cpu" to Capacity(used = 48, total = 64), "disk" to Capacity(used = 700, total = 2048)) },
            "maintenance" to {
                mapOf(
                    "eu-west" to listOf(MaintenanceWindow(start = "2026-10-20T02:00Z", minutes = 90)),
                    "eu-north" to emptyList(),
                )
            },
            "on-call" to { listOf(OnCall(engineer = "ana", rotation = "primary"), null) },
            "pending-alerts" to { emptyList<Any>() },
            "uptime" to { 99.95 },
            "incidents" to { listOf(incidentA, incidentB) },
            "mixed" to {
                listOf(NodeStatus(host = "node-3", load = 0.1), Region(code = "us-east", primary = false))
            },
        ),
    )

    private val consoleJson: Json = Json(DefaultJson) {
        serializersModule = SerializersModule {
            contextual(UuidAsString)
        }
    }

    private val ingested: MutableList<Metric> = CopyOnWriteArrayList()

    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var console: HttpClient
    private lateinit var raw: HttpClient
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(ServerContentNegotiation) {
                json(consoleJson)
            }
            install(StatusPages) {
                exception<IllegalStateException> { call, cause ->
                    call.respondText(
                        "report failed: ${cause.message}",
                        ContentType.Text.Plain,
                        HttpStatusCode.InternalServerError,
                    )
                }
            }
            routing {
                get("/reports/{name}") {
                    val report: Any? = catalog.produce(call.parameters["name"]!!)
                    if (report == null) {
                        call.respondText("no such report", status = HttpStatusCode.NotFound)
                        return@get
                    }
                    call.respond(report)
                }
                post("/metrics") {
                    val batch: List<Metric> = call.receive()
                    ingested.addAll(batch)
                    call.respond(HttpStatusCode.Accepted, IngestAck(accepted = batch.size, names = batch.map { it.name }))
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
        console = HttpClient(ClientCIO) {
            install(ClientContentNegotiation) {
                json(consoleJson)
            }
        }
        raw = HttpClient(ClientCIO)
    }

    @AfterEach
    fun stopServer() {
        console.close()
        raw.close()
        server.stop(gracePeriodMillis = 0L, timeoutMillis = 10_000L)
    }

    private fun url(path: String): String = "http://127.0.0.1:$port$path"

    private suspend fun rawReport(name: String): HttpResponse = raw.get(url("/reports/$name"))

    @Test
    fun `console serves class, collection and map reports produced as Any`() = runBlocking {
        val summary: HttpResponse = console.get(url("/reports/summary"))
        assertEquals(HttpStatusCode.OK, summary.status)
        assertTrue(summary.contentType()!!.match(ContentType.Application.Json))
        assertEquals(ClusterSummary(cluster = "prod-eu", nodes = 3, healthy = true), summary.body<ClusterSummary>())

        val nodes: List<NodeStatus> = console.get(url("/reports/nodes")).body()
        assertEquals(listOf("node-1", "node-2"), nodes.map { it.host })
        assertEquals(listOf("eu-1a", "eu-1b"), nodes.map { it.zone })

        val regions: Set<Region> = console.get(url("/reports/regions")).body()
        assertEquals(setOf(Region("eu-west", true), Region("eu-north", false)), regions)

        val capacity: Map<String, Capacity> = console.get(url("/reports/capacity")).body()
        assertEquals(Capacity(used = 48, total = 64), capacity.getValue("cpu"))
        assertEquals(2048, capacity.getValue("disk").total)

        val maintenance: Map<String, List<MaintenanceWindow>> = console.get(url("/reports/maintenance")).body()
        assertEquals(
            mapOf(
                "eu-west" to listOf(MaintenanceWindow(start = "2026-10-20T02:00Z", minutes = 90)),
                "eu-north" to emptyList(),
            ),
            maintenance,
        )

        val nodesWire: JsonArray = Json.parseToJsonElement(rawReport("nodes").bodyAsText()).jsonArray
        val firstNode: JsonObject = nodesWire[0].jsonObject
        assertEquals(setOf("host", "load", "zone"), firstNode.keys)
        assertEquals(0.42, firstNode.getValue("load").jsonPrimitive.double)
    }

    @Test
    fun `console infers element types for nulls, empty lists, numbers and contextual ids`() = runBlocking {
        val onCall: List<OnCall?> = console.get(url("/reports/on-call")).body()
        assertEquals(listOf(OnCall(engineer = "ana", rotation = "primary"), null), onCall)
        val onCallWire: JsonArray = Json.parseToJsonElement(rawReport("on-call").bodyAsText()).jsonArray
        assertEquals(JsonNull, onCallWire[1])

        assertEquals("[]", rawReport("pending-alerts").bodyAsText())
        assertEquals(emptyList<String>(), console.get(url("/reports/pending-alerts")).body<List<String>>())

        val uptime: HttpResponse = rawReport("uptime")
        assertTrue(uptime.contentType()!!.match(ContentType.Application.Json))
        assertEquals(99.95, Json.parseToJsonElement(uptime.bodyAsText()).jsonPrimitive.double)

        val incidentsWire: JsonArray = Json.parseToJsonElement(rawReport("incidents").bodyAsText()).jsonArray
        assertEquals(
            listOf(incidentA.toString(), incidentB.toString()),
            incidentsWire.map { it.jsonPrimitive.content },
        )
        val incidents: List<UUID> = console.get(url("/reports/incidents")).body()
        assertEquals(listOf(incidentA, incidentB), incidents)

        assertEquals(HttpStatusCode.NotFound, rawReport("capacity-forecast").status)
    }

    @Test
    fun `console refuses a report mixing element types`() = runBlocking {
        val mixed: HttpResponse = rawReport("mixed")
        assertEquals(HttpStatusCode.InternalServerError, mixed.status)
        val message: String = mixed.bodyAsText()
        assertTrue(message.startsWith("report failed: "), message)
        assertTrue(message.contains("different element types"), message)
    }

    @Test
    fun `agent pushes metric batches it only knows as Any`() = runBlocking {
        val firstBatch: Any = listOf(Metric(name = "cpu.load", value = 0.73), Metric(name = "heap.used", value = 512.0))
        val firstAck: HttpResponse = console.post(url("/metrics")) {
            contentType(ContentType.Application.Json)
            setBody(firstBatch)
        }
        assertEquals(HttpStatusCode.Accepted, firstAck.status)
        assertEquals(IngestAck(accepted = 2, names = listOf("cpu.load", "heap.used")), firstAck.body<IngestAck>())

        val secondBatch: Any = setOf(Metric(name = "gc.pauses", value = 3.0))
        val secondAck: IngestAck = console.post(url("/metrics")) {
            contentType(ContentType.Application.Json)
            setBody(secondBatch)
        }.body()
        assertEquals(1, secondAck.accepted)

        assertEquals(listOf("cpu.load", "heap.used", "gc.pauses"), ingested.map { it.name })
        assertEquals(512.0, ingested[1].value)
    }
}
