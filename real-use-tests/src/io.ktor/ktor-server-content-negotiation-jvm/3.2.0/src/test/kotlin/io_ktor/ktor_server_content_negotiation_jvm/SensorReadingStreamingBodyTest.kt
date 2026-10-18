/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_content_negotiation_jvm

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
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Streamed collection bodies: `Flow` responses written and `Sequence` requests read as JSON arrays.
 *
 * A building-telemetry service takes batches of sensor readings as one JSON array and reads
 * them as a lazily decoded `Sequence`, so a gateway's batch is folded into a summary without
 * first materialising a list, and an ingest endpoint that caps batches stops after the first
 * readings it accepts. Reading histories go out as a `Flow`, filtered per sensor or generated
 * on demand, which the JSON converter writes element by element into one array. The test checks
 * that a received sequence yields every element in order, that a capped consumer keeps only
 * the readings it took, and that a flow response, including an empty one, arrives as the
 * JSON array of exactly the elements the flow emitted.
 */
class SensorReadingStreamingBodyTest {

    @Serializable
    data class Reading(val sensor: String, val minute: Int, val celsius: Double)

    @Serializable
    data class BatchSummary(val count: Int, val sensors: List<String>, val warmest: Reading)

    private val stored: ConcurrentLinkedQueue<Reading> = ConcurrentLinkedQueue()

    private fun runTelemetryService(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) {
                json()
            }
            routing {
                post("/readings") {
                    val readings: Sequence<Reading> = call.receive()
                    var count: Int = 0
                    val sensors: MutableSet<String> = linkedSetOf()
                    var warmest: Reading? = null
                    for (reading: Reading in readings) {
                        stored.add(reading)
                        count++
                        sensors.add(reading.sensor)
                        if (warmest == null || reading.celsius > warmest.celsius) {
                            warmest = reading
                        }
                    }
                    call.respond(HttpStatusCode.Created, BatchSummary(count, sensors.toList(), warmest!!))
                }
                post("/readings/capped") {
                    val readings: Sequence<Reading> = call.receive()
                    val accepted: List<Reading> = readings.take(BATCH_CAP).toList()
                    stored.addAll(accepted)
                    call.respond(HttpStatusCode.Accepted, accepted.size)
                }
                get("/sensors/{sensor}/history") {
                    val sensor: String = call.parameters["sensor"]!!
                    val history: Flow<Reading> = stored.toList().asFlow().filter { it.sensor == sensor }
                    call.respond(history)
                }
                get("/sensors/{sensor}/forecast") {
                    val sensor: String = call.parameters["sensor"]!!
                    val hours: Int = call.request.queryParameters["hours"]!!.toInt()
                    val forecast: Flow<Reading> = flow {
                        for (hour: Int in 1..hours) {
                            emit(Reading(sensor, hour * 60, 20.0 + hour / 2.0))
                        }
                    }
                    call.respond(forecast)
                }
                get("/sensors/{sensor}/alarms") {
                    val alarms: Flow<Reading> = emptyFlow()
                    call.respond(alarms)
                }
            }
        }
        block()
    }

    @Test
    fun uploadedBatchIsReadAsASequenceAndFoldedIntoASummary() = runTelemetryService {
        val response: HttpResponse = postBatch("/readings", Json.encodeToString(BATCH))

        assertEquals(HttpStatusCode.Created, response.status)
        assertJson(response)
        assertEquals(
            BatchSummary(BATCH.size, listOf("lobby", "server-room", "roof"), BATCH[3]),
            Json.decodeFromString<BatchSummary>(response.bodyAsText()),
        )
        assertEquals(BATCH, stored.toList())
    }

    @Test
    fun cappedIngestKeepsOnlyTheReadingsItTookFromTheSequence() = runTelemetryService {
        val response: HttpResponse = postBatch("/readings/capped", Json.encodeToString(BATCH))

        assertEquals(HttpStatusCode.Accepted, response.status)
        assertEquals(BATCH_CAP.toString(), response.bodyAsText())
        assertEquals(BATCH.take(BATCH_CAP), stored.toList())
    }

    @Test
    fun historyFlowIsWrittenAsAJsonArrayOfTheEmittedReadings() = runTelemetryService {
        assertEquals(HttpStatusCode.Created, postBatch("/readings", Json.encodeToString(BATCH)).status)

        val lobby: HttpResponse = fetch("/sensors/lobby/history")
        assertEquals(HttpStatusCode.OK, lobby.status)
        assertJson(lobby)
        assertEquals(
            """[{"sensor":"lobby","minute":0,"celsius":21.5},{"sensor":"lobby","minute":5,"celsius":21.75}]""",
            lobby.bodyAsText(),
        )

        val roof: HttpResponse = fetch("/sensors/roof/history")
        assertEquals(BATCH.filter { it.sensor == "roof" }, Json.decodeFromString<List<Reading>>(roof.bodyAsText()))

        val unknown: HttpResponse = fetch("/sensors/basement/history")
        assertEquals(HttpStatusCode.OK, unknown.status)
        assertEquals("[]", unknown.bodyAsText())
    }

    @Test
    fun generatedAndEmptyFlowsArriveAsCompleteJsonArrays() = runTelemetryService {
        val forecast: HttpResponse = fetch("/sensors/roof/forecast?hours=48")
        assertEquals(HttpStatusCode.OK, forecast.status)
        assertJson(forecast)
        val hours: List<Reading> = Json.decodeFromString(forecast.bodyAsText())
        assertEquals((1..48).map { Reading("roof", it * 60, 20.0 + it / 2.0) }, hours)

        val alarms: HttpResponse = fetch("/sensors/roof/alarms")
        assertEquals(HttpStatusCode.OK, alarms.status)
        assertJson(alarms)
        assertEquals("[]", alarms.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.postBatch(path: String, body: String): HttpResponse = client.post(path) {
        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        setBody(body)
    }

    private suspend fun ApplicationTestBuilder.fetch(path: String): HttpResponse = client.get(path) {
        header(HttpHeaders.Accept, ContentType.Application.Json.toString())
    }

    private fun assertJson(response: HttpResponse) {
        val actual: ContentType? = response.contentType()
        assertTrue(actual != null && actual.match(ContentType.Application.Json), "expected JSON, got $actual")
    }

    private companion object {
        const val BATCH_CAP: Int = 3
        val BATCH: List<Reading> = listOf(
            Reading("lobby", 0, 21.5),
            Reading("server-room", 0, 18.25),
            Reading("roof", 0, 9.5),
            Reading("server-room", 5, 26.0),
            Reading("lobby", 5, 21.75),
            Reading("roof", 5, 11.0),
        )
    }
}
