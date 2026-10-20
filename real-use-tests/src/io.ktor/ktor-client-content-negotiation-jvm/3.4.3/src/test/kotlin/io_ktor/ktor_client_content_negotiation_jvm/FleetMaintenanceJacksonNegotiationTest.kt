/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_content_negotiation_jvm

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
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
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.jackson.jackson
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
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Jackson content negotiation through the client ContentNegotiation plugin, with java.time types
 * and a vendor JSON media type, as a fleet application books vehicle maintenance and reads
 * odometer telemetry from a workshop service.
 */
public class FleetMaintenanceJacksonNegotiationTest {

    data class ServiceAppointment(
        val vehicleId: String,
        val scheduledAt: Instant,
        val dueDate: LocalDate,
        val estimatedDuration: Duration,
        val tasks: List<String> = emptyList(),
        val technician: String? = null,
    )

    data class BookingRequest(
        val vehicleId: String,
        val scheduledAt: Instant,
        val estimatedDuration: Duration,
        val tasks: List<String>,
    )

    data class OdometerReading(val vehicleId: String, val kilometres: Long, val recordedAt: Instant)

    data class TelemetryBatch(val depot: String, val readings: List<OdometerReading>)

    data class TelemetryAck(val depot: String, val stored: Int)

    /** What the workshop service saw of one incoming request. */
    data class ReceivedRequest(val path: String, val accept: String?, val contentType: String?, val body: String)

    /** The workshop service; it answers with fixed bodies so the client's decoding is what is checked. */
    class WorkshopService : AutoCloseable {
        val received: MutableList<ReceivedRequest> = CopyOnWriteArrayList()

