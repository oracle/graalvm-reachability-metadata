/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_sse_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.ClientSSESessionWithDeserialization
import io.ktor.client.plugins.sse.deserialize
import io.ktor.client.plugins.sse.sseSession
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.send
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import io.ktor.sse.TypedServerSentEvent
import io.ktor.util.reflect.TypeInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.sse.SSE as ClientSSE

/**
 * Streams a sealed hierarchy of domain events and generic collection payloads as typed server-sent events.
 *
 * An order service publishes an order's timeline: a snapshot of its lines as a
 * `List`, then lifecycle events from a sealed `OrderEvent` hierarchy that the
 * route serializes through the base type's polymorphic serializer. A tracking
 * client holds a typed `sseSession` with a deserializer, decodes every event as
 * the sealed base, and folds them into the order's current state, skipping
 * subtypes a newer publisher added that its model does not know yet.
 */
public class OrderTimelinePolymorphicEventsTest {

    /** One line of an order. */
    @Serializable
    private data class OrderLine(val sku: String, val quantity: Int, val unitCents: Long)

    /** A lifecycle step of an order; the JSON carries the subtype in its `type` field. */
    @Serializable
    private sealed class OrderEvent {
        abstract val orderId: String

        @Serializable
        @SerialName("placed")
        data class Placed(override val orderId: String, val customer: String) : OrderEvent()

        @Serializable
        @SerialName("paid")
        data class Paid(override val orderId: String, val amountCents: Long, val method: String) : OrderEvent()

        @Serializable
        @SerialName("shipped")
        data class Shipped(override val orderId: String, val carrier: String, val trackingCode: String) : OrderEvent()

        @Serializable
        @SerialName("cancelled")
        data class Cancelled(override val orderId: String, val reason: String) : OrderEvent()
    }

    /** What the tracking page shows after folding the timeline. */
    private data class OrderView(
        val lines: List<OrderLine> = emptyList(),
        val customer: String? = null,
        val paidCents: Long = 0L,
        val tracking: String? = null,
        val status: String = "unknown",
        val lastEventId: String? = null,
    )

    private val json: Json = Json { encodeDefaults = true }

    private val lines: List<OrderLine> = listOf(
        OrderLine(sku = "BOOK-1", quantity = 2, unitCents = 1_250L),
        OrderLine(sku = "LAMP-7", quantity = 1, unitCents = 3_999L),
    )

    private val timeline: List<OrderEvent> = listOf(
        OrderEvent.Placed(orderId = "o-42", customer = "ada"),
        OrderEvent.Paid(orderId = "o-42", amountCents = 6_499L, method = "card"),
        OrderEvent.Shipped(orderId = "o-42", carrier = "PostNord", trackingCode = "PN123"),
    )

    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var client: HttpClient
    private var port: Int = 0

    private fun encode(typeInfo: TypeInfo, value: Any): String {
        val serializer: KSerializer<Any?> = json.serializersModule.serializer(typeInfo.kotlinType!!)
        return json.encodeToString(serializer, value)
    }

    private fun decode(typeInfo: TypeInfo, text: String): Any {
        val serializer: KSerializer<Any?> = json.serializersModule.serializer(typeInfo.kotlinType!!)
        return json.decodeFromString(serializer, text)!!
    }

