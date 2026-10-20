/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_auth_jvm

import io.ktor.client.HttpClient
import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.parseUrlEncodedParameters
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.OAuth2Exception
import io.ktor.server.auth.OAuthAccessTokenResponse
import io.ktor.server.auth.OAuthServerSettings
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.auth.principal
import io.ktor.server.auth.verifyWithOAuth2
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.io.encoding.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * OAuth 2.0 resource-owner password grant verifying HTTP Basic credentials with `verifyWithOAuth2`.
 *
 * A build-artifact repository lets command-line tools sign in with plain Basic credentials, but it
 * keeps no passwords itself: each credential is checked by exchanging it at the corporate identity
 * provider's token endpoint for an access token, and the token's scope decides whether the tool
 * may publish. Wrong passwords and locked accounts come back from the provider as OAuth errors,
 * which the repository turns into its own Basic challenge.
 */
class OAuth2PasswordGrantBasicTest {

    /** A repository user whose identity was confirmed by the identity provider. */
    data class RepositoryUser(val name: String, val tokens: OAuthAccessTokenResponse.OAuth2)

    /** The token requests the identity provider received, with their client authentication. */
    class TokenRequestLog {
        val forms: ConcurrentLinkedQueue<Parameters> = ConcurrentLinkedQueue()
        val clientAuthorizations: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()
    }

    private val providerHost: String = "https://id.example.test"

    private fun Application.identityProviderModule(log: TokenRequestLog) {
        routing {
            post("/oauth/token") {
                val form: Parameters = call.receiveText().parseUrlEncodedParameters()
                log.forms.add(form)
                log.clientAuthorizations.add(call.request.headers[HttpHeaders.Authorization] ?: "none")
                val user: String? = form["username"]
                val password: String? = form["password"]
                when {
                    form["grant_type"] != "password" -> call.respondText(
                        """{"error":"unsupported_grant_type"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest,
                    )
                    user == "ada" && password == "analytical-engine" -> call.respondText(
                        """
                        {"access_token":"at-ada-91b0","token_type":"Bearer","expires_in":900,
                         "refresh_token":"rt-ada-4c2d","scope":"artifacts:read artifacts:publish"}
                        """.trimIndent(),
                        ContentType.Application.Json,
                    )
                    user == "grace" && password == "cobol" -> call.respondText(
                        """
                        {"access_token":"at-grace-07aa","token_type":"Bearer","expires_in":900,
                         "scope":"artifacts:read"}
                        """.trimIndent(),
                        ContentType.Application.Json,
                    )
                    else -> call.respondText(
                        """{"error":"invalid_grant","error_description":"bad credentials or locked account"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest,
                    )
                }
            }
        }
    }

    private fun Application.repositoryModule(providerClient: HttpClient) {
        val settings: OAuthServerSettings.OAuth2ServerSettings = OAuthServerSettings.OAuth2ServerSettings(
            name = "corporate",
            authorizeUrl = "$providerHost/oauth/authorize",
            accessTokenUrl = "$providerHost/oauth/token",
            requestMethod = HttpMethod.Post,
            clientId = "artifact-repo",
            clientSecret = "repo-secret",
        )
        install(Authentication) {
            basic("repository-cli") {
                realm = "Artifact Repository"
                validate { credentials ->
                    try {
                        RepositoryUser(credentials.name, verifyWithOAuth2(credentials, providerClient, settings))
                    } catch (refused: OAuth2Exception.InvalidGrant) {
                        null
                    }
                }
            }
        }
        routing {
            authenticate("repository-cli") {
                get("/artifacts/{name}/publish") {
                    val user: RepositoryUser = call.principal<RepositoryUser>()!!
                    val scopes: List<String> = user.tokens.extraParameters["scope"].orEmpty().split(" ")
                    if ("artifacts:publish" in scopes) {
                        call.respondText(
                            "${user.name} published ${call.parameters["name"]} " +
                                "(token ${user.tokens.tokenType}, ${user.tokens.expiresIn}s)",
                        )
                    } else {
                        call.respondText("${user.name} may only read", status = HttpStatusCode.Forbidden)
                    }
                }
            }
        }
    }

    @Test
    fun basicCredentialsAreVerifiedAtTheIdentityProvider() = testApplication {
        val log: TokenRequestLog = TokenRequestLog()
        externalServices {
            hosts(providerHost) { identityProviderModule(log) }
        }
        val providerClient: HttpClient = createClient { }
        application { repositoryModule(providerClient) }

        val publisher: HttpResponse = client.get("/artifacts/core-1.4.jar/publish") {
            basicAuth("ada", "analytical-engine")
        }
        assertEquals(HttpStatusCode.OK, publisher.status)
        assertEquals("ada published core-1.4.jar (token Bearer, 900s)", publisher.bodyAsText())
        val exchange: Parameters = log.forms.poll()!!
        assertEquals("password", exchange["grant_type"])
        assertEquals("ada", exchange["username"])
        assertEquals("analytical-engine", exchange["password"])
        assertEquals(
            "Basic " + Base64.encode("artifact-repo:repo-secret".encodeToByteArray()),
            log.clientAuthorizations.poll(),
            "the repository authenticates itself to the provider as a confidential client",
        )

        val reader: HttpResponse = client.get("/artifacts/core-1.4.jar/publish") {
            basicAuth("grace", "cobol")
        }
        assertEquals(HttpStatusCode.Forbidden, reader.status)
        assertEquals("grace may only read", reader.bodyAsText())
        assertEquals("grace", log.forms.poll()!!["username"])

        val wrongPassword: HttpResponse = client.get("/artifacts/core-1.4.jar/publish") {
            basicAuth("ada", "difference-engine")
        }
        assertEquals(HttpStatusCode.Unauthorized, wrongPassword.status)
        assertEquals(
            "Basic realm=\"Artifact Repository\", charset=UTF-8",
            wrongPassword.headers[HttpHeaders.WWWAuthenticate],
        )
        assertEquals("difference-engine", log.forms.poll()!!["password"])

        log.clientAuthorizations.clear()
        val anonymous: HttpResponse = client.get("/artifacts/core-1.4.jar/publish")
        assertEquals(HttpStatusCode.Unauthorized, anonymous.status)
        assertTrue(log.forms.isEmpty(), "a request without credentials never reaches the provider")
    }
}
