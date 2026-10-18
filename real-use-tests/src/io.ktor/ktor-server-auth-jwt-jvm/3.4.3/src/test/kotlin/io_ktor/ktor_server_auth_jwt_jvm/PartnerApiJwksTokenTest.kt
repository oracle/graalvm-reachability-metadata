/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_auth_jwt_jvm

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
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
import java.net.URI
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.Date
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Asymmetric-key verification through a JWKS key set, where a partner API trusts
 * tokens an identity provider signs and looks up each token's public key by `kid`.
 *
 * The identity provider publishes an RSA and an EC P-256 key on its
 * `/.well-known/jwks.json` endpoint, served here by an embedded CIO server. The
 * partner API verifies through a cached [JwkProvider], so tokens signed with
 * either published key pass, while tokens signed by an unpublished key, carrying
 * an unknown `kid`, or naming another issuer get the default bearer challenge.
 */
public class PartnerApiJwksTokenTest {
    private val rsaKeys: KeyPair = KeyPairGenerator.getInstance("RSA")
        .apply { initialize(2048) }
        .generateKeyPair()
    private val ecKeys: KeyPair = KeyPairGenerator.getInstance("EC")
        .apply { initialize(ECGenParameterSpec("secp256r1")) }
        .generateKeyPair()
    private val jwksFetches: AtomicInteger = AtomicInteger()
    private lateinit var identityProvider: EmbeddedServer<*, *>
    private lateinit var jwksUrl: String

    /** Starts the identity provider that publishes [rsaKeys] and [ecKeys] as a JWKS document. */
    @BeforeEach
    fun startIdentityProvider() {
        val document: String = jwks()
        identityProvider = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/.well-known/jwks.json") {
                    jwksFetches.incrementAndGet()
                    call.respondText(document, ContentType.Application.Json)
                }
            }
        }.start(wait = false)
        val port: Int = runBlocking {
            identityProvider.engine.resolvedConnectors().first().port
        }
        jwksUrl = "http://127.0.0.1:$port/.well-known/jwks.json"
    }

    @AfterEach
    fun stopIdentityProvider() {
        identityProvider.stop(0, 0)
    }

    /** The partner API: the `partner-jwks` provider and the order endpoints it protects. */
    private fun ApplicationTestBuilder.partnerApi() {
        val keys: JwkProvider = JwkProviderBuilder(URI(jwksUrl).toURL())
            .cached(10, 1, TimeUnit.HOURS)
            .build()
        application {
            install(Authentication) {
                jwt("partner-jwks") {
                    realm = REALM
                    verifier(keys, ISSUER) {
                        withAudience(AUDIENCE)
                    }
                    validate { credential ->
                        if (credential.payload.getClaim("partner").isMissing) null else JWTPrincipal(credential.payload)
                    }
                }
            }
            routing {
                authenticate("partner-jwks") {
                    get("/orders") {
                        val principal: JWTPrincipal = call.principal<JWTPrincipal>()!!
                        call.respondText("orders for ${principal["partner"]} as ${principal.subject}")
                    }
                }
            }
        }
    }

    /** A token for [partner] that the identity provider signed with [algorithm] under [kid]. */
    private fun token(
        partner: String,
        kid: String,
        algorithm: Algorithm,
        issuer: String = ISSUER,
    ): String = JWT.create()
        .withKeyId(kid)
        .withIssuer(issuer)
        .withAudience(AUDIENCE)
        .withSubject("svc-$partner")
        .withClaim("partner", partner)
        .withExpiresAt(Date(System.currentTimeMillis() + LIFETIME_MILLIS))
        .sign(algorithm)

    private fun rsaSigner(keys: KeyPair = rsaKeys): Algorithm =
        Algorithm.RSA256(keys.public as RSAPublicKey, keys.private as RSAPrivateKey)

    private fun ecSigner(): Algorithm = Algorithm.ECDSA256(ecKeys.public as ECPublicKey, ecKeys.private as ECPrivateKey)

    @Test
    fun tokensSignedWithEitherPublishedKeyAreAccepted() = testApplication {
        partnerApi()

        val rsa: HttpResponse = client.get("/orders") { bearerAuth(token("acme", RSA_KID, rsaSigner())) }
        val ec: HttpResponse = client.get("/orders") { bearerAuth(token("globex", EC_KID, ecSigner())) }

        assertEquals(HttpStatusCode.OK, rsa.status)
        assertEquals("orders for acme as svc-acme", rsa.bodyAsText())
        assertEquals(HttpStatusCode.OK, ec.status)
        assertEquals("orders for globex as svc-globex", ec.bodyAsText())
    }

    @Test
    fun publishedKeysAreFetchedOnceAndThenServedFromTheCache() = testApplication {
        partnerApi()

        repeat(3) {
            val response: HttpResponse = client.get("/orders") { bearerAuth(token("acme", RSA_KID, rsaSigner())) }
            assertEquals(HttpStatusCode.OK, response.status)
        }

        assertEquals(1, jwksFetches.get())
    }

    @Test
    fun tokensTheKeySetCannotVerifyGetTheDefaultBearerChallenge() = testApplication {
        partnerApi()
        val impostorKeys: KeyPair = KeyPairGenerator.getInstance("RSA")
            .apply { initialize(2048) }
            .generateKeyPair()
        val rejected: List<String> = listOf(
            token("acme", RSA_KID, rsaSigner(impostorKeys)),
            token("acme", "retired-2024", rsaSigner()),
            token("acme", RSA_KID, rsaSigner(), issuer = "https://other-idp.example"),
        )

        for (issued in rejected) {
            val response: HttpResponse = client.get("/orders") { bearerAuth(issued) }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals("Bearer realm=\"$REALM\"", response.headers[HttpHeaders.WWWAuthenticate])
        }
    }

    /** The JWKS document that publishes the RSA and EC public keys under their key IDs. */
    private fun jwks(): String {
        val rsa: RSAPublicKey = rsaKeys.public as RSAPublicKey
        val ec: ECPublicKey = ecKeys.public as ECPublicKey
        return """
            {"keys": [
              {"kty": "RSA", "kid": "$RSA_KID", "use": "sig", "alg": "RS256",
               "n": "${base64Url(rsa.modulus)}", "e": "${base64Url(rsa.publicExponent)}"},
              {"kty": "EC", "kid": "$EC_KID", "use": "sig", "alg": "ES256", "crv": "P-256",
               "x": "${base64Url(ec.w.affineX, EC_COORDINATE_BYTES)}",
               "y": "${base64Url(ec.w.affineY, EC_COORDINATE_BYTES)}"}
            ]}
        """.trimIndent()
    }

    /** [value] as unsigned big-endian bytes, left-padded to [length], in unpadded base64url. */
    private fun base64Url(value: BigInteger, length: Int = 0): String {
        val unsigned: ByteArray = value.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        val padded: ByteArray = ByteArray(maxOf(0, length - unsigned.size)) + unsigned
        return Base64.getUrlEncoder().withoutPadding().encodeToString(padded)
    }

    private companion object {
        const val ISSUER: String = "https://idp.example"
        const val AUDIENCE: String = "partner-api"
        const val REALM: String = "partner api"
        const val RSA_KID: String = "rsa-2026"
        const val EC_KID: String = "ec-2026"
        const val EC_COORDINATE_BYTES: Int = 32
        const val LIFETIME_MILLIS: Long = 3_600_000
    }
}
