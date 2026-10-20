/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_serialization_kotlinx_json_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.serialization.WebsocketDeserializeException
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.DefaultJson
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.websocket.WebSockets as ServerWebSockets

/**
 * Serializes typed WebSocket frames as JSON through a websocket converter built on the
 * `DefaultJson` configuration of the kotlinx JSON module.
 *
 * An auction house exposes a bidding channel: bidders send polymorphic commands as JSON text
 * frames and receive typed outcomes, while a monitoring tool sends a hand-written frame, a
 * malformed one and a binary one and reads the raw JSON the server answers with.
 */
public class AuctionWebSocketJsonTest {

    /** A command a bidder sends over the bidding channel. */
    @Serializable
    private sealed interface BidderCommand {
        /** Ask for the current state of a lot. */
        @Serializable
        @SerialName("watch")
        data class Watch(val lot: String) : BidderCommand

        /** Offer an amount for a lot. */
        @Serializable
        @SerialName("bid")
        data class PlaceBid(val lot: String, val bidder: String, @SerialName("amount_cents") val amountCents: Long) :
            BidderCommand
    }

    /** What the auction house answers with. */
    @Serializable
    private sealed interface AuctionEvent {
        /** The current leading bid on a lot. */
        @Serializable
        @SerialName("lot_status")
        data class LotStatus(
            val lot: String,
            @SerialName("leading_cents") val leadingCents: Long,
            val leader: String? = null,
            val currency: String = "EUR",
        ) : AuctionEvent

        /** A bid that became the leading one. */
        @Serializable
        @SerialName("bid_accepted")
        data class BidAccepted(val lot: String, val bidder: String, @SerialName("amount_cents") val amountCents: Long) :
            AuctionEvent

        /** A bid or frame the auction house refused. */
        @Serializable
        @SerialName("rejected")
        data class Rejected(val reason: String, @SerialName("minimum_cents") val minimumCents: Long? = null) :
            AuctionEvent
    }

    /** The wire format: Ktor's default JSON settings with an explicit discriminator. */
    private val wireJson: Json = Json(DefaultJson) {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
    }

