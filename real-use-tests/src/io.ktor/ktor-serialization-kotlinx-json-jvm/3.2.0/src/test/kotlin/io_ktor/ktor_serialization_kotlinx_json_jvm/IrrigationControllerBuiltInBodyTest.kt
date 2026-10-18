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
import io.ktor.client.request.put
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
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/**
 * Resolves the built-in serializers of top-level body types that are not serializable classes,
 * such as enums, unsigned integers, primitive and object arrays, `Duration`, `Triple` and
 * enum-keyed maps, when `json()` converts whole request and response bodies.
 *
 * An irrigation controller exposes its operating mode as a bare enum, its valves as a
 * `BooleanArray`, soil moisture as a `DoubleArray`, per-zone watering times as `Duration`
 * values and its lifetime water meter as a `ULong`; a gardening app drives it through a typed
 * client, and malformed enum or unsigned bodies are rejected with 400.
 */
public class IrrigationControllerBuiltInBodyTest {

    /** The controller's operating mode, named on the wire in lower case. */
    @Serializable
    private enum class Mode {
        @SerialName("eco")
        ECO,

        @SerialName("boost")
        BOOST,

        @SerialName("off")
        OFF,
    }

    private val zoneCount: Int = 4

    private var mode: Mode = Mode.ECO
    private var valves: BooleanArray = BooleanArray(zoneCount)
    private val zoneModes: Array<Mode> = arrayOf(Mode.ECO, Mode.BOOST, Mode.ECO, Mode.OFF)
    private val moisture: DoubleArray = doubleArrayOf(0.31, 0.18, 0.42, 0.0)
    private val schedule: MutableMap<Int, Duration> = ConcurrentHashMap(mapOf(1 to 15.minutes, 2 to 40.minutes))
    private var lifetimeLitres: ULong = 17_999_999_999_999_999_000uL

    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var app: HttpClient
    private lateinit var raw: HttpClient
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(ServerContentNegotiation) {
                json()
            }
            routing {
                get("/mode") {
                    call.respond(mode)
                }
                put("/mode") {
                    val requested: Mode = call.receive()
                    val previous: Mode = mode
                    mode = requested
                    call.respond(previous)
                }
                get("/valves") {
                    call.respond(valves)
                }
                put("/valves") {
                    val requested: BooleanArray = call.receive()
                    require(requested.size == zoneCount) { "expected $zoneCount valves" }
                    valves = requested
                    val open: Int = requested.count { it }
                    call.respond(open)
                }
                get("/zones/modes") {
                    call.respond(zoneModes)
                }
                get("/moisture") {
                    call.respond(moisture)
                }
                get("/schedule/{zone}") {
                    val zone: Int = call.parameters["zone"]!!.toInt()
                    call.respond(schedule.getValue(zone))
                }
                put("/schedule/{zone}") {
                    val zone: Int = call.parameters["zone"]!!.toInt()
                    val runtime: Duration = call.receive()
                    schedule[zone] = runtime
                    call.respond(HttpStatusCode.Accepted, schedule.values.fold(Duration.ZERO, Duration::plus))
                }
                get("/budget") {
                    val budget: Map<Mode, Duration> = mapOf(Mode.ECO to 30.minutes, Mode.BOOST to 1.5.hours)
                    call.respond(budget)
                }
                get("/calibration") {
                    val calibration: Triple<Double, Double, String> = Triple(0.12, 0.87, "capacitive-v2")
                    call.respond(calibration)
                }
                get("/meter") {
                    call.respond(lifetimeLitres)
                }
                post("/meter") {
                    val readings: LongArray = call.receive()
                    lifetimeLitres += readings.sum().toULong()
                    call.respond(lifetimeLitres)
                }
                post("/meter/reset") {
                    val value: ULong = call.receive()
                    lifetimeLitres = value
                    call.respond(lifetimeLitres)
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
        app = HttpClient(ClientCIO) {
            install(ClientContentNegotiation) {
                json()
            }
        }
        raw = HttpClient(ClientCIO)
    }

    @AfterEach
    fun stopServer() {
        app.close()
        raw.close()
        server.stop(gracePeriodMillis = 0L, timeoutMillis = 10_000L)
    }

    private fun url(path: String): String = "http://127.0.0.1:$port$path"

    private suspend fun rawJson(path: String): String = raw.get(url(path)).bodyAsText()

    @Test
    fun `app switches the mode and drives the valves with enum and array bodies`() = runBlocking {
        assertEquals(Mode.ECO, app.get(url("/mode")).body<Mode>())
        val previous: Mode = app.put(url("/mode")) {
            contentType(ContentType.Application.Json)
            setBody(Mode.BOOST)
        }.body()
        assertEquals(Mode.ECO, previous)
        assertEquals(Mode.BOOST, app.get(url("/mode")).body<Mode>())

        val open: Int = app.put(url("/valves")) {
            contentType(ContentType.Application.Json)
            setBody(booleanArrayOf(true, false, true, true))
        }.body()
        assertEquals(3, open)
        assertArrayEquals(booleanArrayOf(true, false, true, true), app.get(url("/valves")).body<BooleanArray>())

        assertArrayEquals(zoneModes, app.get(url("/zones/modes")).body<Array<Mode>>())
        assertArrayEquals(moisture, app.get(url("/moisture")).body<DoubleArray>(), 0.0)
    }

    @Test
    fun `app schedules zones with durations and reads composite answers`() = runBlocking {
        assertEquals(15.minutes, app.get(url("/schedule/1")).body<Duration>())
        val accepted: HttpResponse = app.put(url("/schedule/3")) {
            contentType(ContentType.Application.Json)
            setBody(1.hours + 5.minutes)
        }
        assertEquals(HttpStatusCode.Accepted, accepted.status)
        assertEquals(2.hours, accepted.body<Duration>())
        assertEquals(65.minutes, app.get(url("/schedule/3")).body<Duration>())

        val budget: Map<Mode, Duration> = app.get(url("/budget")).body()
        assertEquals(mapOf(Mode.ECO to 30.minutes, Mode.BOOST to 90.minutes), budget)

        val calibration: Triple<Double, Double, String> = app.get(url("/calibration")).body()
        assertEquals(Triple(0.12, 0.87, "capacitive-v2"), calibration)
    }

    @Test
    fun `app accumulates the water meter past the signed range`() = runBlocking {
        assertEquals(17_999_999_999_999_999_000uL, app.get(url("/meter")).body<ULong>())
        val total: ULong = app.post(url("/meter")) {
            contentType(ContentType.Application.Json)
            setBody(longArrayOf(400L, 350L, 250L))
        }.body()
        assertEquals(18_000_000_000_000_000_000uL, total)

        val reset: ULong = app.post(url("/meter/reset")) {
            contentType(ContentType.Application.Json)
            setBody(ULong.MAX_VALUE)
        }.body()
        assertEquals(ULong.MAX_VALUE, reset)
        assertEquals("18446744073709551615", rawJson("/meter"))
    }

    @Test
    fun `built-in bodies keep their plain wire forms for other clients`() = runBlocking {
        assertEquals(JsonPrimitive("eco"), Json.parseToJsonElement(rawJson("/mode")))
        assertEquals(
            listOf("eco", "boost", "eco", "off"),
            Json.parseToJsonElement(rawJson("/zones/modes")).jsonArray.map { it.jsonPrimitive.content },
        )
        val valvesWire: JsonArray = Json.parseToJsonElement(rawJson("/valves")).jsonArray
        assertEquals(List(zoneCount) { JsonPrimitive(false) }, valvesWire)
        assertEquals(JsonPrimitive("PT15M"), Json.parseToJsonElement(rawJson("/schedule/1")))

        val budgetWire: JsonObject = Json.parseToJsonElement(rawJson("/budget")).jsonObject
        assertEquals(mapOf("eco" to JsonPrimitive("PT30M"), "boost" to JsonPrimitive("PT1H30M")), budgetWire)

        val calibrationWire: JsonObject = Json.parseToJsonElement(rawJson("/calibration")).jsonObject
        assertEquals(setOf("first", "second", "third"), calibrationWire.keys)
        assertEquals("capacitive-v2", calibrationWire.getValue("third").jsonPrimitive.content)

        val handWritten: HttpResponse = raw.put(url("/schedule/2")) {
            contentType(ContentType.Application.Json)
            setBody("\"PT10M\"")
        }
        assertEquals(HttpStatusCode.Accepted, handWritten.status)
        assertEquals(JsonPrimitive("PT25M"), Json.parseToJsonElement(handWritten.bodyAsText()))
    }

    @Test
    fun `controller rejects unknown modes and negative meter values`() = runBlocking {
        val unknownMode: HttpResponse = raw.put(url("/mode")) {
            contentType(ContentType.Application.Json)
            setBody("\"turbo\"")
        }
        assertEquals(HttpStatusCode.BadRequest, unknownMode.status)
        assertEquals(Mode.ECO, app.get(url("/mode")).body<Mode>())

        val negative: HttpResponse = raw.post(url("/meter/reset")) {
            contentType(ContentType.Application.Json)
            setBody("-1")
        }
        assertEquals(HttpStatusCode.BadRequest, negative.status)
        assertEquals(17_999_999_999_999_999_000uL, app.get(url("/meter")).body<ULong>())
    }
}
