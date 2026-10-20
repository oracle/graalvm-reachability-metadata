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
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.Date
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Structured claim decoding into application types, where an airline app reads
 * the traveler profile and booked trips its identity service packs into the token.
 *
 * `validate` admits a token only when its `traveler` object claim decodes into a
 * [Traveler] through `getClaim(name, KClass)`; handlers then decode the `bookings`
 * array into [Booking]s with `getListClaim`, read typed scalar claims, and read the
 * free-form `preferences` object as a map. A claim of the wrong shape decodes to
 * `null` or an empty list instead of failing the request.
 */
public class AirlineTripsStructuredClaimsTest {
    /** Loyalty tiers the identity service writes into the traveler profile. */
    public enum class Tier { BLUE, SILVER, GOLD }

    /** The `traveler` object claim, bound by property name. */
    public data class Traveler(
        var name: String = "",
        var tier: Tier = Tier.BLUE,
        var miles: Long = 0,
    )

    /** One element of the `bookings` array claim. */
    public data class Booking(
        var locator: String = "",
        var flight: String = "",
        var seat: String = "",
    )

    /** The airline application: the `trips-jwt` provider and the traveler pages. */
    private fun ApplicationTestBuilder.airline() {
        application {
            install(Authentication) {
                jwt("trips-jwt") {
                    realm = REALM
                    verifier(
                        JWT.require(Algorithm.HMAC256(SECRET))
                            .withIssuer(ISSUER)
                            .withAudience(AUDIENCE)
                            .build(),
                    )
                    validate { credential ->
                        val principal: JWTPrincipal = JWTPrincipal(credential.payload)
                        if (principal.getClaim("traveler", Traveler::class) == null) null else principal
                    }
                }
            }
            routing {
                authenticate("trips-jwt") {
                    get("/trips") {
                        val principal: JWTPrincipal = call.principal<JWTPrincipal>()!!
                        val traveler: Traveler = principal.getClaim("traveler", Traveler::class)!!
                        val bookings: List<Booking> = principal.getListClaim("bookings", Booking::class)
                        val trips: String = if (bookings.isEmpty()) {
                            "no trips"
                        } else {
                            bookings.joinToString("; ") { "${it.locator} ${it.flight} seat ${it.seat}" }
                        }
                        call.respondText("${traveler.name} (${traveler.tier}, ${traveler.miles} miles): $trips")
                    }
                    get("/upgrade") {
                        val principal: JWTPrincipal = call.principal<JWTPrincipal>()!!
                        val traveler: Traveler = principal.getClaim("traveler", Traveler::class)!!
                        val eligible: Boolean? = principal.getClaim("upgradeEligible", Boolean::class)
                        val credit: Double? = principal.getClaim("travelCredit", Double::class)
                        val companions: List<Int> = principal.getListClaim("companionAges", Int::class)
                        if (eligible == true && traveler.tier >= Tier.SILVER) {
                            call.respondText(
                                "upgrade offered to ${traveler.name}, credit=$credit, companions=$companions",
                            )
                        } else {
                            call.respondText(
                                "no upgrade for ${traveler.name}, eligible=$eligible",
                                status = HttpStatusCode.Forbidden,
                            )
                        }
                    }
                    get("/preferences") {
                        val principal: JWTPrincipal = call.principal<JWTPrincipal>()!!
                        val preferences: Map<String, Any?> = principal.payload.getClaim("preferences").asMap()
                        call.respondText(
                            preferences.toSortedMap().entries.joinToString(" ") { "${it.key}=${it.value}" },
                        )
                    }
                }
            }
        }
    }

    /** A token for [traveler] holding [bookings], before [customize] adjusts it. */
    private fun token(
        traveler: Any? = mapOf("name" to "Ana Petrovic", "tier" to "GOLD", "miles" to 48_200L),
        bookings: List<Map<String, String>>? = BOOKINGS,
        customize: JWTCreator.Builder.() -> Unit = {},
    ): String {
        val builder: JWTCreator.Builder = JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withSubject("traveler-7731")
            .withExpiresAt(Date(System.currentTimeMillis() + LIFETIME_MILLIS))
        when (traveler) {
            is Map<*, *> -> builder.withClaim("traveler", traveler.mapKeys { it.key as String })
            is String -> builder.withClaim("traveler", traveler)
        }
        if (bookings != null) {
            builder.withClaim("bookings", bookings)
        }
        builder.customize()
        return builder.sign(Algorithm.HMAC256(SECRET))
    }

