/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_serialization_kotlinx_json_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
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
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/**
 * Converts HTTP bodies through the zero-argument `json()` registration and the wire conventions
 * of its `DefaultJson` format: defaults are encoded, lenient input is accepted, `NaN` and
 * infinities travel as literals, and maps with structured keys become flat key-value arrays.
 *
 * A lab statistics service summarizes sample sets that instrument scripts post, reports the
 * offsets between channels keyed by channel pairs, and turns away payloads with unknown fields.
 */
public class LabStatisticsDefaultJsonTest {

    /** Raw measurements of one instrument channel. */
    @Serializable
    private data class SampleSet(
        val channel: String,
        val values: List<Double>,
        val unit: String = "mV",
    )

    /** Descriptive statistics of one sample set. */
    @Serializable
    private data class Summary(
        val channel: String,
        val count: Int,
        val mean: Double,
        val min: Double,
        val max: Double,
        val unit: String,
        @SerialName("outlier_policy") val outlierPolicy: String = "keep",
    )

    /** An ordered pair of channels whose mean offset is reported. */
    @Serializable
    private data class ChannelPair(val from: String, val to: String)

    private val received: MutableList<SampleSet> = CopyOnWriteArrayList()

    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var instrument: HttpClient
    private lateinit var script: HttpClient
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(ServerContentNegotiation) {
                json()
            }
            routing {
                post("/summaries") {
                    val samples: SampleSet = call.receive()
                    received.add(samples)
                    call.respond(summarize(samples))
                }
                post("/offsets") {
                    val sets: List<SampleSet> = call.receive()
                    val means: Map<String, Double> = sets.associate { it.channel to it.values.average() }
                    val offsets: Map<ChannelPair, Double> = buildMap {
                        for (from in sets) {
                            for (to in sets) {
                                if (from.channel < to.channel) {
                                    val pair = ChannelPair(from.channel, to.channel)
                                    put(pair, means.getValue(to.channel) - means.getValue(from.channel))
                                }
                            }
                        }
                    }
                    call.respond(offsets)
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
        instrument = HttpClient(ClientCIO) {
            install(ClientContentNegotiation) {
                json()
            }
        }
        script = HttpClient(ClientCIO)
    }

    @AfterEach
    fun stopServer() {
        instrument.close()
        script.close()
        server.stop(gracePeriodMillis = 0L, timeoutMillis = 10_000L)
    }

    private fun url(path: String): String = "http://127.0.0.1:$port$path"

    private fun summarize(samples: SampleSet): Summary = Summary(
        channel = samples.channel,
        count = samples.values.size,
        mean = samples.values.average(),
        min = samples.values.minOrNull() ?: Double.POSITIVE_INFINITY,
        max = samples.values.maxOrNull() ?: Double.NEGATIVE_INFINITY,
        unit = samples.unit,
    )

    @Test
    fun `instruments exchange summaries with encoded defaults and special floating point values`() = runBlocking {
        val calibrated: Summary = instrument.post(url("/summaries")) {
            contentType(ContentType.Application.Json)
            setBody(SampleSet("thermo-1", listOf(1.5, 2.5, 4.0, 0.0)))
        }.body()
        assertEquals(Summary("thermo-1", 4, 2.0, 0.0, 4.0, "mV"), calibrated)
        assertEquals("mV", received.single().unit)

        val idle: Summary = instrument.post(url("/summaries")) {
            contentType(ContentType.Application.Json)
            setBody(SampleSet("idle-3", emptyList(), unit = "uV"))
        }.body()
        assertEquals(0, idle.count)
        assertTrue(idle.mean.isNaN())
        assertEquals(Double.POSITIVE_INFINITY, idle.min)
        assertEquals(Double.NEGATIVE_INFINITY, idle.max)
        assertEquals("uV", idle.unit)

        val raw: HttpResponse = script.post(url("/summaries")) {
            contentType(ContentType.Application.Json)
            setBody("""{"channel": "idle-4", "values": []}""")
        }
        assertEquals(HttpStatusCode.OK, raw.status)
        val wire: String = raw.bodyAsText()
        assertTrue(wire.contains("\"mean\":NaN"), wire)
        assertTrue(wire.contains("\"min\":Infinity"), wire)
        assertTrue(wire.contains("\"max\":-Infinity"), wire)
        assertFalse(wire.contains("\n"), wire)
        val decoded: JsonObject = DefaultJson.parseToJsonElement(wire).jsonObject
        assertEquals(
            listOf("channel", "count", "mean", "min", "max", "unit", "outlier_policy"),
            decoded.keys.toList(),
        )
        assertEquals("keep", decoded.getValue("outlier_policy").jsonPrimitive.content)
        assertEquals("mV", decoded.getValue("unit").jsonPrimitive.content)
    }

    @Test
    fun `hand written lenient payloads are accepted and unknown fields are rejected`() = runBlocking {
        val lenient: HttpResponse = script.post(url("/summaries")) {
            contentType(ContentType.Application.Json)
            setBody("""{channel: probe7, values: [3.0, "5.0", 7.0], unit: uA}""")
        }
        assertEquals(HttpStatusCode.OK, lenient.status)
        val summary: Summary = DefaultJson.decodeFromString(Summary.serializer(), lenient.bodyAsText())
        assertEquals(Summary("probe7", 3, 5.0, 3.0, 7.0, "uA"), summary)
        assertEquals(SampleSet("probe7", listOf(3.0, 5.0, 7.0), "uA"), received.single())

        val unknownField: HttpResponse = script.post(url("/summaries")) {
            contentType(ContentType.Application.Json)
            setBody("""{"channel": "probe8", "values": [1.0], "gain": 2}""")
        }
        assertEquals(HttpStatusCode.BadRequest, unknownField.status)
        assertEquals(1, received.size)
    }

    @Test
    fun `offsets keyed by channel pairs travel as structured map keys`() = runBlocking {
        val sets: List<SampleSet> = listOf(
            SampleSet("a", listOf(1.0, 3.0)),
            SampleSet("b", listOf(4.0, 6.0)),
            SampleSet("c", listOf(10.0)),
        )
        val offsets: Map<ChannelPair, Double> = instrument.post(url("/offsets")) {
            contentType(ContentType.Application.Json)
            setBody(sets)
        }.body()
        assertEquals(
            mapOf(
                ChannelPair("a", "b") to 3.0,
                ChannelPair("a", "c") to 8.0,
                ChannelPair("b", "c") to 5.0,
            ),
            offsets,
        )

        val raw: HttpResponse = script.post(url("/offsets")) {
            contentType(ContentType.Application.Json)
            setBody("""[{"channel": "x", "values": [2.0]}, {"channel": "y", "values": [0.5]}]""")
        }
        assertEquals(HttpStatusCode.OK, raw.status)
        val flat: JsonArray = DefaultJson.parseToJsonElement(raw.bodyAsText()).jsonArray
        assertEquals(2, flat.size)
        val key: JsonObject = flat[0].jsonObject
        assertEquals("x", key.getValue("from").jsonPrimitive.content)
        assertEquals("y", key.getValue("to").jsonPrimitive.content)
        assertEquals("-1.5", flat[1].jsonPrimitive.content)
    }
}
