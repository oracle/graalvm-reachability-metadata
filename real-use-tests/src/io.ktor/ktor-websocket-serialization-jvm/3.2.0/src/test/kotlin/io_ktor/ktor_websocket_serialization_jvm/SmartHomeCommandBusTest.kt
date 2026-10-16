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
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.websocket.WebSockets as ServerWebSockets

/**
 * Generic, open-polymorphic and contextual kotlinx JSON over WebSockets, where
 * parameterized envelopes resolve through a `SerializersModule` in `sendSerialized` and
 * `receiveDeserialized` with the kotlinx WebSocket converter.
 *
 * A smart-home hub accepts `Envelope<DeviceCommand>` messages whose commands implement an
 * open interface registered in the module, stamps them with a contextual `Instant`, and
 * answers each with an `Envelope<DeviceReport>`; an unknown command closes the session.
 */
public class SmartHomeCommandBusTest {
    /** A correlated message wrapper whose body type the receiver names at the call site. */
    @Serializable
    data class Envelope<T>(val correlationId: String, @Contextual val issuedAt: Instant, val body: T)

    /** An open command family; its implementations are known only to the serializers module. */
    interface DeviceCommand {
        val deviceId: String
    }

    /** Dims a light to a brightness percentage. */
    @Serializable
    @SerialName("dim")
    data class Dim(override val deviceId: String, val percent: Int) : DeviceCommand

    /** Sets a thermostat target. */
    @Serializable
    @SerialName("setTemperature")
    data class SetTemperature(override val deviceId: String, val celsius: Double) : DeviceCommand

    /** Switches every device in the home off. */
    @Serializable
    @SerialName("allOff")
    object AllOff : DeviceCommand {
        override val deviceId: String
            get() = "*"
    }

    /** The hub's answer to one command. */
    @Serializable
    data class DeviceReport(
        val deviceId: String,
        val state: String,
        val accepted: Boolean,
        @Contextual val appliedAt: Instant,
    )

    /** Writes an `Instant` as its ISO-8601 text, the way the hub's web dashboard reads it. */
    object IsoInstantSerializer : KSerializer<Instant> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("SmartHomeInstant", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: Instant) {
            encoder.encodeString(value.toString())
        }

