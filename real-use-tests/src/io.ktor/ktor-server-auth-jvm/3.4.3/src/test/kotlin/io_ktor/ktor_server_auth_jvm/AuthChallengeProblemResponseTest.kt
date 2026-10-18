/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_auth_jvm

import io.ktor.client.HttpClient
import io.ktor.client.request.basicAuth
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.auth.parseAuthorizationHeader
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.ForbiddenResponse
import io.ktor.server.auth.UnauthorizedResponse
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Authentication challenge and refusal responses rewritten by an application's StatusPages error layer.
 *
 * An inventory API answers every failure with a JSON problem document. When a call to a route
 * does not authenticate, the error layer reads the challenges of the provider's
 * `UnauthorizedResponse` into the problem and keeps them as `WWW-Authenticate` headers, so reads
 * ask for a robot bearer token and write-offs for a staff Basic login; a staff member writing
 * stock off without the admin role gets the problem for the
 * `ForbiddenResponse` the route answered; and a credential store outage thrown from `validate`
 * becomes a retryable service-unavailable problem instead of a challenge.
 */
class AuthChallengeProblemResponseTest {

    /** The JSON problem document the API answers failures with. */
    @Serializable
    data class Problem(val type: String, val status: Int, val schemes: List<String> = emptyList())

    /** Thrown by credential validation when the staff directory cannot be reached. */
    class StaffDirectoryUnavailable : RuntimeException("staff directory unavailable")

    private val staffPasswords: Map<String, String> = mapOf("alice" to "pick-list-7", "root" to "ledger-0")
    private val robotTokens: Map<String, String> = mapOf("rt-scanner-12" to "dock-scanner")

    private fun Application.inventoryModule() {
        install(ContentNegotiation) { json() }
        install(StatusPages) {
            exception<StaffDirectoryUnavailable> { call, _ ->
                call.response.headers.append(HttpHeaders.RetryAfter, "30")
                call.respond(HttpStatusCode.ServiceUnavailable, Problem("staff-directory-down", 503))
            }
            status(HttpStatusCode.Unauthorized) { status: HttpStatusCode ->
                val challenges: List<HttpAuthHeader> = (content as UnauthorizedResponse).challenges.toList()
                challenges.forEach { challenge: HttpAuthHeader ->
                    call.response.headers.append(HttpHeaders.WWWAuthenticate, challenge.render())
                }
                call.respond(status, Problem("authentication-required", status.value, challenges.map { it.authScheme }))
            }
            status(HttpStatusCode.Forbidden) { status: HttpStatusCode ->
                val reason: String = if (content is ForbiddenResponse) "role-required" else "forbidden"
                call.respond(status, Problem(reason, status.value))
            }
        }
        install(Authentication) {
            basic("staff") {
                realm = "inventory"
                validate { credentials ->
                    if (credentials.name == "directory-probe") throw StaffDirectoryUnavailable()
                    if (staffPasswords[credentials.name] == credentials.password) UserIdPrincipal(credentials.name) else null
                }
            }
            bearer("robots") {
                realm = "inventory-robots"
                authenticate { token -> robotTokens[token.token]?.let { robot: String -> UserIdPrincipal(robot) } }
            }
        }
        routing {
            authenticate("robots", "staff") {
                get("/stock/{sku}") {
                    val caller: UserIdPrincipal = call.principal<UserIdPrincipal>()!!
                    call.respondText("${call.parameters["sku"]}: 42 on hand (read by ${caller.name})")
                }
            }
            authenticate("staff") {
                delete("/stock/{sku}") {
                    val caller: UserIdPrincipal = call.principal<UserIdPrincipal>()!!
                    if (caller.name != "root") {
                        call.respond(ForbiddenResponse())
                    } else {
                        call.respondText("${call.parameters["sku"]} written off by ${caller.name}")
                    }
                }
            }
        }
    }

    private suspend fun HttpResponse.problem(): Problem {
        assertTrue(contentType()!!.match(ContentType.Application.Json), "failures answer a JSON problem")
        return Json.decodeFromString<Problem>(bodyAsText())
    }

    @Test
    fun authenticationFailuresAnswerProblemDocuments() = testApplication {
        application { inventoryModule() }
        val api: HttpClient = createClient { }

        val byStaff: HttpResponse = api.get("/stock/sku-100") { basicAuth("alice", "pick-list-7") }
        assertEquals(HttpStatusCode.OK, byStaff.status)
        assertEquals("sku-100: 42 on hand (read by alice)", byStaff.bodyAsText())
        val byRobot: HttpResponse = api.get("/stock/sku-100") { bearerAuth("rt-scanner-12") }
        assertEquals("sku-100: 42 on hand (read by dock-scanner)", byRobot.bodyAsText())

        val anonymous: HttpResponse = api.get("/stock/sku-100")
        assertEquals(HttpStatusCode.Unauthorized, anonymous.status)
        assertEquals(Problem("authentication-required", 401, listOf("Bearer")), anonymous.problem())
        val readChallenge: HttpAuthHeader.Parameterized =
            parseAuthorizationHeader(anonymous.headers[HttpHeaders.WWWAuthenticate]!!) as HttpAuthHeader.Parameterized
        assertEquals("Bearer", readChallenge.authScheme)
        assertEquals("inventory-robots", readChallenge.parameter(HttpAuthHeader.Parameters.Realm))

        val wrongPassword: HttpResponse = api.get("/stock/sku-100") { basicAuth("alice", "guess") }
        assertEquals(HttpStatusCode.Unauthorized, wrongPassword.status)
        assertEquals(listOf("Bearer"), wrongPassword.problem().schemes)

        val revokedToken: HttpResponse = api.delete("/stock/sku-100") { bearerAuth("rt-scanner-12") }
        assertEquals(HttpStatusCode.Unauthorized, revokedToken.status, "robots cannot write stock off")
        assertEquals(Problem("authentication-required", 401, listOf("Basic")), revokedToken.problem())
        val writeChallenge: HttpAuthHeader.Parameterized =
            parseAuthorizationHeader(revokedToken.headers[HttpHeaders.WWWAuthenticate]!!) as HttpAuthHeader.Parameterized
        assertEquals("inventory", writeChallenge.parameter(HttpAuthHeader.Parameters.Realm))

        val notAdmin: HttpResponse = api.delete("/stock/sku-100") { basicAuth("alice", "pick-list-7") }
        assertEquals(HttpStatusCode.Forbidden, notAdmin.status)
        assertEquals(Problem("role-required", 403), notAdmin.problem())
        val admin: HttpResponse = api.delete("/stock/sku-100") { basicAuth("root", "ledger-0") }
        assertEquals(HttpStatusCode.OK, admin.status)
        assertEquals("sku-100 written off by root", admin.bodyAsText())

        val outage: HttpResponse = api.get("/stock/sku-100") { basicAuth("directory-probe", "any") }
        assertEquals(HttpStatusCode.ServiceUnavailable, outage.status)
        assertEquals("30", outage.headers[HttpHeaders.RetryAfter])
        assertEquals(null, outage.headers[HttpHeaders.WWWAuthenticate], "an outage is not a challenge")
        assertEquals(Problem("staff-directory-down", 503), outage.problem())
    }
}
