/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_auth_jwt_jvm

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTCreator
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationStrategy
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.Date
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Route authentication strategies across several JWT providers, where a package
 * registry mixes anonymous, user, CI and step-up access on one route tree.
 *
 * Package pages authenticate optionally with the `user-jwt` provider, so
 * anonymous visitors still read them; publishing accepts either a user token or
 * a CI token under the first-successful strategy; yanking a release requires
 * both a user token and a short-lived step-up token sent in its own header.
 */
public class PackageRegistryAuthStrategiesTest {
    /** The registry application: three JWT providers and the routes each strategy guards. */
    private fun ApplicationTestBuilder.registry() {
        application {
            install(Authentication) {
                jwt(USER_PROVIDER) {
                    realm = "registry users"
                    verifier(verifier(USER_SECRET, USER_ISSUER))
                    validate { credential -> JWTPrincipal(credential.payload) }
                }
                jwt(CI_PROVIDER) {
                    realm = "registry ci"
                    verifier(verifier(CI_SECRET, CI_ISSUER))
                    validate { credential ->
                        if (credential.payload.getClaim("pipeline").isMissing) null else JWTPrincipal(credential.payload)
                    }
                }
                jwt(STEP_UP_PROVIDER) {
                    realm = "registry step-up"
                    authHeader { call ->
                        call.request.headers[STEP_UP_HEADER]?.let { HttpAuthHeader.Single("Bearer", it) }
                    }
                    verifier(verifier(STEP_UP_SECRET, USER_ISSUER))
                    validate { credential ->
                        if (credential.payload.getClaim("amr").asString() == "otp") JWTPrincipal(credential.payload) else null
                    }
                }
            }
            routing {
                authenticate(USER_PROVIDER, optional = true) {
                    get("/packages/{name}") {
                        val name: String = call.parameters["name"]!!
                        val visitor: JWTPrincipal? = call.principal<JWTPrincipal>()
                        val greeting: String = visitor?.subject?.let { "signed in as $it" } ?: "anonymous"
                        call.respondText("$name 1.4.0, $greeting")
                    }
                }
                authenticate(USER_PROVIDER, CI_PROVIDER, strategy = AuthenticationStrategy.FirstSuccessful) {
                    put("/packages/{name}/{version}") {
                        val release: String = "${call.parameters["name"]}@${call.parameters["version"]}"
                        val user: JWTPrincipal? = call.principal<JWTPrincipal>(USER_PROVIDER)
                        val ci: JWTPrincipal? = call.principal<JWTPrincipal>(CI_PROVIDER)
                        val publisher: String = when {
                            user != null -> "user ${user.subject}"
                            ci != null -> "pipeline ${ci["pipeline"]}"
                            else -> error("no publisher authenticated")
                        }
                        call.respondText("$release published by $publisher", status = HttpStatusCode.Created)
                    }
                }
                authenticate(USER_PROVIDER, STEP_UP_PROVIDER, strategy = AuthenticationStrategy.Required) {
                    delete("/packages/{name}/{version}") {
                        val release: String = "${call.parameters["name"]}@${call.parameters["version"]}"
                        val user: JWTPrincipal = call.principal<JWTPrincipal>(USER_PROVIDER)!!
                        val stepUp: JWTPrincipal = call.principal<JWTPrincipal>(STEP_UP_PROVIDER)!!
                        if (user.subject == stepUp.subject) {
                            call.respondText("$release yanked by ${user.subject}")
                        } else {
                            call.respondText("step-up belongs to ${stepUp.subject}", status = HttpStatusCode.Forbidden)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun optionalPagesServeAnonymousAndSignedInVisitors() = testApplication {
        registry()

        val anonymous: HttpResponse = client.get("/packages/left-pad")
        val signedIn: HttpResponse = client.get("/packages/left-pad") { bearerAuth(userToken("ana")) }

        assertEquals(HttpStatusCode.OK, anonymous.status)
        assertEquals("left-pad 1.4.0, anonymous", anonymous.bodyAsText())
        assertEquals(HttpStatusCode.OK, signedIn.status)
        assertEquals("left-pad 1.4.0, signed in as ana", signedIn.bodyAsText())
    }

    @Test
    fun optionalPagesStillRejectAForgedToken() = testApplication {
        registry()

        val response: HttpResponse = client.get("/packages/left-pad") {
            bearerAuth(token(USER_ISSUER, "an-attackers-secret") { withSubject("ana") })
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("Bearer realm=\"registry users\"", response.headers[HttpHeaders.WWWAuthenticate])
    }

    @Test
    fun publishingAcceptsEitherAUserOrACiToken() = testApplication {
        registry()

        val byUser: HttpResponse = client.put("/packages/left-pad/1.4.1") { bearerAuth(userToken("ana")) }
        val byCi: HttpResponse = client.put("/packages/left-pad/1.4.2") {
            bearerAuth(token(CI_ISSUER, CI_SECRET) { withClaim("pipeline", "release-42") })
        }

        assertEquals(HttpStatusCode.Created, byUser.status)
        assertEquals("left-pad@1.4.1 published by user ana", byUser.bodyAsText())
        assertEquals(HttpStatusCode.Created, byCi.status)
        assertEquals("left-pad@1.4.2 published by pipeline release-42", byCi.bodyAsText())
    }

    @Test
    fun publishingRejectsTokensNoProviderAccepts() = testApplication {
        registry()
        val rejected: List<String> = listOf(
            token(CI_ISSUER, CI_SECRET),
            token(CI_ISSUER, USER_SECRET) { withClaim("pipeline", "release-42") },
            token(USER_ISSUER, CI_SECRET) { withSubject("ana") },
        )

        assertEquals(HttpStatusCode.Unauthorized, client.put("/packages/left-pad/1.4.3").status)
        for (issued in rejected) {
            assertEquals(HttpStatusCode.Unauthorized, client.put("/packages/left-pad/1.4.3") { bearerAuth(issued) }.status)
        }
    }

    @Test
    fun yankingRequiresTheUserAndTheirStepUpToken() = testApplication {
        registry()
        val user: String = userToken("ana")

        val yanked: HttpResponse = client.delete("/packages/left-pad/1.4.1") {
            bearerAuth(user)
            header(STEP_UP_HEADER, stepUpToken("ana", "otp"))
        }
        val withoutStepUp: HttpResponse = client.delete("/packages/left-pad/1.4.1") { bearerAuth(user) }
        val passwordOnlyStepUp: HttpResponse = client.delete("/packages/left-pad/1.4.1") {
            bearerAuth(user)
            header(STEP_UP_HEADER, stepUpToken("ana", "pwd"))
        }
        val stepUpOnly: HttpResponse = client.delete("/packages/left-pad/1.4.1") {
            header(STEP_UP_HEADER, stepUpToken("ana", "otp"))
        }
        val someoneElsesStepUp: HttpResponse = client.delete("/packages/left-pad/1.4.1") {
            bearerAuth(user)
            header(STEP_UP_HEADER, stepUpToken("milan", "otp"))
        }

        assertEquals(HttpStatusCode.OK, yanked.status)
        assertEquals("left-pad@1.4.1 yanked by ana", yanked.bodyAsText())
        assertEquals(HttpStatusCode.Unauthorized, withoutStepUp.status)
        assertEquals("Bearer realm=\"registry step-up\"", withoutStepUp.headers[HttpHeaders.WWWAuthenticate])
        assertEquals(HttpStatusCode.Unauthorized, passwordOnlyStepUp.status)
        assertEquals(HttpStatusCode.Unauthorized, stepUpOnly.status)
        assertEquals("Bearer realm=\"registry users\"", stepUpOnly.headers[HttpHeaders.WWWAuthenticate])
        assertEquals(HttpStatusCode.Forbidden, someoneElsesStepUp.status)
        assertEquals("step-up belongs to milan", someoneElsesStepUp.bodyAsText())
    }

    /** A verifier that accepts tokens [issuer] signed with [secret] for the registry audience. */
    private fun verifier(secret: String, issuer: String): JWTVerifier = JWT.require(Algorithm.HMAC256(secret))
        .withIssuer(issuer)
        .withAudience(AUDIENCE)
        .build()

    /** A registry token from [issuer], signed with [secret] after [customize] adds its claims. */
    private fun token(issuer: String, secret: String, customize: JWTCreator.Builder.() -> Unit = {}): String {
        val builder: JWTCreator.Builder = JWT.create()
            .withIssuer(issuer)
            .withAudience(AUDIENCE)
            .withExpiresAt(Date(System.currentTimeMillis() + LIFETIME_MILLIS))
        builder.customize()
        return builder.sign(Algorithm.HMAC256(secret))
    }

    /** The token the registry website issues when [user] signs in. */
    private fun userToken(user: String): String = token(USER_ISSUER, USER_SECRET) { withSubject(user) }

    /** The step-up token the website issues to [user] after re-authenticating with method [amr]. */
    private fun stepUpToken(user: String, amr: String): String = token(USER_ISSUER, STEP_UP_SECRET) {
        withSubject(user)
        withClaim("amr", amr)
    }

    private companion object {
        const val USER_PROVIDER: String = "user-jwt"
        const val CI_PROVIDER: String = "ci-jwt"
        const val STEP_UP_PROVIDER: String = "step-up-jwt"
        const val STEP_UP_HEADER: String = "X-Step-Up-Token"
        const val USER_SECRET: String = "registry-user-signing-secret"
        const val CI_SECRET: String = "registry-ci-signing-secret"
        const val STEP_UP_SECRET: String = "registry-step-up-signing-secret"
        const val USER_ISSUER: String = "https://registry.example"
        const val CI_ISSUER: String = "https://ci.registry.example"
        const val AUDIENCE: String = "registry-api"
        const val LIFETIME_MILLIS: Long = 3_600_000
    }
}
