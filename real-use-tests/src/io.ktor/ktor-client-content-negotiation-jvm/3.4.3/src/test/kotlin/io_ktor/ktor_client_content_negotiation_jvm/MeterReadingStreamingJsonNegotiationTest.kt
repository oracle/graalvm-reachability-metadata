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
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.contentType
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Streaming JSON arrays through the kotlinx.serialization JSON extension of the client
 * ContentNegotiation plugin, as a utility metering application uploads meter readings as a
 * `Flow` request body and reads a meter's usage history as a lazily decoded `Sequence`.
 */
public class MeterReadingStreamingJsonNegotiationTest {

    @Serializable
    data class MeterReading(val meterId: String, val intervalStart: String, val kilowattHours: Double)

    @Serializable
    data class UploadReceipt(val batchId: String, val readings: Int, val totalKilowattHours: Double)

    @Serializable
    data class UsageInterval(val day: String, val kilowattHours: Double, val estimated: Boolean = false)

    /** What the metering service saw of one upload. */
    data class ReceivedUpload(
        val contentType: String?,
        val contentLength: String?,
        val transferEncoding: String?,
        val body: String,
    )

    /** The metering service; it answers with fixed JSON so the client's decoding is what is checked. */
    class MeteringService : AutoCloseable {
        val uploads: MutableList<ReceivedUpload> = CopyOnWriteArrayList()

        private val server: EmbeddedServer<*, *> = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                post("/readings") {
                    val body: String = call.receiveText()
                    uploads += ReceivedUpload(
                        call.request.contentType().toString(),
                        call.request.header(HttpHeaders.ContentLength),
                        call.request.header(HttpHeaders.TransferEncoding),
                        body,
                    )
                    call.respondText(
                        """{"batchId":"B-2024-07-01","readings":3,"totalKilowattHours":4.75}""",
                        ContentType.Application.Json,
                        HttpStatusCode.Accepted,
                    )
                }
                get("/meters/{meterId}/usage") {
                    call.respondText(
                        """
                        [
                          {"day":"2024-06-28","kilowattHours":11.2},
                          {"day":"2024-06-29","kilowattHours":9.8,"estimated":true},
                          {"day":"2024-06-30","kilowattHours":12.5,"tariff":"night"}
                        ]
                        """.trimIndent(),
                        ContentType.Application.Json,
                    )
                }
                get("/meters/{meterId}/usage-feed") {
                    call.respondText(
                        """
                        {"day":"2024-07-01","kilowattHours":10.1}
                        {"day":"2024-07-02","kilowattHours":8.4,"estimated":true}
                        """.trimIndent(),
                        ContentType.Application.Json,
                    )
                }
                get("/meters/{meterId}/usage-corrupted") {
                    call.respondText(
                        """[{"day":"2024-07-03","kilowattHours":7.9},{"day":"2024-07-04","kilowattHours":"n/a"}]""",
                        ContentType.Application.Json,
                    )
                }
            }
        }

        val baseUrl: String

        init {
            server.start(wait = false)
            val port: Int = runBlocking { server.engine.resolvedConnectors().first().port }
            baseUrl = "http://127.0.0.1:$port"
        }

        override fun close() {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 2_000)
        }
    }

    /** The JSON format the metering application shares with the service. */
    private val meteringJson: Json = Json {
        ignoreUnknownKeys = true
    }

    private fun meteringClient(baseUrl: String): HttpClient = HttpClient(ClientCIO) {
        install(ContentNegotiation) {
            json(meteringJson)
        }
        defaultRequest { url("$baseUrl/") }
    }

    @Test
    fun readingsProducedByAFlowAreStreamedAsOneJsonArray(): Unit = runBlocking {
        MeteringService().use { service: MeteringService ->
            meteringClient(service.baseUrl).use { client: HttpClient ->
                val readings: Flow<MeterReading> = flow {
                    emit(MeterReading("MTR-114", "2024-07-01T00:00", 1.25))
                    emit(MeterReading("MTR-114", "2024-07-01T00:15", 1.5))
                    emit(MeterReading("MTR-114", "2024-07-01T00:30", 2.0))
                }

                val response: HttpResponse = client.post("readings") {
                    contentType(ContentType.Application.Json)
                    setBody(readings)
                }

                assertThat(response.status).isEqualTo(HttpStatusCode.Accepted)
                val receipt: UploadReceipt = response.body()
                assertThat(receipt).isEqualTo(UploadReceipt("B-2024-07-01", 3, 4.75))

                val upload: ReceivedUpload = service.uploads.single()
                assertThat(upload.contentType).startsWith("application/json")
                assertThat(upload.contentLength).isNull()
                assertThat(upload.transferEncoding).isEqualTo("chunked")
                assertThat(upload.body).startsWith("[").endsWith("]")
                assertThat(meteringJson.decodeFromString(ListSerializer(MeterReading.serializer()), upload.body))
                    .containsExactly(
                        MeterReading("MTR-114", "2024-07-01T00:00", 1.25),
                        MeterReading("MTR-114", "2024-07-01T00:15", 1.5),
                        MeterReading("MTR-114", "2024-07-01T00:30", 2.0),
                    )
            }
        }
    }

    @Test
    fun anEmptyFlowIsSentAsAnEmptyJsonArray(): Unit = runBlocking {
        MeteringService().use { service: MeteringService ->
            meteringClient(service.baseUrl).use { client: HttpClient ->
                val nothingToReport: Flow<MeterReading> = emptyList<MeterReading>().asFlow()

                client.post("readings") {
                    contentType(ContentType.Application.Json)
                    setBody(nothingToReport)
                }

                assertThat(service.uploads.single().body).isEqualTo("[]")
            }
        }
    }

    @Test
    fun usageHistoryIsDecodedAsALazySequence(): Unit = runBlocking {
        MeteringService().use { service: MeteringService ->
            meteringClient(service.baseUrl).use { client: HttpClient ->
                val history: Sequence<UsageInterval> = client.get("meters/MTR-114/usage").body()

                val days: List<UsageInterval> = history.toList()
                assertThat(days).containsExactly(
                    UsageInterval("2024-06-28", 11.2),
                    UsageInterval("2024-06-29", 9.8, estimated = true),
                    UsageInterval("2024-06-30", 12.5),
                )
                assertThatThrownBy { history.toList() }.isInstanceOf(IllegalStateException::class.java)
            }
        }
    }

    @Test
    fun aWhitespaceSeparatedFeedIsDecodedIntoTheSameSequence(): Unit = runBlocking {
        MeteringService().use { service: MeteringService ->
            meteringClient(service.baseUrl).use { client: HttpClient ->
                val feed: Sequence<UsageInterval> = client.get("meters/MTR-114/usage-feed").body()

                assertThat(feed.toList()).containsExactly(
                    UsageInterval("2024-07-01", 10.1),
                    UsageInterval("2024-07-02", 8.4, estimated = true),
                )
            }
        }
    }

    @Test
    fun aCorruptedIntervalFailsOnlyWhenTheSequenceReachesIt(): Unit = runBlocking {
        MeteringService().use { service: MeteringService ->
            meteringClient(service.baseUrl).use { client: HttpClient ->
                val history: Sequence<UsageInterval> = client.get("meters/MTR-114/usage-corrupted").body()
                val iterator: Iterator<UsageInterval> = history.iterator()

                assertThat(iterator.next()).isEqualTo(UsageInterval("2024-07-03", 7.9))
                assertThatThrownBy { iterator.next() }.isInstanceOf(SerializationException::class.java)
            }
        }
    }
}
