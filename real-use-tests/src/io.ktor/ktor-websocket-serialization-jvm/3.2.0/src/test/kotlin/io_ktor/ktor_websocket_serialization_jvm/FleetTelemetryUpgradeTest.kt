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
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.websocket.WebSockets as ServerWebSockets

/**
 * Wire-schema evolution between differently configured kotlinx JSON WebSocket converters,
 * where a v2 server and v1 and v2 clients each decode with their own `Json` settings
 * behind `sendSerialized` and `receiveDeserialized`.
 *
 * A fleet-tracking hub was upgraded to a v2 protocol: it reads legacy `lat`/`lon` aliases,
 * ignores fields old firmware still sends, coerces unknown or null values to defaults,
 * matches statuses case-insensitively and omits null fields from its acknowledgements.
 * Old devices that tolerate unknown keys keep working; a strict old device does not.
 */
@OptIn(ExperimentalSerializationApi::class)
public class FleetTelemetryUpgradeTest {
    /** A vehicle registration code that travels as a bare JSON string. */
    @Serializable
    @JvmInline
    value class VehicleId(val code: String)

    /** The movement state a v2 report carries. */
    @Serializable
    enum class Status { PARKED, MOVING, UNKNOWN }

    /** A v2 position report; the aliases keep v1 field names readable. */
    @Serializable
    data class PositionReport(
        val vehicleId: VehicleId,
        @JsonNames("lat") val latitude: Double,
        @JsonNames("lon") val longitude: Double,
        val status: Status = Status.UNKNOWN,
        val speedKmh: Int = 0,
        val driver: String? = null,
    )

    /** The v2 hub's answer to each report. */
    @Serializable
    data class Acknowledgement(
        val vehicleId: VehicleId,
        val accepted: Status,
        val zone: String?,
        val advisories: List<String>,
    )

    /** What v1 firmware sends: old field names, a free-form status and a firmware tag. */
    @Serializable
    data class LegacyPing(
        val vehicleId: String,
        val lat: Double,
        val lon: Double,
        val status: String,
        val speedKmh: Int?,
        val firmware: String,
    )

    /** The only part of an acknowledgement v1 firmware understands. */
    @Serializable
    data class LegacyAck(val vehicleId: String, val accepted: String)

    @Test
    fun `legacy devices report through aliases and coercion and read only the fields they know`() {
        val received: ConcurrentLinkedQueue<PositionReport> = ConcurrentLinkedQueue()
        val acks: MutableList<LegacyAck> = mutableListOf()

        testApplication {
            installTelemetryHub(received)
            withTimeout(TIMEOUT_MILLIS) {
                deviceClient(TOLERANT_V1_JSON).webSocket("/telemetry") {
                    for (ping: LegacyPing in LEGACY_PINGS) {
                        sendSerialized(ping)
                        acks += receiveDeserialized<LegacyAck>()
                    }
                }
            }
        }

        assertEquals(
            listOf(
                PositionReport(VehicleId("TRK-7"), 52.52, 13.40, Status.MOVING, speedKmh = 0),
                PositionReport(VehicleId("TRK-9"), 41.90, 12.49, Status.UNKNOWN, speedKmh = 104),
                PositionReport(VehicleId("TRK-7"), 52.53, 13.41, Status.PARKED, speedKmh = 0),
            ),
            received.toList(),
        )
        assertEquals(
            listOf(
                LegacyAck("TRK-7", "MOVING"),
                LegacyAck("TRK-9", "UNKNOWN"),
                LegacyAck("TRK-7", "PARKED"),
            ),
            acks,
        )
    }

