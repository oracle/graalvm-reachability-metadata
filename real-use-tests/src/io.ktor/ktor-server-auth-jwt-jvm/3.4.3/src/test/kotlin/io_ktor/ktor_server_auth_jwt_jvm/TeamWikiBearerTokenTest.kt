/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_auth_jwt_jvm

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTCreator
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.Date
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * HMAC-signed bearer-token authentication with a static verifier, where a team
 * wiki accepts tokens it issued itself and reads the caller from their claims.
 *
 * A token with the right signature, issuer, audience and lifetime passes the
 * verifier, and `validate` turns it into a [JWTPrincipal] whose standard, string
 * and list claims the page handlers read; a token that fails the verifier or
 * `validate` gets the wiki's own bearer challenge instead.
 */
public class TeamWikiBearerTokenTest {
    /** The wiki application: the `wiki-jwt` provider and the pages it protects. */
    private fun ApplicationTestBuilder.wiki() {
        application {
            install(Authentication) {
                jwt("wiki-jwt") {
                    realm = REALM
                    verifier(
                        JWT.require(Algorithm.HMAC256(SECRET))
                            .withIssuer(ISSUER)
                            .withAudience(AUDIENCE)
                            .build(),
                    )
                    validate { credential ->
                        if (credential.payload.getClaim("team").asString().isNullOrBlank()) {
                            null
                        } else {
                            JWTPrincipal(credential.payload)
                        }
                    }
                    challenge { defaultScheme, realm ->
                        call.response.header(HttpHeaders.WWWAuthenticate, "$defaultScheme realm=\"$realm\"")
                        call.respondText("sign in to the wiki again", status = HttpStatusCode.Unauthorized)
                    }
                }
            }
            routing {
                authenticate("wiki-jwt") {
                    get("/pages/home") {
                        val principal: JWTPrincipal = call.principal<JWTPrincipal>()!!
                        call.respondText("${principal.subject} of team ${principal["team"]} opened home")
                    }
                    get("/pages/{page}/edit") {
                        val principal: JWTPrincipal = call.principal<JWTPrincipal>()!!
                        val roles: List<String> = principal.getListClaim("roles", String::class)
                        val page: String = call.parameters["page"]!!
                        if ("editor" in roles) {
                            call.respondText("${principal.subject} edits $page as ${roles.joinToString("+")}")
                        } else {
                            call.respondText("${principal.subject} may only read $page", status = HttpStatusCode.Forbidden)
                        }
                    }
                    get("/whoami") {
                        val principal: JWTPrincipal = call.principal<JWTPrincipal>()!!
                        call.respondText(
                            "${principal.issuer} ${principal.audience} ${principal.jwtId} " +
                                "seats=${principal.getClaim("seats", Int::class)}",
                        )
                    }
                }
            }
        }
    }

    /** A token the wiki issued for [subject], before [customize] adjusts it, signed with [secret]. */
    private fun token(
        subject: String,
        secret: String = SECRET,
        customize: JWTCreator.Builder.() -> Unit = {},
    ): String {
        val builder: JWTCreator.Builder = JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withSubject(subject)
            .withClaim("team", "docs")
            .withExpiresAt(Date(System.currentTimeMillis() + LIFETIME_MILLIS))
        builder.customize()
        return builder.sign(Algorithm.HMAC256(secret))
    }

    @Test
    fun validTokenIdentifiesTheCaller() = testApplication {
        wiki()

        val response: HttpResponse = client.get("/pages/home") { bearerAuth(token("ana")) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ana of team docs opened home", response.bodyAsText())
    }

    @Test
    fun handlersReadStandardAndTypedClaims() = testApplication {
        wiki()
        val issued: String = token("ana") {
            withJWTId("session-17")
            withClaim("seats", 4)
            withClaim("roles", listOf("reader", "editor"))
        }

        val whoami: HttpResponse = client.get("/whoami") { bearerAuth(issued) }
        val edit: HttpResponse = client.get("/pages/onboarding/edit") { bearerAuth(issued) }
        val readerEdit: HttpResponse = client.get("/pages/onboarding/edit") {
            bearerAuth(token("milan") { withClaim("roles", listOf("reader")) })
        }

        assertEquals(HttpStatusCode.OK, whoami.status)
        assertEquals("$ISSUER [$AUDIENCE] session-17 seats=4", whoami.bodyAsText())
        assertEquals(HttpStatusCode.OK, edit.status)
        assertEquals("ana edits onboarding as reader+editor", edit.bodyAsText())
        assertEquals(HttpStatusCode.Forbidden, readerEdit.status)
        assertEquals("milan may only read onboarding", readerEdit.bodyAsText())
    }

    @Test
    fun missingTokenGetsTheBearerChallenge() = testApplication {
        wiki()

        val response: HttpResponse = client.get("/pages/home")

        assertUnauthorized(response)
    }

    @Test
    fun tokensTheVerifierRejectsGetTheBearerChallenge() = testApplication {
        wiki()
        val rejected: List<String> = listOf(
            token("ana", secret = "someone-elses-secret"),
            token("ana") { withIssuer("https://evil.example") },
            token("ana") { withAudience("billing") },
            token("ana") { withExpiresAt(Date(EXPIRED_AT_MILLIS)) },
            "not.a.jwt",
        )

        for (issued in rejected) {
            assertUnauthorized(client.get("/pages/home") { bearerAuth(issued) })
        }
    }

    @Test
    fun tokenWithoutTeamIsRejectedByValidate() = testApplication {
        wiki()

        val response: HttpResponse = client.get("/pages/home") {
            bearerAuth(token("ana") { withClaim("team", "") })
        }

        assertUnauthorized(response)
    }

    private suspend fun assertUnauthorized(response: HttpResponse) {
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("Bearer realm=\"$REALM\"", response.headers[HttpHeaders.WWWAuthenticate])
        assertEquals("sign in to the wiki again", response.bodyAsText())
    }

    private companion object {
        const val SECRET: String = "team-wiki-signing-secret"
        const val ISSUER: String = "https://wiki.example"
        const val AUDIENCE: String = "wiki-pages"
        const val REALM: String = "team wiki"
        const val LIFETIME_MILLIS: Long = 3_600_000
        const val EXPIRED_AT_MILLIS: Long = 1_000_000_000_000
    }
}
