/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_auth_jvm

import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.auth.parseAuthorizationHeader
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.auth.AuthenticationStrategy
import io.ktor.server.auth.UnauthorizedResponse
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.auth.basic
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Authentication failure causes recorded on the `AuthenticationContext` and the order in which their challenges answer.
 *
 * A software vendor's download portal checks licence keys with its own provider and records why a
 * key failed: a missing or unknown key is an ordinary credential failure with a `License`
 * challenge, a revoked key is an `AuthenticationFailedCause.Error` whose challenge explains the
 * revocation, and an expired key is only recorded with `error()`, leaving the plain 401 Ktor
 * answers with. Nightly builds also accept staff Basic logins, so the portal relies on Ktor running
 * invalid-credential challenges before missing-credential ones and error challenges last. The
 * public catalogue lists the recorded failures for visitors it lets through anonymously.
 */
class LicenseFailureCauseReportingTest {

    /** The company a licence key was issued to. */
    data class Licensee(val company: String, val seats: Int)

    /** The state of a licence key in the vendor's licence register. */
    sealed interface LicenseRecord {
        data class Active(val licensee: Licensee) : LicenseRecord
        data class Revoked(val reason: String) : LicenseRecord
        data class Expired(val on: String) : LicenseRecord
    }

    /** Provider that resolves the `X-License-Key` header against the licence register. */
    class LicenseKeyProvider(config: Config) : AuthenticationProvider(config) {
        private val register: Map<String, LicenseRecord> = config.register

        class Config(name: String?) : AuthenticationProvider.Config(name) {
            var register: Map<String, LicenseRecord> = emptyMap()
        }

        override suspend fun onAuthenticate(context: AuthenticationContext) {
            val key: String? = context.call.request.headers[LICENSE_HEADER]
            when (val record: LicenseRecord? = key?.let { register[it] }) {
                is LicenseRecord.Active -> context.principal(name, record.licensee)
                is LicenseRecord.Revoked -> {
                    val cause: AuthenticationFailedCause.Error = AuthenticationFailedCause.Error("revoked: ${record.reason}")
                    context.challenge(LICENSE_CHALLENGE, cause) { challenge, call ->
                        val reasons: String = call.authentication.allErrors.joinToString("; ") { it.message }
                        call.respondText("licence refused ($reasons)", status = HttpStatusCode.Forbidden)
                        challenge.complete()
                    }
                }
                is LicenseRecord.Expired -> context.error(LICENSE_CHALLENGE, AuthenticationFailedCause.Error("expired on ${record.on}"))
                null -> {
                    val cause: AuthenticationFailedCause =
                        if (key == null) AuthenticationFailedCause.NoCredentials else AuthenticationFailedCause.InvalidCredentials
                    context.challenge(LICENSE_CHALLENGE, cause) { challenge, call ->
                        val parameters: Map<String, String> =
                            if (cause == AuthenticationFailedCause.InvalidCredentials) {
                                mapOf("realm" to "downloads", "error" to "unknown_key")
                            } else {
                                mapOf("realm" to "downloads")
                            }
                        call.respond(UnauthorizedResponse(HttpAuthHeader.Parameterized("License", parameters)))
                        challenge.complete()
                    }
                }
            }
        }

        companion object {
            const val LICENSE_HEADER: String = "X-License-Key"
            const val LICENSE_CHALLENGE: String = "LicenseKey"
        }
    }

    private fun describe(cause: AuthenticationFailedCause): String = when (cause) {
        AuthenticationFailedCause.NoCredentials -> "no credentials"
        AuthenticationFailedCause.InvalidCredentials -> "invalid credentials"
        is AuthenticationFailedCause.Error -> cause.message
    }

    private fun Application.portalModule() {
        install(Authentication) {
            register(
                LicenseKeyProvider(
                    LicenseKeyProvider.Config("licence").apply {
                        register = mapOf(
                            "LK-ACME-0042" to LicenseRecord.Active(Licensee("Acme Tools", 25)),
                            "LK-GLOBEX-0007" to LicenseRecord.Revoked("chargeback 2026-03"),
                            "LK-INITECH-0311" to LicenseRecord.Expired("2025-12-31"),
                        )
                    },
                ),
            )
            basic("staff") {
                realm = "Release engineering"
                validate { credentials ->
                    if (credentials.name == "rel-eng" && credentials.password == "n1ghtly") UserIdPrincipal(credentials.name) else null
                }
            }
        }
        routing {
            authenticate("licence") {
                get("/downloads/{file}") {
                    val licensee: Licensee = call.principal<Licensee>()!!
                    call.respondText("${call.parameters["file"]} for ${licensee.company} (${licensee.seats} seats)")
                }
            }
            authenticate("staff", "licence") {
                get("/builds/nightly") {
                    val by: String = call.principal<UserIdPrincipal>("staff")?.name ?: call.principal<Licensee>("licence")!!.company
                    call.respondText("nightly build for $by")
                }
            }
            authenticate("licence", strategy = AuthenticationStrategy.Optional) {
                get("/catalogue") {
                    val licensee: Licensee? = call.principal<Licensee>()
                    val failures: List<String> = call.authentication.allFailures.map { describe(it) }
                    call.respondText("catalogue for ${licensee?.company ?: "visitor"}; failures $failures")
                }
            }
        }
    }

