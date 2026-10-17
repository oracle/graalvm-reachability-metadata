/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_content_negotiation_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.cbor.cbor
import io.ktor.serialization.kotlinx.protobuf.protobuf
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.contentType
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.protobuf.ProtoPacked
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Binary content negotiation through the client ContentNegotiation plugin with the kotlinx.serialization
 * CBOR and ProtoBuf converters, as a sensor gateway uploads reading batches as CBOR, fetches device
 * calibrations as ProtoBuf messages, and pushes corrected calibrations back to a telemetry service.
 */
@OptIn(ExperimentalSerializationApi::class)
public class SensorGatewayBinaryNegotiationTest {

    enum class MeasureUnit { CELSIUS, PASCAL, PERCENT }

    @Serializable
    data class Reading(val sensorId: String, val unit: MeasureUnit, val value: Double, val takenAtEpochSecond: Long)

    /** One upload from the gateway; the firmware signature travels as a CBOR byte string. */
    @Serializable
    class ReadingBatch(
        val gatewayId: String,
        val readings: List<Reading>,
        @ByteString val firmwareSignature: ByteArray,
    )

    @Serializable
    data class IngestSummary(val stored: Int, val rejectedSensorIds: List<String> = emptyList(), val maxBySensor: Map<String, Double>)

    /** A device calibration as the telemetry service's `.proto` schema numbers it. */
    @Serializable
    data class Calibration(
        @ProtoNumber(1) val deviceId: String,
        @ProtoNumber(2) val revision: Int,
        @ProtoNumber(3) val offset: Double,
        @ProtoNumber(4) @ProtoPacked val thresholds: List<Int>,
        @ProtoNumber(5) val unit: MeasureUnit = MeasureUnit.CELSIUS,
        @ProtoNumber(6) val note: String = "",
    )

    @Serializable
    data class CalibrationAck(@ProtoNumber(1) val deviceId: String, @ProtoNumber(2) val acceptedRevision: Int)

    /** What the telemetry service saw of one incoming request. */
    class ReceivedRequest(val path: String, val accept: String?, val contentType: String?, val body: ByteArray)

    /** The telemetry service; it answers with bytes it encodes itself so the client's decoding is what is checked. */
    class TelemetryService : AutoCloseable {
        val received: MutableList<ReceivedRequest> = CopyOnWriteArrayList()

