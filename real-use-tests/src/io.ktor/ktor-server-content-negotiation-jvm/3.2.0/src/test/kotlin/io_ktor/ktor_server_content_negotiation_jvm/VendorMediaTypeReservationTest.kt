/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_content_negotiation_jvm

import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.header
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises vendor-specific JSON media-type registration for a versioned partner reservation API.
 */
@Timeout(60)
class VendorMediaTypeReservationTest {
    @Test
    fun acceptsVendorDocumentsAndPublishesTheCreatedResourceInEitherJsonMediaType(): Unit = testApplication {
        val reservationType: ContentType = ContentType("application", "vnd.warehouse.reservation-v1+json")
        val reservations: ConcurrentHashMap<String, Reservation> = ConcurrentHashMap()
        application {
            install(ContentNegotiation) {
                json(contentType = reservationType)
                json()
            }
            routing {
                post("/reservations") {
                    val request: ReservationRequest = call.receive<ReservationRequest>()
                    val reservation: Reservation = Reservation(request.reference, request.sku, request.quantity, "reserved")
                    reservations[request.reference] = reservation
                    call.response.header(HttpHeaders.Location, "/reservations/${request.reference}")
                    call.respond(HttpStatusCode.Created, reservation)
                }
                get("/reservations/{reference}") {
                    val reservation: Reservation = requireNotNull(reservations[call.parameters["reference"]])
                    call.respond(reservation)
                }
            }
        }

        val partnerClient = createClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 20_000
                socketTimeoutMillis = 20_000
            }
        }
        val expected: Reservation = Reservation("partner-42", "BOX-10", 3, "reserved")
        val created = partnerClient.post("/reservations") {
            contentType(reservationType)
            accept(reservationType)
            setBody("""{"reference":"partner-42","sku":"BOX-10","quantity":3}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        assertEquals(reservationType, created.contentType()?.withoutParameters())
        assertEquals(expected, Json.decodeFromString<Reservation>(created.bodyAsText()))
        val location: String = requireNotNull(created.headers[HttpHeaders.Location])
        assertEquals("/reservations/partner-42", location)

        // Internal dashboards use ordinary JSON for the same resource created by the versioned partner API.
        val dashboardResponse = partnerClient.get(location) {
            accept(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, dashboardResponse.status)
        assertEquals(ContentType.Application.Json, dashboardResponse.contentType()?.withoutParameters())
        assertEquals(expected, Json.decodeFromString<Reservation>(dashboardResponse.bodyAsText()))

        // A partner following Location still receives the vendor type, not the preceding client's type.
        val partnerResponse = partnerClient.get(location) {
            accept(reservationType)
        }
        assertEquals(HttpStatusCode.OK, partnerResponse.status)
        assertEquals(reservationType, partnerResponse.contentType()?.withoutParameters())
        assertEquals(expected, Json.decodeFromString<Reservation>(partnerResponse.bodyAsText()))
    }

    @Serializable
    data class ReservationRequest(val reference: String, val sku: String, val quantity: Int)

    @Serializable
    data class Reservation(val reference: String, val sku: String, val quantity: Int, val status: String)
}
