/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_auth_jvm

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
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * HTTP Bearer token authentication resolving API tokens to an application-defined principal.
 *
 * A weather API issues opaque tokens to client applications, accepts them under both the
 * `Bearer` and the legacy `Token` schemes, and exposes a forecast that requires a token next to a
 * public summary that personalises its greeting only when a token is presented. Revoked tokens,
 * foreign schemes, and missing tokens on the protected route are refused with a Bearer challenge.
 */
class BearerTokenApiKeyTest {

    /** The client application a token was issued to. */
    data class ApiClient(val name: String, val plan: String)

    /** Token registry an application would normally back with a database. */
    class TokenRegistry(private val issued: Map<String, ApiClient>) {
        fun resolve(token: String): ApiClient? = issued[token]
    }

    private fun Application.weatherModule(registry: TokenRegistry) {
        install(Authentication) {
            bearer("weather-api") {
                realm = "Weather API"
                authSchemes("Bearer", "Token")
                authenticate { credential -> registry.resolve(credential.token) }
            }
        }
        routing {
            authenticate("weather-api") {
                get("/forecast") {
                    val client: ApiClient = call.principal<ApiClient>()!!
                    call.respondText("forecast for ${client.name} (${client.plan}): sunny, 24C")
                }
            }
            authenticate("weather-api", optional = true) {
                get("/summary") {
                    val client: ApiClient? = call.principal<ApiClient>()
                    call.respondText("summary for ${client?.name ?: "guest"}: mild")
                }
            }
        }
    }

    private fun assertBearerChallenge(response: HttpResponse) {
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val challenge: HttpAuthHeader = parseAuthorizationHeader(response.headers[HttpHeaders.WWWAuthenticate]!!)!!
        assertTrue(challenge is HttpAuthHeader.Parameterized, "the challenge carries parameters: $challenge")
        assertEquals("Bearer", challenge.authScheme)
        assertEquals("Weather API", (challenge as HttpAuthHeader.Parameterized).parameter("realm"))
    }

    @Test
    fun bearerTokensIdentifyApiClients() = testApplication {
        val registry: TokenRegistry = TokenRegistry(
            mapOf(
                "tok-alpine-7f3a" to ApiClient("alpine-app", "pro"),
                "tok-harbor-91c2" to ApiClient("harbor-app", "free"),
            ),
        )
        application { weatherModule(registry) }

        val alpine: HttpResponse = client.get("/forecast") {
            header(HttpHeaders.Authorization, "Bearer tok-alpine-7f3a")
        }
        assertEquals(HttpStatusCode.OK, alpine.status)
        assertEquals("forecast for alpine-app (pro): sunny, 24C", alpine.bodyAsText())

        val legacy: HttpResponse = client.get("/forecast") {
            header(HttpHeaders.Authorization, "Token tok-harbor-91c2")
        }
        assertEquals(HttpStatusCode.OK, legacy.status, "the legacy Token scheme is accepted too")
        assertEquals("forecast for harbor-app (free): sunny, 24C", legacy.bodyAsText())

        assertBearerChallenge(client.get("/forecast") { header(HttpHeaders.Authorization, "Bearer tok-revoked-0000") })
        assertBearerChallenge(client.get("/forecast") { header(HttpHeaders.Authorization, "Basic dG9rOnRvaw==") })
        assertBearerChallenge(client.get("/forecast"))

        val guest: HttpResponse = client.get("/summary")
        assertEquals(HttpStatusCode.OK, guest.status, "the summary is open without a token")
        assertEquals("summary for guest: mild", guest.bodyAsText())

        val member: HttpResponse = client.get("/summary") {
            header(HttpHeaders.Authorization, "Bearer tok-alpine-7f3a")
        }
        assertEquals("summary for alpine-app: mild", member.bodyAsText())
    }
}