        private val server: EmbeddedServer<*, *> = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                post("/readings") {
                    val body: ByteArray = call.receive()
                    record(call.request.local.uri, call.request.header(HttpHeaders.Accept), call.request.contentType().toString(), body)
                    val summary = IngestSummary(
                        stored = 3,
                        rejectedSensorIds = listOf("hum-9"),
                        maxBySensor = mapOf("temp-1" to 21.5, "press-2" to 101_325.0),
                    )
                    call.respondBytes(Cbor.encodeToByteArray(IngestSummary.serializer(), summary), ContentType.Application.Cbor, HttpStatusCode.Accepted)
                }
                get("/calibrations/{deviceId}") {
                    record(call.request.local.uri, call.request.header(HttpHeaders.Accept), null, ByteArray(0))
                    val calibration = Calibration(
                        deviceId = call.parameters["deviceId"]!!,
                        revision = 7,
                        offset = -0.25,
                        thresholds = listOf(-10, 0, 35, 60),
                        unit = MeasureUnit.CELSIUS,
                        note = "factory recalibrated",
                    )
                    call.respondBytes(ProtoBuf.encodeToByteArray(Calibration.serializer(), calibration), ContentType.Application.ProtoBuf)
                }
                put("/calibrations/{deviceId}") {
                    val body: ByteArray = call.receive()
                    record(call.request.local.uri, call.request.header(HttpHeaders.Accept), call.request.contentType().toString(), body)
                    val pushed: Calibration = ProtoBuf.decodeFromByteArray(Calibration.serializer(), body)
                    val ack = CalibrationAck(pushed.deviceId, pushed.revision)
                    call.respondBytes(ProtoBuf.encodeToByteArray(CalibrationAck.serializer(), ack), ContentType.Application.ProtoBuf)
                }
            }
        }

        val baseUrl: String

        init {
            server.start(wait = false)
            val port: Int = runBlocking { server.engine.resolvedConnectors().first().port }
            baseUrl = "http://127.0.0.1:$port"
        }

        private fun record(path: String, accept: String?, contentType: String?, body: ByteArray) {
            received += ReceivedRequest(path, accept, contentType, body)
        }

        override fun close() {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 2_000)
        }
    }

    private fun gatewayClient(baseUrl: String): HttpClient = HttpClient(ClientCIO) {
        install(ContentNegotiation) {
            cbor()
            protobuf()
        }
        defaultRequest { url("$baseUrl/") }
    }

    @Test
    fun aReadingBatchIsUploadedAsCborAndTheSummaryDecoded(): Unit = runBlocking {
        TelemetryService().use { service: TelemetryService ->
            gatewayClient(service.baseUrl).use { client: HttpClient ->
                val signature: ByteArray = byteArrayOf(0x0A, 0x1B, 0x2C, 0x3D, 0x7F)
                val batch = ReadingBatch(
                    gatewayId = "gw-north",
                    readings = listOf(
                        Reading("temp-1", MeasureUnit.CELSIUS, 21.5, 1_760_000_000),
                        Reading("press-2", MeasureUnit.PASCAL, 101_325.0, 1_760_000_005),
                        Reading("hum-9", MeasureUnit.PERCENT, 140.0, 1_760_000_010),
                    ),
                    firmwareSignature = signature,
                )
                val response: HttpResponse = client.post("readings") {
                    contentType(ContentType.Application.Cbor)
                    setBody(batch)
                }

                assertThat(response.status).isEqualTo(HttpStatusCode.Accepted)
                val summary: IngestSummary = response.body()
                assertThat(summary).isEqualTo(
                    IngestSummary(3, listOf("hum-9"), mapOf("temp-1" to 21.5, "press-2" to 101_325.0)),
                )

                val seen: ReceivedRequest = service.received.single()
                assertThat(seen.contentType).isEqualTo("application/cbor")
                assertThat(seen.accept).contains("application/cbor").contains("application/protobuf")
                val uploaded: ReadingBatch = Cbor.decodeFromByteArray(ReadingBatch.serializer(), seen.body)
                assertThat(uploaded.gatewayId).isEqualTo("gw-north")
                assertThat(uploaded.readings).isEqualTo(batch.readings)
                assertThat(uploaded.firmwareSignature).isEqualTo(signature)
                // A CBOR map header (major type 5) opens the body: the wire format is binary, not JSON.
                assertThat(seen.body[0].toInt() and 0xE0).isEqualTo(0xA0)
            }
        }
    }

    @Test
    fun aCalibrationIsFetchedAsAProtoBufMessage(): Unit = runBlocking {
        TelemetryService().use { service: TelemetryService ->
            gatewayClient(service.baseUrl).use { client: HttpClient ->
                val response: HttpResponse = client.get("calibrations/temp-1") {
                    accept(ContentType.Application.ProtoBuf)
                }

                val calibration: Calibration = response.body()
                assertThat(calibration).isEqualTo(
                    Calibration("temp-1", 7, -0.25, listOf(-10, 0, 35, 60), MeasureUnit.CELSIUS, "factory recalibrated"),
                )
                assertThat(service.received.single().accept).startsWith("application/protobuf")
            }
        }
    }

    @Test
    fun aCorrectedCalibrationIsPushedAsProtoBufWithItsFieldNumbers(): Unit = runBlocking {
        TelemetryService().use { service: TelemetryService ->
            gatewayClient(service.baseUrl).use { client: HttpClient ->
                val corrected = Calibration("press-2", 8, 12.75, listOf(90_000, 110_000), MeasureUnit.PASCAL)
                val ack: CalibrationAck = client.put("calibrations/press-2") {
                    contentType(ContentType.Application.ProtoBuf)
                    setBody(corrected)
                }.body()

                assertThat(ack).isEqualTo(CalibrationAck("press-2", 8))

                val seen: ReceivedRequest = service.received.single()
                assertThat(seen.contentType).isEqualTo("application/protobuf")
                assertThat(ProtoBuf.decodeFromByteArray(Calibration.serializer(), seen.body)).isEqualTo(corrected)
                // Field 1 as a length-delimited string: tag byte (1 << 3) | 2, then the length of "press-2".
                assertThat(seen.body[0].toInt()).isEqualTo(0x0A)
                assertThat(seen.body[1].toInt()).isEqualTo("press-2".length)
            }
        }
    }
}
