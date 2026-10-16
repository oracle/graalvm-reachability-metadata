/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_cbor_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.cbor.cbor
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/**
 * Exercises the `Cbor` format as the body codec of an `application/cbor` HTTP API through
 * Ktor content negotiation.
 *
 * A telemetry collector accepts batches of device samples over HTTP and answers with a
 * summary; both ends share one configured `Cbor` instance, the server tolerates fields a
 * newer device firmware adds, and a raw client reads the stored batch back as bytes.
 */
@OptIn(ExperimentalSerializationApi::class)
public class TelemetryCborHttpApiTest {

    @Serializable
    public data class Sample(val sensor: String, val value: Double, val unit: String = "C")

    @Serializable
    public data class Batch(val deviceId: String, val sequence: Long, val samples: List<Sample>)

    /** The batch a newer firmware sends: it carries a field the collector does not model. */
    @Serializable
    public data class BatchV2(
        val deviceId: String,
        val sequence: Long,
        val samples: List<Sample>,
        val firmware: String,
    )

    @Serializable
    public data class Summary(
        val deviceId: String,
        val accepted: Int,
        val maxBySensor: Map<String, Double>,
        val warnings: List<String> = emptyList(),
    )

    /** The format both the collector and its clients use for request and response bodies. */
    private val apiCbor: Cbor = Cbor {
        ignoreUnknownKeys = true
        encodeDefaults = false
        useDefiniteLengthEncoding = true
    }

    /** Installs the collector's routes, keeping each device's last batch in [store]. */
    private fun Application.collector(store: MutableMap<String, Batch>): Unit {
        install(ServerContentNegotiation) {
            cbor(apiCbor)
        }
        routing {
            post("/telemetry") {
                val batch: Batch = call.receive<Batch>()
                store[batch.deviceId] = batch
                val maxBySensor: Map<String, Double> = batch.samples
                    .groupBy { sample: Sample -> sample.sensor }
                    .mapValues { entry: Map.Entry<String, List<Sample>> -> entry.value.maxOf { it.value } }
                val warnings: List<String> = batch.samples
                    .filter { sample: Sample -> sample.value > 80.0 }
                    .map { sample: Sample -> "${sample.sensor} above threshold" }
                call.respond(HttpStatusCode.Created, Summary(batch.deviceId, batch.samples.size, maxBySensor, warnings))
            }
            get("/telemetry/{deviceId}") {
                val deviceId: String = call.parameters["deviceId"]!!
                val batch: Batch? = store[deviceId]
                if (batch == null) {
                    call.respond(HttpStatusCode.NotFound, "unknown device")
                } else {
                    call.respond(batch)
                }
            }
        }
    }

    private fun startCollector(store: MutableMap<String, Batch>): EmbeddedServer<*, *> =
        embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") { collector(store) }.start(wait = false)

    @Test
    fun exchangesCborBodiesWithTheCollector(): Unit = runBlocking {
        val store: MutableMap<String, Batch> = ConcurrentHashMap()
        val server: EmbeddedServer<*, *> = startCollector(store)
        val client: HttpClient = HttpClient(ClientCIO) {
            install(ClientContentNegotiation) {
                cbor(apiCbor)
            }
        }
        try {
            val port: Int = server.engine.resolvedConnectors().first().port
            val baseUrl: String = "http://127.0.0.1:$port"

            val batch: Batch = Batch(
                deviceId = "boiler-7",
                sequence = 41L,
                samples = listOf(
                    Sample("inlet", 61.5),
                    Sample("outlet", 84.25),
                    Sample("inlet", 63.0),
                    Sample("pressure", 1.8, unit = "bar"),
                ),
            )
            val created: HttpResponse = client.post("$baseUrl/telemetry") {
                contentType(ContentType.Application.Cbor)
                setBody(batch)
            }
            assertEquals(HttpStatusCode.Created, created.status)
            assertEquals(ContentType.Application.Cbor, created.contentType()?.withoutParameters())
            val summary: Summary = created.body<Summary>()
            assertEquals(
                Summary(
                    deviceId = "boiler-7",
                    accepted = 4,
                    maxBySensor = mapOf("inlet" to 63.0, "outlet" to 84.25, "pressure" to 1.8),
                    warnings = listOf("outlet above threshold"),
                ),
                summary,
            )
            assertEquals(batch, store["boiler-7"])

            val newerFirmware: BatchV2 = BatchV2(
                deviceId = "boiler-8",
                sequence = 3L,
                samples = listOf(Sample("inlet", 20.0)),
                firmware = "2.4.1",
            )
            val tolerated: Summary = client.post("$baseUrl/telemetry") {
                contentType(ContentType.Application.Cbor)
                setBody(newerFirmware)
            }.body<Summary>()
            assertEquals(Summary("boiler-8", 1, mapOf("inlet" to 20.0)), tolerated)
            assertEquals(Batch("boiler-8", 3L, listOf(Sample("inlet", 20.0))), store["boiler-8"])

            val stored: HttpResponse = client.get("$baseUrl/telemetry/boiler-7")
            assertEquals(HttpStatusCode.OK, stored.status)
            val raw: ByteArray = stored.bodyAsBytes()
            // A definite-length CBOR map with three entries starts with major type 5, length 3.
            assertEquals(0xA3, raw[0].toInt() and 0xFF)
            assertArrayEquals(apiCbor.encodeToByteArray(batch), raw)
            assertEquals(batch, apiCbor.decodeFromByteArray<Batch>(raw))

            val missing: HttpResponse = client.get("$baseUrl/telemetry/unknown")
            assertEquals(HttpStatusCode.NotFound, missing.status)
        } finally {
            client.close()
            server.stop(gracePeriodMillis = 0L, timeoutMillis = 5_000L)
        }
    }
}
