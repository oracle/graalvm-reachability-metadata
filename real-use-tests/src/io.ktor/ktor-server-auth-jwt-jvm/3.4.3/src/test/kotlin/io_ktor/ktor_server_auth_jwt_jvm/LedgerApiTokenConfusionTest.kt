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
import com.auth0.jwt.exceptions.InvalidClaimException
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.JWTVerifier
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
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.Date
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Token-confusion defenses on the JWT provider, where a ledger API accepts only
 * RS256-signed access tokens and refuses every other token its identity provider
 * or an attacker could present.
 *
 * The provider verifies through an application [JWTVerifier] that wraps the
 * auth0 verifier and also enforces the RFC 9068 `typ: at+jwt` JOSE header, so an
 * ID token signed with the same key is refused. Unsigned `alg: none` tokens,
 * HS256 tokens keyed with the published RSA public key, tampered payloads and
 * malformed tokens all get the default bearer challenge.
 */
public class LedgerApiTokenConfusionTest {
    private val signingKeys: KeyPair = KeyPairGenerator.getInstance("RSA")
        .apply { initialize(2048) }
        .generateKeyPair()
    private val publicKey: RSAPublicKey = signingKeys.public as RSAPublicKey
    private val signer: Algorithm = Algorithm.RSA256(publicKey, signingKeys.private as RSAPrivateKey)

    /** Verifies with [delegate], then refuses tokens whose `typ` header is not an access token type. */
    private class AccessTokenTypeVerifier(private val delegate: JWTVerifier) : JWTVerifier {
        override fun verify(token: String): DecodedJWT = verify(JWT.decode(token))

        override fun verify(jwt: DecodedJWT): DecodedJWT {
            val verified: DecodedJWT = delegate.verify(jwt)
            val type: String? = verified.type
            if (ACCESS_TOKEN_TYPES.none { it.equals(type, ignoreCase = true) }) {
                throw InvalidClaimException("The token type '$type' is not an access token type.")
            }
            return verified
        }
    }

    /** The ledger API: the `ledger-jwt` provider and the entries endpoint it protects. */
    private fun ApplicationTestBuilder.ledgerApi() {
        val verifier: JWTVerifier = AccessTokenTypeVerifier(
            JWT.require(Algorithm.RSA256(publicKey, null))
                .withIssuer(ISSUER)
                .withAudience(AUDIENCE)
                .build(),
        )
        application {
            install(Authentication) {
                jwt("ledger-jwt") {
                    realm = REALM
                    verifier(verifier)
                    validate { credential ->
                        if (credential.subject.isNullOrBlank()) null else JWTPrincipal(credential.payload)
                    }
                }
            }
            routing {
                authenticate("ledger-jwt") {
                    get("/ledger/entries") {
                        val principal: JWTPrincipal = call.principal<JWTPrincipal>()!!
                        call.respondText("entries for ${principal.subject} with ${principal["scope"]}")
                    }
                }
            }
        }
    }

    /** Claims every ledger token carries; callers add the header and sign it. */
    private fun claims(subject: String = "acct-204"): JWTCreator.Builder = JWT.create()
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .withSubject(subject)
        .withClaim("scope", "ledger:read")
        .withExpiresAt(Date(System.currentTimeMillis() + LIFETIME_MILLIS))

    private fun accessToken(type: String = "at+jwt", subject: String = "acct-204"): String =
        claims(subject).withHeader(mapOf("typ" to type)).sign(signer)

    private suspend fun ApplicationTestBuilder.entries(token: String): HttpResponse =
        client.get("/ledger/entries") { bearerAuth(token) }

    private fun assertChallenged(response: HttpResponse) {
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("Bearer realm=$REALM", response.headers[HttpHeaders.WWWAuthenticate])
    }

    @Test
    fun accessTokensOfEitherRegisteredTypeReadTheLedger() = testApplication {
        ledgerApi()

        val short: HttpResponse = entries(accessToken())
        val mediaType: HttpResponse = entries(accessToken(type = "application/at+jwt", subject = "acct-311"))

        assertEquals(HttpStatusCode.OK, short.status)
        assertEquals("entries for acct-204 with ledger:read", short.bodyAsText())
        assertEquals(HttpStatusCode.OK, mediaType.status)
        assertEquals("entries for acct-311 with ledger:read", mediaType.bodyAsText())
    }

    @Test
    fun idTokenSignedWithTheSameKeyIsRefused() = testApplication {
        ledgerApi()

        assertChallenged(entries(claims().sign(signer)))
        assertChallenged(entries(accessToken(type = "id+jwt")))
    }

    @Test
    fun unsignedAndAlgorithmConfusedTokensAreRefused() = testApplication {
        ledgerApi()
        val unsigned: String = claims().withHeader(mapOf("typ" to "at+jwt")).sign(Algorithm.none())
        val confused: String = claims()
            .withHeader(mapOf("typ" to "at+jwt"))
            .sign(Algorithm.HMAC256(publicKey.encoded))

        assertChallenged(entries(unsigned))
        assertChallenged(entries(confused))
    }

    @Test
    fun tamperedAndMalformedTokensAreRefused() = testApplication {
        ledgerApi()
        val (header: String, _, signature: String) = accessToken().split('.')
        val forgedPayload: String = Base64.getUrlEncoder().withoutPadding().encodeToString(
            """{"iss":"$ISSUER","aud":"$AUDIENCE","sub":"acct-001","scope":"ledger:admin"}""".toByteArray(),
        )
        val malformed: List<String> = listOf(
            "$header.$forgedPayload.$signature",
            "$header.$forgedPayload",
            "not-a-token",
        )

        for (token in malformed) {
            assertChallenged(entries(token))
        }
    }

    @Test
    fun requestWithoutATokenGetsTheChallenge() = testApplication {
        ledgerApi()

        assertChallenged(client.get("/ledger/entries"))
    }

    private companion object {
        const val ISSUER: String = "https://id.ledger.example"
        const val AUDIENCE: String = "ledger-api"
        const val REALM: String = "ledger"
        const val LIFETIME_MILLIS: Long = 10 * 60 * 1000L
        val ACCESS_TOKEN_TYPES: List<String> = listOf("at+jwt", "application/at+jwt")
    }
}
