/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_auth_jvm

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.decodeURLPart
import io.ktor.http.encodeURLParameter
import io.ktor.http.parseUrlEncodedParameters
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.OAuthAccessTokenResponse
import io.ktor.server.auth.OAuthServerSettings
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.oauth
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * OAuth 1.0a three-legged login with HMAC-SHA1 signed request-token and access-token exchanges.
 *
 * A reading-list site lets readers sign in with their account at a book-cataloguing service that
 * still speaks OAuth 1.0a. The login route obtains a signed request token and redirects the reader
 * to the service's authorize page; the callback trades the token and verifier for an access token
 * whose extra parameters name the reader. The service checks every signature, so a verifier it does
 * not recognise restarts the sign-in, and a consumer whose secret was rotated away ends on the
 * site's own "catalogue unavailable" page.
 */
class OAuth1aSignedLoginTest {

    /** One signed call the catalogue service accepted, as it saw the OAuth header. */
    data class SignedCall(val endpoint: String, val oauth: Map<String, String>, val clientTag: String?)

    /** The catalogue service's view of issued request tokens and the calls it accepted. */
    class CatalogueState {
        val pendingVerifiers: ConcurrentHashMap<String, String> = ConcurrentHashMap()
        val acceptedCalls: ConcurrentLinkedQueue<SignedCall> = ConcurrentLinkedQueue()
        var issuedTokens: Int = 0
    }

    private val catalogueHost: String = "https://books.example.test"
    private val requestTokenUrl: String = "$catalogueHost/oauth/request_token"
    private val accessTokenUrl: String = "$catalogueHost/oauth/access_token"
    private val callbackUrl: String = "http://localhost/auth/catalogue/callback"
    private val consumerKey: String = "reading-list"
    private val consumerSecret: String = "rl-consumer-secret"

