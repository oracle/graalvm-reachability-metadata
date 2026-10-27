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
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Shape-driven kotlinx.serialization JSON negotiation of a loosely typed partner feed, as a hotel
 * booking client reads availability from a hand-maintained channel manager: offers are told apart
 * by the keys they carry (JsonContentPolymorphicSerializer), single values and arrays are
 * normalized both ways (JsonTransformingSerializer), rate tables use data-class map keys
 * (allowStructuredMapKeys), and the feed's comments, trailing commas, unquoted keys and strings and
 * lower-case enum names are accepted by a lenient Json.
 */
public class HotelAvailabilityShapeDrivenJsonNegotiationTest {

    enum class Board { ROOM_ONLY, BREAKFAST, HALF_BOARD }

    enum class HoldStatus { HELD, WAITLISTED }

    /** A room offer; the partner sends no type discriminator, so its keys decide the subtype. */
    @Serializable(with = RoomOfferSerializer::class)
    sealed interface RoomOffer {
        val room: String
        val nightly: Int
    }

    @Serializable
    data class FlexibleOffer(override val room: String, override val nightly: Int, val refundUntil: String) : RoomOffer

    @Serializable
    data class PrepaidOffer(
        override val room: String,
        override val nightly: Int,
        val prepaidDiscountPercent: Int,
    ) : RoomOffer

    /** Picks the offer subtype from the cancellation terms present in the object. */
    object RoomOfferSerializer : JsonContentPolymorphicSerializer<RoomOffer>(RoomOffer::class) {
        override fun selectDeserializer(element: JsonElement): DeserializationStrategy<RoomOffer> {
            val keys: Set<String> = element.jsonObject.keys
            return when {
                "refundUntil" in keys -> FlexibleOffer.serializer()
                "prepaidDiscountPercent" in keys -> PrepaidOffer.serializer()
                else -> throw SerializationException(
                    "Offer for room ${element.jsonObject["room"]?.jsonPrimitive?.content} has neither " +
                        "refundUntil nor prepaidDiscountPercent",
                )
            }
        }
    }

    /** Reads a lone string as a one-element list and writes a one-element list back as a string. */
    object OneOrManySerializer : JsonTransformingSerializer<List<String>>(ListSerializer(String.serializer())) {
        override fun transformDeserialize(element: JsonElement): JsonElement =
            if (element is JsonArray) element else JsonArray(listOf(element))

        override fun transformSerialize(element: JsonElement): JsonElement =
            if (element is JsonArray && element.size == 1) element.single() else element
    }

    @Serializable
    data class StayKey(val roomType: String, val nights: Int)

    @Serializable
    data class Availability(
        val hotel: String,
        val board: Board,
        @Serializable(with = OneOrManySerializer::class) val amenities: List<String>,
        val offers: List<RoomOffer>,
        val rates: Map<StayKey, Int>,
    )

    @Serializable
    data class HoldRequest(
        val hotel: String,
        @Serializable(with = OneOrManySerializer::class) val guests: List<String>,
        val rooms: Map<StayKey, Int>,
    )

    @Serializable
    data class HoldConfirmation(val holdId: String, val status: HoldStatus)

    /** The partner channel manager; it serves hand-edited feeds and records raw hold requests. */
    class ChannelManager : AutoCloseable {
        val holdBodies: MutableList<String> = CopyOnWriteArrayList()
        val holdContentTypes: MutableList<String> = CopyOnWriteArrayList()

