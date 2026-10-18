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
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.Date
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Configuration-driven JWT provider setup, where a fleet telemetry service reads its
 * signing secret, trusted issuers, audience, realm, leeway and token lifetime from the
 * `jwt` section of the application config.
 *
 * The same application module runs under a staging and a production config: each
 * deployment signs vehicle tokens at its own login route and admits only tokens its
 * configured verifier accepts, challenges with its configured realm, tolerates expired
 * tokens only within its configured leeway, and trusts every issuer its config lists.
 */
public class FleetTelemetryConfiguredJwtTest {
    /** The `jwt` settings an application config holds for one deployment. */
    private class JwtSettings(config: ApplicationConfig) {
        val secret: String = config.property("secret").getString()
        val issuers: List<String> = config.property("trusted-issuers").getList()
        val audience: String = config.property("audience").getString()
        val realm: String = config.property("realm").getString()
        val leewaySeconds: Long = config.propertyOrNull("leeway-seconds")?.getString()?.toLong() ?: 0
        val lifetimeMinutes: Long = config.property("lifetime-minutes").getString().toLong()
    }

    /** The telemetry application module, configured only through `environment.config`. */
    private fun Application.telemetry() {
        val settings: JwtSettings = JwtSettings(environment.config.config("jwt"))
        val algorithm: Algorithm = Algorithm.HMAC256(settings.secret)
        install(Authentication) {
            jwt("vehicle-jwt") {
                realm = settings.realm
                verifier(
                    JWT.require(algorithm)
                        .withIssuer(*settings.issuers.toTypedArray())
                        .withAudience(settings.audience)
                        .acceptLeeway(settings.leewaySeconds)
                        .build(),
                )
                validate { credential ->
                    if (credential.payload.getClaim("vin").isMissing) null else JWTPrincipal(credential.payload)
                }
            }
        }
        routing {
            post("/login/{vin}") {
                val issuedAt: Date = Date()
                val token: String = JWT.create()
                    .withIssuer(settings.issuers.first())
                    .withAudience(settings.audience)
                    .withSubject("vehicle")
                    .withClaim("vin", call.parameters["vin"]!!)
                    .withIssuedAt(issuedAt)
                    .withExpiresAt(Date(issuedAt.time + settings.lifetimeMinutes * MILLIS_PER_MINUTE))
                    .sign(algorithm)
                call.respondText(token)
            }
            authenticate("vehicle-jwt") {
                get("/telemetry/session") {
                    val principal: JWTPrincipal = call.principal<JWTPrincipal>()!!
                    val issuedAt: Date? = principal.issuedAt
                    val lifetime: String = if (issuedAt == null) {
                        "of unknown lifetime"
                    } else {
                        "valid ${(principal.expiresAt!!.time - issuedAt.time) / MILLIS_PER_MINUTE} min"
                    }
                    call.respondText("${principal["vin"]} via ${principal.issuer} for ${principal.audience} $lifetime")
                }
            }
        }
    }

    /** Runs [block] against the telemetry module deployed with [config]. */
    private fun deployed(config: MapApplicationConfig, block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            environment { this.config = config }
            application { telemetry() }
            block()
        }

    /** A vehicle token for [vin] issued by [issuer] and signed with [secret], before [customize]. */
    private fun token(
        vin: String,
        issuer: String,
        secret: String,
        audience: String = AUDIENCE,
        customize: JWTCreator.Builder.() -> Unit = {},
    ): String {
        val builder: JWTCreator.Builder = JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("vin", vin)
            .withExpiresAt(Date(System.currentTimeMillis() + MILLIS_PER_MINUTE * 60))
        builder.customize()
        return builder.sign(Algorithm.HMAC256(secret))
    }

