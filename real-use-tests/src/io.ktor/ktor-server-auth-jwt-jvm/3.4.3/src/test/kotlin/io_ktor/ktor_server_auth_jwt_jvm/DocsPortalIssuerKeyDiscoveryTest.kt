/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_auth_jwt_jvm

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Issuer-URL signing-key discovery, where a documentation portal names only its
 * single sign-on issuer and lets the JWT provider find the issuer's keys itself.
 *
 * The portal configures `verifier(issuer)`, so the provider derives the
 * issuer's `/.well-known/jwks.json` address, fetches it over HTTP from an
 * embedded CIO identity provider, and caches the keys by `kid`. The scenario
 * shows the key set fetched once and then reused, a rotated signing key picked
 * up the first time a token names it, tokens for another issuer or audience or
 * signed by an unpublished key refused with the default bearer challenge, and
 * an identity-provider outage refusing tokens until the key set is reachable.
 */
public class DocsPortalIssuerKeyDiscoveryTest {
    private val currentKeys: SigningKey = SigningKey("sso-2026-01", generateRsaKeys())
    private val rotatedKeys: SigningKey = SigningKey("sso-2026-07", generateRsaKeys())
    private val published: AtomicReference<List<SigningKey>> = AtomicReference(listOf(currentKeys))
    private val keySetAvailable: AtomicBoolean = AtomicBoolean(true)
    private val keySetFetches: AtomicInteger = AtomicInteger()
    private lateinit var identityProvider: EmbeddedServer<*, *>
    private lateinit var issuer: String

    /** A signing key pair the identity provider publishes under [kid]. */
    private class SigningKey(val kid: String, val keys: KeyPair) {
        val algorithm: Algorithm = Algorithm.RSA256(keys.public as RSAPublicKey, keys.private as RSAPrivateKey)
    }

