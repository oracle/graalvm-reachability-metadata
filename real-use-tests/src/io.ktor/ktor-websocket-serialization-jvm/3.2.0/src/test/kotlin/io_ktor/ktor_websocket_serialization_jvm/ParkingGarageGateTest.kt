/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_websocket_serialization_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.websocket.WebSockets as ServerWebSockets

/**
 * Kotlin-specific top-level message types over WebSockets, where `sendSerialized` and
 * `receiveDeserialized` resolve the kotlinx JSON serializer from the reified type of a value
 * class, a nullable value class, an enum, an `Array`, an enum-keyed `Map`, and `Pair` and
 * `Triple` of a value class and a `kotlin.time.Duration`.
 *
 * A parking garage gate admits plates until the entry terminal sends `null`, reports which
 * plates park on the levels it is asked about, and bills each exit by the hour after a free
 * quarter hour; it closes normally once the garage is empty and refuses an unknown plate.
 */
public class ParkingGarageGateTest {
    /** A licence plate, sent on its own as a bare JSON string. */
    @Serializable
    @JvmInline
    value class PlateNumber(val value: String)

    /** The gate's answer to a plate at the entry barrier. */
    @Serializable
    enum class GateDecision {
        @SerialName("open")
        OPEN,

        @SerialName("already_inside")
        ALREADY_INSIDE,

        @SerialName("full")
        FULL,
    }

    /** The garage levels, filled in this order. */
    @Serializable
    enum class Level {
        @SerialName("ground")
        GROUND,

        @SerialName("mezzanine")
        MEZZANINE,

        @SerialName("roof")
        ROOF,
    }

    private lateinit var server: EmbeddedServer<*, *>
    private var port: Int = 0

    @BeforeEach
    fun startGate() {
        server = embeddedServer(ServerCIO, port = 0, host = HOST) {
            install(ServerWebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(GATE_JSON)
            }
            routing {
                webSocket("/gate") {
                    val parked: MutableMap<PlateNumber, Level> = linkedMapOf()
                    while (true) {
                        val plate: PlateNumber = receiveDeserialized<PlateNumber?>() ?: break
                        val decision: GateDecision = when {
                            plate in parked -> GateDecision.ALREADY_INSIDE
                            parked.size >= CAPACITY -> GateDecision.FULL
                            else -> {
                                parked[plate] = Level.entries[parked.size % Level.entries.size]
                                GateDecision.OPEN
                            }
                        }
                        sendSerialized(decision)
                    }

                    val levels: Array<Level> = receiveDeserialized<Array<Level>>()
                    val occupancy: Map<Level, List<PlateNumber>> = levels.associateWith { level: Level ->
                        parked.filterValues { it == level }.keys.toList()
                    }
                    sendSerialized(occupancy)

                    while (parked.isNotEmpty()) {
                        val (plate: PlateNumber, stay: Duration) = receiveDeserialized<Pair<PlateNumber, Duration>>()
                        if (parked.remove(plate) == null) {
                            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "unknown plate ${plate.value}"))
                            return@webSocket
                        }
                        val billedHours: Long = if (stay <= FREE_STAY) 0 else (stay.inWholeMinutes + 59) / 60
                        sendSerialized(Triple(plate, billedHours.hours, billedHours * HOURLY_RATE_CENTS))
                    }
                    close(CloseReason(CloseReason.Codes.NORMAL, "garage empty"))
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().single().port }
    }

    @AfterEach
    fun stopGate() {
        server.stop(gracePeriodMillis = 100, timeoutMillis = 5_000)
    }

