/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_websocket_serialization_jvm

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.serialization.jackson.JacksonWebsocketContentConverter
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.websocket.WebSockets as ServerWebSockets

/**
 * Reflection-based Jackson messaging over WebSockets, where plain Kotlin data classes
 * travel through `sendSerialized` and `receiveDeserialized` with the Jackson WebSocket
 * converter inside the Ktor `testApplication` host.
 *
 * An auction house publishes its catalogue as a generic list and runs one bidding
 * session per lot, answering every bid with the lot's status in snake_case JSON.
 */
public class JacksonAuctionHouseTest {
    /** A lot the auction house offers. */
    data class Lot(val lotId: String, val title: String, val reserveCents: Long)

    /** A bid a client places on the lot of its session. */
    data class Bid(val bidderName: String, val amountCents: Long)

    /** The lot's state after a bid, whether that bid took the lead or not. */
    data class LotStatus(
        val lotId: String,
        val leadingBidder: String?,
        val leadingAmountCents: Long,
        val accepted: Boolean,
        val bidCount: Int,
    )

    @Test
    fun `bidder browses the catalogue as a typed generic list`(): Unit = auctionHouse { client: HttpClient ->
        var catalogue: List<Lot>? = null
        var observedClose: CloseReason? = null

        client.webSocket("/catalogue") {
            catalogue = receiveDeserialized<List<Lot>>()
            observedClose = closeReason.await()
        }

        assertEquals(CATALOGUE, catalogue)
        assertEquals("Brass telescope", catalogue!!.first().title)
        assertEquals(CloseReason(CloseReason.Codes.NORMAL, "catalogue sent"), observedClose)
    }

    @Test
    fun `bids below the reserve or the leading bid are refused`(): Unit = auctionHouse { client: HttpClient ->
        val bids: List<Bid> = listOf(
            Bid("dana", 9_000),
            Bid("dana", 12_500),
            Bid("eli", 12_000),
            Bid("eli", 15_000),
        )
        val statuses: MutableList<LotStatus> = mutableListOf()
        var observedClose: CloseReason? = null

        client.webSocket("/lots/lot-1") {
            for (bid: Bid in bids) {
                sendSerialized(bid)
                statuses += receiveDeserialized<LotStatus>()
            }
            sendSerialized(Bid(bidderName = HAMMER, amountCents = 0))
            observedClose = closeReason.await()
        }

        assertEquals(
            listOf(
                LotStatus("lot-1", null, 0, accepted = false, bidCount = 1),
                LotStatus("lot-1", "dana", 12_500, accepted = true, bidCount = 2),
                LotStatus("lot-1", "dana", 12_500, accepted = false, bidCount = 3),
                LotStatus("lot-1", "eli", 15_000, accepted = true, bidCount = 4),
            ),
            statuses,
        )
        assertEquals(CloseReason(CloseReason.Codes.NORMAL, "lot-1 sold to eli"), observedClose)
    }

    @Test
    fun `status frames carry snake_case JSON text`(): Unit = auctionHouse { client: HttpClient ->
        var statusJson: JsonNode? = null

        client.webSocket("/lots/lot-2") {
            send(Frame.Text("""{"bidder_name":"fern","amount_cents":4200}"""))
            val reply: Frame = incoming.receive()
            statusJson = AUCTION_MAPPER.readTree(assertInstanceOf(Frame.Text::class.java, reply).readText())
            sendSerialized(Bid(HAMMER, 0))
            closeReason.await()
        }

        val status: JsonNode = statusJson!!
        assertEquals("lot-2", status.get("lot_id").asText())
        assertEquals("fern", status.get("leading_bidder").asText())
        assertEquals(4200L, status.get("leading_amount_cents").asLong())
        assertEquals(true, status.get("accepted").asBoolean())
        assertEquals(1, status.get("bid_count").asInt())
    }

    /** Runs [scenario] against the auction house application with a Jackson WebSocket client. */
    private fun auctionHouse(scenario: suspend (HttpClient) -> Unit): Unit = testApplication {
        application {
            install(ServerWebSockets) {
                contentConverter = JacksonWebsocketContentConverter(AUCTION_MAPPER)
            }
            routing {
                webSocket("/catalogue") {
                    sendSerialized(CATALOGUE)
                    close(CloseReason(CloseReason.Codes.NORMAL, "catalogue sent"))
                }
                webSocket("/lots/{lotId}") {
                    val lot: Lot = CATALOGUE.single { it.lotId == call.parameters["lotId"] }
                    var leader: Bid? = null
                    var bidCount: Int = 0
                    while (true) {
                        val bid: Bid = receiveDeserialized<Bid>()
                        if (bid.bidderName == HAMMER) {
                            close(CloseReason(CloseReason.Codes.NORMAL, "${lot.lotId} sold to ${leader?.bidderName}"))
                            break
                        }
                        bidCount++
                        val accepted: Boolean = bid.amountCents >= lot.reserveCents &&
                            bid.amountCents > (leader?.amountCents ?: 0)
                        if (accepted) {
                            leader = bid
                        }
                        sendSerialized(
                            LotStatus(lot.lotId, leader?.bidderName, leader?.amountCents ?: 0, accepted, bidCount),
                        )
                    }
                }
            }
        }
        withTimeout(TIMEOUT_MILLIS) {
            scenario(jacksonClient())
        }
    }

    private fun ApplicationTestBuilder.jacksonClient(): HttpClient = createClient {
        install(ClientWebSockets) {
            contentConverter = JacksonWebsocketContentConverter(AUCTION_MAPPER)
        }
    }

    private companion object {
        const val HAMMER: String = "hammer"
        const val TIMEOUT_MILLIS: Long = 30_000

        val CATALOGUE: List<Lot> = listOf(
            Lot("lot-1", "Brass telescope", 10_000),
            Lot("lot-2", "Walnut writing desk", 4_000),
        )

        val AUCTION_MAPPER: ObjectMapper = jacksonObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
    }
}