    /** Starts the identity provider whose base URL is the portal's [issuer]. */
    @BeforeEach
    fun startIdentityProvider() {
        identityProvider = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/.well-known/jwks.json") {
                    keySetFetches.incrementAndGet()
                    if (keySetAvailable.get()) {
                        call.respondText(jwks(published.get()), ContentType.Application.Json)
                    } else {
                        call.respondText("maintenance", status = HttpStatusCode.ServiceUnavailable)
                    }
                }
            }
        }.start(wait = false)
        val port: Int = runBlocking {
            identityProvider.engine.resolvedConnectors().first().port
        }
        issuer = "http://127.0.0.1:$port"
    }

    @AfterEach
    fun stopIdentityProvider() {
        identityProvider.stop(0, 0)
    }

    /** The documentation portal: the `sso` provider and the pages it protects. */
    private fun ApplicationTestBuilder.docsPortal() {
        val trustedIssuer: String = issuer
        application {
            install(Authentication) {
                jwt("sso") {
                    realm = REALM
                    verifier(trustedIssuer) {
                        withIssuer(trustedIssuer)
                        withAudience(AUDIENCE)
                    }
                    validate { credential ->
                        if (credential.payload.getClaim("email").isMissing) null else JWTPrincipal(credential.payload)
                    }
                }
            }
            routing {
                authenticate("sso") {
                    get("/handbook") {
                        val reader: JWTPrincipal = call.principal<JWTPrincipal>()!!
                        call.respondText("handbook for ${reader["email"]} (${reader.subject})")
                    }
                }
            }
        }
    }

    /** A token the identity provider issues to [user], signed with [key] and announcing [kid]. */
    private fun token(
        user: String,
        key: SigningKey = currentKeys,
        kid: String = key.kid,
        tokenIssuer: String = issuer,
        audience: String = AUDIENCE,
    ): String = JWT.create()
        .withKeyId(kid)
        .withIssuer(tokenIssuer)
        .withAudience(audience)
        .withSubject("uid-$user")
        .withClaim("email", "$user@example.com")
        .withExpiresAt(Date(System.currentTimeMillis() + LIFETIME_MILLIS))
        .sign(key.algorithm)

    private suspend fun ApplicationTestBuilder.readHandbook(token: String): HttpResponse =
        client.get("/handbook") { bearerAuth(token) }

    private fun assertRefused(response: HttpResponse) {
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("Bearer realm=\"$REALM\"", response.headers[HttpHeaders.WWWAuthenticate])
    }

    @Test
    fun keysDiscoveredAtTheIssuerUrlAreFetchedOnceAndReused() = testApplication {
        docsPortal()

        val first: HttpResponse = readHandbook(token("ada"))
        val second: HttpResponse = readHandbook(token("grace"))

        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals("handbook for ada@example.com (uid-ada)", first.bodyAsText())
        assertEquals(HttpStatusCode.OK, second.status)
        assertEquals("handbook for grace@example.com (uid-grace)", second.bodyAsText())
        assertEquals(1, keySetFetches.get())
    }

    @Test
    fun rotatedSigningKeyIsPickedUpTheFirstTimeATokenNamesIt() = testApplication {
        docsPortal()
        assertEquals(HttpStatusCode.OK, readHandbook(token("ada")).status)

        published.set(listOf(currentKeys, rotatedKeys))
        val rotated: HttpResponse = readHandbook(token("linus", rotatedKeys))
        val stillCurrent: HttpResponse = readHandbook(token("ada"))
        val rotatedAgain: HttpResponse = readHandbook(token("margaret", rotatedKeys))

        assertEquals(HttpStatusCode.OK, rotated.status)
        assertEquals("handbook for linus@example.com (uid-linus)", rotated.bodyAsText())
        assertEquals(HttpStatusCode.OK, stillCurrent.status)
        assertEquals(HttpStatusCode.OK, rotatedAgain.status)
        assertEquals(2, keySetFetches.get())
    }

    @Test
    fun tokensTheDiscoveredKeysDoNotVouchForGetTheDefaultBearerChallenge() = testApplication {
        docsPortal()
        val unpublished: SigningKey = SigningKey(currentKeys.kid, generateRsaKeys())

        assertRefused(readHandbook(token("mallory", unpublished)))
        assertRefused(readHandbook(token("ada", kid = "sso-2019-01")))
        assertRefused(readHandbook(token("ada", tokenIssuer = "https://sso.other.example")))
        assertRefused(readHandbook(token("ada", audience = "billing")))
        assertEquals(HttpStatusCode.OK, readHandbook(token("ada")).status)
    }

    @Test
    fun identityProviderOutageRefusesTokensUntilTheKeySetIsReachable() = testApplication {
        docsPortal()
        keySetAvailable.set(false)

        assertRefused(readHandbook(token("ada")))

        keySetAvailable.set(true)
        val recovered: HttpResponse = readHandbook(token("ada"))

        assertEquals(HttpStatusCode.OK, recovered.status)
        assertEquals("handbook for ada@example.com (uid-ada)", recovered.bodyAsText())
        assertEquals(2, keySetFetches.get())
    }

    /** The JWKS document that publishes the public half of every key in [keys]. */
    private fun jwks(keys: List<SigningKey>): String = keys.joinToString(
        separator = ",",
        prefix = """{"keys": [""",
        postfix = "]}",
    ) { key ->
        val public: RSAPublicKey = key.keys.public as RSAPublicKey
        """
            {"kty": "RSA", "kid": "${key.kid}", "use": "sig", "alg": "RS256",
             "n": "${base64Url(public.modulus)}", "e": "${base64Url(public.publicExponent)}"}
        """.trimIndent()
    }

    /** [value] as unsigned big-endian bytes in unpadded base64url. */
    private fun base64Url(value: BigInteger): String {
        val unsigned: ByteArray = value.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(unsigned)
    }

    private companion object {
        const val AUDIENCE: String = "docs-portal"
        const val REALM: String = "docs portal"
        const val LIFETIME_MILLIS: Long = 3_600_000

        fun generateRsaKeys(): KeyPair = KeyPairGenerator.getInstance("RSA")
            .apply { initialize(2048) }
            .generateKeyPair()
    }
}
