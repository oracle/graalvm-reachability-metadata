/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_content_negotiation_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Version-tolerant JSON negotiation with a kotlinx.serialization `Json` configured for schema
 * evolution (unknown keys, legacy names, coerced unknown enum values, omitted nulls, and always
 * encoded defaults), as a shipment tracking client built against API v1 reads replies from an older
 * v0 carrier gateway and a newer v2 one through the client ContentNegotiation plugin.
 */
@OptIn(ExperimentalSerializationApi::class)
public class ShipmentTrackingSchemaEvolutionNegotiationTest {

    @Serializable
    enum class ShipmentStatus {
        @SerialName("in_transit")
        IN_TRANSIT,

        @SerialName("delivered")
        DELIVERED,

        @SerialName("unknown")
        UNKNOWN,
    }

    /** A tracking event; the gateways name its kind in an `event` field rather than `type`. */
    @Serializable
    @JsonClassDiscriminator("event")
    sealed class TrackingEvent {
        abstract val location: String

        @Serializable
        @SerialName("scan")
        data class Scan(override val location: String, val facility: String) : TrackingEvent()

        @Serializable
        @SerialName("handover")
        data class Handover(override val location: String, val courier: String) : TrackingEvent()
    }

    /** The v1 shipment view; v0 gateways call the number `tracking_no` and the weight `weight`. */
    @Serializable
    data class Shipment(
        @JsonNames("tracking_no")
        val trackingNumber: String,
        val status: ShipmentStatus = ShipmentStatus.UNKNOWN,
        @JsonNames("weight")
        val weightGrams: Int,
        val signedBy: String? = null,
        val events: List<TrackingEvent> = emptyList(),
    )

    /** A pickup booking; `apiVersion` is always sent, other defaults and nulls are omitted. */
    @Serializable
    data class PickupRequest(
        val trackingNumber: String,
        val window: String,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        val apiVersion: Int = 1,
        val instructions: String? = null,
        val parcels: Int = 1,
        @Transient
        val draftNote: String = "",
    )

    @Serializable
    data class PickupConfirmation(val confirmationId: String, val status: ShipmentStatus = ShipmentStatus.UNKNOWN)

    /** The carrier gateway; each route answers with JSON shaped like one gateway version. */
    class CarrierGateway : AutoCloseable {
        val receivedBodies: MutableList<String> = CopyOnWriteArrayList()

