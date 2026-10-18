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
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.auth.parseAuthorizationHeader
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.Date
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * RFC 6750 bearer error reporting, where a calendar API tells clients why a
 * token was refused by reading the authentication failure cause in its challenge.
 *
 * The `challenge` block inspects the [AuthenticationFailedCause] the JWT provider
 * recorded: a request without a token gets a bare bearer challenge, while a token
 * the verifier or `validate` refused gets `error="invalid_token"`. Handlers read
 * the token's time claims from the [JWTPrincipal] and answer a token that lacks a
 * scope with `error="insufficient_scope"`, so every refusal is machine-readable.
 */
public class CalendarApiBearerErrorReportingTest {
    /** The calendar application: the `calendar-jwt` provider and the endpoints it protects. */
    private fun ApplicationTestBuilder.calendar() {
        application {
            install(Authentication) {
                jwt("calendar-jwt") {
                    realm = REALM
                    verifier(
                        JWT.require(Algorithm.HMAC256(SECRET))
                            .withIssuer(ISSUER)
                            .withAudience(AUDIENCE)
                            .build(),
                    )
                    validate { credential ->
                        if (credential.subject.isNullOrBlank()) null else JWTPrincipal(credential.payload)
                    }
                    challenge { defaultScheme, realm ->
                        val causes: List<AuthenticationFailedCause> = call.authentication.allFailures
                        val parameters: MutableMap<String, String> = linkedMapOf("realm" to realm)
                        if (AuthenticationFailedCause.InvalidCredentials in causes) {
                            parameters["error"] = "invalid_token"
                            parameters["error_description"] = "the access token is invalid or expired"
                        }
                        call.respondBearerError(
                            HttpStatusCode.Unauthorized,
                            HttpAuthHeader.Parameterized(defaultScheme, parameters),
                            causes.joinToString(",") { cause ->
                                when (cause) {
                                    AuthenticationFailedCause.NoCredentials -> "NoCredentials"
                                    AuthenticationFailedCause.InvalidCredentials -> "InvalidCredentials"
                                    is AuthenticationFailedCause.Error -> "Error"
                                }
                            },
                        )
                    }
                }
            }
            routing {
                authenticate("calendar-jwt") {
                    get("/session") {
                        val principal: JWTPrincipal = call.principal<JWTPrincipal>()!!
                        call.respondText(
                            "${principal.subject} iat=${principal.issuedAt!!.time / 1000} " +
                                "nbf=${principal.notBefore!!.time / 1000} exp=${principal.expiresAt!!.time / 1000}",
                        )
                    }
                    get("/events") {
                        call.requireScope("calendar.read") { subject -> "events of $subject: standup, review" }
                    }
                    post("/events") {
                        call.requireScope("calendar.write") { subject -> "$subject created an event" }
                    }
                }
            }
        }
    }

    /** Answers with [body] when the caller's token grants [scope], else with an insufficient-scope error. */
    private suspend fun ApplicationCall.requireScope(scope: String, body: (String) -> String) {
        val principal: JWTPrincipal = principal<JWTPrincipal>()!!
        val granted: List<String> = principal.getListClaim("scope", String::class)
        if (scope in granted) {
            respondText(body(principal.subject!!))
        } else {
            respondBearerError(
                HttpStatusCode.Forbidden,
                HttpAuthHeader.Parameterized(
                    "Bearer",
                    linkedMapOf("realm" to REALM, "error" to "insufficient_scope", "scope" to scope),
                ),
                "missing $scope",
            )
        }
    }

    /** Sends [status] with [header] as the `WWW-Authenticate` challenge and [body] as the text. */
    private suspend fun ApplicationCall.respondBearerError(
        status: HttpStatusCode,
        header: HttpAuthHeader.Parameterized,
        body: String,
    ) {
        response.header(HttpHeaders.WWWAuthenticate, header.render())
        respondText(body, status = status)
    }

