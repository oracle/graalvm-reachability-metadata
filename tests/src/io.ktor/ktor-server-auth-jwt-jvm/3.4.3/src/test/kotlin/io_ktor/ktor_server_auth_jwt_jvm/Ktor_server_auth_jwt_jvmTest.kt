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

    @Test
    fun verifierCallbackSelectsVerifierFromTokenKeyId(): Unit = testApplication {
        val verifiers: Map<String, JWTVerifier> = mapOf(
            PRIMARY_KEY_ID to jwtVerifier(ALGORITHM),
            SECONDARY_KEY_ID to jwtVerifier(SECONDARY_ALGORITHM)
        )

        install(Authentication) {
            jwt("key-id-jwt") {
                realm = REALM
                verifier { authHeader ->
                    val token: String = (authHeader as? HttpAuthHeader.Single)?.blob ?: return@verifier null
                    val keyId: String = JWT.decode(token).keyId ?: return@verifier null
                    verifiers[keyId]
                }
                validate { credential -> JWTPrincipal(credential.payload) }
            }
        }
        routing {
            authenticate("key-id-jwt") {
                get("/key-id") {
                    val principal: JWTPrincipal = requireNotNull(call.principal<JWTPrincipal>())
                    call.respondText("key-id:${principal.payload.subject}")
                }
            }
        }

        val primaryToken: String = jwtToken(subject = "primary-subject", keyId = PRIMARY_KEY_ID)
        val secondaryToken: String = jwtToken(
            subject = "secondary-subject",
            keyId = SECONDARY_KEY_ID,
            algorithm = SECONDARY_ALGORITHM
        )
        val mismatchedSignatureToken: String = jwtToken(
            subject = "mismatched-subject",
            keyId = SECONDARY_KEY_ID
        )

        val primaryResponse = client.get("/key-id") {
            header(HttpHeaders.Authorization, "Bearer $primaryToken")
        }
        val secondaryResponse = client.get("/key-id") {
            header(HttpHeaders.Authorization, "Bearer $secondaryToken")
        }
        val mismatchedSignatureResponse = client.get("/key-id") {
            header(HttpHeaders.Authorization, "Bearer $mismatchedSignatureToken")
        }

        assertThat(primaryResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(primaryResponse.bodyAsText()).isEqualTo("key-id:primary-subject")
        assertThat(secondaryResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(secondaryResponse.bodyAsText()).isEqualTo("key-id:secondary-subject")
        assertThat(mismatchedSignatureResponse.status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    private companion object {
        private const val ISSUER: String = "https://issuer.example.test/"
        private const val AUDIENCE: String = "ktor-server-auth-jwt-tests"
        private const val REALM: String = "ktor jwt test realm"
        private const val SECRET: String = "native-image-friendly-test-secret-that-is-long-enough"
        private const val SECONDARY_SECRET: String = "secondary-native-image-friendly-test-secret"
        private const val PRIMARY_KEY_ID: String = "primary-key"
        private const val SECONDARY_KEY_ID: String = "secondary-key"
        private val ALGORITHM: Algorithm = Algorithm.HMAC256(SECRET)
        private val SECONDARY_ALGORITHM: Algorithm = Algorithm.HMAC256(SECONDARY_SECRET)

        private fun jwtVerifier(algorithm: Algorithm = ALGORITHM): JWTVerifier = JWT.require(algorithm)
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .build()

        private fun jwtToken(
            subject: String = "alice",
            issuer: String = ISSUER,
            audience: String = AUDIENCE,
            expiresAt: Instant = Instant.now().plusSeconds(120),
            keyId: String? = null,
            algorithm: Algorithm = ALGORITHM
        ): String = JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(subject)
            .withClaim("tenant", "tenant-one")
            .withExpiresAt(Date.from(expiresAt))
            .apply { keyId?.let { withKeyId(it) } }
            .sign(algorithm)
    }
}