        override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
    }

    @Test
    fun `app drives every command type and receives correlated reports`(): Unit = smartHomeHub { client: HttpClient ->
        val commands: List<Envelope<DeviceCommand>> = listOf(
            Envelope("c-1", ISSUED_AT, Dim("hall-light", 40)),
            Envelope("c-2", ISSUED_AT.plusSeconds(60), SetTemperature("living-thermostat", 21.5)),
            Envelope("c-3", ISSUED_AT.plusSeconds(120), Dim("hall-light", 140)),
            Envelope("c-4", ISSUED_AT.plusSeconds(180), AllOff),
        )
        val reports: MutableList<Envelope<DeviceReport>> = mutableListOf()
        var observedClose: CloseReason? = null

        client.webSocket("/hub") {
            for (command: Envelope<DeviceCommand> in commands) {
                sendSerialized(command)
                reports += receiveDeserialized<Envelope<DeviceReport>>()
            }
            close(CloseReason(CloseReason.Codes.NORMAL, "app closed"))
            observedClose = closeReason.await()
        }

        assertEquals(
            listOf(
                Envelope("c-1", ISSUED_AT, DeviceReport("hall-light", "brightness=40%", true, ISSUED_AT.plusSeconds(1))),
                Envelope(
                    "c-2",
                    ISSUED_AT.plusSeconds(60),
                    DeviceReport("living-thermostat", "target=21.5C", true, ISSUED_AT.plusSeconds(61)),
                ),
                Envelope(
                    "c-3",
                    ISSUED_AT.plusSeconds(120),
                    DeviceReport("hall-light", "brightness=40%", false, ISSUED_AT.plusSeconds(121)),
                ),
                Envelope("c-4", ISSUED_AT.plusSeconds(180), DeviceReport("*", "off", true, ISSUED_AT.plusSeconds(181))),
            ),
            reports,
        )
        assertEquals(CloseReason.Codes.NORMAL.code, observedClose!!.code)
    }

    @Test
    fun `dashboard sends raw JSON and an unknown command closes the session`(): Unit =
        smartHomeHub { client: HttpClient ->
            var reportJson: JsonObject? = null
            var observedClose: CloseReason? = null

            client.webSocket("/hub") {
                send(
                    Frame.Text(
                        """
                        {"correlationId":"d-1","issuedAt":"2026-01-15T07:30:00Z",
                         "body":{"type":"setTemperature","deviceId":"bedroom","celsius":18.0}}
                        """.trimIndent(),
                    ),
                )
                val reply: Frame = incoming.receive()
                reportJson = Json.parseToJsonElement(assertInstanceOf(Frame.Text::class.java, reply).readText()).jsonObject
                send(
                    Frame.Text(
                        """{"correlationId":"d-2","issuedAt":"2026-01-15T07:31:00Z","body":{"type":"selfDestruct","deviceId":"x"}}""",
                    ),
                )
                observedClose = closeReason.await()
            }

            val report: JsonObject = reportJson!!
            assertEquals("d-1", report.getValue("correlationId").jsonPrimitive.content)
            assertEquals("2026-01-15T07:30:00Z", report.getValue("issuedAt").jsonPrimitive.content)
            val body: JsonObject = report.getValue("body").jsonObject
            assertEquals("bedroom", body.getValue("deviceId").jsonPrimitive.content)
            assertEquals("target=18.0C", body.getValue("state").jsonPrimitive.content)
            assertEquals("2026-01-15T07:30:01Z", body.getValue("appliedAt").jsonPrimitive.content)
            assertEquals(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "unknown command"), observedClose)
        }

    private fun smartHomeHub(scenario: suspend (HttpClient) -> Unit): Unit = testApplication {
        application {
            install(ServerWebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(HOME_JSON)
            }
            routing {
                webSocket("/hub") {
                    val brightness: MutableMap<String, Int> = mutableMapOf()
                    while (true) {
                        val command: Envelope<DeviceCommand> = try {
                            receiveDeserialized<Envelope<DeviceCommand>>()
                        } catch (unknown: SerializationException) {
                            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "unknown command"))
                            break
                        }
                        val report: DeviceReport = apply(command.body, brightness, command.issuedAt.plusSeconds(1))
                        sendSerialized(Envelope(command.correlationId, command.issuedAt, report))
                    }
                }
            }
        }
        withTimeout(TIMEOUT_MILLIS) {
            scenario(homeClient())
        }
    }

    private fun apply(command: DeviceCommand, brightness: MutableMap<String, Int>, appliedAt: Instant): DeviceReport =
        when (command) {
            is Dim -> {
                val accepted: Boolean = command.percent in 0..100
                if (accepted) {
                    brightness[command.deviceId] = command.percent
                }
                DeviceReport(command.deviceId, "brightness=${brightness[command.deviceId]}%", accepted, appliedAt)
            }
            is SetTemperature -> {
                val accepted: Boolean = command.celsius in 5.0..30.0
                DeviceReport(command.deviceId, "target=${command.celsius}C", accepted, appliedAt)
            }
            AllOff -> {
                brightness.clear()
                DeviceReport(command.deviceId, "off", true, appliedAt)
            }
            else -> error("unregistered command ${command::class}")
        }

    private fun ApplicationTestBuilder.homeClient(): HttpClient = createClient {
        install(ClientWebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(HOME_JSON)
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS: Long = 30_000

        val ISSUED_AT: Instant = Instant.parse("2026-01-15T07:00:00Z")

        val HOME_JSON: Json = Json {
            classDiscriminator = "type"
            serializersModule = SerializersModule {
                contextual(Instant::class, IsoInstantSerializer)
                polymorphic(DeviceCommand::class) {
                    subclass(Dim::class)
                    subclass(SetTemperature::class)
                    subclass(AllOff::class)
                }
            }
        }
    }
}
