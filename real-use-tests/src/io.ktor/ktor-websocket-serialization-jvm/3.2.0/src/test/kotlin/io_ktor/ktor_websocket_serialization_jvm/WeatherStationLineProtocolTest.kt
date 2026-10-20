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
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.WebsocketContentConverter
import io.ktor.serialization.WebsocketConverterNotFoundException
import io.ktor.serialization.WebsocketDeserializeException
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import io.ktor.util.reflect.TypeInfo
import io.ktor.websocket.Frame
import io.ktor.websocket.FrameType
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.charset.Charset
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.websocket.WebSockets as ServerWebSockets

/**
 * An application-defined `WebsocketContentConverter` behind `sendSerialized` and
 * `receiveDeserialized`, encoding in the charset negotiated through `Accept-Charset`, including
 * the library's type-mismatch, null-content and frame-type rejections.
 *
 * Legacy weather stations speak a pipe-delimited line protocol in Latin-1 binary frames. The
 * collector acknowledges every reading, answers an empty heartbeat with its status, and reports
 * frames it cannot accept as text.
 */
public class WeatherStationLineProtocolTest {
    /** A temperature reading one station reports. */
    data class Reading(val station: String, val celsius: Double, val note: String)

    /** The collector's acknowledgement: readings accepted so far and the warmest one. */
    data class Ack(val accepted: Int, val maxCelsius: Double, val warmestStation: String)

    /** A maintenance command; the collector does not accept these on the reading feed. */
    data class Command(val name: String)

    /** Encodes the line protocol into binary frames in the charset the session is given. */
    object StationLineConverter : WebsocketContentConverter {
        override suspend fun serialize(charset: Charset, typeInfo: TypeInfo, value: Any?): Frame {
            val line: String = when (value) {
                null -> EMPTY
                is Reading -> listOf(READING, value.station, value.celsius, value.note).joinToString(SEPARATOR)
                is Ack -> listOf(ACK, value.accepted, value.maxCelsius, value.warmestStation).joinToString(SEPARATOR)
                is Command -> listOf(COMMAND, value.name).joinToString(SEPARATOR)
                else -> throw IllegalArgumentException("No line format for ${typeInfo.type.simpleName}")
            }
            return Frame.Binary(true, line.toByteArray(charset))
        }

        override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: Frame): Any? {
            val fields: List<String> = content.readBytes().toString(charset).split(SEPARATOR)
            return when (fields.first()) {
                EMPTY -> null
                READING -> Reading(fields[1], fields[2].toDouble(), fields[3])
                ACK -> Ack(fields[1].toInt(), fields[2].toDouble(), fields[3])
                COMMAND -> Command(fields[1])
                else -> throw WebsocketDeserializeException("Unknown line kind ${fields.first()}", frame = content)
            }
        }