        private val server: EmbeddedServer<*, *> = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/availability/lakeside") {
                    call.respondText(LAKESIDE_FEED, ContentType.Application.Json)
                }
                get("/availability/harbour") {
                    call.respondText(HARBOUR_FEED, ContentType.Application.Json)
                }
                get("/availability/broken") {
                    call.respondText(BROKEN_FEED, ContentType.Application.Json)
                }
                post("/holds") {
                    holdContentTypes += call.request.contentType().withoutParameters().toString()
                    holdBodies += call.receiveText()
                    val status: String = if (holdBodies.size == 1) "held" else "Waitlisted"
                    call.respondText(
                        """{ holdId: "H-${holdBodies.size}", status: $status, }""",
                        ContentType.Application.Json,
                        HttpStatusCode.Created,
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

    @OptIn(ExperimentalSerializationApi::class)
    private val partnerJson: Json = Json {
        isLenient = true
        allowComments = true
        allowTrailingComma = true
        decodeEnumsCaseInsensitive = true
        allowStructuredMapKeys = true
    }

    private fun bookingClient(baseUrl: String): HttpClient = HttpClient(ClientCIO) {
        install(ContentNegotiation) {
            json(partnerJson)
        }
        defaultRequest { url("$baseUrl/") }
    }

    @Test
    fun aHandEditedFeedIsDecodedWithOffersToldApartByTheirKeys(): Unit = runBlocking {
        ChannelManager().use { manager: ChannelManager ->
            bookingClient(manager.baseUrl).use { client: HttpClient ->
                val availability: Availability = client.get("availability/lakeside").body()

                assertThat(availability.hotel).isEqualTo("Lakeside Inn")
                assertThat(availability.board).isEqualTo(Board.BREAKFAST)
                assertThat(availability.amenities).containsExactly("wifi")
                assertThat(availability.offers).containsExactly(
                    FlexibleOffer("DBL", 120, "2026-11-01"),
                    PrepaidOffer("STE", 210, 15),
                )
                assertThat(availability.rates).containsExactlyInAnyOrderEntriesOf(
                    mapOf(StayKey("DBL", 2) to 230, StayKey("DBL", 7) to 760),
                )
            }
        }
    }

    @Test
    fun arraysWhereTheFeedSendsManyValuesAreKeptAsTheyAre(): Unit = runBlocking {
        ChannelManager().use { manager: ChannelManager ->
            bookingClient(manager.baseUrl).use { client: HttpClient ->
                val availability: Availability = client.get("availability/harbour").body()

                assertThat(availability.board).isEqualTo(Board.HALF_BOARD)
                assertThat(availability.amenities).containsExactly("wifi", "parking", "spa")
                assertThat(availability.offers).containsExactly(
                    PrepaidOffer("TWN", 85, 10),
                    PrepaidOffer("SGL", 100, 5),
                )
                assertThat(availability.rates).isEmpty()
            }
        }
    }

    @Test
    fun holdRequestsCollapseSingleGuestsAndWriteStructuredRoomKeys(): Unit = runBlocking {
        ChannelManager().use { manager: ChannelManager ->
            bookingClient(manager.baseUrl).use { client: HttpClient ->
                val single: HttpResponse = client.post("holds") {
                    contentType(ContentType.Application.Json)
                    setBody(HoldRequest("Lakeside Inn", listOf("Ada Byrne"), mapOf(StayKey("DBL", 2) to 1)))
                }
                assertThat(single.status).isEqualTo(HttpStatusCode.Created)
                assertThat(single.body<HoldConfirmation>()).isEqualTo(HoldConfirmation("H-1", HoldStatus.HELD))

                val party: HoldConfirmation = client.post("holds") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        HoldRequest(
                            "Harbour Lodge",
                            listOf("Ada Byrne", "Lin Okafor"),
                            mapOf(StayKey("TWN", 3) to 1, StayKey("STE", 3) to 1),
                        ),
                    )
                }.body()
                assertThat(party).isEqualTo(HoldConfirmation("H-2", HoldStatus.WAITLISTED))

                assertThat(manager.holdContentTypes).containsOnly("application/json")
                assertThat(manager.holdBodies).containsExactly(
                    """{"hotel":"Lakeside Inn","guests":"Ada Byrne","rooms":[{"roomType":"DBL","nights":2},1]}""",
                    """{"hotel":"Harbour Lodge","guests":["Ada Byrne","Lin Okafor"],""" +
                        """"rooms":[{"roomType":"TWN","nights":3},1,{"roomType":"STE","nights":3},1]}""",
                )
                val decodedBack: HoldRequest = partnerJson.decodeFromString(manager.holdBodies.first())
                assertThat(decodedBack.guests).containsExactly("Ada Byrne")
                assertThat(partnerJson.parseToJsonElement(manager.holdBodies.first()).jsonObject["guests"])
                    .isEqualTo(JsonPrimitive("Ada Byrne"))
            }
        }
    }

    @Test
    fun anOfferOfUnknownShapeFailsTheConversionWithTheSelectorsReason(): Unit = runBlocking {
        ChannelManager().use { manager: ChannelManager ->
            bookingClient(manager.baseUrl).use { client: HttpClient ->
                val response: HttpResponse = client.get("availability/broken")

                assertThat(response.status).isEqualTo(HttpStatusCode.OK)
                assertThatThrownBy { runBlocking { response.body<Availability>() } }
                    .isInstanceOf(JsonConvertException::class.java)
                    .hasMessageContaining("Offer for room TWN has neither refundUntil nor prepaidDiscountPercent")
            }
        }
    }

    private companion object {
        val LAKESIDE_FEED: String = """
            /* Lakeside Inn availability, maintained by the front desk */
            {
              hotel: "Lakeside Inn",
              board: breakfast,        // unquoted and lower case
              amenities: "wifi",       // a single amenity is not wrapped in an array
              offers: [
                { room: DBL, nightly: 120, refundUntil: "2026-11-01", },
                { room: STE, nightly: 210, prepaidDiscountPercent: 15 },
              ],
              rates: [
                { roomType: DBL, nights: 2 }, 230,
                { roomType: DBL, nights: 7 }, 760,
              ],
            }
        """.trimIndent()

        val HARBOUR_FEED: String = """
            {
              "hotel": "Harbour Lodge",
              "board": "Half_Board",
              "amenities": ["wifi", "parking", "spa"],
              "offers": [
                { "room": "TWN", "nightly": 85, "prepaidDiscountPercent": 10 },
                { "room": "SGL", "nightly": 100, "prepaidDiscountPercent": 5 }
              ],
              "rates": []
            }
        """.trimIndent()

        val BROKEN_FEED: String = """
            {
              hotel: "Harbour Lodge",
              board: room_only,
              amenities: [],
              offers: [ { room: TWN, nightly: 99 } ],
              rates: [],
            }
        """.trimIndent()
    }
}