    private fun challengeOf(response: HttpResponse): HttpAuthHeader.Parameterized {
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        return parseAuthorizationHeader(response.headers[HttpHeaders.WWWAuthenticate]!!) as HttpAuthHeader.Parameterized
    }

    @Test
    fun failureCausesDecideTheAnswerToEachLicenceKey() = testApplication {
        application { portalModule() }
        val header: String = LicenseKeyProvider.LICENSE_HEADER

        val active: HttpResponse = client.get("/downloads/studio-9.2.zip") { header(header, "LK-ACME-0042") }
        assertEquals(HttpStatusCode.OK, active.status)
        assertEquals("studio-9.2.zip for Acme Tools (25 seats)", active.bodyAsText())

        val missing: HttpAuthHeader.Parameterized = challengeOf(client.get("/downloads/studio-9.2.zip"))
        assertEquals("License", missing.authScheme)
        assertEquals("downloads", missing.parameter("realm"))
        assertNull(missing.parameter("error"), "a missing key is not reported as a wrong key")

        val unknown: HttpAuthHeader.Parameterized =
            challengeOf(client.get("/downloads/studio-9.2.zip") { header(header, "LK-NOBODY-9999") })
        assertEquals("unknown_key", unknown.parameter("error"))

        val revoked: HttpResponse = client.get("/downloads/studio-9.2.zip") { header(header, "LK-GLOBEX-0007") }
        assertEquals(HttpStatusCode.Forbidden, revoked.status, "the error challenge answers a revoked key")
        assertEquals("licence refused (revoked: chargeback 2026-03)", revoked.bodyAsText())

        val expired: HttpResponse = client.get("/downloads/studio-9.2.zip") { header(header, "LK-INITECH-0311") }
        assertEquals(HttpStatusCode.Unauthorized, expired.status, "an error without a challenge ends in a plain 401")
        assertNull(expired.headers[HttpHeaders.WWWAuthenticate], "Ktor's fallback 401 names no scheme")
    }

    @Test
    fun challengesAnswerInOrderOfTheirFailureCause() = testApplication {
        application { portalModule() }
        val header: String = LicenseKeyProvider.LICENSE_HEADER

        val byStaff: HttpResponse = client.get("/builds/nightly") { basicAuth("rel-eng", "n1ghtly") }
        assertEquals("nightly build for rel-eng", byStaff.bodyAsText())
        val byLicence: HttpResponse = client.get("/builds/nightly") { header(header, "LK-ACME-0042") }
        assertEquals("nightly build for Acme Tools", byLicence.bodyAsText())

        val anonymous: HttpAuthHeader.Parameterized = challengeOf(client.get("/builds/nightly"))
        assertEquals("Basic", anonymous.authScheme, "with two missing credentials the first provider answers")
        assertEquals("Release engineering", anonymous.parameter("realm"))

        val wrongKey: HttpAuthHeader.Parameterized = challengeOf(client.get("/builds/nightly") { header(header, "LK-NOBODY-9999") })
        assertEquals("License", wrongKey.authScheme, "an invalid key outranks the missing staff login")
        assertEquals("unknown_key", wrongKey.parameter("error"))

        val revokedKey: HttpAuthHeader.Parameterized =
            challengeOf(client.get("/builds/nightly") { header(header, "LK-GLOBEX-0007") })
        assertEquals("Basic", revokedKey.authScheme, "error challenges run only after credential challenges")
    }

    @Test
    fun optionalCatalogueReportsRecordedFailures() = testApplication {
        application { portalModule() }
        val header: String = LicenseKeyProvider.LICENSE_HEADER

        val licensed: HttpResponse = client.get("/catalogue") { header(header, "LK-ACME-0042") }
        assertEquals("catalogue for Acme Tools; failures []", licensed.bodyAsText())

        val visitor: HttpResponse = client.get("/catalogue")
        assertEquals(HttpStatusCode.OK, visitor.status, "Optional lets a visitor without a key through")
        assertEquals("catalogue for visitor; failures [no credentials]", visitor.bodyAsText())

        val wrongKey: HttpAuthHeader.Parameterized = challengeOf(client.get("/catalogue") { header(header, "LK-NOBODY-9999") })
        assertEquals("unknown_key", wrongKey.parameter("error"), "Optional still refuses a wrong key")
    }
}
