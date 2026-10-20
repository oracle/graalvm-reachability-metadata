/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_auth_jvm

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.formUrlEncode
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
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * OAuth 2.0 authorization-code login hardened with PKCE and HTTP Basic client authentication.
 *
 * A wiki signs editors in through an identity provider that demands proof keys: when the OAuth
 * provider mints a state, the wiki creates a code verifier for it (`onStateCreated`) and puts
 * the S256 challenge on the authorize redirect (`authorizeUrlInterceptor`). On the callback a
 * per-call `providerLookup` hands that verifier to the token exchange as an extra token
 * parameter, while `accessTokenRequiresBasicAuth` authenticates the wiki itself with an HTTP Basic
 * header. The provider answers with a form-encoded token response, and the wiki discards the
 * verifier of a completed sign-in. A replayed callback finds no verifier left, is refused by the
 * provider, and sends the editor back to sign in again.
 */
class OAuth2PkceLoginTest {

    /** Code verifiers the wiki issued, keyed by the OAuth state they belong to. */
    class PkceVerifierStore {
        private val random: SecureRandom = SecureRandom()
        private val verifiers: ConcurrentHashMap<String, String> = ConcurrentHashMap()

        fun create(state: String) {
            val bytes: ByteArray = ByteArray(32)
            random.nextBytes(bytes)
            verifiers[state] = base64Url(bytes)
        }

        fun challengeFor(state: String): String = s256(verifiers.getValue(state))

        fun verifierFor(state: String): String? = verifiers[state]

        /** Forgets the verifier of a completed sign-in so its callback cannot be replayed. */
        fun discard(state: String) {
            verifiers.remove(state)
        }

        fun pending(): Int = verifiers.size
    }

    /** One request the provider's token endpoint received. */
    data class TokenRequest(val authorization: String?, val form: Parameters)

    /** The identity provider's memory of issued codes and the token requests it served. */
    class ProviderLedger {
        private val nextCode: AtomicInteger = AtomicInteger(1)
        val challenges: ConcurrentHashMap<String, String> = ConcurrentHashMap()
        val tokenRequests: ConcurrentLinkedQueue<TokenRequest> = ConcurrentLinkedQueue()

        fun issueCode(challenge: String): String {
            val code: String = "code-${nextCode.getAndIncrement()}"
            challenges[code] = challenge
            return code
        }
    }

    private val providerHost: String = "https://login.example.test"
    private val callbackUrl: String = "http://localhost/auth/callback"
    private val clientBasic: String =
        "Basic " + Base64.getEncoder().encodeToString("wiki:wiki-secret".toByteArray())