    /** Recomputes the HMAC-SHA1 signature the way an OAuth 1.0a service checks it. */
    private fun expectedSignature(url: String, oauth: Map<String, String>, extra: Parameters): String {
        val pairs: List<Pair<String, String>> = oauth.filterKeys { it != "oauth_signature" }.toList() +
            extra.entries().flatMap { entry -> entry.value.map { entry.key to it } }
        val normalized: String = pairs
            .map { it.first.encodeURLParameter() to it.second.encodeURLParameter() }
            .sortedWith(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })
            .joinToString("&") { "${it.first}=${it.second}" }
        val baseString: String = listOf("POST", url, normalized).joinToString("&") { it.encodeURLParameter() }
        val mac: Mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec("$consumerSecret&".toByteArray(), "HmacSHA1"))
        return Base64.getEncoder().encodeToString(mac.doFinal(baseString.toByteArray()))
    }

    /** Reads the `Authorization: OAuth ...` header into decoded parameter values. */
    private fun ApplicationCall.oauthParameters(): Map<String, String> {
        val header: HttpAuthHeader.Parameterized = request.parseAuthorizationHeader() as HttpAuthHeader.Parameterized
        assertEquals("OAuth", header.authScheme)
        return header.parameters.associate { it.name to it.value.decodeURLPart() }
    }

    private fun Application.catalogueModule(state: CatalogueState) {
        routing {
            post("/oauth/request_token") {
                val oauth: Map<String, String> = call.oauthParameters()
                val validSignature: Boolean = oauth["oauth_consumer_key"] == consumerKey &&
                    oauth["oauth_signature"] == expectedSignature(requestTokenUrl, oauth, Parameters.Empty)
                if (!validSignature) {
                    call.respondText("oauth_problem=signature_invalid", status = HttpStatusCode.Unauthorized)
                    return@post
                }
                state.acceptedCalls.add(SignedCall("request_token", oauth, null))
                state.issuedTokens += 1
                val token: String = "rt-${state.issuedTokens}"
                state.pendingVerifiers[token] = "verifier-${state.issuedTokens}"
                call.respondText(
                    "oauth_token=$token&oauth_token_secret=rts-${state.issuedTokens}&oauth_callback_confirmed=true",
                    ContentType.Application.FormUrlEncoded,
                )
            }
            post("/oauth/access_token") {
                val oauth: Map<String, String> = call.oauthParameters()
                val form: Parameters = call.receiveText().parseUrlEncodedParameters()
                val token: String? = oauth["oauth_token"]
                val accepted: Boolean = oauth["oauth_signature"] == expectedSignature(accessTokenUrl, oauth, form) &&
                    token != null && state.pendingVerifiers[token] == form["oauth_verifier"]
                if (!accepted) {
                    call.respondText("oauth_problem=verifier_invalid", status = HttpStatusCode.Unauthorized)
                    return@post
                }
                state.pendingVerifiers.remove(token)
                state.acceptedCalls.add(SignedCall("access_token", oauth, call.request.headers["X-Client-Tag"]))
                call.respondText(
                    "oauth_token=at-grace-91&oauth_token_secret=ats-grace-91&user_id=1906&screen_name=ghopper",
                    ContentType.Application.FormUrlEncoded,
                )
            }
        }
    }

    private fun catalogueSettings(name: String, secret: String): OAuthServerSettings.OAuth1aServerSettings =
        OAuthServerSettings.OAuth1aServerSettings(
            name = name,
            requestTokenUrl = requestTokenUrl,
            authorizeUrl = "$catalogueHost/oauth/authorize",
            accessTokenUrl = accessTokenUrl,
            consumerKey = consumerKey,
            consumerSecret = secret,
            accessTokenInterceptor = { header("X-Client-Tag", "reading-list/2.1") },
        )

    private fun Application.readingListModule(catalogueClient: HttpClient) {
        install(Authentication) {
            oauth("catalogue") {
                client = catalogueClient
                urlProvider = { callbackUrl }
                providerLookup = { catalogueSettings("catalogue", consumerSecret) }
            }
            oauth("catalogue-rotated") {
                client = catalogueClient
                urlProvider = { callbackUrl }
                providerLookup = { catalogueSettings("catalogue", "secret-revoked-last-week") }
                fallback = { _ ->
                    respondText("catalogue unavailable, try again later", status = HttpStatusCode.ServiceUnavailable)
                }
            }
        }
        routing {
            authenticate("catalogue") {
                get("/auth/catalogue/login") {
                    // The catalogue redirect is sent before this handler runs.
                }
                get("/auth/catalogue/callback") {
                    val tokens: OAuthAccessTokenResponse.OAuth1a = call.principal<OAuthAccessTokenResponse.OAuth1a>()!!
                    call.respondText(
                        "signed in as @${tokens.extraParameters["screen_name"]} " +
                            "(catalogue user ${tokens.extraParameters["user_id"]}) with token ${tokens.token}",
                    )
                }
            }
            authenticate("catalogue-rotated") {
                get("/auth/legacy/login") {
                    // Never reached: the catalogue rejects the rotated consumer secret.
                }
            }
        }
    }

    @Test
    fun readersSignInThroughTheCatalogueService() = testApplication {
        val state: CatalogueState = CatalogueState()
        externalServices {
            hosts(catalogueHost) { catalogueModule(state) }
        }
        val catalogueClient: HttpClient = createClient { }
        application { readingListModule(catalogueClient) }
        val browser: HttpClient = createClient { followRedirects = false }

        val login: HttpResponse = browser.get("/auth/catalogue/login")
        assertEquals(HttpStatusCode.Found, login.status)
        val authorize: Url = Url(login.headers[HttpHeaders.Location]!!)
        assertEquals("books.example.test", authorize.host)
        assertEquals("/oauth/authorize", authorize.encodedPath)
        assertEquals("rt-1", authorize.parameters["oauth_token"])
        val requestTokenCall: SignedCall = state.acceptedCalls.poll()!!
        assertEquals("request_token", requestTokenCall.endpoint)
        assertEquals(callbackUrl, requestTokenCall.oauth["oauth_callback"])
        assertEquals("HMAC-SHA1", requestTokenCall.oauth["oauth_signature_method"])
        assertEquals("1.0", requestTokenCall.oauth["oauth_version"])
        assertTrue(requestTokenCall.oauth["oauth_nonce"]!!.isNotBlank(), "the request token call carries a nonce")
        assertTrue(requestTokenCall.oauth["oauth_timestamp"]!!.all { it.isDigit() }, "the timestamp is in seconds")

        val callback: HttpResponse = browser.get("/auth/catalogue/callback") {
            parameter("oauth_token", "rt-1")
            parameter("oauth_verifier", "verifier-1")
        }
        assertEquals(HttpStatusCode.OK, callback.status)
        assertEquals("signed in as @ghopper (catalogue user 1906) with token at-grace-91", callback.bodyAsText())
        val accessTokenCall: SignedCall = state.acceptedCalls.poll()!!
        assertEquals("access_token", accessTokenCall.endpoint)
        assertEquals("rt-1", accessTokenCall.oauth["oauth_token"])
        assertEquals("reading-list/2.1", accessTokenCall.clientTag)
        assertTrue(
            requestTokenCall.oauth["oauth_nonce"] != accessTokenCall.oauth["oauth_nonce"],
            "each signed call uses a fresh nonce",
        )

        val replayed: HttpResponse = browser.get("/auth/catalogue/callback") {
            parameter("oauth_token", "rt-1")
            parameter("oauth_verifier", "verifier-1")
        }
        assertEquals(HttpStatusCode.Found, replayed.status, "a spent verifier restarts the sign-in")
        assertEquals("rt-2", Url(replayed.headers[HttpHeaders.Location]!!).parameters["oauth_token"])
        assertEquals("request_token", state.acceptedCalls.poll()!!.endpoint)
        assertTrue(state.acceptedCalls.isEmpty(), "the spent verifier earned no access token")

        val rotated: HttpResponse = browser.get("/auth/legacy/login")
        assertEquals(HttpStatusCode.ServiceUnavailable, rotated.status)
        assertEquals("catalogue unavailable, try again later", rotated.bodyAsText())
        assertTrue(state.acceptedCalls.isEmpty(), "a badly signed consumer is never issued a token")
        assertEquals(2, state.issuedTokens)
    }
}
