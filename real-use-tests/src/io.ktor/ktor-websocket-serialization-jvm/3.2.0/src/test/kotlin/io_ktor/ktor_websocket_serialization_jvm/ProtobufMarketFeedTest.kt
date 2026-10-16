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
import io.ktor.serialization.kotlinx.protobuf.DefaultProtoBuf
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoIntegerType
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.protobuf.ProtoPacked
import kotlinx.serialization.protobuf.ProtoType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.websocket.WebSockets as ServerWebSockets

/**
 * Protocol Buffers market data over WebSockets, where traders subscribe to a quote
 * feed with `sendSerialized` and `receiveDeserialized` through the kotlinx WebSocket
 * converter built on Ktor's `DefaultProtoBuf`.
 *
 * Messages carry explicit field numbers, numbered enum entries, zigzag-encoded
 * signed prices and packed size lists. The scenario checks the frames byte for byte
 * against hand-assembled protobuf, so a client written in another language could
 * talk to the same feed.
 */
@OptIn(ExperimentalSerializationApi::class)
public class ProtobufMarketFeedTest {
    /** Which side of the book a quote stands on. */
    @Serializable
    enum class Side {
        @ProtoNumber(1)
        BID,

        @ProtoNumber(2)
        ASK,
    }

    /** A trader's request for the top `depth` quotes of each symbol. */
    @Serializable
    data class Subscribe(
        @ProtoNumber(1) val symbols: List<String>,
        @ProtoNumber(2) val depth: Int,
    )

    /** One price level: the price offset in ticks from the reference and the resting order sizes. */
    @Serializable
    data class Quote(
        @ProtoNumber(1) val symbol: String,
        @ProtoNumber(2) val side: Side,
        @ProtoNumber(3) @ProtoType(ProtoIntegerType.SIGNED) val priceTicks: Long,
        @ProtoNumber(4) @ProtoPacked val sizes: List<Int>,
        @ProtoNumber(5) val venue: String = "",
    )

    private lateinit var server: EmbeddedServer<*, *>
    private var port: Int = 0

    @BeforeEach
    fun startFeed() {
        server = embeddedServer(ServerCIO, port = 0, host = HOST) {
            install(ServerWebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(DefaultProtoBuf)
            }
            routing {
                webSocket("/quotes") {
                    val request: Subscribe = receiveDeserialized<Subscribe>()
                    val unknown: String? = request.symbols.firstOrNull { symbol: String -> symbol !in BOOK }
                    if (unknown != null) {
                        close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "unknown symbol $unknown"))
                        return@webSocket
                    }
                    var sent: Int = 0
                    for (symbol: String in request.symbols) {
                        for (quote: Quote in BOOK.getValue(symbol).take(request.depth)) {
                            sendSerialized(quote)
                            sent++
                        }
                    }
                    close(CloseReason(CloseReason.Codes.NORMAL, "sent $sent quotes"))
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().single().port }
    }

    @AfterEach
    fun stopFeed() {
        server.stop(gracePeriodMillis = 100, timeoutMillis = 5_000)
    }

    @Test
    fun `trader receives the top of book for every subscribed symbol`(): Unit = runBlocking {
        val received: MutableList<Quote> = mutableListOf()
        var observedClose: CloseReason? = null

        withTimeout(TIMEOUT_MILLIS) {
            traderClient().use { client: HttpClient ->
                client.webSocket(host = HOST, port = port, path = "/quotes") {
                    sendSerialized(Subscribe(listOf("ACME", "INIT"), depth = 2))
                    repeat(4) { received += receiveDeserialized<Quote>() }
                    observedClose = closeReason.await()
                }
            }
        }

        assertEquals(BOOK.getValue("ACME").take(2) + BOOK.getValue("INIT").take(2), received)
        assertEquals("", received[0].venue)
        assertEquals("XNAS", received[1].venue)
        assertEquals(-3L, received[2].priceTicks)
        assertEquals(CloseReason(CloseReason.Codes.NORMAL, "sent 4 quotes"), observedClose)
    }

    @Test
    fun `feed accepts a hand-assembled subscription and answers with standard protobuf bytes`(): Unit =
        runBlocking {
            // Field 1 (length-delimited) "INIT", then field 2 (varint) 1.
            val subscription: ByteArray = bytes(0x0A, 0x04, 'I', 'N', 'I', 'T', 0x10, 0x01)
            var quoteBytes: ByteArray? = null
            var observedClose: CloseReason? = null

            withTimeout(TIMEOUT_MILLIS) {
                traderClient().use { client: HttpClient ->
                    client.webSocket(host = HOST, port = port, path = "/quotes") {
                        send(Frame.Binary(fin = true, data = subscription))
                        quoteBytes = assertInstanceOf(Frame.Binary::class.java, incoming.receive()).data
                        observedClose = closeReason.await()
                    }
                }
            }

            // "INIT", ASK (2), zigzag(-3) = 5, packed sizes [10, 20], and the empty default
            // venue, which DefaultProtoBuf still encodes.
            val expected: ByteArray = bytes(
                0x0A, 0x04, 'I', 'N', 'I', 'T',
                0x10, 0x02,
                0x18, 0x05,
                0x22, 0x02, 0x0A, 0x14,
                0x2A, 0x00,
            )
            assertArrayEquals(expected, quoteBytes)
            assertEquals(
                Quote("INIT", Side.ASK, -3, listOf(10, 20)),
                DefaultProtoBuf.decodeFromByteArray<Quote>(quoteBytes!!),
            )
            assertEquals(CloseReason(CloseReason.Codes.NORMAL, "sent 1 quotes"), observedClose)
        }

    @Test
    fun `feed refuses a subscription to a symbol it does not list`(): Unit = runBlocking {
        var observedClose: CloseReason? = null

        withTimeout(TIMEOUT_MILLIS) {
            traderClient().use { client: HttpClient ->
                client.webSocket(host = HOST, port = port, path = "/quotes") {
                    sendSerialized(Subscribe(listOf("ACME", "VOID"), depth = 1))
                    observedClose = closeReason.await()
                }
            }
        }

        assertEquals(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "unknown symbol VOID"), observedClose)
    }

    private fun traderClient(): HttpClient = HttpClient(ClientCIO) {
        install(ClientWebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(DefaultProtoBuf)
        }
    }

    private companion object {
        const val HOST: String = "127.0.0.1"
        const val TIMEOUT_MILLIS: Long = 30_000

        val BOOK: Map<String, List<Quote>> = mapOf(
            "ACME" to listOf(
                Quote("ACME", Side.BID, 12, listOf(300, 150)),
                Quote("ACME", Side.ASK, 14, listOf(75), venue = "XNAS"),
                Quote("ACME", Side.BID, 11, listOf(1_000)),
            ),
            "INIT" to listOf(
                Quote("INIT", Side.ASK, -3, listOf(10, 20)),
                Quote("INIT", Side.BID, -5, listOf(40_000), venue = "XLON"),
            ),
        )

        /** Builds a byte array from wire bytes given as integers or ASCII characters. */
        fun bytes(vararg parts: Any): ByteArray = ByteArray(parts.size) { index: Int ->
            when (val part: Any = parts[index]) {
                is Int -> part.toByte()
                is Char -> part.code.toByte()
                else -> error("unsupported wire part $part")
            }
        }
    }
}