    private fun Application.identityProviderModule(ledger: ProviderLedger) {
        routing {
            get("/oauth/authorize") {
                val query: Parameters = call.request.queryParameters
                val challenge: String? = query["code_challenge"]
                if (query["code_challenge_method"] != "S256" || challenge == null) {
                    call.respondText("PKCE required", status = HttpStatusCode.BadRequest)
                    return@get
                }
                val code: String = ledger.issueCode(challenge)
                val back: URLBuilder = URLBuilder(query["redirect_uri"]!!)
                back.parameters.append("code", code)
                back.parameters.append("state", query["state"]!!)
                call.respondRedirect(back.buildString())
            }
            post("/oauth/token") {
                val authorization: String? = call.request.headers[HttpHeaders.Authorization]
                val form: Parameters = call.receiveText().parseUrlEncodedParameters()
                ledger.tokenRequests.add(TokenRequest(authorization, form))
                val challenge: String? = form["code"]?.let { ledger.challenges.remove(it) }
                val verifier: String? = form["code_verifier"]
                val proven: Boolean = authorization == clientBasic &&
                    form["grant_type"] == "authorization_code" &&
                    form["redirect_uri"] == callbackUrl &&
                    challenge != null && verifier != null && s256(verifier) == challenge
                if (proven) {
                    val tokens: String = listOf(
                        "access_token" to "at-${form["code"]}",
                        "token_type" to "Bearer",
                        "expires_in" to "900",
                        "scope" to "wiki.edit",
                    ).formUrlEncode()
                    call.respondText(tokens, ContentType.Application.FormUrlEncoded)
                } else {
                    call.respondText(
                        """{"error":"invalid_grant","error_description":"code verifier mismatch"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest,
                    )
                }
            }
        }
    }

    private fun Application.wikiModule(providerClient: HttpClient, verifiers: PkceVerifierStore) {
        install(Authentication) {
            oauth("wiki-sso") {
                client = providerClient
                urlProvider = { callbackUrl }
                // Ktor may look the settings up more than once per call, so this only reads.
                providerLookup = {
                    val returnedState: String? = request.queryParameters["state"]
                    val verifier: String? = returnedState?.let { verifiers.verifierFor(it) }
                    OAuthServerSettings.OAuth2ServerSettings(
                        name = "wiki-login",
                        authorizeUrl = "$providerHost/oauth/authorize",
                        accessTokenUrl = "$providerHost/oauth/token",
                        requestMethod = HttpMethod.Post,
                        clientId = "wiki",
                        clientSecret = "wiki-secret",
                        defaultScopes = listOf("wiki.edit"),
                        accessTokenRequiresBasicAuth = true,
                        onStateCreated = { _, state -> verifiers.create(state) },
                        authorizeUrlInterceptor = {
                            parameters.append("code_challenge", verifiers.challengeFor(parameters["state"]!!))
                            parameters.append("code_challenge_method", "S256")
                        },
                        extraTokenParameters = listOfNotNull(verifier?.let { "code_verifier" to it }),
                    )
                }
            }
        }
        routing {
            authenticate("wiki-sso") {
                get("/auth/login") {
                    // The provider redirects before this handler runs.
                }
                get("/auth/callback") {
                    val tokens: OAuthAccessTokenResponse.OAuth2 = call.principal<OAuthAccessTokenResponse.OAuth2>()!!
                    verifiers.discard(tokens.state!!)
                    call.respondText(
                        "editor signed in with ${tokens.tokenType} ${tokens.accessToken}, " +
                            "expires in ${tokens.expiresIn}s, scope ${tokens.extraParameters["scope"]}",
                    )
                }
            }
        }
    }

    @Test
    fun editorsSignInWithProofKeyAndCannotReplayTheCallback() = testApplication {
        val ledger: ProviderLedger = ProviderLedger()
        val verifiers: PkceVerifierStore = PkceVerifierStore()
        externalServices {
            hosts(providerHost) { identityProviderModule(ledger) }
        }
        val providerClient: HttpClient = createClient { }
        application { wikiModule(providerClient, verifiers) }
        val browser: HttpClient = createClient { followRedirects = false }

        val login: HttpResponse = browser.get("/auth/login")
        assertEquals(HttpStatusCode.Found, login.status)
        val authorize: Url = Url(login.headers[HttpHeaders.Location]!!)
        assertEquals("/oauth/authorize", authorize.encodedPath)
        assertEquals("wiki", authorize.parameters["client_id"])
        assertEquals("wiki.edit", authorize.parameters["scope"])
        assertEquals("S256", authorize.parameters["code_challenge_method"])
        val state: String = authorize.parameters["state"]!!
        val challenge: String = authorize.parameters["code_challenge"]!!
        assertEquals(43, challenge.length, "an S256 challenge is an unpadded base64url SHA-256 digest")
        assertNull(authorize.parameters["code_verifier"], "the verifier never leaves the wiki on the redirect")
        assertEquals(1, verifiers.pending())

        val approved: HttpResponse = browser.get(authorize.toString())
        assertEquals(HttpStatusCode.Found, approved.status)
        val callback: Url = Url(approved.headers[HttpHeaders.Location]!!)
        assertEquals(state, callback.parameters["state"])
        assertEquals("code-1", callback.parameters["code"])

        val signedIn: HttpResponse = browser.get(callback.toString())
        assertEquals(HttpStatusCode.OK, signedIn.status)
        assertEquals(
            "editor signed in with Bearer at-code-1, expires in 900s, scope wiki.edit",
            signedIn.bodyAsText(),
        )
        val exchange: TokenRequest = ledger.tokenRequests.poll()!!
        assertEquals(clientBasic, exchange.authorization, "the wiki authenticates itself with HTTP Basic")
        assertEquals("code-1", exchange.form["code"])
        assertEquals(state, exchange.form["state"])
        assertEquals(challenge, s256(exchange.form["code_verifier"]!!))
        assertEquals(0, verifiers.pending(), "a completed sign-in discards its verifier")

        val replay: HttpResponse = browser.get(callback.toString())
        assertEquals(HttpStatusCode.Found, replay.status, "a replayed callback restarts the sign-in")
        val restart: Url = Url(replay.headers[HttpHeaders.Location]!!)
        assertEquals("/oauth/authorize", restart.encodedPath)
        assertNotEquals(state, restart.parameters["state"], "the restarted sign-in carries a fresh state")
        assertNotEquals(challenge, restart.parameters["code_challenge"], "and a fresh proof key")
        val refused: TokenRequest = ledger.tokenRequests.poll()!!
        assertFalse(refused.form.contains("code_verifier"), "a replay has no verifier left to send")
        assertTrue(ledger.tokenRequests.isEmpty())
        assertEquals(1, verifiers.pending(), "only the restarted sign-in holds a verifier")
    }

    companion object {
        fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        fun s256(verifier: String): String =
            base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    }
}
