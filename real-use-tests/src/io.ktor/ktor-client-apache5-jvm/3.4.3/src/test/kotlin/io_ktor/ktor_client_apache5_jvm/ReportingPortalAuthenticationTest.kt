/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_apache5_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createApplicationPlugin
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
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Basic and bearer-token client authentication with token refresh on the `Apache5` engine.
 *
 * A reporting portal signs administrators in with HTTP Basic credentials and lets dashboards call
 * its report API with short-lived bearer tokens. The client answers the service's `401`
 * challenges, refreshes an expired access token through the refresh endpoint, reuses the refreshed
 * token for later calls, and surfaces rejected credentials to the application as a `401`.
 */
class ReportingPortalAuthenticationTest {

    /** Token issuer of the portal: hands out access tokens and remembers which ones are live. */
    class TokenIssuer {
        private val liveTokens: MutableMap<String, String> = ConcurrentHashMap()
        private val issued: AtomicInteger = AtomicInteger()
        val refreshCalls: AtomicInteger = AtomicInteger()

        fun refresh(refreshToken: String): String? {
            refreshCalls.incrementAndGet()
            if (!refreshToken.startsWith("refresh-")) {
                return null
            }
            val token: String = "access-${issued.incrementAndGet()}"
            liveTokens[token] = refreshToken.removePrefix("refresh-")
            return token
        }

        fun owner(accessToken: String): String? = liveTokens[accessToken]
    }

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private lateinit var issuer: TokenIssuer
    private val unauthenticatedAdminCalls: AtomicInteger = AtomicInteger()
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        issuer = TokenIssuer()
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(
                createApplicationPlugin("AdminCallCounter") {
                    onCall { call ->
                        val anonymous: Boolean = call.request.headers[HttpHeaders.Authorization] == null
                        if (call.request.local.uri.startsWith("/admin") && anonymous) {
                            unauthenticatedAdminCalls.incrementAndGet()
                        }
                    }
                }
            )
            install(Authentication) {
                basic("admin") {
                    realm = "reporting-admin"
                    validate { credentials ->
                        if (credentials.name == "ops-lead" && credentials.password == "s3cret-pass") {
                            UserIdPrincipal(credentials.name)
                        } else {
                            null
                        }
                    }
                }
                bearer("reports") {
                    realm = "reporting-api"
                    authenticate { credential -> issuer.owner(credential.token)?.let { UserIdPrincipal(it) } }
                }
            }
            routing {
                post("/oauth/refresh") {
                    val token: String? = issuer.refresh(call.receiveText())
                    if (token == null) {
                        call.respondText("invalid refresh token", status = HttpStatusCode.BadRequest)
                    } else {
                        call.respondText(token)
                    }
                }
                authenticate("admin") {
                    get("/admin/settings") {
                        call.respondText("settings for ${call.principal<UserIdPrincipal>()!!.name}")
                    }
                }
                authenticate("reports") {
                    get("/reports/{id}") {
                        val user: String = call.principal<UserIdPrincipal>()!!.name
                        call.respondText("report ${call.parameters["id"]} for $user")
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

    private fun adminClient(password: String, preemptive: Boolean): HttpClient = HttpClient(Apache5) {
        install(Auth) {
            basic {
                realm = "reporting-admin"
                credentials { BasicAuthCredentials(username = "ops-lead", password = password) }
                sendWithoutRequest { preemptive }
            }
        }
    }

    @Test
    fun answersBasicChallengeWithCredentials() {
        adminClient(password = "s3cret-pass", preemptive = false).use { client ->
            runBlocking {
                val response: HttpResponse = client.get("http://127.0.0.1:$port/admin/settings")

                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("settings for ops-lead", response.bodyAsText())
                assertEquals(1, unauthenticatedAdminCalls.get())
            }
        }
    }

    @Test
    fun sendsBasicCredentialsPreemptively() {
        adminClient(password = "s3cret-pass", preemptive = true).use { client ->
            runBlocking {
                val response: HttpResponse = client.get("http://127.0.0.1:$port/admin/settings")

                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("settings for ops-lead", response.bodyAsText())
                assertEquals(0, unauthenticatedAdminCalls.get())
            }
        }
    }

    @Test
    fun surfacesRejectedBasicCredentials() {
        adminClient(password = "wrong-pass", preemptive = true).use { client ->
            runBlocking {
                val response: HttpResponse = client.get("http://127.0.0.1:$port/admin/settings")

                assertEquals(HttpStatusCode.Unauthorized, response.status)
                val challenge: String = response.headers[HttpHeaders.WWWAuthenticate]!!
                assertTrue(challenge.startsWith("Basic"), challenge)
                assertTrue(challenge.contains("reporting-admin"), challenge)
            }
        }
    }

    @Test
    fun refreshesExpiredBearerTokenOnceAndReusesIt() {
        val refreshedTokens: MutableList<String> = mutableListOf()

        HttpClient(Apache5) {
            install(Auth) {
                bearer {
                    loadTokens { BearerTokens(accessToken = "access-expired", refreshToken = "refresh-dashboard") }
                    refreshTokens {
                        val response: HttpResponse = client.post("http://127.0.0.1:$port/oauth/refresh") {
                            markAsRefreshTokenRequest()
                            setBody(oldTokens!!.refreshToken!!)
                        }
                        val accessToken: String = response.bodyAsText()
                        refreshedTokens.add(accessToken)
                        BearerTokens(accessToken = accessToken, refreshToken = oldTokens!!.refreshToken)
                    }
                }
            }
        }.use { client ->
            runBlocking {
                val first: HttpResponse = client.get("http://127.0.0.1:$port/reports/weekly")
                assertEquals(HttpStatusCode.OK, first.status)
                assertEquals("report weekly for dashboard", first.bodyAsText())

                val second: HttpResponse = client.get("http://127.0.0.1:$port/reports/monthly")
                assertEquals(HttpStatusCode.OK, second.status)
                assertEquals("report monthly for dashboard", second.bodyAsText())

                assertEquals(listOf("access-1"), refreshedTokens)
                assertEquals(1, issuer.refreshCalls.get())
            }
        }
    }

    @Test
    fun reportsUnauthorizedWhenRefreshYieldsNoToken() {
        HttpClient(Apache5) {
            install(Auth) {
                bearer {
                    loadTokens { BearerTokens(accessToken = "access-revoked", refreshToken = "revoked") }
                    refreshTokens {
                        val response: HttpResponse = client.post("http://127.0.0.1:$port/oauth/refresh") {
                            markAsRefreshTokenRequest()
                            setBody(oldTokens!!.refreshToken!!)
                        }
                        if (response.status == HttpStatusCode.OK) {
                            BearerTokens(accessToken = response.bodyAsText(), refreshToken = oldTokens!!.refreshToken)
                        } else {
                            null
                        }
                    }
                }
            }
        }.use { client ->
            runBlocking {
                val response: HttpResponse = client.get("http://127.0.0.1:$port/reports/weekly")

                assertEquals(HttpStatusCode.Unauthorized, response.status)
                assertTrue(response.headers[HttpHeaders.WWWAuthenticate]!!.startsWith("Bearer"))
                assertEquals(1, issuer.refreshCalls.get())
                assertNull(issuer.owner("access-revoked"))
            }
        }
    }
}
