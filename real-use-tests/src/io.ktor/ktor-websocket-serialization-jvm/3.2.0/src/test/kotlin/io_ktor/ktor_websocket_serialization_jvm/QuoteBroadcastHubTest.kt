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
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.FrameTooBigException
import io.ktor.websocket.close
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.websocket.WebSockets as ServerWebSockets

/**
 * Multi-session typed broadcast, where a quote hub keeps the server sessions of
 * subscribed clients and a publisher outside any handler fans kotlinx JSON messages
 * out to them with `sendSerialized`, while each handler learns that its client is
 * gone when `receiveDeserialized` ends with a closed channel.
 *
 * A subscription whose frame exceeds the server's `maxFrameSize` is never decoded:
 * the handler's typed receive fails with `FrameTooBigException`, and the client's
 * ends with a closed channel after a `TOO_BIG` close.
 */
public class QuoteBroadcastHubTest {
    /** The first message every client sends: who it is and which symbols it follows. */
    @Serializable
    data class Subscribe(val clientId: String, val symbols: Set<String>)

    /** Everything the hub sends to a subscriber, told apart by the `type` discriminator. */
    @Serializable
    sealed class HubEvent {
        @Serializable
        @SerialName("subscribed")
        data class Subscribed(val clientId: String, val symbols: List<String>) : HubEvent()

        @Serializable
        @SerialName("tick")
        data class Tick(val sequence: Int, val symbol: String, val priceCents: Long) : HubEvent()
    }

    /** How a server handler saw its session end. */
    data class Departure(val clientId: String, val failure: String, val reason: CloseReason?)

    /** A subscriber the hub fans quotes out to, holding its live server session. */
    class Subscriber(val session: DefaultWebSocketServerSession, val symbols: Set<String>)

    /** The application's subscription registry and the publisher that feeds it. */
    class QuoteHub {
        val subscribers: ConcurrentHashMap<String, Subscriber> = ConcurrentHashMap()
        val departures: ConcurrentHashMap<String, CompletableDeferred<Departure>> = ConcurrentHashMap()
        val rejected: CompletableDeferred<FrameTooBigException> = CompletableDeferred()
        private var sequence: Int = 0

        fun departureOf(clientId: String): CompletableDeferred<Departure> =
            departures.computeIfAbsent(clientId) { CompletableDeferred() }

        /** Sends one tick to every subscriber of [symbol] and returns how many got it. */
        suspend fun publish(symbol: String, priceCents: Long): Int {
            sequence++
            val tick: HubEvent = HubEvent.Tick(sequence, symbol, priceCents)
            var delivered: Int = 0
            for (subscriber: Subscriber in subscribers.values) {
                if (symbol in subscriber.symbols) {
                    subscriber.session.sendSerialized(tick)
                    delivered++
                }
            }
            return delivered
        }

        suspend fun serve(session: DefaultWebSocketServerSession) {
            val subscribe: Subscribe = try {
                session.receiveDeserialized<Subscribe>()
            } catch (tooBig: FrameTooBigException) {
                rejected.complete(tooBig)
                return
            }
            subscribers[subscribe.clientId] = Subscriber(session, subscribe.symbols)
            val ack: HubEvent = HubEvent.Subscribed(subscribe.clientId, subscribe.symbols.sorted())
            session.sendSerialized(ack)
            try {
                // A subscriber only listens; the next typed receive ends when it leaves.
                session.receiveDeserialized<Subscribe>()
            } catch (closed: ClosedReceiveChannelException) {
                subscribers.remove(subscribe.clientId)
                val reason: CloseReason? = session.closeReason.await()
                val departure = Departure(subscribe.clientId, closed::class.java.simpleName, reason)
                departureOf(subscribe.clientId).complete(departure)
            }
        }
    }

    private lateinit var hub: QuoteHub
    private lateinit var server: EmbeddedServer<*, *>
    private var port: Int = 0