    @BeforeEach
    fun startServer() {
        server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(SSE)
            routing {
                sse("/orders/o-42/timeline", serialize = { typeInfo: TypeInfo, value: Any -> encode(typeInfo, value) }) {
                    send(lines, event = "snapshot", id = "0")
                    timeline.forEachIndexed { index: Int, step: OrderEvent ->
                        send(TypedServerSentEvent(data = step, event = "order", id = "${index + 1}"))
                    }
                    val late: OrderEvent = OrderEvent.Cancelled(orderId = "o-42", reason = "address rejected")
                    send(late, event = "order", id = "4")
                }
                // A newer publisher already emits a subtype this client's model does not have.
                sse("/v2/orders/o-42/timeline") {
                    send(ServerSentEvent("{\"type\":\"placed\",\"orderId\":\"o-42\",\"customer\":\"grace\"}", "order", "1"))
                    send(
                        ServerSentEvent(
                            "{\"type\":\"paid\",\"orderId\":\"o-42\",\"amountCents\":2500,\"method\":\"iban\"}",
                            "order",
                            "2",
                        ),
                    )
                    send(ServerSentEvent("{\"type\":\"refunded\",\"orderId\":\"o-42\",\"amountCents\":500}", "order", "3"))
                    send(
                        ServerSentEvent(
                            "{\"type\":\"shipped\",\"orderId\":\"o-42\",\"carrier\":\"DHL\",\"trackingCode\":\"D9\"}",
                            "order",
                            "4",
                        ),
                    )
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
        client = HttpClient(ClientCIO) {
            install(ClientSSE)
        }
    }

    @AfterEach
    fun stopServer() {
        client.close()
        server.stop(gracePeriodMillis = 0L, timeoutMillis = 10_000L)
    }

    /** Applies one decoded event to the tracking view. */
    private fun fold(view: OrderView, step: OrderEvent, id: String?): OrderView = when (step) {
        is OrderEvent.Placed -> view.copy(customer = step.customer, status = "placed", lastEventId = id)
        is OrderEvent.Paid -> view.copy(paidCents = step.amountCents, status = "paid", lastEventId = id)
        is OrderEvent.Shipped -> view.copy(tracking = "${step.carrier}:${step.trackingCode}", status = "shipped", lastEventId = id)
        is OrderEvent.Cancelled -> view.copy(status = "cancelled: ${step.reason}", lastEventId = id)
    }

    @Test
    fun `tracking client decodes the sealed timeline through a typed session`() = runBlocking {
        val session: ClientSSESessionWithDeserialization = client.sseSession(
            host = "127.0.0.1",
            port = port,
            path = "/orders/o-42/timeline",
            deserialize = { typeInfo: TypeInfo, text: String -> decode(typeInfo, text) },
        )
        val received: List<TypedServerSentEvent<String>> = withTimeout(10_000L) { session.incoming.toList() }

        var view: OrderView = OrderView()
        val decodedSteps: MutableList<OrderEvent> = mutableListOf()
        for (event: TypedServerSentEvent<String> in received) {
            when (event.event) {
                "snapshot" -> view = view.copy(lines = session.deserialize<List<OrderLine>>(event)!!, lastEventId = event.id)
                "order" -> {
                    val step: OrderEvent = session.deserialize<OrderEvent>(event.data)!!
                    decodedSteps.add(step)
                    view = fold(view, step, event.id)
                }
                else -> error("Unexpected event ${event.event}")
            }
        }
        session.cancel()
        val sessionJob: Job = requireNotNull(session.coroutineContext[Job])
        withTimeout(10_000L) { sessionJob.join() }

        assertEquals(listOf("0", "1", "2", "3", "4"), received.map { it.id })
        assertEquals(timeline + OrderEvent.Cancelled(orderId = "o-42", reason = "address rejected"), decodedSteps)
        assertEquals(
            OrderView(
                lines = lines,
                customer = "ada",
                paidCents = 6_499L,
                tracking = "PostNord:PN123",
                status = "cancelled: address rejected",
                lastEventId = "4",
            ),
            view,
        )
        assertEquals(view.paidCents, view.lines.sumOf { it.quantity * it.unitCents })
        assertTrue(received.drop(1).all { it.data!!.startsWith("{\"type\":") }, received.toString())
        assertTrue(received.first().data!!.startsWith("[{\"sku\":\"BOOK-1\""), received.first().data)
    }

    @Test
    fun `an older client skips subtypes it does not know and keeps folding the rest`() = runBlocking {
        val session: ClientSSESessionWithDeserialization = client.sseSession(
            host = "127.0.0.1",
            port = port,
            path = "/v2/orders/o-42/timeline",
            deserialize = { typeInfo: TypeInfo, text: String -> decode(typeInfo, text) },
        )
        val received: List<TypedServerSentEvent<String>> = withTimeout(10_000L) { session.incoming.toList() }
        session.cancel()

        var view: OrderView = OrderView()
        val skipped: MutableList<String?> = mutableListOf()
        for (event: TypedServerSentEvent<String> in received) {
            try {
                view = fold(view, session.deserialize<OrderEvent>(event)!!, event.id)
            } catch (unknown: SerializationException) {
                skipped.add(event.id)
            }
        }

        assertEquals(listOf("1", "2", "3", "4"), received.map { it.id })
        assertEquals(listOf("3"), skipped)
        assertTrue(received[2].data!!.contains("\"type\":\"refunded\""), received[2].data)
        assertEquals(
            OrderView(customer = "grace", paidCents = 2_500L, tracking = "DHL:D9", status = "shipped", lastEventId = "4"),
            view,
        )
    }
}