        private val server: EmbeddedServer<*, *> = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/appointments") {
                    record(call.request.local.uri, call.request.header(HttpHeaders.Accept), null, "")
                    call.respondText(
                        """
                        [
                          {"vehicleId":"VAN-014","scheduledAt":"2026-03-14T08:30:00Z","dueDate":"2026-03-20",
                           "estimatedDuration":"PT1H30M","tasks":["oil change","brake check"],
                           "technician":"R. Okafor","bay":3},
                          {"vehicleId":"TRK-202","scheduledAt":"2026-03-15T13:00:00Z","dueDate":"2026-03-31",
                           "estimatedDuration":"PT4H"}
                        ]
                        """.trimIndent(),
                        ContentType.Application.Json,
                    )
                }
                post("/appointments") {
                    val body: String = call.receiveText()
                    record(call.request.local.uri, call.request.header(HttpHeaders.Accept), call.request.contentType().toString(), body)
                    call.respondText(
                        """
                        {"vehicleId":"VAN-077","scheduledAt":"2026-04-02T07:15:00Z","dueDate":"2026-04-02",
                         "estimatedDuration":"PT45M","tasks":["tyre rotation"],"technician":"M. Lindqvist"}
                        """.trimIndent(),
                        ContentType.Application.Json,
                        HttpStatusCode.Created,
                    )
                }
                post("/telemetry") {
                    val body: String = call.receiveText()
                    record(call.request.local.uri, call.request.header(HttpHeaders.Accept), call.request.contentType().toString(), body)
                    call.respondText("""{"depot":"North","stored":2}""", FLEET_TELEMETRY)
                }
                get("/appointments/broken") {
                    call.respondText("""{"vehicleId":"VAN-014","scheduledAt":"next tuesday"}""", ContentType.Application.Json)
                }
            }
        }

        val baseUrl: String

        init {
            server.start(wait = false)
            val port: Int = runBlocking { server.engine.resolvedConnectors().first().port }
            baseUrl = "http://127.0.0.1:$port"
        }

        private fun record(path: String, accept: String?, contentType: String?, body: String) {
            received += ReceivedRequest(path, accept, contentType, body)
        }

        override fun close() {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 2_000)
        }
    }

    /** Applies the date and tolerance settings the fleet application shares with the workshop. */
    private fun ObjectMapper.fleetSettings() {
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    private fun workshopClient(baseUrl: String): HttpClient = HttpClient(ClientCIO) {
        install(ContentNegotiation) {
            jackson { fleetSettings() }
            jackson(FLEET_TELEMETRY) { fleetSettings() }
        }
        defaultRequest { url("$baseUrl/") }
    }

    @Test
    fun appointmentsAreDecodedWithJavaTimeValues(): Unit = runBlocking {
        WorkshopService().use { service: WorkshopService ->
            workshopClient(service.baseUrl).use { client: HttpClient ->
                val appointments: List<ServiceAppointment> = client.get("appointments").body()

                assertThat(appointments).containsExactly(
                    ServiceAppointment(
                        "VAN-014",
                        Instant.parse("2026-03-14T08:30:00Z"),
                        LocalDate.of(2026, 3, 20),
                        Duration.ofMinutes(90),
                        listOf("oil change", "brake check"),
                        "R. Okafor",
                    ),
                    ServiceAppointment(
                        "TRK-202",
                        Instant.parse("2026-03-15T13:00:00Z"),
                        LocalDate.of(2026, 3, 31),
                        Duration.ofHours(4),
                    ),
                )
                assertThat(service.received.single().accept).contains("application/json")
            }
        }
    }

    @Test
    fun aBookingIsWrittenWithIsoDatesAndTheConfirmationDecoded(): Unit = runBlocking {
        WorkshopService().use { service: WorkshopService ->
            workshopClient(service.baseUrl).use { client: HttpClient ->
                val booking = BookingRequest(
                    "VAN-077",
                    Instant.parse("2026-04-02T07:15:00Z"),
                    Duration.ofMinutes(45),
                    listOf("tyre rotation"),
                )
                val response: HttpResponse = client.post("appointments") {
                    contentType(ContentType.Application.Json)
                    setBody(booking)
                }

                assertThat(response.status).isEqualTo(HttpStatusCode.Created)
                val confirmed: ServiceAppointment = response.body()
                assertThat(confirmed.technician).isEqualTo("M. Lindqvist")
                assertThat(confirmed.dueDate).isEqualTo(LocalDate.of(2026, 4, 2))
                assertThat(confirmed.scheduledAt.plus(confirmed.estimatedDuration))
                    .isEqualTo(Instant.parse("2026-04-02T08:00:00Z"))

                val seen: ReceivedRequest = service.received.single()
                assertThat(seen.contentType).startsWith("application/json")
                assertThat(seen.body)
                    .contains("\"scheduledAt\":\"2026-04-02T07:15:00Z\"")
                    .contains("\"estimatedDuration\":\"PT45M\"")
            }
        }
    }

    @Test
    fun telemetryTravelsUnderTheVendorMediaType(): Unit = runBlocking {
        WorkshopService().use { service: WorkshopService ->
            workshopClient(service.baseUrl).use { client: HttpClient ->
                val batch = TelemetryBatch(
                    "North",
                    listOf(
                        OdometerReading("VAN-014", 84_210, Instant.parse("2026-03-14T06:00:00Z")),
                        OdometerReading("TRK-202", 311_904, Instant.parse("2026-03-14T06:05:00Z")),
                    ),
                )
                val response: HttpResponse = client.post("telemetry") {
                    contentType(FLEET_TELEMETRY)
                    setBody(batch)
                }

                assertThat(response.contentType()?.withoutParameters()).isEqualTo(FLEET_TELEMETRY)
                val ack: TelemetryAck = response.body()
                assertThat(ack).isEqualTo(TelemetryAck("North", 2))

                val seen: ReceivedRequest = service.received.single()
                assertThat(seen.contentType).startsWith(FLEET_TELEMETRY.toString())
                assertThat(seen.accept).contains("application/json").contains(FLEET_TELEMETRY.toString())
                val mapper: ObjectMapper = ObjectMapper().apply { fleetSettings() }
                assertThat(mapper.readTree(seen.body).path("readings").path(1).path("kilometres").asLong())
                    .isEqualTo(311_904L)
                assertThat(seen.body).contains("\"recordedAt\":\"2026-03-14T06:05:00Z\"")
            }
        }
    }

    @Test
    fun anUnparseableAppointmentSurfacesAsAJsonConversionFailure(): Unit = runBlocking {
        WorkshopService().use { service: WorkshopService ->
            workshopClient(service.baseUrl).use { client: HttpClient ->
                val response: HttpResponse = client.get("appointments/broken")

                assertThatThrownBy { runBlocking { response.body<ServiceAppointment>() } }
                    .isInstanceOf(JsonConvertException::class.java)
            }
        }
    }

    private companion object {
        val FLEET_TELEMETRY: ContentType = ContentType("application", "vnd.fleet.telemetry+json")
    }
}
