/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_java_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.body
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.encodedPath
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.HexFormat
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Custom client plugins built with createClientPlugin and HttpSend interceptors over the Java
 * engine, as a fleet-telematics application signs its calls to a vehicle API, encodes telemetry
 * as line protocol, decodes vehicle status replies, and falls back to the legacy API version.
 */
public class FleetTelematicsCustomPluginTest {

    /** A position report the application sends for one vehicle. */
    data class Telemetry(val vin: String, val speedKph: Int, val fuelPercent: Int)

    /** The decoded status reply of one vehicle. */
    data class VehicleStatus(val vin: String, val latitude: Double, val longitude: Double, val speedKph: Int, val api: String)

    class SigningConfig {
        var keyId: String = ""
        var secret: String = ""
        var clock: () -> Long = { 0L }
    }

    class CorrelationConfig {
        var prefix: String = "req"
        var echoMismatches: MutableList<String> = CopyOnWriteArrayList()
    }

    companion object {
        const val SECRET: String = "fleet-shared-secret"
        const val FIXED_TIME: Long = 1_760_000_000L

        fun hmac(secret: String, text: String): String {
            val mac: Mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            return HexFormat.of().formatHex(mac.doFinal(text.toByteArray(Charsets.UTF_8)))
        }

        fun canonical(method: String, path: String, timestamp: String, body: String): String =
            "$method\n$path\n$timestamp\n$body"

        /** Signs the final request, after the URL is resolved and the body is encoded. */
        val RequestSigning: ClientPlugin<SigningConfig> = createClientPlugin("RequestSigning", ::SigningConfig) {
            val keyId: String = pluginConfig.keyId
            val secret: String = pluginConfig.secret
            val clock: () -> Long = pluginConfig.clock
            on(Send) { request: HttpRequestBuilder ->
                val timestamp: String = clock().toString()
                val body: String = (request.body as? TextContent)?.text ?: ""
                val signed: String = canonical(request.method.value, request.url.encodedPath, timestamp, body)
                request.headers["X-Key-Id"] = keyId
                request.headers["X-Timestamp"] = timestamp
                request.headers["X-Signature"] = hmac(secret, signed)
                proceed(request)
            }
        }

        /** Tags each logical request with a correlation ID and checks the service echoes it. */
        val CorrelationIds: ClientPlugin<CorrelationConfig> = createClientPlugin("CorrelationIds", ::CorrelationConfig) {
            val prefix: String = pluginConfig.prefix
            val mismatches: MutableList<String> = pluginConfig.echoMismatches
            val counter: AtomicInteger = AtomicInteger()
            onRequest { request: HttpRequestBuilder, _: Any ->
                request.headers["X-Correlation-Id"] = "$prefix-${counter.incrementAndGet()}"
            }
            onResponse { response: HttpResponse ->
                val sent: String? = response.call.request.headers["X-Correlation-Id"]
                val echoed: String? = response.headers["X-Correlation-Id"]
                if (sent != echoed) {
                    mismatches += "sent=$sent echoed=$echoed"
                }
            }
        }

        /** Encodes telemetry as line protocol and decodes the key=value status replies. */
        val TelematicsCodec: ClientPlugin<Unit> = createClientPlugin("TelematicsCodec") {
            transformRequestBody { _: HttpRequestBuilder, content: Any, _ ->
                if (content is Telemetry) {
                    val line: String = "telemetry,vin=${content.vin} speed=${content.speedKph},fuel=${content.fuelPercent}"
                    TextContent(line, ContentType.Text.Plain)
                } else {
                    null
                }
            }
            transformResponseBody { _: HttpResponse, content: ByteReadChannel, requestedType ->
                if (requestedType.type == VehicleStatus::class) {
                    val fields: Map<String, String> = content.toByteArray().decodeToString().split(';')
                        .associate { pair: String -> pair.substringBefore('=') to pair.substringAfter('=') }
                    VehicleStatus(
                        fields.getValue("vin"),
                        fields.getValue("lat").toDouble(),
                        fields.getValue("lon").toDouble(),
                        fields.getValue("speed").toInt(),
                        fields.getValue("api"),
                    )
                } else {
                    null
                }
            }
        }

        /** Replays a v2 call against v1 when the vehicle's region still runs the legacy API. */
        val LegacyApiFallback: ClientPlugin<Unit> = createClientPlugin("LegacyApiFallback") {
            on(Send) { request: HttpRequestBuilder ->
                val first: HttpClientCall = proceed(request)
                if (first.response.status == HttpStatusCode.Gone && request.url.encodedPath.startsWith("/v2/")) {
                    request.url.encodedPath = "/v1/" + request.url.encodedPath.removePrefix("/v2/")
                    proceed(request)
                } else {
                    first
                }
            }
        }
    }

