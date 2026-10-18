/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_auth_jwt_jvm

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTDecodeException
import com.auth0.jwt.interfaces.JWTVerifier
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.auth.principal
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.Date
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Per-request verifier selection with a custom token transport, where a
 * multi-tenant gateway picks each tenant's signing key from the token it receives.
 *
 * The `verifier { header -> ... }` function reads the unverified `tenant` claim
 * and returns that tenant's verifier, or `null` for a tenant the gateway does not
 * serve. `authHeader` takes the token from a browser session cookie before the
 * `Authorization` header, and `authSchemes` accepts the legacy `Token` scheme
 * next to `Bearer`; anything else gets the default bearer challenge.
 */
public class TenantGatewayVerifierSelectionTest {
    /** A tenant the gateway serves, with the key and issuer its tokens are signed with. */
    private data class Tenant(val name: String, val secret: String, val issuer: String)

    /** The gateway application: the `tenant-jwt` provider and the routes it protects. */
    private fun ApplicationTestBuilder.gateway() {
        application {
            install(Authentication) {
                jwt("tenant-jwt") {
                    realm = REALM
                    authSchemes("Bearer", "Token")
                    authHeader { call ->
                        val session: String? = call.request.cookies[SESSION_COOKIE]
                        if (session != null) {
                            HttpAuthHeader.Single("Bearer", session)
                        } else {
                            call.request.parseAuthorizationHeader()
                        }
                    }
                    verifier { header -> verifierFor(header) }
                    validate { credential -> JWTPrincipal(credential.payload) }
                }
            }
            routing {
                authenticate("tenant-jwt") {
                    get("/orders") {
                        val principal: JWTPrincipal = call.principal<JWTPrincipal>()!!
                        call.respondText(
                            "${principal["tenant"]} orders for ${principal.subject} from ${principal.issuer}",
                        )
                    }
                }
            }
        }
    }

    /** The verifier of the tenant [header]'s token names, or `null` when no served tenant matches. */
    private fun verifierFor(header: HttpAuthHeader): JWTVerifier? {
        val token: String = (header as? HttpAuthHeader.Single)?.blob ?: return null
        val tenantName: String = try {
            JWT.decode(token).getClaim("tenant").asString()
        } catch (_: JWTDecodeException) {
            null
        } ?: return null
        val tenant: Tenant = TENANTS[tenantName] ?: return null
        return JWT.require(Algorithm.HMAC256(tenant.secret))
            .withIssuer(tenant.issuer)
            .withClaim("tenant", tenant.name)
            .build()
    }

    /** A token for [subject] that names [tenant] and is signed with [secret]. */
    private fun token(
        tenant: Tenant,
        subject: String,
        secret: String = tenant.secret,
        claimedTenant: String = tenant.name,
    ): String = JWT.create()
        .withIssuer(tenant.issuer)
        .withSubject(subject)
        .withClaim("tenant", claimedTenant)
        .withExpiresAt(Date(System.currentTimeMillis() + LIFETIME_MILLIS))
        .sign(Algorithm.HMAC256(secret))

    @Test
    fun eachTenantIsVerifiedWithItsOwnKey() = testApplication {
        gateway()

        val acmeOrders: HttpResponse = client.get("/orders") { bearerAuth(token(ACME, "road-runner")) }
        val globexOrders: HttpResponse = client.get("/orders") { bearerAuth(token(GLOBEX, "hank")) }

        assertEquals(HttpStatusCode.OK, acmeOrders.status)
        assertEquals("acme orders for road-runner from ${ACME.issuer}", acmeOrders.bodyAsText())
        assertEquals(HttpStatusCode.OK, globexOrders.status)
        assertEquals("globex orders for hank from ${GLOBEX.issuer}", globexOrders.bodyAsText())
    }

    @Test
    fun browserSessionCookieCarriesTheToken() = testApplication {
        gateway()

        val response: HttpResponse = client.get("/orders") { cookie(SESSION_COOKIE, token(GLOBEX, "frank")) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("globex orders for frank from ${GLOBEX.issuer}", response.bodyAsText())
    }

    @Test
    fun legacyTokenSchemeIsAcceptedButOtherSchemesAreNot() = testApplication {
        gateway()
        val issued: String = token(ACME, "wile")

        val legacy: HttpResponse = client.get("/orders") { header(HttpHeaders.Authorization, "Token $issued") }
        val basic: HttpResponse = client.get("/orders") { header(HttpHeaders.Authorization, "Basic $issued") }

        assertEquals(HttpStatusCode.OK, legacy.status)
        assertEquals("acme orders for wile from ${ACME.issuer}", legacy.bodyAsText())
        assertUnauthorized(basic)
    }

    @Test
    fun tokensWithoutAMatchingTenantVerifierGetTheBearerChallenge() = testApplication {
        gateway()
        val rejected: List<String> = listOf(
            token(ACME, "wile", secret = GLOBEX.secret),
            token(GLOBEX, "hank", claimedTenant = ACME.name),
            token(Tenant("initech", "initech-secret", "https://initech.example"), "peter"),
            "not.a.jwt",
        )

        for (issued in rejected) {
            assertUnauthorized(client.get("/orders") { bearerAuth(issued) })
        }
        assertUnauthorized(client.get("/orders"))
    }

    private suspend fun assertUnauthorized(response: HttpResponse) {
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("Bearer realm=\"$REALM\"", response.headers[HttpHeaders.WWWAuthenticate])
    }

    private companion object {
        const val REALM: String = "tenant gateway"
        const val SESSION_COOKIE: String = "gateway_session"
        const val LIFETIME_MILLIS: Long = 3_600_000
        val ACME: Tenant = Tenant("acme", "acme-signing-secret", "https://acme.example")
        val GLOBEX: Tenant = Tenant("globex", "globex-signing-secret", "https://globex.example")
        val TENANTS: Map<String, Tenant> = listOf(ACME, GLOBEX).associateBy { it.name }
    }
}
