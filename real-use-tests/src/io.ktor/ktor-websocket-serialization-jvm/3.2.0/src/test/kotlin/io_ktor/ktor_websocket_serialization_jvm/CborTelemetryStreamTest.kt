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
import io.ktor.serialization.WebsocketDeserializeException
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
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
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.websocket.WebSockets as ServerWebSockets

/**
 * Binary CBOR telemetry over WebSockets, where sensors stream kotlinx-serialization
 * batches as binary frames through `sendSerialized` and `receiveDeserialized` with
 * the kotlinx CBOR WebSocket converter, and a frame the converter cannot read is
 * rejected through `WebsocketDeserializeException`.
 *
 * The collector acknowledges every batch with running statistics, closes normally
 * after the final batch, and closes with `CANNOT_ACCEPT` when a sensor sends text.
 */
@OptIn(ExperimentalSerializationApi::class)
public class CborTelemetryStreamTest {
    /** A batch of readings one sensor uploads. */
    @Serializable
    data class SensorBatch(val sensorId: String, val readings: List<Double>, val last: Boolean = false)

    /** The collector's running statistics for one sensor after a batch. */
    @Serializable
    data class BatchAck(
        val sensorId: String,
        val batchesSeen: Int,
        val readingCount: Int,
        val minimum: Double,
        val maximum: Double,
    )

    private lateinit var server: EmbeddedServer<*, *>
    private var port: Int = 0

    @BeforeEach
    fun startCollector() {
        server = embeddedServer(ServerCIO, port = 0, host = HOST) {
            install(ServerWebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(TELEMETRY_CBOR)
            }
            routing {
                webSocket("/telemetry") {
                    val readings: MutableList<Double> = mutableListOf()
                    var batchesSeen: Int = 0
                    while (true) {
                        val batch: SensorBatch = try {
                            receiveDeserialized<SensorBatch>()
                        } catch (rejected: WebsocketDeserializeException) {
                            val frameType: String = rejected.frame.frameType.name
                            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "binary CBOR only, got $frameType"))
                            break
                        }
                        batchesSeen++
                        readings += batch.readings
                        sendSerialized(
                            BatchAck(batch.sensorId, batchesSeen, readings.size, readings.min(), readings.max()),
                        )
                        if (batch.last) {
                            close(CloseReason(CloseReason.Codes.NORMAL, "stored ${readings.size} readings"))
                            break
                        }
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
    fun `sensor streams batches and receives running statistics until the final batch`(): Unit = runBlocking {
        val batches: List<SensorBatch> = listOf(
            SensorBatch("boiler-1", listOf(71.5, 72.25)),
            SensorBatch("boiler-1", listOf(69.0)),
            SensorBatch("boiler-1", listOf(74.75, 70.5), last = true),
        )
        val acks: MutableList<BatchAck> = mutableListOf()
        var observedClose: CloseReason? = null

        withTimeout(TIMEOUT_MILLIS) {
            sensorClient().use { client: HttpClient ->
                client.webSocket(host = HOST, port = port, path = "/telemetry") {
                    for (batch: SensorBatch in batches) {
                        sendSerialized(batch)
                        acks += receiveDeserialized<BatchAck>()
                    }
                    observedClose = closeReason.await()
                }
            }
        }

        assertEquals(
            listOf(
                BatchAck("boiler-1", 1, 2, 71.5, 72.25),
                BatchAck("boiler-1", 2, 3, 69.0, 72.25),
                BatchAck("boiler-1", 3, 5, 69.0, 74.75),
            ),
            acks,
        )
        assertEquals(CloseReason(CloseReason.Codes.NORMAL, "stored 5 readings"), observedClose)
    }

    @Test
    fun `acknowledgements travel as binary CBOR frames that a plain decoder can read`(): Unit = runBlocking {
        var ackBytes: ByteArray? = null

        withTimeout(TIMEOUT_MILLIS) {
            sensorClient().use { client: HttpClient ->
                client.webSocket(host = HOST, port = port, path = "/telemetry") {
                    sendSerialized(SensorBatch("chiller-7", listOf(4.5, 3.75), last = true))
                    val reply: Frame = incoming.receive()
                    ackBytes = assertInstanceOf(Frame.Binary::class.java, reply).data
                    closeReason.await()
                }
            }
        }

        val decoded: BatchAck = TELEMETRY_CBOR.decodeFromByteArray<BatchAck>(ackBytes!!)
        assertEquals(BatchAck("chiller-7", 1, 2, 3.75, 4.5), decoded)
    }

    @Test
    fun `collector rejects a text frame with a cannot-accept close`(): Unit = runBlocking {
        var firstAck: BatchAck? = null
        var observedClose: CloseReason? = null

        withTimeout(TIMEOUT_MILLIS) {
            sensorClient().use { client: HttpClient ->
                client.webSocket(host = HOST, port = port, path = "/telemetry") {
                    sendSerialized(SensorBatch("freezer-2", listOf(-18.0)))
                    firstAck = receiveDeserialized<BatchAck>()
                    send(Frame.Text("""{"sensorId":"freezer-2","readings":[-17.5]}"""))
                    observedClose = closeReason.await()
                }
            }
        }

        assertEquals(BatchAck("freezer-2", 1, 1, -18.0, -18.0), firstAck)
        assertEquals(
            CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "binary CBOR only, got TEXT"),
            observedClose,
        )
    }

    private fun sensorClient(): HttpClient = HttpClient(ClientCIO) {
        install(ClientWebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(TELEMETRY_CBOR)
        }
    }

    private companion object {
        const val HOST: String = "127.0.0.1"
        const val TIMEOUT_MILLIS: Long = 30_000

        val TELEMETRY_CBOR: Cbor = Cbor {
            encodeDefaults = true
        }
    }
}
