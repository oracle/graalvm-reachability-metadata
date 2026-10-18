/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.util.getOrFail
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/**
 * The CIO engine's error handling: request body validation and status pages that turn
 * validation failures, domain exceptions, malformed input, and bare status codes into
 * uniform JSON error responses.
 *
 * A clinic booking API runs on an embedded CIO server. Patients book appointment slots
 * as JSON; the server validates every booking before the route sees it, rejects a slot
 * that is already taken, and reports unknown bookings, forbidden cancellations, malformed
 * bodies, and an unexpected internal failure. The test checks that each failure reaches
 * the client as the status code and JSON error an application would show, and that the
 * internal failure was recorded on the server without leaking its message.
 */
class ClinicBookingErrorHandlingTest {

    @Serializable
    data class Booking(val patient: String, val slot: String, val minutes: Int)

    @Serializable
    data class Confirmation(val id: Int, val patient: String, val slot: String)

    @Serializable
    data class ApiError(val code: String, val details: List<String>)

    /** A domain failure the booking route raises when a slot is already held. */
    class SlotTakenException(val slot: String) : RuntimeException("slot $slot is taken")

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0
    private val bookings: MutableMap<Int, Confirmation> = ConcurrentHashMap()
    private val takenSlots: MutableMap<String, Int> = ConcurrentHashMap()
    private val internalFailures: MutableList<String> = CopyOnWriteArrayList()

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(ServerContentNegotiation) { json() }
            install(RequestValidation) {
                validate<Booking> { booking: Booking ->
                    val problems: List<String> = buildList {
                        if (booking.patient.isBlank()) add("patient is required")
                        if (!booking.slot.matches(Regex("""\d{2}:\d{2}"""))) add("slot must be HH:MM")
                        if (booking.minutes !in 15..60) add("minutes must be between 15 and 60")
                    }
                    if (problems.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(problems)
                }
            }
            install(StatusPages) {
                exception<RequestValidationException> { call, cause ->
                    call.respond(HttpStatusCode.UnprocessableEntity, ApiError("invalid-booking", cause.reasons))
                }
                exception<SlotTakenException> { call, cause ->
                    call.respond(HttpStatusCode.Conflict, ApiError("slot-taken", listOf(cause.slot)))
                }
                exception<NotFoundException> { call, cause ->
                    call.respond(HttpStatusCode.NotFound, ApiError("unknown-booking", listOf(cause.message ?: "")))
                }
                exception<BadRequestException> { call, _ ->
                    call.respond(HttpStatusCode.BadRequest, ApiError("malformed-request", emptyList()))
                }
                exception<Throwable> { call, cause ->
                    internalFailures += "${cause::class.simpleName}: ${cause.message}"
                    call.respond(HttpStatusCode.InternalServerError, ApiError("internal", emptyList()))
                }
                status(HttpStatusCode.NotFound, HttpStatusCode.Forbidden) { call, status ->
                    call.respond(status, ApiError("status-${status.value}", listOf(call.request.local.uri)))
                }
            }
            routing {
                post("/bookings") {
                    val booking: Booking = call.receive()
                    val id: Int = bookings.size + 1
                    if (takenSlots.putIfAbsent(booking.slot, id) != null) {
                        throw SlotTakenException(booking.slot)
                    }
                    val confirmation = Confirmation(id, booking.patient, booking.slot)
                    bookings[id] = confirmation
                    call.respond(HttpStatusCode.Created, confirmation)
                }
                get("/bookings/{id}") {
                    val id: Int = call.parameters.getOrFail<Int>("id")
                    val confirmation: Confirmation = bookings[id] ?: throw NotFoundException("booking $id")
                    call.respond(confirmation)
                }
                delete("/bookings/{id}") {
                    call.respond(HttpStatusCode.Forbidden)
                }
                get("/reports/daily") {
                    error("report store is offline")
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stop() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun bookingFailuresReachTheClientAsJsonErrors() {
        val client = HttpClient(ClientCIO) {
            install(ClientContentNegotiation) { json() }
        }

        client.use {
            runBlocking {
                val base = "http://127.0.0.1:$port"

                val booked: HttpResponse = it.post("$base/bookings") {
                    contentType(ContentType.Application.Json)
                    setBody(Booking("Ana", "09:30", 30))
                }
                assertEquals(HttpStatusCode.Created, booked.status)
                assertEquals(Confirmation(1, "Ana", "09:30"), booked.body<Confirmation>())

                val invalid: HttpResponse = it.post("$base/bookings") {
                    contentType(ContentType.Application.Json)
                    setBody(Booking(" ", "9.45", 90))
                }
                assertEquals(HttpStatusCode.UnprocessableEntity, invalid.status)
                assertEquals(
                    ApiError(
                        "invalid-booking",
                        listOf("patient is required", "slot must be HH:MM", "minutes must be between 15 and 60"),
                    ),
                    invalid.body<ApiError>(),
                )

                val conflict: HttpResponse = it.post("$base/bookings") {
                    contentType(ContentType.Application.Json)
                    setBody(Booking("Ben", "09:30", 15))
                }
                assertEquals(HttpStatusCode.Conflict, conflict.status)
                assertEquals(ApiError("slot-taken", listOf("09:30")), conflict.body<ApiError>())

                val malformed: HttpResponse = it.post("$base/bookings") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"patient": "Cleo", "slot": """)
                }
                assertEquals(HttpStatusCode.BadRequest, malformed.status)
                assertEquals(ApiError("malformed-request", emptyList()), malformed.body<ApiError>())

                val badId: HttpResponse = it.get("$base/bookings/first")
                assertEquals(HttpStatusCode.BadRequest, badId.status)
                assertEquals(ApiError("malformed-request", emptyList()), badId.body<ApiError>())

                val found: Confirmation = it.get("$base/bookings/1").body()
                assertEquals(Confirmation(1, "Ana", "09:30"), found)

                val unknown: HttpResponse = it.get("$base/bookings/7")
                assertEquals(HttpStatusCode.NotFound, unknown.status)
                assertEquals(ApiError("unknown-booking", listOf("booking 7")), unknown.body<ApiError>())

                val forbidden: HttpResponse = it.delete("$base/bookings/1")
                assertEquals(HttpStatusCode.Forbidden, forbidden.status)
                assertEquals(ApiError("status-403", listOf("/bookings/1")), forbidden.body<ApiError>())

                val unrouted: HttpResponse = it.get("$base/waiting-room")
                assertEquals(HttpStatusCode.NotFound, unrouted.status)
                assertEquals(ApiError("status-404", listOf("/waiting-room")), unrouted.body<ApiError>())

                val broken: HttpResponse = it.get("$base/reports/daily")
                assertEquals(HttpStatusCode.InternalServerError, broken.status)
                assertEquals(ApiError("internal", emptyList()), broken.body<ApiError>())
            }
        }

        assertEquals(listOf("IllegalStateException: report store is offline"), internalFailures)
        assertEquals(mapOf("09:30" to 1), takenSlots.toMap())
    }
}
