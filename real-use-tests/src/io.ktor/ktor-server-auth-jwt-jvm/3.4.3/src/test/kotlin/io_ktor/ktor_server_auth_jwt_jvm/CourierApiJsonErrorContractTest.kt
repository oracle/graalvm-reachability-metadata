/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_auth_jwt_jvm

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTCreator
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.Payload
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPayloadHolder
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.Date
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JSON error contract around the JWT provider, where a parcel courier API answers every
 * authentication outcome with a JSON document through StatusPages and ContentNegotiation.
 *
 * The provider's `challenge` throws an application exception that StatusPages renders as a
 * JSON 401 carrying the bearer `WWW-Authenticate` header; the verifier demands a depot claim;
 * `validate` maps the payload to a courier principal extending `JWTPayloadHolder`, and when the
 * courier roster is down the provider's bare 401 is reshaped by StatusPages' `status` handler.
 * Handlers serialize the principal's claims to JSON and refuse other depots with a JSON 403.
 */
public class CourierApiJsonErrorContractTest {
    /** The error document every failed request receives. */
    @Serializable
    private data class ApiError(val error: String, val detail: String)

    /** The signed-in courier, as the app shows it on the profile screen. */
    @Serializable
    private data class CourierProfile(val courierId: String, val depot: String, val vehicle: String, val issuer: String)

    /** A delivery the courier has just completed. */
    @Serializable
    private data class Delivery(val parcel: String, val depot: String, val courier: String)

    /** The courier principal `validate` builds from a verified token payload. */
    private class Courier(payload: Payload) : JWTPayloadHolder(payload) {
        val id: String get() = subject!!
        val depot: String get() = getClaim("depot", String::class)!!
        val vehicle: String get() = getClaim("vehicle", String::class) ?: "bike"
    }

    /** Thrown from the JWT challenge so StatusPages can answer with the JSON contract. */
    private class SignInRequiredException(val scheme: String, val realm: String, val reason: String) :
        RuntimeException(reason)

    /** Thrown by a handler when a courier touches a parcel of another depot. */
    private class WrongDepotException(val parcel: String, val depot: String) :
        RuntimeException("parcel $parcel belongs to $depot")

    /** Raised when the courier roster cannot be read. */
    private class RosterUnavailableException : RuntimeException("courier roster unavailable")

    /** The roster of couriers allowed to work today. */
    private class Roster {
        @Volatile
        var available: Boolean = true
        private val suspended: Set<String> = setOf("c-77")

        fun isActive(courierId: String): Boolean {
            if (!available) {
                throw RosterUnavailableException()
            }
            return courierId !in suspended
        }
    }

    private val roster: Roster = Roster()
    private val parcelDepots: Map<String, String> = mapOf("P-100" to "north", "P-200" to "south")
    private val json: Json = Json { ignoreUnknownKeys = true }