    @Test
    fun `entry terminal admits plates, reads occupancy and bills every exit until the garage is empty`(): Unit =
        runBlocking {
            val arrivals: List<PlateNumber> = listOf("KT-301", "BX-552", "KT-301", "LM-777", "QZ-100").map(::PlateNumber)
            val exits: List<Pair<PlateNumber, Duration>> = listOf(
                PlateNumber("BX-552") to 10.minutes,
                PlateNumber("KT-301") to 2.hours + 5.minutes,
                PlateNumber("LM-777") to 59.minutes,
            )
            val decisions: MutableList<GateDecision> = mutableListOf()
            var occupancy: Map<Level, List<PlateNumber>>? = null
            val invoices: MutableList<Triple<PlateNumber, Duration, Long>> = mutableListOf()
            var observedClose: CloseReason? = null

            withTimeout(TIMEOUT_MILLIS) {
                gateClient().use { client: HttpClient ->
                    client.webSocket(host = HOST, port = port, path = "/gate") {
                        for (plate: PlateNumber in arrivals) {
                            sendSerialized(plate)
                            decisions += receiveDeserialized<GateDecision>()
                        }
                        sendSerialized<PlateNumber?>(null)

                        sendSerialized(arrayOf(Level.ROOF, Level.GROUND))
                        occupancy = receiveDeserialized<Map<Level, List<PlateNumber>>>()

                        for (exit: Pair<PlateNumber, Duration> in exits) {
                            sendSerialized(exit)
                            invoices += receiveDeserialized<Triple<PlateNumber, Duration, Long>>()
                        }
                        observedClose = closeReason.await()
                    }
                }
            }

            assertEquals(
                listOf(
                    GateDecision.OPEN,
                    GateDecision.OPEN,
                    GateDecision.ALREADY_INSIDE,
                    GateDecision.OPEN,
                    GateDecision.FULL,
                ),
                decisions,
            )
            assertEquals(
                mapOf(Level.ROOF to listOf(PlateNumber("LM-777")), Level.GROUND to listOf(PlateNumber("KT-301"))),
                occupancy,
            )
            assertEquals(listOf(Level.ROOF, Level.GROUND), occupancy!!.keys.toList())
            assertEquals(
                listOf(
                    Triple(PlateNumber("BX-552"), Duration.ZERO, 0L),
                    Triple(PlateNumber("KT-301"), 3.hours, 750L),
                    Triple(PlateNumber("LM-777"), 1.hours, 250L),
                ),
                invoices,
            )
            assertEquals(CloseReason(CloseReason.Codes.NORMAL, "garage empty"), observedClose)
        }

    @Test
    fun `hand-written terminal sees bare strings, enum names and ISO durations and is refused an unknown plate`():
        Unit = runBlocking {
            val replies: MutableList<String> = mutableListOf()
            var observedClose: CloseReason? = null

            withTimeout(TIMEOUT_MILLIS) {
                gateClient().use { client: HttpClient ->
                    client.webSocket(host = HOST, port = port, path = "/gate") {
                        for (message: String in listOf("\"AB-1\"", "\"CD-2\"")) {
                            send(Frame.Text(message))
                            replies += assertInstanceOf(Frame.Text::class.java, incoming.receive()).readText()
                        }
                        send(Frame.Text("null"))
                        send(Frame.Text("""["mezzanine","ground"]"""))
                        replies += assertInstanceOf(Frame.Text::class.java, incoming.receive()).readText()
                        send(Frame.Text("""{"first":"AB-1","second":"PT1H30M"}"""))
                        replies += assertInstanceOf(Frame.Text::class.java, incoming.receive()).readText()
                        sendSerialized(PlateNumber("ZZ-9") to 1.hours)
                        observedClose = closeReason.await()
                    }
                }
            }

            assertEquals(
                listOf(
                    "\"open\"",
                    "\"open\"",
                    """{"mezzanine":["CD-2"],"ground":["AB-1"]}""",
                    """{"first":"AB-1","second":"PT2H","third":500}""",
                ),
                replies,
            )
            assertEquals(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "unknown plate ZZ-9"), observedClose)
        }

    private fun gateClient(): HttpClient = HttpClient(ClientCIO) {
        install(ClientWebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(GATE_JSON)
        }
    }

    private companion object {
        const val HOST: String = "127.0.0.1"
        const val CAPACITY: Int = 3
        const val HOURLY_RATE_CENTS: Long = 250
        const val TIMEOUT_MILLIS: Long = 30_000

        val FREE_STAY: Duration = 15.minutes
        val GATE_JSON: Json = Json
    }
}
