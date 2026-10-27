/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_auth_jvm

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.http.parseUrlEncodedParameters
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.OAuthAccessTokenResponse
import io.ktor.server.auth.OAuthServerSettings
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.oauth
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.util.StatelessHmacNonceManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * OAuth 2.0 authorization-code login delegated to an external identity provider.
 *
 * A project tracker signs readers in through a corporate identity provider: the login route
 * redirects to the provider's authorize page with an HMAC-signed state, the callback exchanges the
 * returned code for an access token, and the tracker reads the reader's profile with that token.
 * An expired code sends the reader back to the provider to sign in again, while a forged state or
 * a consent the reader declined ends on the tracker's own sign-in failure page.
 */
class OAuth2AuthorizationCodeLoginTest {

    /** Profile the identity provider returns from its userinfo endpoint. */
    @Serializable
    data class UserInfo(val sub: String, val name: String, val email: String)

    /** The token requests the identity provider received, in arrival order. */
    class TokenRequestLog {
        val requests: ConcurrentLinkedQueue<Parameters> = ConcurrentLinkedQueue()
    }

    private val providerHost: String = "https://id.example.test"
    private val callbackUrl: String = "http://localhost/auth/callback"

    private fun Application.identityProviderModule(log: TokenRequestLog) {
        routing {
            post("/oauth/token") {
                val form: Parameters = call.receiveText().parseUrlEncodedParameters()
                log.requests.add(form)
                val validExchange: Boolean = form["grant_type"] == "authorization_code" &&
                    form["code"] == "code-4711" &&
                    form["client_id"] == "tracker" &&
                    form["client_secret"] == "tracker-secret" &&
                    form["redirect_uri"] == callbackUrl
                if (validExchange) {
                    call.respondText(
                        """
                        {"access_token":"at-ada-2f9c","token_type":"Bearer","expires_in":3600,
                         "refresh_token":"rt-ada-77e1","scope":"openid profile email"}
                        """.trimIndent(),
                        ContentType.Application.Json,
                    )
                } else {
                    call.respondText(
                        """{"error":"invalid_grant","error_description":"code expired"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest,
                    )
                }
            }
            get("/userinfo") {
                if (call.request.headers[HttpHeaders.Authorization] == "Bearer at-ada-2f9c") {
                    call.respondText(
                        """{"sub":"u-1815","name":"Ada Lovelace","email":"ada@example.test"}""",
                        ContentType.Application.Json,
                    )
                } else {
                    call.respondText("", status = HttpStatusCode.Unauthorized)
                }
            }
        }
    }

    private fun Application.trackerModule(providerClient: HttpClient) {
        install(Authentication) {
            oauth("corporate-sso") {
                client = providerClient
                urlProvider = { callbackUrl }
                providerLookup = {
                    OAuthServerSettings.OAuth2ServerSettings(
                        name = "corporate",
                        authorizeUrl = "$providerHost/oauth/authorize",
                        accessTokenUrl = "$providerHost/oauth/token",
                        requestMethod = HttpMethod.Post,
                        clientId = "tracker",
                        clientSecret = "tracker-secret",
                        defaultScopes = listOf("openid", "profile", "email"),
                        nonceManager = StatelessHmacNonceManager(
                            key = "tracker-state-signing-key".toByteArray(),
                            timeoutMillis = 600_000L,
                        ),
                        extraAuthParameters = listOf("prompt" to "consent"),
                    )
                }
                fallback = { cause ->
                    respondText("sign-in failed: ${cause.message}", status = HttpStatusCode.Forbidden)
                }
            }
        }
        routing {
            authenticate("corporate-sso") {
                get("/auth/login") {
                    // The provider redirects before this handler runs.
                }
                get("/auth/callback") {
                    val tokens: OAuthAccessTokenResponse.OAuth2 = call.principal<OAuthAccessTokenResponse.OAuth2>()!!
                    val profileJson: String = providerClient.get("$providerHost/userinfo") {
                        bearerAuth(tokens.accessToken)
                    }.bodyAsText()
                    val profile: UserInfo = Json.decodeFromString<UserInfo>(profileJson)
                    call.respondText(
                        "welcome ${profile.name} <${profile.email}>; token ${tokens.tokenType} " +
                            "expires in ${tokens.expiresIn}s, refresh ${tokens.refreshToken}, " +
                            "scope ${tokens.extraParameters["scope"]}",
                    )
                }
            }
        }
    }

    @Test
    fun readersSignInThroughTheIdentityProvider() = testApplication {
        val log: TokenRequestLog = TokenRequestLog()
        externalServices {
            hosts(providerHost) { identityProviderModule(log) }
        }
        val providerClient: HttpClient = createClient { }
        application { trackerModule(providerClient) }
        val browser: HttpClient = createClient { followRedirects = false }

        val login: HttpResponse = browser.get("/auth/login")
        assertEquals(HttpStatusCode.Found, login.status)
        val authorize: Url = Url(login.headers[HttpHeaders.Location]!!)
        assertEquals("id.example.test", authorize.host)
        assertEquals("/oauth/authorize", authorize.encodedPath)
        assertEquals("tracker", authorize.parameters["client_id"])
        assertEquals("code", authorize.parameters["response_type"])
        assertEquals(callbackUrl, authorize.parameters["redirect_uri"])
        assertEquals("openid profile email", authorize.parameters["scope"])
        assertEquals("consent", authorize.parameters["prompt"])
        val state: String = authorize.parameters["state"]!!
        assertTrue(state.isNotBlank(), "the authorize redirect carries a signed state")

        val callback: HttpResponse = browser.get("/auth/callback") {
            parameter("code", "code-4711")
            parameter("state", state)
        }
        assertEquals(HttpStatusCode.OK, callback.status)
        assertEquals(
            "welcome Ada Lovelace <ada@example.test>; token Bearer expires in 3600s, " +
                "refresh rt-ada-77e1, scope openid profile email",
            callback.bodyAsText(),
        )
        val exchange: Parameters = log.requests.poll()!!
        assertEquals("authorization_code", exchange["grant_type"])
        assertEquals(state, exchange["state"])

        val forged: HttpResponse = browser.get("/auth/callback") {
            parameter("code", "code-4711")
            parameter("state", "forged-state")
        }
        assertEquals(HttpStatusCode.Forbidden, forged.status)
        assertTrue(forged.bodyAsText().startsWith("sign-in failed"), "a forged state is refused")
        assertTrue(log.requests.isEmpty(), "a forged state never reaches the token endpoint")

        val expired: HttpResponse = browser.get("/auth/callback") {
            parameter("code", "code-0000")
            parameter("state", state)
        }
        assertEquals(HttpStatusCode.Found, expired.status, "an expired code restarts the sign-in")
        val retry: Url = Url(expired.headers[HttpHeaders.Location]!!)
        assertEquals("/oauth/authorize", retry.encodedPath)
        assertTrue(!retry.parameters["state"].isNullOrBlank(), "the restarted sign-in carries a new state")
        assertEquals("code-0000", log.requests.poll()!!["code"])

        val declined: HttpResponse = browser.get("/auth/callback") {
            parameter("error", "access_denied")
            parameter("error_description", "reader declined")
            parameter("state", state)
        }
        assertEquals(HttpStatusCode.Forbidden, declined.status)
        assertTrue(declined.bodyAsText().startsWith("sign-in failed"), "a declined consent is refused")
        assertTrue(log.requests.isEmpty(), "a declined consent never reaches the token endpoint")
    }
}
