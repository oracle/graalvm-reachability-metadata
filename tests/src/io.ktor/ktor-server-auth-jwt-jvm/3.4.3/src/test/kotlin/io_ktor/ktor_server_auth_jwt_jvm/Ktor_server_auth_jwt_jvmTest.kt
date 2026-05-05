/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_auth_jwt_jvm

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.request.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Date

public class KtorServerAuthJwtJvmTest {
    @Test
    fun authenticatedRouteReceivesJwtPrincipalClaims(): Unit = testApplication {
        install(Authentication) {
            jwt("jwt-auth") {
                realm = REALM
                verifier(jwtVerifier())
                validate { credential ->
                    val tenant: String? = credential.payload.getClaim("tenant").asString()
                    if (tenant == "tenant-one" && credential.payload.audience.contains(AUDIENCE)) {
                        JWTPrincipal(credential.payload)
                    } else {
                        null
                    }
                }
            }
        }
        routing {
            authenticate("jwt-auth") {
                get("/protected") {
                    val principal: JWTPrincipal = requireNotNull(call.principal<JWTPrincipal>())
                    val tenant: String = principal.payload.getClaim("tenant").asString()
                    call.respondText(
                        "subject=${principal.payload.subject};issuer=${principal.payload.issuer};tenant=$tenant"
                    )
                }
            }
        }

        val response = client.get("/protected") {
            header(HttpHeaders.Authorization, "Bearer ${jwtToken(subject = "alice")}")
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.bodyAsText()).isEqualTo(
            "subject=alice;issuer=$ISSUER;tenant=tenant-one"
        )
    }

    @Test
    fun invalidTokenUsesConfiguredChallengeAndDoesNotInvokeHandler(): Unit = testApplication {
        var handlerReached: Boolean = false

        install(Authentication) {
            jwt("jwt-auth") {
                realm = REALM
                verifier(jwtVerifier())
                validate { credential -> JWTPrincipal(credential.payload) }
                challenge { _, realm ->
                    call.response.headers.append("X-Challenge-Realm", realm)
                    call.respondText("jwt rejected for realm $realm", status = HttpStatusCode.Unauthorized)
                }
            }
        }
        routing {
            authenticate("jwt-auth") {
                get("/protected") {
                    handlerReached = true
                    call.respondText("unreachable")
                }
            }
        }

        val response = client.get("/protected") {
            header(HttpHeaders.Authorization, "Bearer ${jwtToken(audience = "wrong-audience")}")
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(response.headers["X-Challenge-Realm"]).isEqualTo(REALM)
        assertThat(response.bodyAsText()).isEqualTo("jwt rejected for realm $REALM")
        assertThat(handlerReached).isFalse()
    }

    @Test
    fun customAuthenticationHeaderCanSupplyBearerToken(): Unit = testApplication {
        install(Authentication) {
            jwt("custom-header-jwt") {
                realm = REALM
                verifier(jwtVerifier())
                authHeader { call ->
                    call.request.header("X-Api-Token")?.let { token ->
                        HttpAuthHeader.Single("Bearer", token)
                    }
                }
                validate { credential -> JWTPrincipal(credential.payload) }
            }
        }
        routing {
            authenticate("custom-header-jwt") {
                get("/custom-header") {
                    val principal: JWTPrincipal = requireNotNull(call.principal<JWTPrincipal>())
                    call.respondText("custom:${principal.payload.subject}")
                }
            }
        }

        val ignoredAuthorizationResponse = client.get("/custom-header") {
            header(HttpHeaders.Authorization, "Bearer ${jwtToken(subject = "from-authorization")}")
        }
        val customHeaderResponse = client.get("/custom-header") {
            header("X-Api-Token", jwtToken(subject = "from-custom-header"))
        }

        assertThat(ignoredAuthorizationResponse.status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(customHeaderResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(customHeaderResponse.bodyAsText()).isEqualTo("custom:from-custom-header")
    }

    @Test
    fun customAuthorizationSchemeAuthenticatesToken(): Unit = testApplication {
        install(Authentication) {
            jwt("token-scheme-jwt") {
                realm = REALM
                authSchemes("Token")
                verifier(jwtVerifier())
                validate { credential -> JWTPrincipal(credential.payload) }
            }
        }
        routing {
            authenticate("token-scheme-jwt") {
                get("/custom-scheme") {
                    val principal: JWTPrincipal = requireNotNull(call.principal<JWTPrincipal>())
                    call.respondText("scheme:${principal.payload.subject}")
                }
            }
        }

        val bearerResponse = client.get("/custom-scheme") {
            header(HttpHeaders.Authorization, "Bearer ${jwtToken(subject = "bearer-subject")}")
        }
        val tokenSchemeResponse = client.get("/custom-scheme") {
            header(HttpHeaders.Authorization, "Token ${jwtToken(subject = "token-subject")}")
        }

        assertThat(bearerResponse.status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(tokenSchemeResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(tokenSchemeResponse.bodyAsText()).isEqualTo("scheme:token-subject")
    }

    private companion object {
        private const val ISSUER: String = "https://issuer.example.test/"
        private const val AUDIENCE: String = "ktor-server-auth-jwt-tests"
        private const val REALM: String = "ktor jwt test realm"
        private const val SECRET: String = "native-image-friendly-test-secret-that-is-long-enough"
        private val ALGORITHM: Algorithm = Algorithm.HMAC256(SECRET)

        private fun jwtVerifier(): JWTVerifier = JWT.require(ALGORITHM)
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .build()

        private fun jwtToken(
            subject: String = "alice",
            issuer: String = ISSUER,
            audience: String = AUDIENCE,
            expiresAt: Instant = Instant.now().plusSeconds(120)
        ): String = JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(subject)
            .withClaim("tenant", "tenant-one")
            .withExpiresAt(Date.from(expiresAt))
            .sign(ALGORITHM)
    }
}
