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
import io.ktor.server.auth.basic
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * Authentication strategies combining an application-defined API-key provider with HTTP Basic on one route.
 *
 * A partner gateway authenticates partner systems with an `X-Api-Key` header through its own
 * `AuthenticationProvider` subclass and staff with Basic credentials. The catalog accepts either
 * (`FirstSuccessful`), settlements demand both a partner key and a staff login (`Required`), and
 * the news feed is open to anonymous readers but still refuses a forged key (`Optional`).
 */
class MultiProviderStrategyTest {

    /** A partner system identified by its API key. */
    data class Partner(val name: String)

    /** Provider that resolves the `X-Api-Key` header to a [Partner]. */
    class ApiKeyProvider(config: Config) : AuthenticationProvider(config) {
        private val keys: Map<String, Partner> = config.keys

        class Config(name: String?) : AuthenticationProvider.Config(name) {
            var keys: Map<String, Partner> = emptyMap()
        }

        override suspend fun onAuthenticate(context: AuthenticationContext) {
            val key: String? = context.call.request.headers[API_KEY_HEADER]
            val partner: Partner? = key?.let { keys[it] }
            if (partner != null) {
                context.principal(name, partner)
                return
            }
            val cause: AuthenticationFailedCause =
                if (key == null) AuthenticationFailedCause.NoCredentials else AuthenticationFailedCause.InvalidCredentials
            context.challenge(API_KEY_CHALLENGE, cause) { challenge, call ->
                call.respond(UnauthorizedResponse(HttpAuthHeader.Parameterized("ApiKey", mapOf("realm" to "Partners"))))
                challenge.complete()
            }
        }

        companion object {
            const val API_KEY_HEADER: String = "X-Api-Key"
            const val API_KEY_CHALLENGE: String = "PartnerApiKey"
        }
    }

    private fun Application.gatewayModule() {
        install(Authentication) {
            register(
                ApiKeyProvider(
                    ApiKeyProvider.Config("partner-key").apply {
                        keys = mapOf("pk-nordic-5521" to Partner("nordic-freight"), "pk-delta-0918" to Partner("delta-retail"))
                    },
                ),
            )
            basic("staff") {
                realm = "Gateway staff"
                validate { credentials ->
                    if (credentials.name == "ines" && credentials.password == "s3ttle") UserIdPrincipal(credentials.name) else null
                }
            }
        }
        routing {
            authenticate("partner-key", "staff", strategy = AuthenticationStrategy.FirstSuccessful) {
                get("/catalog") {
                    val partner: Partner? = call.principal<Partner>("partner-key")
                    val staff: UserIdPrincipal? = call.principal<UserIdPrincipal>("staff")
                    call.respondText("catalog for ${partner?.name ?: "staff ${staff!!.name}"}")
                }
            }
            authenticate("partner-key", "staff", strategy = AuthenticationStrategy.Required) {
                get("/settlements") {
                    val partner: Partner = call.principal<Partner>("partner-key")!!
                    val staff: UserIdPrincipal = call.principal<UserIdPrincipal>("staff")!!
                    call.respondText("settlements of ${partner.name} approved by ${staff.name}")
                }
            }
            authenticate("partner-key", strategy = AuthenticationStrategy.Optional) {
                get("/news") {
                    call.respondText("news for ${call.principal<Partner>()?.name ?: "everyone"}")
                }
            }
        }
    }

    private fun challengeOf(response: HttpResponse): HttpAuthHeader.Parameterized {
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        return parseAuthorizationHeader(response.headers[HttpHeaders.WWWAuthenticate]!!) as HttpAuthHeader.Parameterized
    }

    @Test
    fun strategiesDecideWhichProvidersMustSucceed() = testApplication {
        application { gatewayModule() }

        val byKey: HttpResponse = client.get("/catalog") { header(ApiKeyProvider.API_KEY_HEADER, "pk-nordic-5521") }
        assertEquals(HttpStatusCode.OK, byKey.status)
        assertEquals("catalog for nordic-freight", byKey.bodyAsText())

        val byStaff: HttpResponse = client.get("/catalog") { basicAuth("ines", "s3ttle") }
        assertEquals(HttpStatusCode.OK, byStaff.status, "FirstSuccessful accepts the second provider alone")
        assertEquals("catalog for staff ines", byStaff.bodyAsText())

        val anonymousCatalog: HttpAuthHeader.Parameterized = challengeOf(client.get("/catalog"))
        assertEquals("ApiKey", anonymousCatalog.authScheme, "the first provider's challenge answers")
        assertEquals("Partners", anonymousCatalog.parameter("realm"))

        val settled: HttpResponse = client.get("/settlements") {
            header(ApiKeyProvider.API_KEY_HEADER, "pk-delta-0918")
            basicAuth("ines", "s3ttle")
        }
        assertEquals(HttpStatusCode.OK, settled.status)
        assertEquals("settlements of delta-retail approved by ines", settled.bodyAsText())

        val keyOnly: HttpAuthHeader.Parameterized = challengeOf(
            client.get("/settlements") { header(ApiKeyProvider.API_KEY_HEADER, "pk-delta-0918") },
        )
        assertEquals("Basic", keyOnly.authScheme, "Required still asks for the missing staff login")
        assertEquals("Gateway staff", keyOnly.parameter("realm"))

        val staffOnly: HttpAuthHeader.Parameterized = challengeOf(client.get("/settlements") { basicAuth("ines", "s3ttle") })
        assertEquals("ApiKey", staffOnly.authScheme, "Required still asks for the missing partner key")

        val wrongPassword: HttpAuthHeader.Parameterized = challengeOf(
            client.get("/settlements") {
                header(ApiKeyProvider.API_KEY_HEADER, "pk-delta-0918")
                basicAuth("ines", "guess")
            },
        )
        assertEquals("Basic", wrongPassword.authScheme)

        val anonymousNews: HttpResponse = client.get("/news")
        assertEquals(HttpStatusCode.OK, anonymousNews.status, "Optional lets readers without a key through")
        assertEquals("news for everyone", anonymousNews.bodyAsText())

        val partnerNews: HttpResponse = client.get("/news") { header(ApiKeyProvider.API_KEY_HEADER, "pk-nordic-5521") }
        assertEquals("news for nordic-freight", partnerNews.bodyAsText())

        val forged: HttpResponse = client.get("/news") { header(ApiKeyProvider.API_KEY_HEADER, "pk-forged-0000") }
        assertEquals("ApiKey", challengeOf(forged).authScheme, "Optional refuses a key that does not resolve")
        assertFalse(forged.bodyAsText().startsWith("news"), "the forged key never reaches the feed")
    }
}
