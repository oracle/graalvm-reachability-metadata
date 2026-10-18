/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_auth_jwt_jvm

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.application.isHandled
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.Collections
import java.util.Date
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Route-level authorization layered on the JWT provider: a route-scoped plugin
 * hooked on [AuthenticationChecked] enforces the role claims of the [JWTPrincipal],
 * and `skipWhen` lets a trusted internal sync job bypass token authentication.
 *
 * A clinic records service lets nurses and doctors read charts but only doctors
 * write prescriptions; a signed-in clinician without the role gets 403 from the
 * authorization plugin, a missing or forged token still gets the provider's bearer
 * challenge, and the nightly sync job presenting the shared sync key skips the JWT
 * check altogether, so even a forged token it carries is never inspected.
 */
public class ClinicRecordsRoleAuthorizationTest {
    /** Configuration of the [requireRole] plugin: the roles any one of which admits a caller. */
    public class RoleRequirement {
        public var anyOf: Set<String> = emptySet()
    }

    /** Admits the call only if the authenticated clinician holds one of the configured roles. */
    private val requireRole = createRouteScopedPlugin("RequireRole", ::RoleRequirement) {
        val allowed: Set<String> = pluginConfig.anyOf
        on(AuthenticationChecked) { call ->
            if (call.isHandled) {
                return@on
            }
            val principal: JWTPrincipal = call.principal<JWTPrincipal>() ?: return@on
            val roles: List<String> = principal.getListClaim("roles", String::class)
            if (roles.none { it in allowed }) {
                denials += "${principal.subject} ${call.request.local.uri}"
                call.respondText(
                    "role required: ${allowed.sorted().joinToString(" or ")}",
                    status = HttpStatusCode.Forbidden,
                )
            }
        }
    }