    @Test
    fun loginTokenCarriesTheConfiguredLifetimeAndIdentity() = deployed(stagingConfig()) {
        val issued: String = client.post("/login/WVW-STAGE-01").bodyAsText()

        val response: HttpResponse = client.get("/telemetry/session") { bearerAuth(issued) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("WVW-STAGE-01 via $STAGING_ISSUER for [$AUDIENCE] valid 15 min", response.bodyAsText())
    }

    @Test
    fun eachDeploymentAcceptsOnlyTokensSignedWithItsOwnSecret() {
        var stagingToken: String = ""
        deployed(stagingConfig()) {
            stagingToken = client.post("/login/WVW-STAGE-02").bodyAsText()
            assertEquals(HttpStatusCode.OK, client.get("/telemetry/session") { bearerAuth(stagingToken) }.status)
        }

        deployed(productionConfig()) {
            val productionToken: String = client.post("/login/WVW-PROD-02").bodyAsText()
            val rejected: HttpResponse = client.get("/telemetry/session") { bearerAuth(stagingToken) }
            val accepted: HttpResponse = client.get("/telemetry/session") { bearerAuth(productionToken) }

            assertNotEquals(stagingToken, productionToken)
            assertEquals(HttpStatusCode.Unauthorized, rejected.status)
            assertEquals("Bearer realm=\"$PRODUCTION_REALM\"", rejected.headers[HttpHeaders.WWWAuthenticate])
            assertEquals(HttpStatusCode.OK, accepted.status)
            assertEquals("WVW-PROD-02 via $PRODUCTION_ISSUER for [$AUDIENCE] valid 5 min", accepted.bodyAsText())
        }
    }

    @Test
    fun missingTokenIsChallengedWithTheConfiguredRealm() = deployed(stagingConfig()) {
        val response: HttpResponse = client.get("/telemetry/session")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("Bearer realm=\"$STAGING_REALM\"", response.headers[HttpHeaders.WWWAuthenticate])
    }

    @Test
    fun everyConfiguredIssuerIsTrusted() = deployed(productionConfig()) {
        val fromPrimary: String = token("WVW-PROD-03", PRODUCTION_ISSUER, PRODUCTION_SECRET)
        val fromLegacy: String = token("WVW-PROD-04", LEGACY_ISSUER, PRODUCTION_SECRET)
        val fromStranger: String = token("WVW-PROD-05", "https://unknown.example", PRODUCTION_SECRET)
        val forOtherAudience: String = token("WVW-PROD-06", PRODUCTION_ISSUER, PRODUCTION_SECRET, audience = "billing")

        val legacy: HttpResponse = client.get("/telemetry/session") { bearerAuth(fromLegacy) }

        assertEquals(HttpStatusCode.OK, client.get("/telemetry/session") { bearerAuth(fromPrimary) }.status)
        assertEquals(HttpStatusCode.OK, legacy.status)
        assertEquals("WVW-PROD-04 via $LEGACY_ISSUER for [$AUDIENCE] of unknown lifetime", legacy.bodyAsText())
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get("/telemetry/session") { bearerAuth(fromStranger) }.status,
        )
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get("/telemetry/session") { bearerAuth(forOtherAudience) }.status,
        )
    }

    @Test
    fun recentlyExpiredTokenPassesOnlyWithinTheConfiguredLeeway() {
        val expiredRecently: (String, String) -> String = { issuer: String, secret: String ->
            token("WVW-EDGE-01", issuer, secret) {
                withExpiresAt(Date(System.currentTimeMillis() - RECENTLY_EXPIRED_MILLIS))
            }
        }
        val expiredLongAgo: (String, String) -> String = { issuer: String, secret: String ->
            token("WVW-EDGE-02", issuer, secret) {
                withExpiresAt(Date(System.currentTimeMillis() - LONG_EXPIRED_MILLIS))
            }
        }

        deployed(stagingConfig()) {
            val recent: HttpResponse = client.get("/telemetry/session") {
                bearerAuth(expiredRecently(STAGING_ISSUER, STAGING_SECRET))
            }
            val old: HttpResponse = client.get("/telemetry/session") {
                bearerAuth(expiredLongAgo(STAGING_ISSUER, STAGING_SECRET))
            }
            assertEquals(HttpStatusCode.OK, recent.status)
            assertEquals(HttpStatusCode.Unauthorized, old.status)
        }

        deployed(productionConfig()) {
            val recent: HttpResponse = client.get("/telemetry/session") {
                bearerAuth(expiredRecently(PRODUCTION_ISSUER, PRODUCTION_SECRET))
            }
            assertEquals(HttpStatusCode.Unauthorized, recent.status)
        }
    }

    @Test
    fun tokenWithoutVehicleIdentityIsRejected() = deployed(stagingConfig()) {
        val anonymous: String = JWT.create()
            .withIssuer(STAGING_ISSUER)
            .withAudience(AUDIENCE)
            .withExpiresAt(Date(System.currentTimeMillis() + MILLIS_PER_MINUTE))
            .sign(Algorithm.HMAC256(STAGING_SECRET))

        val response: HttpResponse = client.get("/telemetry/session") { bearerAuth(anonymous) }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    /** Staging: one issuer, a generous leeway for drifting test-rig clocks, short-ish tokens. */
    private fun stagingConfig(): MapApplicationConfig = MapApplicationConfig(
        "jwt.secret" to STAGING_SECRET,
        "jwt.audience" to AUDIENCE,
        "jwt.realm" to STAGING_REALM,
        "jwt.leeway-seconds" to "600",
        "jwt.lifetime-minutes" to "15",
    ).apply { put("jwt.trusted-issuers", listOf(STAGING_ISSUER)) }

    /** Production: a primary and a legacy issuer, no leeway, very short tokens. */
    private fun productionConfig(): MapApplicationConfig = MapApplicationConfig(
        "jwt.secret" to PRODUCTION_SECRET,
        "jwt.audience" to AUDIENCE,
        "jwt.realm" to PRODUCTION_REALM,
        "jwt.lifetime-minutes" to "5",
    ).apply { put("jwt.trusted-issuers", listOf(PRODUCTION_ISSUER, LEGACY_ISSUER)) }

    private companion object {
        const val STAGING_SECRET: String = "fleet-staging-signing-secret"
        const val PRODUCTION_SECRET: String = "fleet-production-signing-secret"
        const val STAGING_ISSUER: String = "https://auth.staging.fleet.example"
        const val PRODUCTION_ISSUER: String = "https://auth.fleet.example"
        const val LEGACY_ISSUER: String = "https://legacy-auth.fleet.example"
        const val AUDIENCE: String = "fleet-telemetry"
        const val STAGING_REALM: String = "fleet telemetry (staging)"
        const val PRODUCTION_REALM: String = "fleet telemetry"
        const val MILLIS_PER_MINUTE: Long = 60_000
        const val RECENTLY_EXPIRED_MILLIS: Long = 60_000
        const val LONG_EXPIRED_MILLIS: Long = 3_600_000
    }
}