    /** The courier application: JSON plugins, the `courier-jwt` provider and its routes. */
    private fun ApplicationTestBuilder.courierApi() {
        application {
            install(ContentNegotiation) {
                json()
            }
            install(StatusPages) {
                exception<SignInRequiredException> { call, cause ->
                    call.response.header(HttpHeaders.WWWAuthenticate, "${cause.scheme} realm=\"${cause.realm}\"")
                    call.respond(HttpStatusCode.Unauthorized, ApiError(cause.reason, "sign in to the courier app"))
                }
                exception<WrongDepotException> { call, cause ->
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiError("wrong_depot", "parcel ${cause.parcel} is handled by ${cause.depot}"),
                    )
                }
                status(HttpStatusCode.Unauthorized) { call, status ->
                    call.respond(status, ApiError("auth_unavailable", "try again shortly"))
                }
            }
            install(Authentication) {
                jwt("courier-jwt") {
                    realm = REALM
                    verifier(ISSUER, AUDIENCE, Algorithm.HMAC256(SECRET)) {
                        withClaimPresence("depot")
                    }
                    validate { credential ->
                        if (roster.isActive(credential.subject!!)) Courier(credential.payload) else null
                    }
                    challenge { defaultScheme, realm ->
                        val reason: String =
                            if (call.request.headers[HttpHeaders.Authorization] == null) "missing_token" else "invalid_token"
                        throw SignInRequiredException(defaultScheme, realm, reason)
                    }
                }
            }
            routing {
                authenticate("courier-jwt") {
                    get("/courier/me") {
                        val courier: Courier = call.principal<Courier>()!!
                        call.respond(CourierProfile(courier.id, courier.depot, courier.vehicle, courier.issuer!!))
                    }
                    post("/parcels/{parcel}/delivered") {
                        val courier: Courier = call.principal<Courier>()!!
                        val parcel: String = call.parameters["parcel"]!!
                        val depot: String = parcelDepots.getValue(parcel)
                        if (depot != courier.depot) {
                            throw WrongDepotException(parcel, depot)
                        }
                        call.respond(HttpStatusCode.Created, Delivery(parcel, depot, courier.id))
                    }
                }
            }
        }
    }

    @Test
    fun `signed-in courier reads a JSON profile built from token claims`() = testApplication {
        courierApi()

        val withVehicle: HttpResponse = client.get("/courier/me") {
            bearerAuth(token("c-12") { withClaim("depot", "north").withClaim("vehicle", "van") })
        }
        assertEquals(HttpStatusCode.OK, withVehicle.status)
        assertTrue(withVehicle.contentType()!!.match(ContentType.Application.Json))
        assertEquals(
            CourierProfile("c-12", "north", "van", ISSUER),
            json.decodeFromString<CourierProfile>(withVehicle.bodyAsText()),
        )

        val defaultVehicle: HttpResponse = client.get("/courier/me") {
            bearerAuth(token("c-31") { withClaim("depot", "south") })
        }
        assertEquals(
            CourierProfile("c-31", "south", "bike", ISSUER),
            json.decodeFromString<CourierProfile>(defaultVehicle.bodyAsText()),
        )
    }

    @Test
    fun `challenge exceptions become JSON 401 documents with the bearer challenge`() = testApplication {
        courierApi()

        val missing: HttpResponse = client.get("/courier/me")
        assertUnauthorized(missing, "missing_token")

        val noDepot: HttpResponse = client.get("/courier/me") { bearerAuth(token("c-12") { this }) }
        assertUnauthorized(noDepot, "invalid_token")

        val expired: HttpResponse = client.get("/courier/me") {
            bearerAuth(token("c-12", expiresAt = Date(System.currentTimeMillis() - 3_600_000)) {
                withClaim("depot", "north")
            })
        }
        assertUnauthorized(expired, "invalid_token")

        val forged: String = JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withSubject("c-12")
            .withClaim("depot", "north")
            .sign(Algorithm.HMAC256("not-the-courier-secret"))
        assertUnauthorized(client.get("/courier/me") { bearerAuth(forged) }, "invalid_token")

        val suspended: HttpResponse = client.get("/courier/me") {
            bearerAuth(token("c-77") { withClaim("depot", "north") })
        }
        assertUnauthorized(suspended, "invalid_token")
    }

    @Test
    fun `handlers refuse parcels of another depot with a JSON 403`() = testApplication {
        courierApi()
        val northCourier: String = token("c-12") { withClaim("depot", "north") }

        val delivered: HttpResponse = client.post("/parcels/P-100/delivered") { bearerAuth(northCourier) }
        assertEquals(HttpStatusCode.Created, delivered.status)
        assertEquals(Delivery("P-100", "north", "c-12"), json.decodeFromString<Delivery>(delivered.bodyAsText()))

        val foreign: HttpResponse = client.post("/parcels/P-200/delivered") { bearerAuth(northCourier) }
        assertEquals(HttpStatusCode.Forbidden, foreign.status)
        assertEquals(
            ApiError("wrong_depot", "parcel P-200 is handled by south"),
            json.decodeFromString<ApiError>(foreign.bodyAsText()),
        )
        assertNull(foreign.headers[HttpHeaders.WWWAuthenticate])
    }

    @Test
    fun `roster outage turns the provider error into a JSON 401 without a challenge`() = testApplication {
        courierApi()
        val northCourier: String = token("c-12") { withClaim("depot", "north") }

        roster.available = false
        val outage: HttpResponse = client.get("/courier/me") { bearerAuth(northCourier) }
        assertEquals(HttpStatusCode.Unauthorized, outage.status)
        assertNull(outage.headers[HttpHeaders.WWWAuthenticate])
        assertEquals(
            ApiError("auth_unavailable", "try again shortly"),
            json.decodeFromString<ApiError>(outage.bodyAsText()),
        )

        roster.available = true
        val recovered: HttpResponse = client.get("/courier/me") { bearerAuth(northCourier) }
        assertEquals(HttpStatusCode.OK, recovered.status)
        assertEquals("c-12", json.decodeFromString<CourierProfile>(recovered.bodyAsText()).courierId)
    }

    /** Asserts the JSON 401 the challenge produces, including the bearer challenge header. */
    private suspend fun assertUnauthorized(response: HttpResponse, reason: String) {
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("Bearer realm=\"$REALM\"", response.headers[HttpHeaders.WWWAuthenticate])
        assertTrue(response.contentType()!!.match(ContentType.Application.Json))
        assertEquals(
            ApiError(reason, "sign in to the courier app"),
            json.decodeFromString<ApiError>(response.bodyAsText()),
        )
    }

    /** Signs a courier token; [claims] adds the depot and vehicle claims under test. */
    private fun token(
        courierId: String,
        expiresAt: Date = Date(System.currentTimeMillis() + 600_000),
        claims: JWTCreator.Builder.() -> JWTCreator.Builder,
    ): String = JWT.create()
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .withSubject(courierId)
        .withExpiresAt(expiresAt)
        .claims()
        .sign(Algorithm.HMAC256(SECRET))

    private companion object {
        const val SECRET: String = "courier-signing-secret"
        const val ISSUER: String = "https://auth.parcels.example"
        const val AUDIENCE: String = "courier-app"
        const val REALM: String = "parcels"
    }
}
