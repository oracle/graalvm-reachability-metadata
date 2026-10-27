/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_okhttp_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.clearAuthTokens
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.auth.AuthScheme
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.auth.parseAuthorizationHeader
import io.ktor.http.encodedPath
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Response
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/**
 * Bearer and Basic authentication through the client Auth plugin over the OkHttp engine: tokens
 * loaded from storage, refreshed and retried on a 401 challenge, dropped with `clearAuthTokens`.
 *
 * A back-office app reads orders from an API guarded by OAuth-style bearer tokens. The tokens it
 * starts with have expired, so the first call is challenged; the Auth plugin calls the app's
 * refresh hook, which trades the refresh token at a token endpoint guarded by HTTP Basic client
 * credentials, and replays the call with the new access token. When the token service later
 * revokes the refresh token, the app sees the 401, signs in again, drops the cached tokens and
 * carries on. A network interceptor records every request the engine put on the wire, so the
 * test checks the exact sequence of challenges, retries and Authorization headers.
 */
class OrdersApiBearerRefreshTest {

    @Serializable
    data class Order(val id: String, val total: Int, val placedBy: String)

    @Serializable
    data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String,
    )

    /** One request as seen on the wire by a network interceptor. */
    data class WireRequest(val path: String, val authorization: String?, val status: Int)

    /** The app's token storage, the place `loadTokens` reads from. */
    class TokenStore(var tokens: BearerTokens)

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    private val validAccessToken: AtomicReference<String?> = AtomicReference(null)
    private val validRefreshToken: AtomicReference<String?> = AtomicReference("refresh-0")
    private val issued: AtomicInteger = AtomicInteger(0)
    private val wire: CopyOnWriteArrayList<WireRequest> = CopyOnWriteArrayList()

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(ServerContentNegotiation) { json() }
            install(Authentication) {
                bearer("orders-api") {
                    realm = "orders"
                    authenticate { credential ->
                        if (credential.token == validAccessToken.get()) UserIdPrincipal("clerk") else null
                    }
                }
                basic("token-endpoint") {
                    realm = "oauth"
                    validate { credential ->
                        if (credential.name == "backoffice" && credential.password == "s3cret") {
                            UserIdPrincipal(credential.name)
                        } else {
                            null
                        }
                    }
                }
            }
            routing {
                get("/status") {
                    call.respond(mapOf("state" to "up"))
                }
                authenticate("token-endpoint") {
                    post("/oauth/token") {
                        val form: Parameters = call.receiveParameters()
                        val granted: Boolean = when (form["grant_type"]) {
                            "refresh_token" -> form["refresh_token"] == validRefreshToken.get()
                            "password" -> form["username"] == "clerk" && form["password"] == "hunter2"
                            else -> false
                        }
                        if (!granted) {
                            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid_grant"))
                            return@post
                        }
                        val generation: Int = issued.incrementAndGet()
                        val response = TokenResponse("access-$generation", "refresh-$generation")
                        validAccessToken.set(response.accessToken)
                        validRefreshToken.set(response.refreshToken)
                        call.respond(response)
                    }
                }
                authenticate("orders-api") {
                    get("/orders") {
                        val user: String = call.principal<UserIdPrincipal>()!!.name
                        call.respond(listOf(Order("ORD-7", 120, user), Order("ORD-9", 45, user)))
                    }
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stop() {
        server.stop(0, 0)
    }

    /** Records the path, Authorization header and status of every request the engine sends. */
    private fun recordingInterceptor(): Interceptor = Interceptor { chain ->
        val response: Response = chain.proceed(chain.request())
        wire += WireRequest(
            chain.request().url.encodedPath,
            chain.request().header(HttpHeaders.Authorization),
            response.code,
        )
        response
    }

    /** Client for the token endpoint: answers the endpoint's Basic challenge with client credentials. */
    private fun tokenClient(): HttpClient = HttpClient(OkHttp) {
        engine { addNetworkInterceptor(recordingInterceptor()) }
        install(ClientContentNegotiation) { json() }
        install(Auth) {
            basic {
                realm = "oauth"
                credentials { BasicAuthCredentials(username = "backoffice", password = "s3cret") }
            }
        }
        expectSuccess = false
    }

    /** API client: bearer tokens from [store], refreshed through [tokens] when the API rejects them. */
    private fun apiClient(store: TokenStore, tokens: HttpClient, loads: AtomicInteger, refreshes: AtomicInteger): HttpClient =
        HttpClient(OkHttp) {
            engine { addNetworkInterceptor(recordingInterceptor()) }
            install(ClientContentNegotiation) { json() }
            install(Auth) {
                bearer {
                    realm = "orders"
                    loadTokens {
                        loads.incrementAndGet()
                        store.tokens
                    }
                    refreshTokens {
                        refreshes.incrementAndGet()
                        val response: HttpResponse = tokens.submitForm(
                            url = "http://127.0.0.1:$port/oauth/token",
                            formParameters = parameters {
                                append("grant_type", "refresh_token")
                                append("refresh_token", oldTokens?.refreshToken.orEmpty())
                            },
                        )
                        if (response.status != HttpStatusCode.OK) {
                            null
                        } else {
                            val granted: TokenResponse = response.body()
                            BearerTokens(granted.accessToken, granted.refreshToken).also { store.tokens = it }
                        }
                    }
                    sendWithoutRequest { request -> request.url.encodedPath.startsWith("/orders") }
                }
            }
        }

    @Test
    fun expiredAccessTokenIsRefreshedAndTheCallReplayed(): Unit = runBlocking {
        val store = TokenStore(BearerTokens("expired-0", "refresh-0"))
        val loads = AtomicInteger(0)
        val refreshes = AtomicInteger(0)
        tokenClient().use { tokens: HttpClient ->
            apiClient(store, tokens, loads, refreshes).use { client: HttpClient ->
                val status: HttpResponse = client.get("http://127.0.0.1:$port/status")
                assertEquals(HttpStatusCode.OK, status.status)

                val first: List<Order> = client.get("http://127.0.0.1:$port/orders").body()
                assertEquals(listOf("ORD-7", "ORD-9"), first.map { it.id })
                assertEquals("clerk", first.first().placedBy)

                val second: HttpResponse = client.get("http://127.0.0.1:$port/orders")
                assertEquals(HttpStatusCode.OK, second.status)
            }
        }

        assertEquals(1, loads.get())
        assertEquals(1, refreshes.get())
        assertEquals("access-1", store.tokens.accessToken)
        assertEquals("refresh-1", store.tokens.refreshToken)
        val basic: String = Credentials.basic("backoffice", "s3cret")
        assertEquals(
            listOf(
                WireRequest("/status", null, 200),
                WireRequest("/orders", "Bearer expired-0", 401),
                WireRequest("/oauth/token", null, 401),
                WireRequest("/oauth/token", basic, 200),
                WireRequest("/orders", "Bearer access-1", 200),
                WireRequest("/orders", "Bearer access-1", 200),
            ),
            wire.toList(),
        )
    }

    @Test
    fun revokedRefreshTokenSurfacesTheChallengeUntilTheAppSignsInAgain(): Unit = runBlocking {
        val store = TokenStore(BearerTokens("expired-0", "refresh-0"))
        val loads = AtomicInteger(0)
        val refreshes = AtomicInteger(0)
        tokenClient().use { tokens: HttpClient ->
            apiClient(store, tokens, loads, refreshes).use { client: HttpClient ->
                assertEquals(HttpStatusCode.OK, client.get("http://127.0.0.1:$port/orders").status)

                // The token service revokes the session: neither the access nor the refresh token works.
                validAccessToken.set(null)
                validRefreshToken.set(null)
                val rejected: HttpResponse = client.get("http://127.0.0.1:$port/orders")
                assertEquals(HttpStatusCode.Unauthorized, rejected.status)
                val challenge = parseAuthorizationHeader(rejected.headers[HttpHeaders.WWWAuthenticate]!!)
                    as HttpAuthHeader.Parameterized
                assertEquals(AuthScheme.Bearer, challenge.authScheme)
                assertEquals("orders", challenge.parameter("realm"))

                // The app signs the user in again, stores the new tokens and drops the cached ones.
                val login: TokenResponse = tokens.submitForm(
                    url = "http://127.0.0.1:$port/oauth/token",
                    formParameters = parameters {
                        append("grant_type", "password")
                        append("username", "clerk")
                        append("password", "hunter2")
                    },
                ).body()
                store.tokens = BearerTokens(login.accessToken, login.refreshToken)
                client.clearAuthTokens()

                val orders: List<Order> = client.get("http://127.0.0.1:$port/orders").body()
                assertEquals(2, orders.size)
            }
        }

        assertEquals(2, loads.get())
        assertEquals(2, refreshes.get())
        assertEquals("access-2", store.tokens.accessToken)
        assertEquals("refresh-2", store.tokens.refreshToken)
        val ordersCalls: List<WireRequest> = wire.filter { it.path == "/orders" }
        assertEquals(
            listOf(
                WireRequest("/orders", "Bearer expired-0", 401),
                WireRequest("/orders", "Bearer access-1", 200),
                WireRequest("/orders", "Bearer access-1", 401),
                WireRequest("/orders", "Bearer access-2", 200),
            ),
            ordersCalls,
        )
        val tokenStatuses: List<Int> = wire.filter { it.path == "/oauth/token" && it.authorization != null }
            .map { it.status }
        assertEquals(listOf(200, 400, 200), tokenStatuses)
    }
}