        private val server: EmbeddedServer<*, *> = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/v0/shipments/LEG-100") {
                    call.respondText(
                        """{"tracking_no":"LEG-100","status":"delivered","weight":2300,"signedBy":"R. Ortega"}""",
                        ContentType.Application.Json,
                    )
                }
                get("/v2/shipments/NEW-200") {
                    call.respondText(
                        """
                        {
                          "trackingNumber": "NEW-200",
                          "status": "held_at_customs",
                          "weightGrams": 870,
                          "signedBy": null,
                          "carbonGrams": 412,
                          "events": [
                            {"event": "scan", "location": "Rotterdam", "facility": "RTM-HUB", "scannerId": "S-9"},
                            {"event": "handover", "location": "Utrecht", "courier": "J. de Vries"}
                          ]
                        }
                        """.trimIndent(),
                        ContentType.Application.Json,
                    )
                }
                get("/v2/shipments/NEW-201") {
                    call.respondText(
                        """{"trackingNumber":"NEW-201","status":null,"weightGrams":1500}""",
                        ContentType.Application.Json,
                    )
                }
                post("/v1/pickups") {
                    receivedBodies += call.receiveText()
                    call.respondText(
                        """{"confirmationId":"PU-5531","status":"awaiting_courier","slot":"morning"}""",
                        ContentType.Application.Json,
                    )
                }
            }
        }

        val baseUrl: String

        init {
            server.start(wait = false)
            val port: Int = runBlocking { server.engine.resolvedConnectors().first().port }
            baseUrl = "http://127.0.0.1:$port"
        }

        override fun close() {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 2_000)
        }
    }

    /** The JSON format the tracking client uses to stay compatible across gateway versions. */
    private val trackingJson: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = false
    }

    private fun trackingClient(baseUrl: String): HttpClient = HttpClient(ClientCIO) {
        install(ContentNegotiation) {
            json(trackingJson)
        }
        defaultRequest { url("$baseUrl/") }
    }

    @Test
    fun aLegacyGatewayReplyIsDecodedThroughAlternativeNames(): Unit = runBlocking {
        CarrierGateway().use { gateway: CarrierGateway ->
            trackingClient(gateway.baseUrl).use { client: HttpClient ->
                val shipment: Shipment = client.get("v0/shipments/LEG-100").body()

                assertThat(shipment).isEqualTo(
                    Shipment("LEG-100", ShipmentStatus.DELIVERED, 2300, signedBy = "R. Ortega"),
                )
                assertThat(shipment.events).isEmpty()
            }
        }
    }

    @Test
    fun aNewerGatewayReplyKeepsKnownFieldsAndCoercesUnknownValues(): Unit = runBlocking {
        CarrierGateway().use { gateway: CarrierGateway ->
            trackingClient(gateway.baseUrl).use { client: HttpClient ->
                val shipment: Shipment = client.get("v2/shipments/NEW-200").body()

                assertThat(shipment.trackingNumber).isEqualTo("NEW-200")
                assertThat(shipment.status).isEqualTo(ShipmentStatus.UNKNOWN)
                assertThat(shipment.weightGrams).isEqualTo(870)
                assertThat(shipment.signedBy).isNull()
                assertThat(shipment.events).containsExactly(
                    TrackingEvent.Scan("Rotterdam", "RTM-HUB"),
                    TrackingEvent.Handover("Utrecht", "J. de Vries"),
                )

                val nullStatus: Shipment = client.get("v2/shipments/NEW-201").body()
                assertThat(nullStatus).isEqualTo(Shipment("NEW-201", ShipmentStatus.UNKNOWN, 1500))
            }
        }
    }

    @Test
    fun aPickupRequestOmitsNullsAndDefaultsButAlwaysCarriesItsApiVersion(): Unit = runBlocking {
        CarrierGateway().use { gateway: CarrierGateway ->
            trackingClient(gateway.baseUrl).use { client: HttpClient ->
                val confirmation: PickupConfirmation = client.post("v1/pickups") {
                    contentType(ContentType.Application.Json)
                    setBody(PickupRequest("NEW-200", "2026-10-19T08:00/12:00", draftNote = "call first?"))
                }.body()

                assertThat(confirmation).isEqualTo(PickupConfirmation("PU-5531", ShipmentStatus.UNKNOWN))

                val sent: JsonObject = trackingJson.parseToJsonElement(gateway.receivedBodies.single()).jsonObject
                assertThat(sent.keys).containsExactlyInAnyOrder("trackingNumber", "window", "apiVersion")
                assertThat(sent.getValue("apiVersion").jsonPrimitive.content).isEqualTo("1")
                assertThat(sent.getValue("window").jsonPrimitive.content).isEqualTo("2026-10-19T08:00/12:00")
            }
        }
    }

    @Test
    fun explicitlySetOptionalFieldsAreSentAlongsideTheApiVersion(): Unit = runBlocking {
        CarrierGateway().use { gateway: CarrierGateway ->
            trackingClient(gateway.baseUrl).use { client: HttpClient ->
                client.post("v1/pickups") {
                    contentType(ContentType.Application.Json)
                    setBody(PickupRequest("LEG-100", "2026-10-20T13:00/17:00", instructions = "Dock 4", parcels = 3))
                }

                val sent: JsonObject = trackingJson.parseToJsonElement(gateway.receivedBodies.single()).jsonObject
                assertThat(sent.keys)
                    .containsExactlyInAnyOrder("trackingNumber", "window", "apiVersion", "instructions", "parcels")
                assertThat(sent.getValue("instructions").jsonPrimitive.content).isEqualTo("Dock 4")
                assertThat(sent.getValue("parcels").jsonPrimitive.content).isEqualTo("3")
                assertThat(trackingJson.decodeFromJsonElement(PickupRequest.serializer(), sent))
                    .isEqualTo(PickupRequest("LEG-100", "2026-10-20T13:00/17:00", instructions = "Dock 4", parcels = 3))
            }
        }
    }
}
