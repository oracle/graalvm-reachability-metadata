/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_auth_jwt_jvm

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.encodedPath
import io.ktor.http.formUrlEncode
import io.ktor.http.parameters
import io.ktor.http.parseQueryString
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Access and refresh token lifecycle with the issuer, audience and algorithm
 * verifier shortcut, where a web shop signs shoppers in and keeps their session
 * alive by trading a refresh token for a new access token.
 *
 * Two `jwt` providers share one key: `access-jwt` checks the `shop-api` audience
 * with a clock-skew leeway and `validate` maps the credential to an application
 * [Shopper], and `refresh-jwt` checks the `shop-refresh` audience. A ktor client
 * with the bearer `Auth` plugin answers the default bearer challenge for an
 * expired access token by calling the refresh route and retrying the request.
 */
public class ShopSessionRefreshTokenTest {
    /** The signed-in shopper an application handler works with, built from the token claims. */
    private data class Shopper(val id: String, val scopes: List<String>, val expiresAt: Date)

    /** The shop application; [refreshes] counts the access tokens the refresh route issued. */
    private fun ApplicationTestBuilder.shop(refreshes: AtomicInteger) {
        application {
            install(Authentication) {
                jwt("access-jwt") {
                    realm = REALM
                    verifier(ISSUER, ACCESS_AUDIENCE, Algorithm.HMAC256(SECRET)) {
                        acceptLeeway(LEEWAY_SECONDS)
                    }
                    validate { credential ->
                        val scopes: List<String> = credential.getListClaim("scope", String::class)
                        if (credential.subject == null || scopes.isEmpty()) {
                            null
                        } else {
                            Shopper(credential.subject!!, scopes, credential.expiresAt!!)
                        }
                    }
                }
                jwt("refresh-jwt") {
                    realm = REALM
                    verifier(ISSUER, REFRESH_AUDIENCE, Algorithm.HMAC256(SECRET))
                    validate { credential -> credential.subject?.let { Shopper(it, emptyList(), credential.expiresAt!!) } }
                }
            }
            routing {
                post("/login") {
                    val form: Parameters = call.receiveParameters()
                    val shopper: String = form["username"]!!
                    if (form["password"] != "correct horse") {
                        call.respondText("wrong password", status = HttpStatusCode.Unauthorized)
                    } else {
                        call.respondTokens(shopper)
                    }
                }
                authenticate("refresh-jwt") {
                    post("/token/refresh") {
                        val shopper: Shopper = call.principal<Shopper>()!!
                        refreshes.incrementAndGet()
                        call.respondTokens(shopper.id)
                    }
                }
                authenticate("access-jwt") {
                    get("/cart") {
                        val shopper: Shopper = call.principal<Shopper>()!!
                        call.respondText("cart of ${shopper.id} (${shopper.scopes.joinToString(",")})")
                    }
                    get("/orders") {
                        val shopper: Shopper = call.principal<Shopper>()!!
                        if ("orders:read" in shopper.scopes) {
                            call.respondText("orders of ${shopper.id}")
                        } else {
                            call.respondText("no order access", status = HttpStatusCode.Forbidden)
                        }
                    }
                    get("/session") {
                        val shopper: Shopper = call.principal<Shopper>()!!
                        val active: Boolean = shopper.expiresAt.after(Date())
                        call.respondText("${shopper.id} session ${if (active) "active" else "in leeway"}")
                    }
                }
            }
        }
    }

    /** Answers with a fresh form-encoded access and refresh token pair for [shopper]. */
    private suspend fun ApplicationCall.respondTokens(shopper: String) {
        val now: Long = System.currentTimeMillis()
        val pair: Parameters = parameters {
            append("access_token", accessToken(shopper, expiresAtMillis = now + ACCESS_LIFETIME_MILLIS))
            append("refresh_token", refreshToken(shopper))
        }
        respondText(pair.formUrlEncode(), ContentType.Application.FormUrlEncoded)
    }

    /** A client of the shop that keeps the session alive through the refresh route. */
    private fun ApplicationTestBuilder.shopClient(initial: BearerTokens): HttpClient = createClient {
        install(Auth) {
            bearer {
                realm = REALM
                loadTokens { initial }
                // The refresh route carries the refresh token, never the access token.
                sendWithoutRequest { request -> request.url.encodedPath != "/token/refresh" }
                refreshTokens {
                    val refreshed: HttpResponse = client.post("/token/refresh") {
                        markAsRefreshTokenRequest()
                        bearerAuth(oldTokens!!.refreshToken!!)
                    }
                    val pair: Parameters = parseQueryString(refreshed.bodyAsText())
                    BearerTokens(pair["access_token"]!!, pair["refresh_token"]!!)
                }
            }
        }
    }