        override fun isApplicable(frame: Frame): Boolean = frame is Frame.Binary
    }

    private lateinit var server: EmbeddedServer<*, *>
    private var port: Int = 0

    @BeforeEach
    fun startCollector() {
        server = embeddedServer(ServerCIO, port = 0, host = HOST) {
            install(ServerWebSockets) {
                contentConverter = StationLineConverter
            }
            routing {
                webSocket("/stations") {
                    var accepted: Int = 0
                    var warmest: Reading? = null
                    try {
                        while (true) {
                            val reading: Reading? = try {
                                receiveDeserialized<Reading?>()
                            } catch (rejected: WebsocketDeserializeException) {
                                send(Frame.Text("rejected ${rejected.frame.frameType.name}: ${rejected.message}"))
                                continue
                            }
                            if (reading != null) {
                                accepted++
                                if (warmest == null || reading.celsius > warmest.celsius) {
                                    warmest = reading
                                }
                            }
                            val status: Ack? = warmest?.let { Ack(accepted, it.celsius, it.station) }
                            sendSerialized<Ack?>(status)
                        }
                    } catch (closed: ClosedReceiveChannelException) {
                        // The station hung up.
                    }
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().single().port }
    }

    @AfterEach
    fun stopCollector() {
        server.stop(gracePeriodMillis = 100, timeoutMillis = 5_000)
    }

    @Test
    fun `station feed is acknowledged and unacceptable frames are reported`(): Unit = runBlocking {
        var statusBeforeReadings: Ack? = Ack(-1, 0.0, "unset")
        val acks: MutableList<Ack> = mutableListOf()
        val rejections: MutableList<String> = mutableListOf()

        withTimeout(TIMEOUT_MILLIS) {
            stationClient().use { client: HttpClient ->
                client.webSocket(host = HOST, port = port, path = "/stations", request = { latin1() }) {
                    sendSerialized<Reading?>(null)
                    statusBeforeReadings = receiveDeserialized<Ack?>()

                    for (reading: Reading in READINGS) {
                        sendSerialized(reading)
                        acks += receiveDeserialized<Ack>()
                    }
                    sendSerialized<Reading?>(null)
                    acks += receiveDeserialized<Ack>()

                    sendSerialized(Command("reboot"))
                    rejections += assertInstanceOf(Frame.Text::class.java, incoming.receive()).readText()
                    send(Frame.Text("READING|Basel|19.0|typed by hand"))
                    rejections += assertInstanceOf(Frame.Text::class.java, incoming.receive()).readText()

                    sendSerialized(Reading("Basel", 19.0, "after the rejections"))
                    acks += receiveDeserialized<Ack>()
                }
            }
        }

        assertNull(statusBeforeReadings)
        assertEquals(
            listOf(
                Ack(1, 21.5, "Z\u00fcrich"),
                Ack(2, 24.0, "Gen\u00e8ve"),
                Ack(3, 24.0, "Gen\u00e8ve"),
                Ack(3, 24.0, "Gen\u00e8ve"),
                Ack(4, 24.0, "Gen\u00e8ve"),
            ),
            acks,
        )
        assertEquals(
            listOf(
                "rejected BINARY: Can't deserialize value: expected value of type Reading, got Command",
                "rejected TEXT: Converter doesn't support frame type TEXT",
            ),
            rejections,
        )
    }

    @Test
    fun `the negotiated charset decides the bytes on the wire`(): Unit = runBlocking {
        var latin1Ack: ByteArray? = null
        var defaultAck: ByteArray? = null

        withTimeout(TIMEOUT_MILLIS) {
            stationClient().use { client: HttpClient ->
                client.webSocket(host = HOST, port = port, path = "/stations", request = { latin1() }) {
                    val deviceLine: ByteArray = "READING|Z\u00fcrich|21.5|F\u00f6hn".toByteArray(Charsets.ISO_8859_1)
                    send(Frame.Binary(true, deviceLine))
                    latin1Ack = assertInstanceOf(Frame.Binary::class.java, incoming.receive()).readBytes()
                }
                client.webSocket(host = HOST, port = port, path = "/stations") {
                    sendSerialized(Reading("Z\u00fcrich", 21.5, "F\u00f6hn"))
                    defaultAck = assertInstanceOf(Frame.Binary::class.java, incoming.receive()).readBytes()
                }
            }
        }

        val prefix: List<Byte> = "ACK|1|21.5|Z".toByteArray(Charsets.US_ASCII).toList()
        val suffix: List<Byte> = "rich".toByteArray(Charsets.US_ASCII).toList()
        assertEquals(prefix + 0xFC.toByte() + suffix, latin1Ack!!.toList())
        assertEquals(prefix + 0xC3.toByte() + 0xBC.toByte() + suffix, defaultAck!!.toList())
    }

    @Test
    fun `non-null receive of an empty status frame is rejected`(): Unit = runBlocking {
        var failure: WebsocketDeserializeException? = null

        withTimeout(TIMEOUT_MILLIS) {
            stationClient().use { client: HttpClient ->
                client.webSocket(host = HOST, port = port, path = "/stations", request = { latin1() }) {
                    sendSerialized<Reading?>(null)
                    try {
                        receiveDeserialized<Ack>()
                    } catch (rejected: WebsocketDeserializeException) {
                        failure = rejected
                    }
                }
            }
        }

        val rejected: WebsocketDeserializeException = checkNotNull(failure) { "the receive was not rejected" }
        assertEquals("Frame has null content", rejected.message)
        assertEquals(FrameType.BINARY, rejected.frame.frameType)
        assertArrayEquals(EMPTY.toByteArray(Charsets.ISO_8859_1), rejected.frame.readBytes())
    }

    @Test
    fun `client without a converter cannot send typed readings`(): Unit = runBlocking {
        var failure: WebsocketConverterNotFoundException? = null
        var statusAfterRawHeartbeat: Frame? = null

        withTimeout(TIMEOUT_MILLIS) {
            HttpClient(ClientCIO) { install(ClientWebSockets) }.use { client: HttpClient ->
                client.webSocket(host = HOST, port = port, path = "/stations") {
                    try {
                        sendSerialized(READINGS.first())
                    } catch (missing: WebsocketConverterNotFoundException) {
                        failure = missing
                    }
                    send(Frame.Binary(true, EMPTY.toByteArray(Charsets.US_ASCII)))
                    statusAfterRawHeartbeat = incoming.receive()
                }
            }
        }

        val missing: WebsocketConverterNotFoundException = checkNotNull(failure) { "the send found a converter" }
        assertTrue(missing.message.orEmpty().contains("converter", ignoreCase = true), missing.message)
        val status: Frame.Binary = assertInstanceOf(Frame.Binary::class.java, statusAfterRawHeartbeat)
        assertEquals(EMPTY, status.readBytes().toString(Charsets.US_ASCII))
    }

    private fun HttpRequestBuilder.latin1() {
        header(HttpHeaders.AcceptCharset, STATION_CHARSET.name())
    }

    private fun stationClient(): HttpClient = HttpClient(ClientCIO) {
        install(ClientWebSockets) {
            contentConverter = StationLineConverter
        }
    }

    private companion object {
        const val HOST: String = "127.0.0.1"
        const val TIMEOUT_MILLIS: Long = 30_000
        const val SEPARATOR: String = "|"
        const val EMPTY: String = "EMPTY"
        const val READING: String = "READING"
        const val ACK: String = "ACK"
        const val COMMAND: String = "COMMAND"

        val STATION_CHARSET: Charset = Charsets.ISO_8859_1

        val READINGS: List<Reading> = listOf(
            Reading("Z\u00fcrich", 21.5, "F\u00f6hn"),
            Reading("Gen\u00e8ve", 24.0, "bise calme"),
            Reading("Bern", 18.25, "clear"),
        )
    }
}
