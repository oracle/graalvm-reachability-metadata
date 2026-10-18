/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_content_negotiation_jvm

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Route-scoped installation of the plugin, with its own converters and ignored types per route.
 *
 * A shipment-tracking service keeps its first API version on Jackson, with `java.time` dates
 * written as ISO strings, and moves its second version to kotlinx JSON that also encodes plain
 * strings as JSON values. Each version installs the plugin on its own route subtree, and a
 * health route installs it nowhere. The test checks that each subtree encodes and decodes
 * bodies with its own converter, that `String` responses skip the converter only where it is
 * still ignored, and that a route outside both subtrees cannot send an object body and
 * answers `406 Not Acceptable`.
 */
class ShipmentApiVersionScopeTest {

    data class LegacyShipment(val trackingId: String, val carrier: String, val shippedOn: LocalDate, val parcels: Int)

    @Serializable
    data class Shipment(val trackingId: String, val carrier: String, val shippedOn: String, val parcels: Int)

    private val booked: ConcurrentLinkedQueue<LegacyShipment> = ConcurrentLinkedQueue()

    private fun runService(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            routing {
                route("/v1") {
                    install(ContentNegotiation) {
                        jackson {
                            registerModule(JavaTimeModule())
                            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        }
                    }
                    get("/shipments/{id}") {
                        call.respond(LEGACY[call.parameters["id"]!!]!!)
                    }
                    post("/shipments") {
                        val shipment: LegacyShipment = call.receive()
                        booked.add(shipment)
                        call.respond(HttpStatusCode.Created, shipment)
                    }
                    get("/status") {
                        call.respond("v1 up")
                    }
                }
                route("/v2") {
                    install(ContentNegotiation) {
                        json()
                        removeIgnoredType<String>()
                    }
                    get("/shipments/{id}") {
                        val legacy: LegacyShipment = LEGACY[call.parameters["id"]!!]!!
                        call.respond(Shipment(legacy.trackingId, legacy.carrier, legacy.shippedOn.toString(), legacy.parcels))
                    }
                    get("/status") {
                        call.respond("v2 up")
                    }
                }
                get("/health") {
                    call.respond("healthy")
                }
                get("/health/details") {
                    call.respond(Shipment("none", "none", "none", 0))
                }
            }
        }
        block()
    }

    @Test
    fun eachVersionEncodesWithItsOwnConverter() = runService {
        val v1: HttpResponse = client.get("/v1/shipments/TRK-42") { header(HttpHeaders.Accept, "application/json") }
        assertEquals(HttpStatusCode.OK, v1.status)
        assertJson(v1)
        assertEquals(
            """{"trackingId":"TRK-42","carrier":"Nordpost","shippedOn":"2026-03-14","parcels":3}""",
            v1.bodyAsText(),
        )

        val v2: HttpResponse = client.get("/v2/shipments/TRK-42") { header(HttpHeaders.Accept, "application/json") }
        assertEquals(HttpStatusCode.OK, v2.status)
        assertJson(v2)
        assertEquals(
            Shipment("TRK-42", "Nordpost", "2026-03-14", 3),
            Json.decodeFromString<Shipment>(v2.bodyAsText()),
        )
    }

    @Test
    fun legacyVersionDecodesJavaTimeBodies() = runService {
        val created: HttpResponse = client.post("/v1/shipments") {
            header(HttpHeaders.Accept, "application/json")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"trackingId":"TRK-77","carrier":"Alpenexpress","shippedOn":"2026-02-28","parcels":1}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        assertJson(created)
        assertEquals(
            """{"trackingId":"TRK-77","carrier":"Alpenexpress","shippedOn":"2026-02-28","parcels":1}""",
            created.bodyAsText(),
        )
        assertEquals(listOf(LegacyShipment("TRK-77", "Alpenexpress", LocalDate.of(2026, 2, 28), 1)), booked.toList())
    }

    @Test
    fun stringResponsesSkipTheConverterOnlyWhereIgnored() = runService {
        val v1: HttpResponse = client.get("/v1/status") { header(HttpHeaders.Accept, "application/json") }
        assertEquals(HttpStatusCode.OK, v1.status)
        assertEquals("v1 up", v1.bodyAsText())
        assertTrue(v1.contentType()!!.match(ContentType.Text.Plain), "got ${v1.contentType()}")

        val v2: HttpResponse = client.get("/v2/status") { header(HttpHeaders.Accept, "application/json") }
        assertEquals(HttpStatusCode.OK, v2.status)
        assertJson(v2)
        assertEquals("\"v2 up\"", v2.bodyAsText())
    }

    @Test
    fun routesOutsideBothVersionsAreNotConverted() = runService {
        val health: HttpResponse = client.get("/health") { header(HttpHeaders.Accept, "application/json") }
        assertEquals(HttpStatusCode.OK, health.status)
        assertEquals("healthy", health.bodyAsText())
        assertTrue(health.contentType()!!.match(ContentType.Text.Plain), "got ${health.contentType()}")

        val details: HttpResponse = client.get("/health/details") { header(HttpHeaders.Accept, "application/json") }
        assertEquals(HttpStatusCode.NotAcceptable, details.status)
        assertEquals(emptyList<LegacyShipment>(), booked.toList())
    }

    private fun assertJson(response: HttpResponse) {
        val actual: ContentType? = response.contentType()
        assertTrue(actual != null && actual.match(ContentType.Application.Json), "expected JSON, got $actual")
    }

    private companion object {
        val LEGACY: Map<String, LegacyShipment> = mapOf(
            "TRK-42" to LegacyShipment("TRK-42", "Nordpost", LocalDate.of(2026, 3, 14), 3),
        )
    }
}
