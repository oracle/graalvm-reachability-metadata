/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_websocket_serialization_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.cio.webSocketRaw
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
import io.ktor.serialization.WebsocketDeserializeException
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocketRaw
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
 * Typed messaging on raw WebSocket sessions, where a `webSocketRaw` ledger route calls
 * `receiveDeserialized` with the kotlinx JSON converter and handles the ping and close
 * control frames it rejects through `WebsocketDeserializeException.frame` itself.
 *
 * A raw session has no ponger and no close handshake, so the ledger answers every ping
 * with a pong carrying the same payload and settles the day when the close frame arrives.
 */
public class RawLedgerSessionTest {
    /** One booking a bookkeeper posts to the ledger. */
    @Serializable
    data class LedgerEntry(val id: String, val amountCents: Long, val memo: String? = null)

    /** The running balance the ledger answers every entry with. */
    @Serializable
    data class Balance(val lastEntry: String, val balanceCents: Long, val pingsAnswered: Int)

    /** What the ledger recorded when a bookkeeper closed the session. */
    data class Settlement(
        val closeCode: Short,
        val closeMessage: String,
        val balanceCents: Long,
        val entries: List<String>,
        val pingsAnswered: Int,
    )

    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var settlements: Channel<Settlement>
    private var port: Int = 0

    @BeforeEach
    fun startLedgerServer() {
        val recorded: Channel<Settlement> = Channel(Channel.UNLIMITED)
        settlements = recorded
        server = embeddedServer(ServerCIO, port = 0, host = HOST) {
            install(ServerWebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(LEDGER_JSON)
            }
            routing {
                webSocketRaw("/ledger") {
                    val entries: MutableList<String> = mutableListOf()
                    var balanceCents: Long = 0
                    var pingsAnswered: Int = 0
                    while (true) {
                        val entry: LedgerEntry = try {
                            receiveDeserialized<LedgerEntry>()
                        } catch (rejected: WebsocketDeserializeException) {
                            when (val control: Frame? = rejected.frame) {
                                is Frame.Ping -> {
                                    pingsAnswered++
                                    outgoing.send(Frame.Pong(control.readBytes()))
                                    continue
                                }
                                is Frame.Close -> {
                                    val reason: CloseReason = checkNotNull(control.readReason())
                                    val settledText: String = "settled $balanceCents after ${entries.size} entries"
                                    outgoing.send(Frame.Close(CloseReason(CloseReason.Codes.NORMAL, settledText)))
                                    recorded.send(
                                        Settlement(reason.code, reason.message, balanceCents, entries, pingsAnswered),
                                    )
                                    break
                                }
                                else -> throw rejected
                            }
                        }
                        balanceCents += entry.amountCents
                        entries += entry.id
                        sendSerialized(Balance(entry.id, balanceCents, pingsAnswered))
                    }
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().single().port }
    }

    @AfterEach
    fun stopLedgerServer() {
        server.stop(gracePeriodMillis = 100, timeoutMillis = 5_000)
    }

    @Test
    fun `bookkeeper posts entries around a ping and the raw ledger settles on close`(): Unit = runBlocking {
        val balances: MutableList<Balance> = mutableListOf()

        val settlement: Settlement = withTimeout(TIMEOUT_MILLIS) {
            ledgerClient().use { client: HttpClient ->
                client.webSocket(host = HOST, port = port, path = "/ledger") {
                    sendSerialized(LedgerEntry("inv-1001", 125_00, "consulting"))
                    balances += receiveDeserialized<Balance>()
                    outgoing.send(Frame.Ping("still-there".encodeToByteArray()))
                    sendSerialized(LedgerEntry("refund-17", -40_50))
                    balances += receiveDeserialized<Balance>()
                    sendSerialized(LedgerEntry("inv-1002", 9_99, "hosting"))
                    balances += receiveDeserialized<Balance>()
                    close(CloseReason(CloseReason.Codes.NORMAL, "end of day"))
                }
            }
            settlements.receive()
        }

        assertEquals(
            listOf(
                Balance("inv-1001", 125_00, 0),
                Balance("refund-17", 84_50, 1),
                Balance("inv-1002", 94_49, 1),
            ),
            balances,
        )
        assertEquals(
            Settlement(
                CloseReason.Codes.NORMAL.code,
                "end of day",
                94_49,
                listOf("inv-1001", "refund-17", "inv-1002"),
                1,
            ),
            settlement,
        )
    }

    @Test
    fun `raw client sees the ledger echo its ping and answer its close`(): Unit = runBlocking {
        var balanceText: String? = null
        var pongPayload: ByteArray? = null
        var serverClose: CloseReason? = null

        val settlement: Settlement = withTimeout(TIMEOUT_MILLIS) {
            ledgerClient().use { client: HttpClient ->
                client.webSocketRaw(HttpMethod.Get, HOST, port, "/ledger") {
                    outgoing.send(Frame.Ping(byteArrayOf(7, 3, 1)))
                    pongPayload = assertInstanceOf(Frame.Pong::class.java, incoming.receive()).readBytes()
                    outgoing.send(Frame.Text("""{"id":"cash-1","amountCents":2500}"""))
                    balanceText = assertInstanceOf(Frame.Text::class.java, incoming.receive()).readText()
                    outgoing.send(Frame.Close(CloseReason(CloseReason.Codes.GOING_AWAY, "terminal shutdown")))
                    serverClose = assertInstanceOf(Frame.Close::class.java, incoming.receive()).readReason()
                }
            }
            settlements.receive()
        }

        assertArrayEquals(byteArrayOf(7, 3, 1), pongPayload)
        assertEquals(Balance("cash-1", 2500, 1), LEDGER_JSON.decodeFromString<Balance>(balanceText!!))
        assertEquals(CloseReason(CloseReason.Codes.NORMAL, "settled 2500 after 1 entries"), serverClose)
        assertEquals(
            Settlement(CloseReason.Codes.GOING_AWAY.code, "terminal shutdown", 2500, listOf("cash-1"), 1),
            settlement,
        )
    }

    private fun ledgerClient(): HttpClient = HttpClient(ClientCIO) {
        install(ClientWebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(LEDGER_JSON)
        }
    }

    private companion object {
        const val HOST: String = "127.0.0.1"
        const val TIMEOUT_MILLIS: Long = 30_000

        val LEDGER_JSON: Json = Json {
            encodeDefaults = true
        }
    }
}
