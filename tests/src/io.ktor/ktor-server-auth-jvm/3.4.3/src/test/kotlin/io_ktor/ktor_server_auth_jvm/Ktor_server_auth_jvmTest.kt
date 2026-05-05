/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_auth_jvm

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.auth.parseAuthorizationHeader
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.AuthenticationStrategy
import io.ktor.server.auth.BearerTokenCredential
import io.ktor.server.auth.DigestCredential
import io.ktor.server.auth.OAuthAccessTokenResponse
import io.ktor.server.auth.OAuthCallback
import io.ktor.server.auth.OAuthServerSettings
import io.ktor.server.auth.OAuthVersion
import io.ktor.server.auth.UserHashedTableAuth
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.UserPasswordCredential
import io.ktor.server.auth.allProviders
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.auth.bearer
import io.ktor.server.auth.digest
import io.ktor.server.auth.expectedDigest
import io.ktor.server.auth.form
import io.ktor.server.auth.principal
import io.ktor.server.auth.toDigestCredential
import io.ktor.server.auth.verifier
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.util.NonceManager
import io.ktor.util.hex
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.Base64

public class KtorServerAuthJvmTest {
    @Test
    fun basicAuthenticationValidatesCredentialsAndEmitsChallenges() = testApplication {
        application {
            install(Authentication) {
                basic("basic") {
                    realm = "test-realm"
                    validate { credential ->
                        if (credential.name == "alice" && credential.password == "wonderland") {
                            UserIdPrincipal(credential.name)
                        } else {
                            null
                        }
                    }
                }
            }
            routing {
                authenticate("basic") {
                    get("/basic") {
                        call.respondText("basic:${call.principal<UserIdPrincipal>()?.name}")
                    }
                }
            }
        }

        val successfulResponse = client.get("/basic") {
            header(HttpHeaders.Authorization, basicAuthorization("alice", "wonderland"))
        }
        assertThat(successfulResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(successfulResponse.bodyAsText()).isEqualTo("basic:alice")

        val rejectedResponse = client.get("/basic") {
            header(HttpHeaders.Authorization, basicAuthorization("alice", "wrong"))
        }
        assertThat(rejectedResponse.status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(rejectedResponse.headers[HttpHeaders.WWWAuthenticate])
            .isEqualTo("Basic realm=test-realm, charset=UTF-8")

        val missingResponse = client.get("/basic")
        assertThat(missingResponse.status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(missingResponse.bodyAsText()).isEmpty()
    }

    @Test
    fun bearerAuthenticationAcceptsConfiguredSchemesAndRejectsBadTokens() = testApplication {
        application {
            install(Authentication) {
                bearer("api") {
                    realm = "api"
                    authSchemes("Bearer", "Token")
                    authenticate { credential: BearerTokenCredential ->
                        if (credential.token == "s3cr3t") UserIdPrincipal("token-user") else null
                    }
                }
            }
            routing {
                authenticate("api") {
                    get("/bearer") {
                        call.respondText("bearer:${call.principal<UserIdPrincipal>()?.name}")
                    }
                }
            }
        }

        val tokenSchemeResponse = client.get("/bearer") {
            header(HttpHeaders.Authorization, "Token s3cr3t")
        }
        assertThat(tokenSchemeResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(tokenSchemeResponse.bodyAsText()).isEqualTo("bearer:token-user")

        val rejectedResponse = client.get("/bearer") {
            header(HttpHeaders.Authorization, "Bearer wrong")
        }
        assertThat(rejectedResponse.status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(rejectedResponse.headers[HttpHeaders.WWWAuthenticate]).isEqualTo("Bearer realm=api")

        val wrongSchemeResponse = client.get("/bearer") {
            header(HttpHeaders.Authorization, basicAuthorization("token-user", "s3cr3t"))
        }
        assertThat(wrongSchemeResponse.status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun formAuthenticationUsesCustomParameterNamesAndChallenge() = testApplication {
        application {
            install(Authentication) {
                form("login-form") {
                    userParamName = "email"
                    passwordParamName = "secret"
                    validate { credential ->
                        if (credential.name == "alice@example.test" && credential.password == "correct") {
                            UserIdPrincipal(credential.name)
                        } else {
                            null
                        }
                    }
                    challenge { credential ->
                        call.respondText(
                            "form-denied:${credential?.name ?: "missing"}",
                            status = HttpStatusCode.Unauthorized
                        )
                    }
                }
            }
            routing {
                authenticate("login-form") {
                    post("/form") {
                        call.respondText("form:${call.principal<UserIdPrincipal>()?.name}")
                    }
                }
            }
        }

        val successfulResponse = client.post("/form") {
            setBody(formBody("email" to "alice@example.test", "secret" to "correct"))
        }
        assertThat(successfulResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(successfulResponse.bodyAsText()).isEqualTo("form:alice@example.test")

        val invalidResponse = client.post("/form") {
            setBody(formBody("email" to "mallory@example.test", "secret" to "wrong"))
        }
        assertThat(invalidResponse.status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(invalidResponse.bodyAsText()).isEqualTo("form-denied:mallory@example.test")

        val missingResponse = client.post("/form") {
            setBody(formBody("email" to "alice@example.test"))
        }
        assertThat(missingResponse.status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(missingResponse.bodyAsText()).isEqualTo("form-denied:missing")
    }

    @Test
    fun digestAuthenticationVerifiesRfcExampleCredentialsInARoute() = testApplication {
        application {
            install(Authentication) {
                digest("digest") {
                    realm = DigestRealm
                    nonceManager = FixedNonceManager
                    digestProvider { userName, realm ->
                        digestBytes("$userName:$realm:Circle Of Life")
                    }
                    validate { credential -> UserIdPrincipal(credential.userName) }
                }
            }
            routing {
                authenticate("digest") {
                    get("/dir/index.html") {
                        call.respondText("digest:${call.principal<UserIdPrincipal>()?.name}")
                    }
                }
            }
        }

        val successfulResponse = client.get("/dir/index.html") {
            header(HttpHeaders.Authorization, RfcDigestAuthorizationHeader)
        }
        assertThat(successfulResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(successfulResponse.bodyAsText()).isEqualTo("digest:Mufasa")

        val invalidResponse = client.get("/dir/index.html") {
            header(HttpHeaders.Authorization, RfcDigestAuthorizationHeader.replace(DigestRealm, "other-realm"))
        }
        assertThat(invalidResponse.status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(invalidResponse.headers[HttpHeaders.WWWAuthenticate]).contains("realm=\"$DigestRealm\"")
        assertThat(invalidResponse.headers[HttpHeaders.WWWAuthenticate]).contains("nonce=\"$DigestNonce\"")
    }

    @Test
    fun digestCredentialsParseAndVerifyExpectedDigest() = runBlocking {
        val credential = ((parseAuthorizationHeader(RfcDigestAuthorizationHeader) ?: error("Header was not parsed"))
            as HttpAuthHeader.Parameterized).toDigestCredential()

        assertThat(credential).isEqualTo(
            DigestCredential(
                realm = DigestRealm,
                userName = "Mufasa",
                digestUri = "/dir/index.html",
                nonce = DigestNonce,
                opaque = "5ccc069c403ebaf9f0171e9517f40e41",
                nonceCount = "00000001",
                algorithm = null,
                response = "6629fae49393a05397450978507c4ef1",
                cnonce = "0a4f113b",
                qop = "auth"
            )
        )

        val digester = MessageDigest.getInstance("MD5")
        val expectedDigest = credential.expectedDigest(
            HttpMethod.Get,
            digester,
            digestBytes("Mufasa:$DigestRealm:Circle Of Life")
        )
        assertThat(hex(expectedDigest)).isEqualTo(credential.response)
        assertThat(credential.verifier(HttpMethod.Get, digester) { userName, realm ->
            digestBytes("$userName:$realm:Circle Of Life")
        }).isTrue()
        assertThat(credential.verifier(HttpMethod.Get, digester) { userName, realm ->
            digestBytes("$userName:$realm:wrong-password")
        }).isFalse()
        assertThat(credential.copy(response = "not-a-hex-digest").verifier(HttpMethod.Get, digester) { _, _ ->
            digestBytes("Mufasa:$DigestRealm:Circle Of Life")
        }).isFalse()
    }

    @Test
    fun multipleProvidersSupportFirstSuccessfulAndOptionalStrategies() = testApplication {
        application {
            install(Authentication) {
                basic("password") {
                    validate { credential ->
                        if (credential.name == "basic-user" && credential.password == "basic-pass") {
                            UserIdPrincipal("from-basic")
                        } else {
                            null
                        }
                    }
                }
                bearer("token") {
                    authenticate { credential ->
                        if (credential.token == "token-pass") UserIdPrincipal("from-bearer") else null
                    }
                }
            }
            routing {
                authenticate("password", "token", strategy = AuthenticationStrategy.FirstSuccessful) {
                    get("/either") {
                        call.respondText(call.principal<UserIdPrincipal>()?.name ?: "missing")
                    }
                }
                authenticate("password", optional = true) {
                    get("/optional") {
                        call.respondText(call.principal<UserIdPrincipal>()?.name ?: "anonymous")
                    }
                }
            }
        }

        val bearerResponse = client.get("/either") {
            header(HttpHeaders.Authorization, "Bearer token-pass")
        }
        assertThat(bearerResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(bearerResponse.bodyAsText()).isEqualTo("from-bearer")

        val basicResponse = client.get("/either") {
            header(HttpHeaders.Authorization, basicAuthorization("basic-user", "basic-pass"))
        }
        assertThat(basicResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(basicResponse.bodyAsText()).isEqualTo("from-basic")

        val optionalResponse = client.get("/optional")
        assertThat(optionalResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(optionalResponse.bodyAsText()).isEqualTo("anonymous")
    }

    @Test
    fun authenticationConfigRejectsDuplicateProviderNamesAndExposesProviders() {
        val config = AuthenticationConfig()
        config.basic("configured-basic", description = "HTTP basic credentials") {
            validate { credential -> UserIdPrincipal(credential.name) }
        }
        config.bearer("configured-bearer") {
            authenticate { credential -> UserIdPrincipal(credential.token) }
        }

        val providers = config.allProviders()
        assertThat(providers.keys).containsExactlyInAnyOrder("configured-basic", "configured-bearer")
        assertThat(providers["configured-basic"]?.name).isEqualTo("configured-basic")
        assertThat(providers["configured-basic"]?.description).isEqualTo("HTTP basic credentials")
        assertThat(providers["configured-bearer"]?.name).isEqualTo("configured-bearer")

        assertThatThrownBy {
            config.basic("configured-basic") {
                validate { null }
            }
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("configured-basic")
    }

    @Test
    fun simpleCredentialAndOAuthValueApisPreserveAuthenticationData() {
        val tableAuth = UserHashedTableAuth(
            digester = { value: String -> value.reversed().encodeToByteArray() },
            table = mapOf("alice" to "password".reversed().encodeToByteArray())
        )
        assertThat(tableAuth.authenticate(UserPasswordCredential("alice", "password")))
            .isEqualTo(UserIdPrincipal("alice"))
        assertThat(tableAuth.authenticate(UserPasswordCredential("alice", "wrong"))).isNull()
        assertThat(tableAuth.authenticate(UserPasswordCredential("missing", "password"))).isNull()

        val settings = OAuthServerSettings.OAuth2ServerSettings(
            name = "example-oauth",
            authorizeUrl = "https://provider.example.test/authorize",
            accessTokenUrl = "https://provider.example.test/token",
            refreshUrl = "https://provider.example.test/refresh",
            requestMethod = HttpMethod.Post,
            clientId = "client-id",
            clientSecret = "client-secret",
            defaultScopes = listOf("profile", "email"),
            defaultScopeDescriptions = mapOf("email" to "Read email address"),
            extraAuthParameters = listOf("prompt" to "consent"),
            extraTokenParameters = listOf("audience" to "ktor-tests")
        )
        assertThat(settings.name).isEqualTo("example-oauth")
        assertThat(settings.version).isEqualTo(OAuthVersion.V20)
        assertThat(settings.defaultScopes).containsExactly("profile", "email")
        assertThat(settings.defaultScopeDescriptions).containsEntry("email", "Read email address")
        assertThat(settings.extraAuthParameters).containsExactly("prompt" to "consent")
        assertThat(settings.extraTokenParameters).containsExactly("audience" to "ktor-tests")

        val token = OAuthAccessTokenResponse.OAuth2(
            accessToken = "access-token",
            tokenType = "Bearer",
            expiresIn = 3600,
            refreshToken = "refresh-token",
            extraParameters = Parameters.build { append("scope", "profile email") },
            state = "csrf-state"
        )
        assertThat(token.accessToken).isEqualTo("access-token")
        assertThat(token.extraParameters["scope"]).isEqualTo("profile email")
        assertThat(token.state).isEqualTo("csrf-state")
        assertThat(OAuthCallback.Error("access_denied", "The user denied access").errorDescription)
            .isEqualTo("The user denied access")
    }

    private fun basicAuthorization(userName: String, password: String): String {
        val credentials: String = Base64.getEncoder().encodeToString("$userName:$password".encodeToByteArray())
        return "Basic $credentials"
    }

    private fun formBody(vararg pairs: Pair<String, String>): FormDataContent = FormDataContent(
        Parameters.build {
            pairs.forEach { (name, value) -> append(name, value) }
        }
    )

    private fun digestBytes(value: String): ByteArray {
        val digester: MessageDigest = MessageDigest.getInstance("MD5")
        digester.update(value.toByteArray(Charsets.ISO_8859_1))
        return digester.digest()
    }

    private object FixedNonceManager : NonceManager {
        override suspend fun newNonce(): String = DigestNonce

        override suspend fun verifyNonce(nonce: String): Boolean = nonce == DigestNonce
    }

    private companion object {
        private const val DigestRealm: String = "testrealm@host.com"
        private const val DigestNonce: String = "dcd98b7102dd2f0e8b11d0f600bfb0c093"
        private const val RfcDigestAuthorizationHeader: String = "Digest " +
            "username=\"Mufasa\", " +
            "realm=\"$DigestRealm\", " +
            "nonce=\"$DigestNonce\", " +
            "uri=\"/dir/index.html\", " +
            "qop=auth, " +
            "nc=00000001, " +
            "cnonce=\"0a4f113b\", " +
            "response=\"6629fae49393a05397450978507c4ef1\", " +
            "opaque=\"5ccc069c403ebaf9f0171e9517f40e41\""
    }
}