    @BeforeEach
    fun startQuoteServer() {
        hub = QuoteHub()
        server = embeddedServer(ServerCIO, port = 0, host = HOST) {
            install(ServerWebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(HUB_JSON)
                maxFrameSize = MAX_FRAME_BYTES
            }
            routing {
                webSocket("/quotes") {
                    hub.serve(this)
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().single().port }
    }

    @AfterEach
    fun stopQuoteServer() {
        server.stop(gracePeriodMillis = 100, timeoutMillis = 5_000)
    }

    @Test
    fun `publisher fans ticks out to the sessions subscribed to each symbol`(): Unit = runBlocking {
        val wanted: Map<String, Set<String>> = linkedMapOf(
            "alice" to setOf("MSFT", "AAPL"),
            "bob" to setOf("MSFT"),
            "carol" to setOf("GOOG", "AAPL"),
        )
        val expectedTicks: Map<String, Int> = mapOf("alice" to 3, "bob" to 1, "carol" to 3)
        val acks: MutableMap<String, HubEvent> = ConcurrentHashMap()
        val ready: Map<String, CompletableDeferred<Unit>> = wanted.keys.associateWith { CompletableDeferred() }
        val deliveries: MutableList<Int> = mutableListOf()
        lateinit var received: Map<String, List<HubEvent>>
        val departures: MutableMap<String, Departure> = linkedMapOf()

        withTimeout(TIMEOUT_MILLIS) {
            hubClient().use { client: HttpClient ->
                coroutineScope {
                    val listeners: List<Deferred<Pair<String, List<HubEvent>>>> = wanted.map { (clientId, symbols) ->
                        async {
                            val ticks: MutableList<HubEvent> = mutableListOf()
                            client.webSocket(host = HOST, port = port, path = "/quotes") {
                                sendSerialized(Subscribe(clientId, symbols))
                                acks[clientId] = receiveDeserialized<HubEvent>()
                                ready.getValue(clientId).complete(Unit)
                                repeat(expectedTicks.getValue(clientId)) {
                                    ticks += receiveDeserialized<HubEvent>()
                                }
                                close(CloseReason(CloseReason.Codes.NORMAL, "$clientId is done"))
                            }
                            clientId to ticks.toList()
                        }
                    }
                    ready.values.awaitAll()
                    assertEquals(wanted.keys, hub.subscribers.keys)
                    deliveries += hub.publish("AAPL", 23_150)
                    deliveries += hub.publish("MSFT", 41_020)
                    deliveries += hub.publish("GOOG", 17_385)
                    deliveries += hub.publish("AAPL", 23_199)
                    deliveries += hub.publish("TSLA", 25_000)
                    received = listeners.awaitAll().toMap()
                }
            }
            for (clientId: String in wanted.keys) {
                departures[clientId] = hub.departureOf(clientId).await()
            }
        }

        assertEquals(listOf(2, 2, 1, 2, 0), deliveries)
        assertEquals(HubEvent.Subscribed("alice", listOf("AAPL", "MSFT")), acks["alice"])
        assertEquals(HubEvent.Subscribed("bob", listOf("MSFT")), acks["bob"])
        assertEquals(HubEvent.Subscribed("carol", listOf("AAPL", "GOOG")), acks["carol"])
        assertEquals(
            listOf(
                HubEvent.Tick(1, "AAPL", 23_150),
                HubEvent.Tick(2, "MSFT", 41_020),
                HubEvent.Tick(4, "AAPL", 23_199),
            ),
            received["alice"],
        )
        assertEquals(listOf(HubEvent.Tick(2, "MSFT", 41_020)), received["bob"])
        assertEquals(
            listOf(
                HubEvent.Tick(1, "AAPL", 23_150),
                HubEvent.Tick(3, "GOOG", 17_385),
                HubEvent.Tick(4, "AAPL", 23_199),
            ),
            received["carol"],
        )
        assertTrue(hub.subscribers.isEmpty(), "departed clients are still subscribed: ${hub.subscribers.keys}")
        for (clientId: String in wanted.keys) {
            val departure: Departure = departures.getValue(clientId)
            assertEquals("ClosedReceiveChannelException", departure.failure)
            assertEquals(CloseReason(CloseReason.Codes.NORMAL, "$clientId is done"), departure.reason)
        }
    }

    @Test
    fun `oversized subscription is closed as too big before it is decoded`(): Unit = runBlocking {
        var clientFailure: Throwable? = null
        var observedClose: CloseReason? = null
        lateinit var rejection: FrameTooBigException

        withTimeout(TIMEOUT_MILLIS) {
            hubClient().use { client: HttpClient ->
                client.webSocket(host = HOST, port = port, path = "/quotes") {
                    sendSerialized(Subscribe("bot-" + "x".repeat(MAX_FRAME_BYTES.toInt()), setOf("AAPL")))
                    clientFailure = runCatching { receiveDeserialized<HubEvent>() }.exceptionOrNull()
                    observedClose = closeReason.await()
                }
            }
            rejection = hub.rejected.await()
        }

        assertTrue(clientFailure is ClosedReceiveChannelException, "client receive ended with $clientFailure")
        assertEquals(CloseReason.Codes.TOO_BIG, observedClose?.knownReason)
        assertTrue(rejection.frameSize > MAX_FRAME_BYTES, "rejected frame size ${rejection.frameSize}")
        assertTrue(hub.subscribers.isEmpty(), "rejected client was subscribed: ${hub.subscribers.keys}")
    }

    private fun hubClient(): HttpClient = HttpClient(ClientCIO) {
        install(ClientWebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(HUB_JSON)
        }
    }

    private companion object {
        const val HOST: String = "127.0.0.1"
        const val MAX_FRAME_BYTES: Long = 1_024
        const val TIMEOUT_MILLIS: Long = 30_000

        val HUB_JSON: Json = Json {
            classDiscriminator = "type"
        }
    }
}