    /** The vehicle API, which verifies signatures and echoes correlation IDs. */
    class VehicleApi : AutoCloseable {
        val telemetryLines: MutableList<String> = CopyOnWriteArrayList()
        val rejected: MutableList<String> = CopyOnWriteArrayList()
        val correlationIdsByPath: MutableList<String> = CopyOnWriteArrayList()
        private val legacyRegion: Set<String> = setOf("VIN-NORDIC-7")

        private suspend fun verified(call: ApplicationCall, body: String): Boolean {
            val correlationId: String = call.request.headers["X-Correlation-Id"] ?: "none"
            call.response.header("X-Correlation-Id", correlationId)
            correlationIdsByPath += "${call.request.path()} $correlationId"
            val timestamp: String = call.request.headers["X-Timestamp"] ?: ""
            val expected: String = hmac(SECRET, canonical(call.request.httpMethod.value, call.request.path(), timestamp, body))
            val valid: Boolean = call.request.headers["X-Key-Id"] == "fleet-app" && call.request.headers["X-Signature"] == expected
            if (!valid) {
                rejected += call.request.path()
                call.respondText("bad signature", status = HttpStatusCode.Unauthorized)
            }
            return valid
        }

        private val server: EmbeddedServer<*, *> = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            routing {
                post("/v2/telemetry") {
                    val body: String = call.receiveText()
                    if (verified(call, body)) {
                        telemetryLines += body
                        call.respondText("accepted", status = HttpStatusCode.Accepted)
                    }
                }
                get("/v2/vehicles/{vin}/status") {
                    val vin: String = call.parameters["vin"]!!
                    if (verified(call, "")) {
                        if (vin in legacyRegion) {
                            call.respondText("region runs v1", status = HttpStatusCode.Gone)
                        } else {
                            call.respondText("vin=$vin;lat=52.5200;lon=13.4050;speed=64;api=v2")
                        }
                    }
                }
                get("/v1/vehicles/{vin}/status") {
                    val vin: String = call.parameters["vin"]!!
                    if (verified(call, "")) {
                        call.respondText("vin=$vin;lat=59.9139;lon=10.7522;speed=0;api=v1")
                    }
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

    /** The client the telematics module ships; the fallback is installed first so its replays get signed. */
    private fun fleetClient(baseUrl: String, secret: String, mismatches: MutableList<String>): HttpClient = HttpClient(Java) {
        defaultRequest { url("$baseUrl/v2/") }
        install(LegacyApiFallback)
        install(TelematicsCodec)
        install(CorrelationIds) {
            prefix = "fleet"
            echoMismatches = mismatches
        }
        install(RequestSigning) {
            keyId = "fleet-app"
            this.secret = secret
            clock = { FIXED_TIME }
        }
    }

    @Test
    fun sendsSignedTelemetryEncodedAsLineProtocol(): Unit = runBlocking {
        VehicleApi().use { api: VehicleApi ->
            val mismatches: MutableList<String> = CopyOnWriteArrayList()
            fleetClient(api.baseUrl, SECRET, mismatches).use { client: HttpClient ->
                val response: HttpResponse = client.post("telemetry") { setBody(Telemetry("VIN-BERLIN-1", 64, 71)) }
                assertThat(response.status).isEqualTo(HttpStatusCode.Accepted)
                assertThat(response.bodyAsText()).isEqualTo("accepted")
                assertThat(response.call.request.headers["X-Timestamp"]).isEqualTo(FIXED_TIME.toString())
                assertThat(response.call.request.content.contentType?.withoutParameters()).isEqualTo(ContentType.Text.Plain)
            }
            assertThat(api.telemetryLines).containsExactly("telemetry,vin=VIN-BERLIN-1 speed=64,fuel=71")
            assertThat(api.rejected).isEmpty()
            assertThat(mismatches).isEmpty()
        }
    }

    @Test
    fun theServiceRejectsRequestsSignedWithTheWrongSecret(): Unit = runBlocking {
        VehicleApi().use { api: VehicleApi ->
            val mismatches: MutableList<String> = CopyOnWriteArrayList()
            fleetClient(api.baseUrl, "rotated-out-secret", mismatches).use { client: HttpClient ->
                val response: HttpResponse = client.post("telemetry") { setBody(Telemetry("VIN-BERLIN-1", 10, 50)) }
                assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
                assertThat(response.bodyAsText()).isEqualTo("bad signature")
            }
            assertThat(api.telemetryLines).isEmpty()
            assertThat(api.rejected).containsExactly("/v2/telemetry")
        }
    }

    @Test
    fun decodesVehicleStatusThroughTheResponseTransformer(): Unit = runBlocking {
        VehicleApi().use { api: VehicleApi ->
            val mismatches: MutableList<String> = CopyOnWriteArrayList()
            fleetClient(api.baseUrl, SECRET, mismatches).use { client: HttpClient ->
                val status: VehicleStatus = client.get("vehicles/VIN-BERLIN-1/status").body()
                assertThat(status).isEqualTo(VehicleStatus("VIN-BERLIN-1", 52.52, 13.405, 64, "v2"))

                val raw: String = client.get("vehicles/VIN-BERLIN-1/status").body()
                assertThat(raw).isEqualTo("vin=VIN-BERLIN-1;lat=52.5200;lon=13.4050;speed=64;api=v2")
            }
            assertThat(api.correlationIdsByPath).containsExactly(
                "/v2/vehicles/VIN-BERLIN-1/status fleet-1",
                "/v2/vehicles/VIN-BERLIN-1/status fleet-2",
            )
            assertThat(mismatches).isEmpty()
        }
    }

    @Test
    fun replaysLegacyRegionCallsAgainstV1UnderOneCorrelationId(): Unit = runBlocking {
        VehicleApi().use { api: VehicleApi ->
            val mismatches: MutableList<String> = CopyOnWriteArrayList()
            fleetClient(api.baseUrl, SECRET, mismatches).use { client: HttpClient ->
                val wireAttempts: MutableList<String> = CopyOnWriteArrayList()
                client.plugin(HttpSend).intercept { request: HttpRequestBuilder ->
                    wireAttempts += "${request.method.value} ${request.url.encodedPath}"
                    execute(request)
                }

                val status: VehicleStatus = client.get("vehicles/VIN-NORDIC-7/status").body()
                assertThat(status).isEqualTo(VehicleStatus("VIN-NORDIC-7", 59.9139, 10.7522, 0, "v1"))
                assertThat(wireAttempts).containsExactly(
                    "GET /v2/vehicles/VIN-NORDIC-7/status",
                    "GET /v1/vehicles/VIN-NORDIC-7/status",
                )
            }
            assertThat(api.correlationIdsByPath).containsExactly(
                "/v2/vehicles/VIN-NORDIC-7/status fleet-1",
                "/v1/vehicles/VIN-NORDIC-7/status fleet-1",
            )
            assertThat(api.rejected).isEmpty()
            assertThat(mismatches).isEmpty()
        }
    }
}
