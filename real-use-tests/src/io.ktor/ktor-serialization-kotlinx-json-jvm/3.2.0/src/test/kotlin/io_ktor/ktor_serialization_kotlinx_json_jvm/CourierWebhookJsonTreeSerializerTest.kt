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
import io.ktor.serialization.kotlinx.json.jsonIo
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/**
 * Converts bodies whose serializers inspect the JSON tree (content-based polymorphism without a
 * discriminator, transforming serializers, `@JsonNames` aliases and a snake-case naming strategy)
 * through the converters `json()` and `jsonIo()` register with server and client content negotiation.
 *
 * A parcel-tracking hub ingests webhook batches from couriers that each shape their events
 * differently and name fields inconsistently; the hub tells the event kinds apart by the keys
 * they carry, accepts a label as a bare string or an array, and serves parcel statuses that a
 * dispatcher dashboard reads as typed objects through the kotlinx-io converter.
 */
@OptIn(ExperimentalSerializationApi::class)
public class CourierWebhookJsonTreeSerializerTest {

    /** A tracking update of any kind, told apart by the keys it carries. */
    @Serializable(with = TrackingEventSerializer::class)
    private sealed interface TrackingEvent {
        val parcelId: String
    }

    /** The parcel passed through a sorting facility. */
    @Serializable
    private data class ScanEvent(override val parcelId: String, val facility: String) : TrackingEvent

    /** The parcel was handed over to the recipient. */
    @Serializable
    private data class DeliveryEvent(override val parcelId: String, val signedBy: String) : TrackingEvent

    /** The courier could not move the parcel on. */
    @Serializable
    private data class ExceptionEvent(override val parcelId: String, val reason: String) : TrackingEvent

    /** Picks the event kind from the keys courier payloads carry, since none sends a type field. */
    private object TrackingEventSerializer : JsonContentPolymorphicSerializer<TrackingEvent>(TrackingEvent::class) {
        override fun selectDeserializer(element: JsonElement): DeserializationStrategy<TrackingEvent> {
            val keys: Set<String> = element.jsonObject.keys
            return when {
                "signed_by" in keys -> DeliveryEvent.serializer()
                "facility" in keys -> ScanEvent.serializer()
                "reason" in keys -> ExceptionEvent.serializer()
                else -> throw SerializationException("Unrecognized tracking event with keys $keys")
            }
        }
    }

    /** Accepts a single label as a bare string and writes a single label back the same way. */
    private object LabelListSerializer : JsonTransformingSerializer<List<String>>(ListSerializer(String.serializer())) {
        override fun transformDeserialize(element: JsonElement): JsonElement =
            if (element is JsonArray) element else JsonArray(listOf(element))

        override fun transformSerialize(element: JsonElement): JsonElement =
            if (element.jsonArray.size == 1) element.jsonArray.single() else element
    }

    /** One webhook delivery; older couriers still call the event list `updates`. */
    @Serializable
    private data class WebhookBatch(
        val courierAccount: String,
        @JsonNames("updates") val events: List<TrackingEvent>,
        @Serializable(with = LabelListSerializer::class) val labels: List<String> = emptyList(),
    )

    /** The hub's acknowledgement of a batch. */
    @Serializable
    private data class InboxReceipt(
        val courierAccount: String,
        val acceptedEvents: Int,
        val parcelIds: List<String>,
        @Serializable(with = LabelListSerializer::class) val labels: List<String>,
    )

    /** What the hub knows about one parcel. */
    @Serializable
    private data class ParcelStatus(
        val parcelId: String,
        val state: String,
        val lastFacility: String? = null,
        val detail: String? = null,
        val eventCount: Int,
    )

    /** A rejected request. */
    @Serializable
    private data class Problem(val status: Int, val title: String)

