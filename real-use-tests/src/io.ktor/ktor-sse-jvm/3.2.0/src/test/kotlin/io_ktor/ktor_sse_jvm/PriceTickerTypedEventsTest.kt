/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_sse_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.deserialize
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.send
import io.ktor.server.sse.sse
import io.ktor.sse.TypedServerSentEvent
import io.ktor.util.reflect.TypeInfo
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.sse.SSE as ClientSSE

/**
 * Serializes typed server-sent events into data fields and deserializes them on the client.
 *
 * A trading service streams price quotes and a market-status notice as JSON
 * payloads of `TypedServerSentEvent`s, and a dashboard client turns each
 * event's data back into the matching Kotlin type by its event name.
 */
public class PriceTickerTypedEventsTest {

    /** A price for one instrument at a point in the trading session. */
    @Serializable
    private data class Quote(
        val symbol: String,
        val priceCents: Long,
        val sequence: Int,
    )

    /** A session-wide notice that the market changed state. */
    @Serializable
    private data class MarketStatus(
        val open: Boolean,
        val reason: String,
    )

    /** What the dashboard observed for one event, with its payload already decoded. */
    private data class Received(
        val event: String?,
        val id: String?,
        val retry: Long?,
        val payload: Any,
    )

    private val json: Json = Json { encodeDefaults = true }

    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var client: HttpClient
    private var port: Int = 0

    private val quotes: List<Quote> = listOf(
        Quote(symbol = "ACME", priceCents = 12_345L, sequence = 1),
        Quote(symbol = "GLOBEX", priceCents = 9_870L, sequence = 2),
        Quote(symbol = "ACME", priceCents = 12_401L, sequence = 3),
    )

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
                sse("/ticker", serialize = { typeInfo: TypeInfo, value: Any -> encode(typeInfo, value) }) {
                    send(MarketStatus(open = true, reason = "session start"), event = "status", retry = 2_000L)
                    for (quote: Quote in quotes) {
                        send(
                            TypedServerSentEvent(
                                data = quote,
                                event = "quote",
                                id = "q-${quote.sequence}",
                            ),
                        )
                    }
                    send(MarketStatus(open = false, reason = "circuit breaker"), event = "status", id = "s-2")
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

    @Test
    fun `dashboard decodes each typed event by its event name`() = runBlocking {
        val received: MutableList<Received> = mutableListOf()

        client.sse(
            host = "127.0.0.1",
            port = port,
            path = "/ticker",
            deserialize = { typeInfo: TypeInfo, text: String -> decode(typeInfo, text) },
        ) {
            incoming.collect { event: TypedServerSentEvent<String> ->
                val payload: Any = when (event.event) {
                    "quote" -> deserialize<Quote>(event)!!
                    "status" -> deserialize<MarketStatus>(event.data)!!
                    else -> error("Unexpected event ${event.event}")
                }
                received.add(Received(event = event.event, id = event.id, retry = event.retry, payload = payload))
            }
        }

        assertEquals(
            listOf(
                Received(
                    event = "status",
                    id = null,
                    retry = 2_000L,
                    payload = MarketStatus(open = true, reason = "session start"),
                ),
                Received(event = "quote", id = "q-1", retry = null, payload = quotes[0]),
                Received(event = "quote", id = "q-2", retry = null, payload = quotes[1]),
                Received(event = "quote", id = "q-3", retry = null, payload = quotes[2]),
                Received(
                    event = "status",
                    id = "s-2",
                    retry = null,
                    payload = MarketStatus(open = false, reason = "circuit breaker"),
                ),
            ),
            received,
        )
        val lastPrices: Map<String, Long> = received
            .map { it.payload }
            .filterIsInstance<Quote>()
            .associate { it.symbol to it.priceCents }
        assertEquals(mapOf("ACME" to 12_401L, "GLOBEX" to 9_870L), lastPrices)
    }

    @Test
    fun `typed payloads travel as JSON data fields`() = runBlocking {
        val body: String = HttpClient(ClientCIO).use { plainClient: HttpClient ->
            plainClient.get("http://127.0.0.1:$port/ticker").bodyAsText()
        }

        assertEquals(
            listOf(
                "data: {\"open\":true,\"reason\":\"session start\"}",
                "data: {\"symbol\":\"ACME\",\"priceCents\":12345,\"sequence\":1}",
                "data: {\"symbol\":\"GLOBEX\",\"priceCents\":9870,\"sequence\":2}",
                "data: {\"symbol\":\"ACME\",\"priceCents\":12401,\"sequence\":3}",
                "data: {\"open\":false,\"reason\":\"circuit breaker\"}",
            ),
            body.split("\r\n").filter { line: String -> line.startsWith("data: ") },
        )
    }
}
