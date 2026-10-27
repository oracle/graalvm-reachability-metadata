/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_content_negotiation_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.protobuf.protobuf
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises Protocol Buffers request conversion and negotiated replies for a sensor telemetry gateway.
 */
@OptIn(ExperimentalSerializationApi::class)
@Timeout(60)
class ProtobufTelemetryIngestionTest {
    @Test
    fun uploadsBinarySamplesAndReadsThemBackForDashboardAndDeviceConsumers(): Unit = testApplication {
        val latestBatch: AtomicReference<TelemetryBatch?> = AtomicReference(null)
        application {
            install(ContentNegotiation) {
                protobuf()
                json()
            }
            routing {
                post("/telemetry") {
                    val batch: TelemetryBatch = call.receive<TelemetryBatch>()
                    require(batch.samples.isNotEmpty())
                    latestBatch.set(batch)
                    call.respond(
                        HttpStatusCode.Created,
                        UploadReceipt(
                            batch.deviceId,
                            batch.samples.size,
                            batch.samples.minOf { it.temperatureMilliCelsius },
                            batch.samples.maxOf { it.temperatureMilliCelsius }
                        )
                    )
                }
                get("/telemetry/latest") {
                    call.respond(requireNotNull(latestBatch.get()))
                }
            }
        }

        val gatewayClient: HttpClient = createClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 20_000
                socketTimeoutMillis = 20_000
            }
        }
        val batch: TelemetryBatch = TelemetryBatch(
            "freezer-west-4",
            listOf(Sample(1, -18250), Sample(2, -17500), Sample(3, -19000))
        )

        // Devices send compact binary batches and receive a binary acknowledgement.
        val uploadResponse: HttpResponse = gatewayClient.post("/telemetry") {
            contentType(ContentType.Application.ProtoBuf)
            accept(ContentType.Application.ProtoBuf)
            setBody(ProtoBuf.encodeToByteArray(batch))
        }
        assertEquals(HttpStatusCode.Created, uploadResponse.status)
        assertEquals(ContentType.Application.ProtoBuf, uploadResponse.contentType()?.withoutParameters())
        val receipt: UploadReceipt = ProtoBuf.decodeFromByteArray(uploadResponse.bodyAsBytes())
        assertEquals(UploadReceipt("freezer-west-4", 3, -19000, -17500), receipt)

        // The operations dashboard views the stored binary upload as an ordinary JSON document.
        val dashboardResponse: HttpResponse = gatewayClient.get("/telemetry/latest") {
            accept(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, dashboardResponse.status)
        assertEquals(ContentType.Application.Json, dashboardResponse.contentType()?.withoutParameters())
        assertEquals(batch, Json.decodeFromString<TelemetryBatch>(dashboardResponse.bodyAsText()))

        // A device reading after the dashboard still gets its requested binary representation.
        val deviceResponse: HttpResponse = gatewayClient.get("/telemetry/latest") {
            accept(ContentType.Application.ProtoBuf)
        }
        assertEquals(HttpStatusCode.OK, deviceResponse.status)
        assertEquals(ContentType.Application.ProtoBuf, deviceResponse.contentType()?.withoutParameters())
        assertEquals(batch, ProtoBuf.decodeFromByteArray<TelemetryBatch>(deviceResponse.bodyAsBytes()))
    }

    @Serializable
    data class TelemetryBatch(
        @ProtoNumber(1) val deviceId: String,
        @ProtoNumber(2) val samples: List<Sample>
    )

    @Serializable
    data class Sample(
        @ProtoNumber(1) val sequence: Int,
        @ProtoNumber(2) val temperatureMilliCelsius: Int
    )

    @Serializable
    data class UploadReceipt(
        @ProtoNumber(1) val deviceId: String,
        @ProtoNumber(2) val acceptedSamples: Int,
        @ProtoNumber(3) val minimumMilliCelsius: Int,
        @ProtoNumber(4) val maximumMilliCelsius: Int
    )
}