    /** The hub's wire format: snake-case keys for every courier and the dashboard. */
    private val hubJson: Json = Json {
        namingStrategy = JsonNamingStrategy.SnakeCase
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val timelines: MutableMap<String, MutableList<TrackingEvent>> = ConcurrentHashMap()

    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var dashboard: HttpClient
    private lateinit var courier: HttpClient
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(ServerContentNegotiation) {
                json(hubJson)
            }
            install(StatusPages) {
                exception<BadRequestException> { call, _ ->
                    call.respond(HttpStatusCode.BadRequest, Problem(status = 400, title = "Unreadable webhook"))
                }
            }
            routing {
                post("/webhooks") {
                    val batch: WebhookBatch = call.receive()
                    batch.events.forEach { event: TrackingEvent ->
                        timelines.computeIfAbsent(event.parcelId) { mutableListOf() }.add(event)
                    }
                    call.respond(
                        HttpStatusCode.Accepted,
                        InboxReceipt(
                            courierAccount = batch.courierAccount,
                            acceptedEvents = batch.events.size,
                            parcelIds = batch.events.map { it.parcelId }.distinct(),
                            labels = batch.labels,
                        ),
                    )
                }
                get("/parcels/{id}") {
                    val parcelId: String = call.parameters["id"]!!
                    val timeline: List<TrackingEvent>? = timelines[parcelId]
                    if (timeline == null) {
                        call.respond(HttpStatusCode.NotFound, Problem(status = 404, title = "Unknown parcel"))
                        return@get
                    }
                    val lastFacility: String? = timeline.filterIsInstance<ScanEvent>().lastOrNull()?.facility
                    val status: ParcelStatus = when (val latest: TrackingEvent = timeline.last()) {
                        is ScanEvent -> ParcelStatus(parcelId, "in_transit", lastFacility, null, timeline.size)
                        is DeliveryEvent -> ParcelStatus(parcelId, "delivered", lastFacility, latest.signedBy, timeline.size)
                        is ExceptionEvent -> ParcelStatus(parcelId, "held", lastFacility, latest.reason, timeline.size)
                    }
                    call.respond(status)
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
        dashboard = HttpClient(ClientCIO) {
            install(ClientContentNegotiation) {
                jsonIo(hubJson)
            }
        }
        courier = HttpClient(ClientCIO)
    }

    @AfterEach
    fun stopServer() {
        dashboard.close()
        courier.close()
        server.stop(gracePeriodMillis = 0L, timeoutMillis = 10_000L)
    }

    private fun url(path: String): String = "http://127.0.0.1:$port$path"

    /** Posts a courier's hand-written payload; String bodies pass content negotiation untouched. */
    private suspend fun deliver(client: HttpClient, payload: String): HttpResponse = client.post(url("/webhooks")) {
        contentType(ContentType.Application.Json)
        setBody(payload)
    }

    @Test
    fun `couriers post differently shaped events that the dashboard reads back as statuses`() = runBlocking {
        val northbound: HttpResponse = deliver(
            dashboard,
            """
            {
              "courier_account": "northbound-express",
              "events": [
                {"parcel_id": "NB-100", "facility": "Leipzig hub"},
                {"parcel_id": "NB-200", "facility": "Leipzig hub"},
                {"parcel_id": "NB-100", "facility": "Berlin depot", "conveyor": 7},
                {"parcel_id": "NB-100", "signed_by": "A. Novak"}
              ],
              "labels": "priority"
            }
            """.trimIndent(),
        )
        assertEquals(HttpStatusCode.Accepted, northbound.status)
        assertEquals(
            InboxReceipt("northbound-express", 4, listOf("NB-100", "NB-200"), listOf("priority")),
            northbound.body<InboxReceipt>(),
        )

        val legacy: HttpResponse = deliver(
            dashboard,
            """
            {
              "courier_account": "old-post",
              "updates": [
                {"parcel_id": "NB-200", "reason": "address incomplete"}
              ],
              "labels": ["fragile", "signature-required"]
            }
            """.trimIndent(),
        )
        assertEquals(HttpStatusCode.Accepted, legacy.status)
        assertEquals(
            InboxReceipt("old-post", 1, listOf("NB-200"), listOf("fragile", "signature-required")),
            legacy.body<InboxReceipt>(),
        )

        val delivered: ParcelStatus = dashboard.get(url("/parcels/NB-100")).body()
        assertEquals(ParcelStatus("NB-100", "delivered", "Berlin depot", "A. Novak", 3), delivered)
        val held: ParcelStatus = dashboard.get(url("/parcels/NB-200")).body()
        assertEquals(ParcelStatus("NB-200", "held", "Leipzig hub", "address incomplete", 2), held)

        val missing: HttpResponse = dashboard.get(url("/parcels/XX-1"))
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertEquals(Problem(status = 404, title = "Unknown parcel"), missing.body<Problem>())
    }

    @Test
    fun `receipts use snake case keys and collapse a single label to a bare string`() = runBlocking {
        val single: HttpResponse = deliver(
            courier,
            """{"courier_account": "cityhop", "events": [{"parcel_id": "CH-1", "facility": "Dock 2"}], "labels": ["cold-chain"]}""",
        )
        assertTrue(single.contentType()!!.match(ContentType.Application.Json))
        val singleWire: JsonObject = Json.parseToJsonElement(single.bodyAsText()).jsonObject
        assertEquals(setOf("courier_account", "accepted_events", "parcel_ids", "labels"), singleWire.keys)
        assertEquals(JsonPrimitive("cold-chain"), singleWire.getValue("labels"))
        assertEquals(1, singleWire.getValue("accepted_events").jsonPrimitive.int)

        val several: HttpResponse = deliver(
            courier,
            """{"courier_account": "cityhop", "events": [{"parcel_id": "CH-2", "facility": "Dock 3"}], "labels": ["bulky", "two-person"]}""",
        )
        val severalWire: JsonObject = Json.parseToJsonElement(several.bodyAsText()).jsonObject
        assertEquals(
            listOf("bulky", "two-person"),
            severalWire.getValue("labels").jsonArray.map { it.jsonPrimitive.content },
        )

        val status: JsonObject = Json.parseToJsonElement(courier.get(url("/parcels/CH-1")).bodyAsText()).jsonObject
        assertEquals(setOf("parcel_id", "state", "last_facility", "event_count"), status.keys)
        assertEquals("in_transit", status.getValue("state").jsonPrimitive.content)
        assertEquals("Dock 2", status.getValue("last_facility").jsonPrimitive.content)

        val unlabelled: InboxReceipt = deliver(dashboard, """{"courier_account": "cityhop", "events": []}""").body()
        assertEquals(InboxReceipt("cityhop", 0, emptyList(), emptyList()), unlabelled)
    }

    @Test
    fun `an event matching no known shape rejects the whole batch`() = runBlocking {
        val rejected: HttpResponse = deliver(
            courier,
            """
            {
              "courier_account": "northbound-express",
              "events": [
                {"parcel_id": "NB-300", "facility": "Leipzig hub"},
                {"parcel_id": "NB-300", "weight_grams": 1200}
              ]
            }
            """.trimIndent(),
        )
        assertEquals(HttpStatusCode.BadRequest, rejected.status)
        assertEquals(
            Problem(status = 400, title = "Unreadable webhook"),
            hubJson.decodeFromString<Problem>(rejected.bodyAsText()),
        )
        assertEquals(emptySet<String>(), timelines.keys)
    }
}
