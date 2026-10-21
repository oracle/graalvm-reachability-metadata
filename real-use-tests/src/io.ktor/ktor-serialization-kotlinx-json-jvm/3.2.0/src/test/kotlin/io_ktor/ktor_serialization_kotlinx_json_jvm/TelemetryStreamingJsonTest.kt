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
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/**
 * Streams JSON arrays element by element through the kotlinx JSON extension that `json()` adds
 * to content negotiation: `Flow` bodies are written incrementally and `Sequence` bodies are
 * decoded lazily.
 *
 * A telemetry gateway lets sensors upload batches of readings as a flow, decodes each upload as
 * a sequence to aggregate it without materializing the batch, and exports the stored readings of
 * a sensor as a flow that dashboards read back either as typed lists or as raw JSON.
 */
public class TelemetryStreamingJsonTest {

    /** One measurement taken by a sensor. */
    @Serializable
    private data class Reading(
        @SerialName("sensor_id") val sensorId: String,
        val sequence: Int,
        val celsius: Double,
        val tags: List<String> = emptyList(),
    )

    /** What the gateway reports back for one uploaded batch. */
    @Serializable
    private data class IngestSummary(
        val accepted: Int,
        val sensors: List<String>,
        @SerialName("max_celsius") val maxCelsius: Double,
    )

    /** The wire format sensors and dashboards share. */
    private val telemetryJson: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val stored: MutableMap<String, MutableList<Reading>> = ConcurrentHashMap()

    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var sensor: HttpClient
    private lateinit var dashboard: HttpClient
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(ServerContentNegotiation) {
                json(telemetryJson)
            }
            routing {
                post("/readings") {
                    val batch: Sequence<Reading> = call.receive()
                    // The sequence reads the request body as it is iterated, so walk it off the event loop.
                    val summary: IngestSummary = withContext(Dispatchers.IO) {
                        var accepted = 0
                        var maxCelsius = Double.NEGATIVE_INFINITY
                        val sensors = linkedSetOf<String>()
                        for (reading in batch) {
                            stored.getOrPut(reading.sensorId) { CopyOnWriteArrayList() }.add(reading)
                            sensors.add(reading.sensorId)
                            maxCelsius = maxOf(maxCelsius, reading.celsius)
                            accepted++
                        }
                        IngestSummary(accepted, sensors.toList(), maxCelsius)
                    }
                    call.respond(HttpStatusCode.Accepted, summary)
                }
                get("/sensors/{id}/readings") {
                    val readings: List<Reading> = stored[call.parameters["id"]].orEmpty().toList()
                    val export: Flow<Reading> = flow {
                        for (reading in readings.sortedBy { it.sequence }) {
                            emit(reading)
                        }
                    }
                    call.respond(export)
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
        sensor = HttpClient(ClientCIO) {
            install(ClientContentNegotiation) {
                json(telemetryJson)
            }
        }
        dashboard = HttpClient(ClientCIO)
    }

    @AfterEach
    fun stopServer() {
        sensor.close()
        dashboard.close()
        server.stop(gracePeriodMillis = 0L, timeoutMillis = 10_000L)
    }

    private fun url(path: String): String = "http://127.0.0.1:$port$path"

    @Test
    fun `sensors upload readings as a flow and the gateway aggregates them as a sequence`() = runBlocking {
        val batch: Flow<Reading> = flow {
            for (i in 1..40) {
                val sensorId = if (i % 2 == 0) "boiler-2" else "boiler-1"
                emit(Reading(sensorId, sequence = i, celsius = 60.0 + i / 4.0, tags = listOf("hall-a")))
            }
        }
        val accepted: HttpResponse = sensor.post(url("/readings")) {
            contentType(ContentType.Application.Json)
            setBody(batch)
        }
        assertEquals(HttpStatusCode.Accepted, accepted.status)
        assertEquals(
            IngestSummary(accepted = 40, sensors = listOf("boiler-1", "boiler-2"), maxCelsius = 70.0),
            accepted.body<IngestSummary>(),
        )

        val late: List<Reading> = listOf(Reading("boiler-1", sequence = 41, celsius = 58.5))
        val second: IngestSummary = sensor.post(url("/readings")) {
            contentType(ContentType.Application.Json)
            setBody(late.asFlow())
        }.body()
        assertEquals(IngestSummary(accepted = 1, sensors = listOf("boiler-1"), maxCelsius = 58.5), second)

        assertEquals(21, stored.getValue("boiler-1").size)
        assertEquals(20, stored.getValue("boiler-2").size)
        assertEquals((2..40 step 2).toList(), stored.getValue("boiler-2").map { it.sequence })
    }

    @Test
    fun `dashboards read an exported flow as a typed list and as a raw JSON array`() = runBlocking {
        val handWritten: String = """
            [
              {"sensor_id": "freezer-7", "sequence": 3, "celsius": -18.25, "firmware": "2.1"},
              {"sensor_id": "freezer-7", "sequence": 1, "celsius": -19.5, "tags": ["door-closed"]},
              {"sensor_id": "freezer-7", "sequence": 2, "celsius": -17.0}
            ]
        """.trimIndent()
        val uploaded: IngestSummary = sensor.post(url("/readings")) {
            contentType(ContentType.Application.Json)
            setBody(handWritten)
        }.body()
        assertEquals(3, uploaded.accepted)

        val typed: List<Reading> = sensor.get(url("/sensors/freezer-7/readings")).body()
        assertEquals(
            listOf(
                Reading("freezer-7", sequence = 1, celsius = -19.5, tags = listOf("door-closed")),
                Reading("freezer-7", sequence = 2, celsius = -17.0),
                Reading("freezer-7", sequence = 3, celsius = -18.25),
            ),
            typed,
        )

        val raw: HttpResponse = dashboard.get(url("/sensors/freezer-7/readings"))
        assertEquals(HttpStatusCode.OK, raw.status)
        assertTrue(raw.contentType()!!.match(ContentType.Application.Json))
        val wire: JsonArray = Json.parseToJsonElement(raw.bodyAsText()).jsonArray
        assertEquals(listOf(-19.5, -17.0, -18.25), wire.map { it.jsonObject.getValue("celsius").jsonPrimitive.double })
        assertEquals(setOf("sensor_id", "sequence", "celsius", "tags"), wire[0].jsonObject.keys)
        assertEquals(setOf("sensor_id", "sequence", "celsius"), wire[1].jsonObject.keys)

        val empty: HttpResponse = dashboard.get(url("/sensors/unknown/readings"))
        assertEquals(HttpStatusCode.OK, empty.status)
        assertEquals("[]", empty.bodyAsText())
    }
}