    /** Requests the authorization plugin turned away, in arrival order. */
    private val denials: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** The records application: the `clinic-staff` provider and its role-guarded routes. */
    private fun ApplicationTestBuilder.clinic() {
        application {
            install(Authentication) {
                jwt("clinic-staff") {
                    realm = REALM
                    skipWhen { call -> call.request.headers[SYNC_KEY_HEADER] == SYNC_KEY }
                    verifier(
                        JWT.require(Algorithm.HMAC256(SECRET))
                            .withIssuer(ISSUER)
                            .withAudience(AUDIENCE)
                            .build(),
                    )
                    validate { credential -> JWTPrincipal(credential.payload) }
                }
            }
            routing {
                authenticate("clinic-staff") {
                    route("/charts/{patient}") {
                        install(requireRole) { anyOf = setOf("nurse", "doctor") }
                        get {
                            val reader: String = call.principal<JWTPrincipal>()?.subject ?: "sync-job"
                            call.respondText("chart ${call.parameters["patient"]} read by $reader")
                        }
                    }
                    route("/prescriptions") {
                        install(requireRole) { anyOf = setOf("doctor") }
                        post {
                            val prescriber: String = call.principal<JWTPrincipal>()?.subject ?: "sync-job"
                            call.respondText("prescription recorded by $prescriber", status = HttpStatusCode.Created)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun nurseReadsChartsButCannotPrescribe() = testApplication {
        clinic()
        val nurse: String = token("nurse.kim", listOf("nurse"))

        val chart: HttpResponse = client.get("/charts/p-104") { bearerAuth(nurse) }
        assertEquals(HttpStatusCode.OK, chart.status)
        assertEquals("chart p-104 read by nurse.kim", chart.bodyAsText())

        val prescription: HttpResponse = client.post("/prescriptions") { bearerAuth(nurse) }
        assertEquals(HttpStatusCode.Forbidden, prescription.status)
        assertEquals("role required: doctor", prescription.bodyAsText())
        assertNull(prescription.headers[HttpHeaders.WWWAuthenticate])
        assertEquals(listOf("nurse.kim /prescriptions"), denials.toList())
    }

    @Test
    fun doctorReadsChartsAndPrescribes() = testApplication {
        clinic()
        val doctor: String = token("dr.osei", listOf("doctor", "on-call"))

        val chart: HttpResponse = client.get("/charts/p-221") { bearerAuth(doctor) }
        assertEquals("chart p-221 read by dr.osei", chart.bodyAsText())

        val prescription: HttpResponse = client.post("/prescriptions") { bearerAuth(doctor) }
        assertEquals(HttpStatusCode.Created, prescription.status)
        assertEquals("prescription recorded by dr.osei", prescription.bodyAsText())
        assertEquals(emptyList<String>(), denials.toList())
    }

    @Test
    fun clinicianWithoutClinicalRoleIsForbiddenEverywhere() = testApplication {
        clinic()
        val receptionist: String = token("front.desk", listOf("reception"))

        val chart: HttpResponse = client.get("/charts/p-104") { bearerAuth(receptionist) }
        assertEquals(HttpStatusCode.Forbidden, chart.status)
        assertEquals("role required: doctor or nurse", chart.bodyAsText())

        val prescription: HttpResponse = client.post("/prescriptions") { bearerAuth(receptionist) }
        assertEquals(HttpStatusCode.Forbidden, prescription.status)
        assertEquals(listOf("front.desk /charts/p-104", "front.desk /prescriptions"), denials.toList())
    }

    @Test
    fun missingOrForgedTokenGetsBearerChallengeBeforeAuthorization() = testApplication {
        clinic()

        val anonymous: HttpResponse = client.get("/charts/p-104")
        assertEquals(HttpStatusCode.Unauthorized, anonymous.status)
        assertEquals(CHALLENGE, anonymous.headers[HttpHeaders.WWWAuthenticate])

        val forged: HttpResponse = client.post("/prescriptions") {
            bearerAuth(token("dr.osei", listOf("doctor"), secret = "stolen-laptop-secret"))
        }
        assertEquals(HttpStatusCode.Unauthorized, forged.status)
        assertEquals(CHALLENGE, forged.headers[HttpHeaders.WWWAuthenticate])

        val wrongSyncKey: HttpResponse = client.get("/charts/p-104") { header(SYNC_KEY_HEADER, "guess") }
        assertEquals(HttpStatusCode.Unauthorized, wrongSyncKey.status)
        assertEquals(emptyList<String>(), denials.toList())
    }

    @Test
    fun syncJobSkipsTokenAuthenticationWithTheSyncKey() = testApplication {
        clinic()

        val chart: HttpResponse = client.get("/charts/p-104") { header(SYNC_KEY_HEADER, SYNC_KEY) }
        assertEquals(HttpStatusCode.OK, chart.status)
        assertEquals("chart p-104 read by sync-job", chart.bodyAsText())

        val withForgedToken: HttpResponse = client.post("/prescriptions") {
            header(SYNC_KEY_HEADER, SYNC_KEY)
            bearerAuth(token("dr.osei", listOf("doctor"), secret = "stolen-laptop-secret"))
        }
        assertEquals(HttpStatusCode.Created, withForgedToken.status)
        assertEquals("prescription recorded by sync-job", withForgedToken.bodyAsText())
        assertEquals(emptyList<String>(), denials.toList())
    }

    private fun token(subject: String, roles: List<String>, secret: String = SECRET): String =
        JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withSubject(subject)
            .withClaim("roles", roles)
            .withExpiresAt(Date(System.currentTimeMillis() + 600_000L))
            .sign(Algorithm.HMAC256(secret))

    private companion object {
        const val SECRET: String = "clinic-records-signing-secret"
        const val ISSUER: String = "https://sso.clinic.example"
        const val AUDIENCE: String = "clinic-records"
        const val REALM: String = "clinic records"
        const val CHALLENGE: String = "Bearer realm=\"clinic records\""
        const val SYNC_KEY_HEADER: String = "X-Sync-Key"
        const val SYNC_KEY: String = "nightly-sync-7f3a"
    }
}