    private val leadingBids: MutableMap<String, AuctionEvent.BidAccepted> = ConcurrentHashMap()

    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var bidderClient: HttpClient
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(ServerWebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(wireJson)
            }
            routing {
                webSocket("/auction") {
                    while (true) {
                        val command: BidderCommand = try {
                            receiveDeserialized<BidderCommand>()
                        } catch (closed: ClosedReceiveChannelException) {
                            break
                        } catch (wrongFrame: WebsocketDeserializeException) {
                            sendSerialized<AuctionEvent>(AuctionEvent.Rejected(reason = "unsupported_frame"))
                            continue
                        } catch (unknownCommand: SerializationException) {
                            sendSerialized<AuctionEvent>(AuctionEvent.Rejected(reason = "unreadable_command"))
                            continue
                        }
                        sendSerialized<AuctionEvent>(handle(command))
                    }
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
        bidderClient = HttpClient(ClientCIO) {
            install(ClientWebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(wireJson)
            }
        }
    }

    @AfterEach
    fun stopServer() {
        bidderClient.close()
        server.stop(gracePeriodMillis = 0L, timeoutMillis = 10_000L)
    }

    /** Applies one command to the auction book. */
    private fun handle(command: BidderCommand): AuctionEvent = when (command) {
        is BidderCommand.Watch -> {
            val leading: AuctionEvent.BidAccepted? = leadingBids[command.lot]
            AuctionEvent.LotStatus(
                lot = command.lot,
                leadingCents = leading?.amountCents ?: OPENING_CENTS,
                leader = leading?.bidder,
            )
        }
        is BidderCommand.PlaceBid -> {
            val minimum: Long = (leadingBids[command.lot]?.amountCents ?: OPENING_CENTS) + INCREMENT_CENTS
            if (command.amountCents < minimum) {
                AuctionEvent.Rejected(reason = "bid_too_low", minimumCents = minimum)
            } else {
                val accepted = AuctionEvent.BidAccepted(command.lot, command.bidder, command.amountCents)
                leadingBids[command.lot] = accepted
                accepted
            }
        }
    }

    @Test
    fun `bidders exchange typed commands and events over the bidding channel`() = runBlocking {
        withTimeout(30_000L) {
            bidderClient.webSocket(host = "127.0.0.1", port = port, path = "/auction") {
                sendSerialized<BidderCommand>(BidderCommand.Watch(lot = "lot-7"))
                assertEquals(
                    AuctionEvent.LotStatus(lot = "lot-7", leadingCents = OPENING_CENTS),
                    receiveDeserialized<AuctionEvent>(),
                )

                sendSerialized<BidderCommand>(BidderCommand.PlaceBid("lot-7", "ada", 12_500L))
                assertEquals(AuctionEvent.BidAccepted("lot-7", "ada", 12_500L), receiveDeserialized<AuctionEvent>())

                sendSerialized<BidderCommand>(BidderCommand.PlaceBid("lot-7", "grace", 12_600L))
                assertEquals(
                    AuctionEvent.Rejected(reason = "bid_too_low", minimumCents = 13_000L),
                    receiveDeserialized<AuctionEvent>(),
                )

                sendSerialized<BidderCommand>(BidderCommand.Watch(lot = "lot-7"))
                val status: AuctionEvent = receiveDeserialized()
                assertEquals(AuctionEvent.LotStatus("lot-7", 12_500L, leader = "ada"), status)
            }
        }
        assertEquals(mapOf("lot-7" to AuctionEvent.BidAccepted("lot-7", "ada", 12_500L)), leadingBids.toMap())
    }

    @Test
    fun `monitor sends raw frames and reads the event wire format`() = runBlocking {
        withTimeout(30_000L) {
            bidderClient.webSocket(host = "127.0.0.1", port = port, path = "/auction") {
                send(Frame.Text("""{"type":"bid","lot":"lot-3","bidder":"monitor","amount_cents":10500,"via":"ops"}"""))
                val accepted: JsonObject = readEvent(incoming.receive())
                assertEquals(setOf("type", "lot", "bidder", "amount_cents"), accepted.keys)
                assertEquals("bid_accepted", accepted.getValue("type").jsonPrimitive.content)
                assertEquals(10_500L, accepted.getValue("amount_cents").jsonPrimitive.long)

                send(Frame.Text("""{"type":"watch","lot":"lot-3"}"""))
                val status: JsonObject = readEvent(incoming.receive())
                assertEquals("lot_status", status.getValue("type").jsonPrimitive.content)
                assertEquals("monitor", status.getValue("leader").jsonPrimitive.content)
                assertEquals("EUR", status.getValue("currency").jsonPrimitive.content)

                send(Frame.Text("""{"type":"withdraw","lot":"lot-3"}"""))
                val rejected: JsonObject = readEvent(incoming.receive())
                assertEquals("rejected", rejected.getValue("type").jsonPrimitive.content)
                assertEquals("unreadable_command", rejected.getValue("reason").jsonPrimitive.content)
                assertEquals(
                    AuctionEvent.Rejected(reason = "unreadable_command"),
                    wireJson.decodeFromJsonElement(AuctionEvent.serializer(), rejected),
                )

                send(Frame.Binary(true, byteArrayOf(0x01, 0x02)))
                val unsupported: JsonObject = readEvent(incoming.receive())
                assertEquals("unsupported_frame", unsupported.getValue("reason").jsonPrimitive.content)
            }
        }
    }

    /** Reads one server event as a raw JSON object. */
    private fun readEvent(frame: Frame): JsonObject {
        assertTrue(frame is Frame.Text, "expected a text frame but got ${frame.frameType}")
        return wireJson.parseToJsonElement((frame as Frame.Text).readText()).jsonObject
    }

    private companion object {
        const val OPENING_CENTS: Long = 10_000L
        const val INCREMENT_CENTS: Long = 500L
    }
}
