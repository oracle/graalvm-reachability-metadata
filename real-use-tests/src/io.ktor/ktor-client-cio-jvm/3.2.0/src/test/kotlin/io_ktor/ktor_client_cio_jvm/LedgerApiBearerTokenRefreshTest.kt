/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Bearer authentication with token refresh over the CIO client engine (the client `Auth` plugin).
 *
 * A bookkeeping client reads balances from the ledger API, which only accepts access tokens its
 * token endpoint has issued. The client starts from a stale access token cached by an earlier
 * session; the ledger rejects it with a Bearer challenge, so the `Auth` plugin trades the cached
 * refresh token for a fresh pair and replays the request. Later reads reuse the fresh token
 * without another exchange until the ledger revokes it, which triggers exactly one more rotation.
 */
class LedgerApiBearerTokenRefreshTest {

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    /** Access tokens the ledger currently accepts, mapped to the account owner they belong to. */
    private val validAccessTokens: ConcurrentHashMap<String, String> = ConcurrentHashMap()

    /** The refresh token the token endpoint will accept next. */
    @Volatile
    private var currentRefreshToken: String = CACHED_REFRESH_TOKEN

    /** Refresh tokens presented to the token endpoint, in order. */
    private val presentedRefreshTokens: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()

    /** `Authorization` headers the ledger saw on balance reads, in order. */
    private val balanceAuthorizations: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()

    /** Number of token pairs the token endpoint has issued. */
    private val issuedPairs: AtomicInteger = AtomicInteger()

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(Authentication) {
                bearer("ledger") {
                    realm = "ledger"
                    authenticate { credential ->
                        validAccessTokens[credential.token]?.let { owner -> UserIdPrincipal(owner) }
                    }
                }
            }
            routing {
                post("/oauth/token") {
                    val refreshToken: String = call.receiveText()
                    presentedRefreshTokens += refreshToken
                    if (refreshToken != currentRefreshToken) {
                        call.respondText("invalid_grant", status = HttpStatusCode.BadRequest)
                        return@post
                    }
                    val generation: Int = issuedPairs.incrementAndGet()
                    val accessToken = "access-$generation"
                    currentRefreshToken = "refresh-$generation"
                    validAccessTokens[accessToken] = ACCOUNT_OWNER
                    call.respondText("$accessToken $currentRefreshToken")
                }
                authenticate("ledger") {
                    get("/accounts/{account}/balance") {
                        balanceAuthorizations += call.request.header(HttpHeaders.Authorization).orEmpty()
                        val owner: String = call.principal<UserIdPrincipal>()!!.name
                        val account: String = call.parameters["account"]!!
                        call.respondText("$owner:$account:${account.length * 100}")
                    }
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stopServer() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun authPluginRefreshesStaleAndRevokedAccessTokens() {
        val baseUrl = "http://127.0.0.1:$port"
        val client = HttpClient(ClientCIO) {
            install(Auth) {
                bearer {
                    realm = "ledger"
                    loadTokens { BearerTokens(STALE_ACCESS_TOKEN, CACHED_REFRESH_TOKEN) }
                    refreshTokens {
                        val response: HttpResponse = client.post("$baseUrl/oauth/token") {
                            markAsRefreshTokenRequest()
                            setBody(oldTokens!!.refreshToken!!)
                        }
                        assertEquals(HttpStatusCode.OK, response.status)
                        val (accessToken: String, refreshToken: String) = response.bodyAsText().split(" ")
                        BearerTokens(accessToken, refreshToken)
                    }
                }
            }
        }

        client.use {
            runBlocking {
                assertEquals("$ACCOUNT_OWNER:cash:400", balance(it, baseUrl, "cash"))
                assertEquals("$ACCOUNT_OWNER:payables:800", balance(it, baseUrl, "payables"))

                validAccessTokens.remove("access-1")

                assertEquals("$ACCOUNT_OWNER:receivables:1100", balance(it, baseUrl, "receivables"))
                assertEquals("$ACCOUNT_OWNER:equity:600", balance(it, baseUrl, "equity"))
            }
        }

        assertEquals(listOf(CACHED_REFRESH_TOKEN, "refresh-1"), presentedRefreshTokens.toList())
        assertEquals(2, issuedPairs.get())
        assertEquals(
            listOf("Bearer access-1", "Bearer access-1", "Bearer access-2", "Bearer access-2"),
            balanceAuthorizations.toList(),
        )
    }

    private suspend fun balance(client: HttpClient, baseUrl: String, account: String): String {
        val response: HttpResponse = client.get("$baseUrl/accounts/$account/balance")
        assertEquals(HttpStatusCode.OK, response.status)
        return response.bodyAsText()
    }

    private companion object {
        const val ACCOUNT_OWNER: String = "bookkeeper"
        const val STALE_ACCESS_TOKEN: String = "access-0"
        const val CACHED_REFRESH_TOKEN: String = "refresh-0"
    }
}