    @Test
    fun loginIssuesTokensThatOpenTheCart() = testApplication {
        val refreshes: AtomicInteger = AtomicInteger()
        shop(refreshes)

        val wrong: HttpResponse = client.submitForm(
            "/login",
            parameters {
                append("username", "ana")
                append("password", "guess")
            },
        )
        val login: HttpResponse = client.submitForm(
            "/login",
            parameters {
                append("username", "ana")
                append("password", "correct horse")
            },
        )
        val pair: Parameters = parseQueryString(login.bodyAsText())
        val shopper: HttpClient = shopClient(BearerTokens(pair["access_token"]!!, pair["refresh_token"]!!))
        val cart: HttpResponse = shopper.get("/cart")
        val session: HttpResponse = shopper.get("/session")

        assertEquals(HttpStatusCode.Unauthorized, wrong.status)
        assertEquals(HttpStatusCode.OK, login.status)
        assertEquals(HttpStatusCode.OK, cart.status)
        assertEquals("cart of ana (cart:write,orders:read)", cart.bodyAsText())
        assertEquals("ana session active", session.bodyAsText())
        assertEquals(0, refreshes.get())
    }

    @Test
    fun expiredAccessTokenIsRefreshedOnceAndTheRequestRetried() = testApplication {
        val refreshes: AtomicInteger = AtomicInteger()
        shop(refreshes)
        val stale: String = accessToken("milan", expiresAtMillis = System.currentTimeMillis() - STALE_MILLIS)
        val shopper: HttpClient = shopClient(BearerTokens(stale, refreshToken("milan")))

        val orders: HttpResponse = shopper.get("/orders")
        val cart: HttpResponse = shopper.get("/cart")

        assertEquals(HttpStatusCode.OK, orders.status)
        assertEquals("orders of milan", orders.bodyAsText())
        assertEquals(HttpStatusCode.OK, cart.status)
        assertEquals("cart of milan (cart:write,orders:read)", cart.bodyAsText())
        assertEquals(1, refreshes.get())
    }

    @Test
    fun accessTokenWithinTheLeewayIsStillAccepted() = testApplication {
        shop(AtomicInteger())
        val justExpired: String = accessToken("ana", expiresAtMillis = System.currentTimeMillis() - JUST_EXPIRED_MILLIS)

        val cart: HttpResponse = client.get("/cart") { bearerAuth(justExpired) }
        val session: HttpResponse = client.get("/session") { bearerAuth(justExpired) }

        assertEquals(HttpStatusCode.OK, cart.status)
        assertEquals("cart of ana (cart:write,orders:read)", cart.bodyAsText())
        assertEquals("ana session in leeway", session.bodyAsText())
    }

    @Test
    fun tokensAreOnlyAcceptedForTheirOwnAudienceAndScopes() = testApplication {
        val refreshes: AtomicInteger = AtomicInteger()
        shop(refreshes)
        val access: String = accessToken("ana", expiresAtMillis = System.currentTimeMillis() + ACCESS_LIFETIME_MILLIS)
        val unscoped: String = accessToken("ana", System.currentTimeMillis() + ACCESS_LIFETIME_MILLIS, emptyList())
        val cartOnly: String = accessToken("ana", System.currentTimeMillis() + ACCESS_LIFETIME_MILLIS, listOf("cart:write"))

        val refreshAsAccess: HttpResponse = client.get("/cart") { bearerAuth(refreshToken("ana")) }
        val accessAsRefresh: HttpResponse = client.post("/token/refresh") { bearerAuth(access) }
        val withoutScopes: HttpResponse = client.get("/cart") { bearerAuth(unscoped) }
        val withoutOrderScope: HttpResponse = client.get("/orders") { bearerAuth(cartOnly) }

        assertBearerChallenge(refreshAsAccess)
        assertBearerChallenge(accessAsRefresh)
        assertBearerChallenge(withoutScopes)
        assertEquals(HttpStatusCode.Forbidden, withoutOrderScope.status)
        assertEquals("no order access", withoutOrderScope.bodyAsText())
        assertEquals(0, refreshes.get())
    }

    private fun assertBearerChallenge(response: HttpResponse) {
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("Bearer realm=\"$REALM\"", response.headers[HttpHeaders.WWWAuthenticate])
    }

    /** An access token for [shopper] granting [scopes], expiring at [expiresAtMillis]. */
    private fun accessToken(
        shopper: String,
        expiresAtMillis: Long,
        scopes: List<String> = listOf("cart:write", "orders:read"),
    ): String = JWT.create()
        .withIssuer(ISSUER)
        .withAudience(ACCESS_AUDIENCE)
        .withSubject(shopper)
        .withClaim("scope", scopes)
        .withExpiresAt(Date(expiresAtMillis))
        .sign(Algorithm.HMAC256(SECRET))

    /** A long-lived refresh token for [shopper], only valid on the refresh route. */
    private fun refreshToken(shopper: String): String = JWT.create()
        .withIssuer(ISSUER)
        .withAudience(REFRESH_AUDIENCE)
        .withSubject(shopper)
        .withExpiresAt(Date(System.currentTimeMillis() + REFRESH_LIFETIME_MILLIS))
        .sign(Algorithm.HMAC256(SECRET))

    private companion object {
        const val SECRET: String = "shop-session-signing-secret"
        const val ISSUER: String = "https://shop.example"
        const val ACCESS_AUDIENCE: String = "shop-api"
        const val REFRESH_AUDIENCE: String = "shop-refresh"
        const val REALM: String = "web shop"
        const val LEEWAY_SECONDS: Long = 300
        const val JUST_EXPIRED_MILLIS: Long = 30_000
        const val STALE_MILLIS: Long = 3_600_000
        const val ACCESS_LIFETIME_MILLIS: Long = 900_000
        const val REFRESH_LIFETIME_MILLIS: Long = 86_400_000
    }
}