    @Test
    fun objectAndArrayClaimsDecodeIntoApplicationTypes() = testApplication {
        airline()

        val response: HttpResponse = client.get("/trips") { bearerAuth(token()) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            "Ana Petrovic (GOLD, 48200 miles): AB12C LH1411 seat 3A; XY98Z OS201 seat 14C",
            response.bodyAsText(),
        )
    }

    @Test
    fun typedScalarAndListClaimsDriveTheUpgradeDecision() = testApplication {
        airline()
        val offered: String = token {
            withClaim("upgradeEligible", true)
            withClaim("travelCredit", 125.5)
            withClaim("companionAges", listOf(34, 9))
        }
        val blueTier: String = token(traveler = mapOf("name" to "Milan Ilic", "tier" to "BLUE", "miles" to 900L)) {
            withClaim("upgradeEligible", true)
        }

        val upgrade: HttpResponse = client.get("/upgrade") { bearerAuth(offered) }
        val withoutFlag: HttpResponse = client.get("/upgrade") { bearerAuth(token()) }
        val blue: HttpResponse = client.get("/upgrade") { bearerAuth(blueTier) }

        assertEquals(HttpStatusCode.OK, upgrade.status)
        assertEquals("upgrade offered to Ana Petrovic, credit=125.5, companions=[34, 9]", upgrade.bodyAsText())
        assertEquals(HttpStatusCode.Forbidden, withoutFlag.status)
        assertEquals("no upgrade for Ana Petrovic, eligible=null", withoutFlag.bodyAsText())
        assertEquals(HttpStatusCode.Forbidden, blue.status)
        assertEquals("no upgrade for Milan Ilic, eligible=true", blue.bodyAsText())
    }

    @Test
    fun freeFormObjectClaimReadsAsAMap() = testApplication {
        airline()
        val issued: String = token {
            withClaim("preferences", mapOf("meal" to "vegetarian", "seat" to "aisle", "lounge" to true))
        }

        val response: HttpResponse = client.get("/preferences") { bearerAuth(issued) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("lounge=true meal=vegetarian seat=aisle", response.bodyAsText())
    }

    @Test
    fun missingOrMisshapenBookingsReadAsNoTrips() = testApplication {
        airline()
        val misshapen: String = token(bookings = null) { withClaim("bookings", "AB12C") }

        val missing: HttpResponse = client.get("/trips") { bearerAuth(token(bookings = null)) }
        val notAnArray: HttpResponse = client.get("/trips") { bearerAuth(misshapen) }

        assertEquals(HttpStatusCode.OK, missing.status)
        assertEquals("Ana Petrovic (GOLD, 48200 miles): no trips", missing.bodyAsText())
        assertEquals(HttpStatusCode.OK, notAnArray.status)
        assertEquals("Ana Petrovic (GOLD, 48200 miles): no trips", notAnArray.bodyAsText())
    }

    @Test
    fun tokenWhoseTravelerClaimDoesNotDecodeIsRejected() = testApplication {
        airline()
        val rejected: List<String> = listOf(
            token(traveler = "Ana Petrovic"),
            token(traveler = mapOf("name" to "Ana Petrovic", "tier" to "PLATINUM", "miles" to 1L)),
            token(traveler = null),
        )

        for (issued in rejected) {
            val response: HttpResponse = client.get("/trips") { bearerAuth(issued) }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    private companion object {
        const val SECRET: String = "airline-trips-signing-secret"
        const val ISSUER: String = "https://id.airline.example"
        const val AUDIENCE: String = "trips-app"
        const val REALM: String = "airline trips"
        const val LIFETIME_MILLIS: Long = 3_600_000
        val BOOKINGS: List<Map<String, String>> = listOf(
            mapOf("locator" to "AB12C", "flight" to "LH1411", "seat" to "3A"),
            mapOf("locator" to "XY98Z", "flight" to "OS201", "seat" to "14C"),
        )
    }
}