    /** A token the calendar issued for [subject] with [scopes], before [customize] adjusts it. */
    private fun token(
        subject: String,
        scopes: List<String> = listOf("calendar.read"),
        customize: JWTCreator.Builder.() -> Unit = {},
    ): String {
        val builder: JWTCreator.Builder = JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withSubject(subject)
            .withClaim("scope", scopes)
            .withIssuedAt(Date(ISSUED_AT_SECONDS * 1000))
            .withNotBefore(Date(ISSUED_AT_SECONDS * 1000))
            .withExpiresAt(Date(EXPIRES_AT_SECONDS * 1000))
        builder.customize()
        return builder.sign(Algorithm.HMAC256(SECRET))
    }

    /** The `WWW-Authenticate` challenge of [response], parsed back into its parameters. */
    private fun challengeOf(response: HttpResponse): HttpAuthHeader.Parameterized =
        parseAuthorizationHeader(response.headers[HttpHeaders.WWWAuthenticate]!!) as HttpAuthHeader.Parameterized

    @Test
    fun handlersReadTheTokenTimeClaims() = testApplication {
        calendar()

        val response: HttpResponse = client.get("/session") { bearerAuth(token("ana")) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ana iat=$ISSUED_AT_SECONDS nbf=$ISSUED_AT_SECONDS exp=$EXPIRES_AT_SECONDS", response.bodyAsText())
        assertNull(response.headers[HttpHeaders.WWWAuthenticate])
    }

    @Test
    fun requestWithoutTokenGetsABareChallenge() = testApplication {
        calendar()

        val response: HttpResponse = client.get("/events")
        val challenge: HttpAuthHeader.Parameterized = challengeOf(response)

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("NoCredentials", response.bodyAsText())
        assertEquals("Bearer", challenge.authScheme)
        assertEquals(REALM, challenge.parameter("realm"))
        assertNull(challenge.parameter("error"))
    }

    @Test
    fun refusedTokensGetTheInvalidTokenError() = testApplication {
        calendar()
        val refused: List<String> = listOf(
            token("ana") { withExpiresAt(Date(EXPIRED_AT_SECONDS * 1000)) },
            token("ana") { withNotBefore(Date(NOT_YET_VALID_SECONDS * 1000)) },
            token("ana") { withAudience("mail") },
            token(""),
            "calendar.token.garbage",
        )

        for (issued in refused) {
            val response: HttpResponse = client.get("/events") { bearerAuth(issued) }
            val challenge: HttpAuthHeader.Parameterized = challengeOf(response)

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals("InvalidCredentials", response.bodyAsText())
            assertEquals(REALM, challenge.parameter("realm"))
            assertEquals("invalid_token", challenge.parameter("error"))
            assertEquals("the access token is invalid or expired", challenge.parameter("error_description"))
        }
    }

    @Test
    fun missingScopeGetsTheInsufficientScopeError() = testApplication {
        calendar()
        val readOnly: String = token("milan")
        val readWrite: String = token("ana", scopes = listOf("calendar.read", "calendar.write"))

        val list: HttpResponse = client.get("/events") { bearerAuth(readOnly) }
        val denied: HttpResponse = client.post("/events") { bearerAuth(readOnly) }
        val created: HttpResponse = client.post("/events") { bearerAuth(readWrite) }
        val challenge: HttpAuthHeader.Parameterized = challengeOf(denied)

        assertEquals(HttpStatusCode.OK, list.status)
        assertEquals("events of milan: standup, review", list.bodyAsText())
        assertEquals(HttpStatusCode.Forbidden, denied.status)
        assertEquals("missing calendar.write", denied.bodyAsText())
        assertEquals("insufficient_scope", challenge.parameter("error"))
        assertEquals("calendar.write", challenge.parameter("scope"))
        assertEquals(HttpStatusCode.OK, created.status)
        assertEquals("ana created an event", created.bodyAsText())
    }

    private companion object {
        const val SECRET: String = "calendar-signing-secret"
        const val ISSUER: String = "https://calendar.example"
        const val AUDIENCE: String = "calendar-api"
        const val REALM: String = "calendar"
        const val ISSUED_AT_SECONDS: Long = 1_700_000_000
        const val EXPIRES_AT_SECONDS: Long = 4_100_000_000
        const val EXPIRED_AT_SECONDS: Long = 1_000_000_000
        const val NOT_YET_VALID_SECONDS: Long = 4_000_000_000
    }
}