    @Test
    fun `v2 device receives typed acknowledgements whose null zone is left off the wire`() {
        val received: ConcurrentLinkedQueue<PositionReport> = ConcurrentLinkedQueue()
        val southern: PositionReport = PositionReport(VehicleId("VAN-3"), 41.90, 12.49, Status.MOVING, 95, "dana")
        val northern: PositionReport = PositionReport(VehicleId("VAN-3"), 59.33, 18.07, Status.PARKED)
        var southernAck: JsonObject? = null
        var northernAck: Acknowledgement? = null

        testApplication {
            installTelemetryHub(received)
            withTimeout(TIMEOUT_MILLIS) {
                deviceClient(HUB_JSON).webSocket("/telemetry") {
                    sendSerialized(southern)
                    val frame: Frame = incoming.receive()
                    val text: String = assertInstanceOf(Frame.Text::class.java, frame).readText()
                    southernAck = Json.parseToJsonElement(text).jsonObject
                    sendSerialized(northern)
                    northernAck = receiveDeserialized<Acknowledgement>()
                }
            }
        }

        assertEquals(listOf(southern, northern), received.toList())
        val wire: JsonObject = southernAck!!
        assertEquals(setOf("vehicleId", "accepted", "advisories"), wire.keys)
        assertEquals("VAN-3", wire.getValue("vehicleId").jsonPrimitive.content)
        assertEquals("MOVING", wire.getValue("accepted").jsonPrimitive.content)
        assertEquals(listOf("overspeed"), wire.getValue("advisories").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(Acknowledgement(VehicleId("VAN-3"), Status.PARKED, "nordic", emptyList()), northernAck)
    }

    @Test
    fun `strict legacy device rejects acknowledgements carrying fields it never knew`() {
        val received: ConcurrentLinkedQueue<PositionReport> = ConcurrentLinkedQueue()
        var failure: SerializationException? = null

        testApplication {
            installTelemetryHub(received)
            withTimeout(TIMEOUT_MILLIS) {
                deviceClient(Json).webSocket("/telemetry") {
                    sendSerialized(LEGACY_PINGS.first())
                    failure = try {
                        receiveDeserialized<LegacyAck>()
                        null
                    } catch (rejected: SerializationException) {
                        rejected
                    }
                }
            }
        }

        assertEquals(1, received.size)
        val message: String = failure!!.message.orEmpty()
        assertTrue(message.contains("advisories"), message)
        assertFalse(message.contains("firmware"), message)
    }

    /** Installs the v2 hub, which records every report it decodes into [received]. */
    private fun ApplicationTestBuilder.installTelemetryHub(received: ConcurrentLinkedQueue<PositionReport>) {
        install(ServerWebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(HUB_JSON)
        }
        routing {
            webSocket("/telemetry") {
                while (true) {
                    val report: PositionReport = try {
                        receiveDeserialized<PositionReport>()
                    } catch (closed: ClosedReceiveChannelException) {
                        break
                    }
                    received += report
                    sendSerialized(acknowledge(report))
                }
            }
        }
    }

    private fun ApplicationTestBuilder.deviceClient(json: Json): HttpClient = createClient {
        install(ClientWebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(json)
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS: Long = 30_000
        const val SPEED_LIMIT_KMH: Int = 90
        const val NORDIC_LATITUDE: Double = 55.0

        /** The v2 hub's settings: tolerant on input, compact on output. */
        val HUB_JSON: Json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            decodeEnumsCaseInsensitive = true
            explicitNulls = false
        }

        /** v1 firmware that was built to skip fields added after it shipped. */
        val TOLERANT_V1_JSON: Json = Json { ignoreUnknownKeys = true }

        val LEGACY_PINGS: List<LegacyPing> = listOf(
            LegacyPing("TRK-7", 52.52, 13.40, "Moving", null, "1.4.2"),
            LegacyPing("TRK-9", 41.90, 12.49, "teleporting", 104, "1.3.0"),
            LegacyPing("TRK-7", 52.53, 13.41, "parked", null, "1.4.2"),
        )

        fun acknowledge(report: PositionReport): Acknowledgement {
            val advisories: List<String> = buildList {
                if (report.speedKmh > SPEED_LIMIT_KMH) add("overspeed")
                if (report.status == Status.UNKNOWN) add("status-unrecognised")
            }
            val zone: String? = if (report.latitude >= NORDIC_LATITUDE) "nordic" else null
            return Acknowledgement(report.vehicleId, report.status, zone, advisories)
        }
    }
}
