/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_websockets_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.converter
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets

/**
 * Covers typed frame conversion through the plugin's content converter: an order-tracking feed
 * receives a subscription as a JSON object, streams a sealed hierarchy of tracking events back
 * with `sendSerialized`, and answers a frame it cannot deserialize with a typed error reply.
 */
class TypedFrameConversionTest {

    /** What a dashboard sends to follow a set of orders. */
    @Serializable
    data class Subscription(val customer: String, val orderIds: List<Long>)

    /** One event on the tracking feed; the `type` discriminator names the subclass. */
    @Serializable
    sealed interface TrackingEvent {
        val orderId: Long

        @Serializable
        @SerialName("dispatched")
        data class Dispatched(override val orderId: Long, val carrier: String) : TrackingEvent

        @Serializable
        @SerialName("delivered")
        data class Delivered(override val orderId: Long, val signedBy: String?) : TrackingEvent

        @Serializable
        @SerialName("rejected")
        data class Rejected(override val orderId: Long, val reason: String) : TrackingEvent
    }

    /** Builds the tracking events the feed knows for an order. */
    private object Warehouse {

        fun history(customer: String, orderId: Long): List<TrackingEvent> = when (orderId % 3) {
            0L -> listOf(
                TrackingEvent.Dispatched(orderId, "PostNord"),
                TrackingEvent.Delivered(orderId, customer),
            )
            1L -> listOf(TrackingEvent.Dispatched(orderId, "DHL"))
            else -> listOf(TrackingEvent.Rejected(orderId, "address incomplete"))
        }
    }

    @Test
    fun streamsTypedEventsForEachSubscribedOrder(): Unit = testApplication {
        installTrackingFeed()
        val client: HttpClient = jsonClient()
        val received: MutableList<TrackingEvent> = mutableListOf()
        var serverConverterSeen = false

        client.webSocket("/tracking") {
            sendSerialized(Subscription(customer = "Ada Lovelace", orderIds = listOf(300L, 301L, 302L)))
            repeat(EVENTS_FOR_300_301_302) {
                received += receiveDeserialized<TrackingEvent>()
            }
            serverConverterSeen = receiveDeserialized<Boolean>()
            close(CloseReason(CloseReason.Codes.NORMAL, "dashboard closed"))
        }

        assertEquals(
            listOf(
                TrackingEvent.Dispatched(300L, "PostNord"),
                TrackingEvent.Delivered(300L, "Ada Lovelace"),
                TrackingEvent.Dispatched(301L, "DHL"),
                TrackingEvent.Rejected(302L, "address incomplete"),
            ),
            received,
        )
        assertTrue(serverConverterSeen, "the feed should see the converter the plugin was configured with")
    }

    @Test
    fun answersAFrameItCannotDeserializeWithATypedError(): Unit = testApplication {
        installTrackingFeed()
        val client: HttpClient = jsonClient()
        var error: TrackingEvent? = null
        var afterRetry: TrackingEvent? = null

        client.webSocket("/tracking") {
            outgoing.send(Frame.Text("""{"customer": "Grace Hopper", "orderIds": "not-a-list"}"""))
            error = receiveDeserialized<TrackingEvent>()
            sendSerialized(Subscription(customer = "Grace Hopper", orderIds = listOf(400L)))
            afterRetry = receiveDeserialized<TrackingEvent>()
            receiveDeserialized<Boolean>()
            close(CloseReason(CloseReason.Codes.NORMAL, "dashboard closed"))
        }

        val rejected: TrackingEvent.Rejected = error as TrackingEvent.Rejected
        assertEquals(UNKNOWN_ORDER, rejected.orderId)
        assertTrue(rejected.reason.startsWith("unreadable subscription"), rejected.reason)
        assertEquals(TrackingEvent.Dispatched(400L, "DHL"), afterRetry)
    }

    private fun ApplicationTestBuilder.installTrackingFeed() {
        application {
            install(WebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(Json)
            }
            routing {
                webSocket("/tracking") {
                    while (true) {
                        val subscription: Subscription = try {
                            receiveDeserialized<Subscription>()
                        } catch (failure: SerializationException) {
                            val event: TrackingEvent = TrackingEvent.Rejected(
                                UNKNOWN_ORDER,
                                "unreadable subscription: ${failure.message}",
                            )
                            sendSerialized(event)
                            continue
                        }
                        for (orderId: Long in subscription.orderIds) {
                            for (event: TrackingEvent in Warehouse.history(subscription.customer, orderId)) {
                                sendSerialized(event)
                            }
                        }
                        sendSerialized(converter is KotlinxWebsocketSerializationConverter)
                        break
                    }
                }
            }
        }
    }

    private fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient {
        install(ClientWebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(Json)
        }
    }

    private companion object {

        const val EVENTS_FOR_300_301_302: Int = 4

        const val UNKNOWN_ORDER: Long = -1L
    }
}
