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
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Signature-algorithm selection from JWKS key entries, where a firmware update
 * service verifies device tokens against a key set the operator ships as a
 * `jwks.json` file next to the deployment.
 *
 * The service configures `verifier(jwkProvider) { ... }` without pinning an
 * issuer in the provider call, so the issuer allow-list and audience come from
 * the configure block. The JWT provider derives each key's algorithm from its
 * JWK entry: an explicit `alg` selects RS384, RS512, ES384 or ES512, while a
 * legacy entry without `alg` falls back to RS256 for RSA keys and to the curve
 * size for EC keys. Tokens whose header algorithm disagrees with the key's
 * algorithm get the default bearer challenge.
 */
public class FirmwareUpdateKeyAlgorithmTest {
    @TempDir
    lateinit var deployment: Path

    /** The update service: the `devices` provider over the key set file and the channel endpoint. */
    private fun ApplicationTestBuilder.updateService() {
        val keySetFile: Path = deployment.resolve("jwks.json")
        Files.writeString(keySetFile, jwks())
        val keys: JwkProvider = JwkProviderBuilder(keySetFile.toUri().toURL())
            .cached(16, 1, TimeUnit.HOURS)
            .rateLimited(false)
            .build()
        application {
            install(Authentication) {
                jwt("devices") {
                    realm = REALM
                    verifier(keys) {
                        withIssuer(FACTORY_ISSUER, FIELD_ISSUER)
                        withAudience(AUDIENCE)
                    }
                    validate { credential ->
                        if (credential["channel"] == null) null else JWTPrincipal(credential.payload)
                    }
                }
            }
            routing {
                authenticate("devices") {
                    get("/firmware/latest") {
                        val device: JWTPrincipal = call.principal<JWTPrincipal>()!!
                        call.respondText("${device.subject} on ${device["channel"]} from ${device.issuer}")
                    }
                }
            }
        }
    }

    /** A token for [device] signed by [algorithm] under [kid]. */
    private fun token(
        device: String,
        kid: String,
        algorithm: Algorithm,
        issuer: String = FACTORY_ISSUER,
    ): String = JWT.create()
        .withKeyId(kid)
        .withIssuer(issuer)
        .withAudience(AUDIENCE)
        .withSubject(device)
        .withClaim("channel", "stable")
        .withExpiresAt(Date(System.currentTimeMillis() + LIFETIME_MILLIS))
        .sign(algorithm)

    private suspend fun ApplicationTestBuilder.latestFirmware(token: String): HttpResponse =
        client.get("/firmware/latest") { bearerAuth(token) }

    private fun assertChallenged(response: HttpResponse) {
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("Bearer realm=\"$REALM\"", response.headers[HttpHeaders.WWWAuthenticate])
    }

    @Test
    fun keysWithAnExplicitAlgVerifyTokensSignedWithThatAlgorithm() = testApplication {
        updateService()
        val signed: Map<String, String> = mapOf(
            "gateway-1" to token("gateway-1", OPS_RS384_KID, rsaSigner(OPS_RS384, 384)),
            "gateway-2" to token("gateway-2", OPS_RS512_KID, rsaSigner(OPS_RS512, 512)),
            "sensor-1" to token("sensor-1", EDGE_ES384_KID, ecSigner(EDGE_ES384, 384)),
            "sensor-2" to token("sensor-2", EDGE_ES512_KID, ecSigner(EDGE_ES512, 512)),
        )

        for ((device, issued) in signed) {
            val response: HttpResponse = latestFirmware(issued)
            assertEquals(HttpStatusCode.OK, response.status, device)
            assertEquals("$device on stable from $FACTORY_ISSUER", response.bodyAsText())
        }
    }

    @Test
    fun keysWithoutAlgFallBackToRs256AndToTheCurveSize() = testApplication {
        updateService()

        val rsa: HttpResponse = latestFirmware(token("meter-7", LEGACY_RSA_KID, rsaSigner(LEGACY_RSA, 256)))
        val ec: HttpResponse = latestFirmware(token("meter-8", LEGACY_EC_KID, ecSigner(LEGACY_EC, 384)))

        assertEquals(HttpStatusCode.OK, rsa.status)
        assertEquals("meter-7 on stable from $FACTORY_ISSUER", rsa.bodyAsText())
        assertEquals(HttpStatusCode.OK, ec.status)
        assertEquals("meter-8 on stable from $FACTORY_ISSUER", ec.bodyAsText())
    }

    @Test
    fun tokensSignedWithAnotherAlgorithmThanTheKeyDeclaresAreRefused() = testApplication {
        updateService()
        val mismatched: List<String> = listOf(
            token("gateway-1", OPS_RS384_KID, rsaSigner(OPS_RS384, 512)),
            token("gateway-2", OPS_RS512_KID, rsaSigner(OPS_RS512, 256)),
            token("meter-7", LEGACY_RSA_KID, rsaSigner(LEGACY_RSA, 384)),
            token("sensor-1", EDGE_ES384_KID, rsaSigner(OPS_RS384, 384)),
        )

        for (issued in mismatched) {
            assertChallenged(latestFirmware(issued))
        }
    }

    @Test
    fun theConfigureBlockAllowsListedIssuersOnly() = testApplication {
        updateService()
        val signer: Algorithm = ecSigner(EDGE_ES384, 384)

        val field: HttpResponse = latestFirmware(token("sensor-1", EDGE_ES384_KID, signer, issuer = FIELD_ISSUER))
        val foreign: HttpResponse = latestFirmware(
            token("sensor-1", EDGE_ES384_KID, signer, issuer = "https://lab.example"),
        )

        assertEquals(HttpStatusCode.OK, field.status)
        assertEquals("sensor-1 on stable from $FIELD_ISSUER", field.bodyAsText())
        assertChallenged(foreign)
    }

    /** The operator's key set: explicit-`alg` entries first, then the legacy entries without `alg`. */
    private fun jwks(): String = listOf(
        rsaEntry(OPS_RS384_KID, OPS_RS384, "RS384"),
        rsaEntry(OPS_RS512_KID, OPS_RS512, "RS512"),
        ecEntry(EDGE_ES384_KID, EDGE_ES384, "P-384", "ES384"),
        ecEntry(EDGE_ES512_KID, EDGE_ES512, "P-521", "ES512"),
        rsaEntry(LEGACY_RSA_KID, LEGACY_RSA, null),
        ecEntry(LEGACY_EC_KID, LEGACY_EC, "P-384", null),
    ).joinToString(separator = ",\n", prefix = "{\"keys\": [\n", postfix = "\n]}")

    private fun rsaEntry(kid: String, keys: KeyPair, alg: String?): String {
        val key: RSAPublicKey = keys.public as RSAPublicKey
        return """
            {"kty": "RSA", "kid": "$kid", "use": "sig",${algMember(alg)}
             "n": "${base64Url(key.modulus)}", "e": "${base64Url(key.publicExponent)}"}
        """.trimIndent()
    }

    private fun ecEntry(kid: String, keys: KeyPair, curve: String, alg: String?): String {
        val key: ECPublicKey = keys.public as ECPublicKey
        val size: Int = (key.params.order.bitLength() + 7) / 8
        return """
            {"kty": "EC", "kid": "$kid", "use": "sig", "crv": "$curve",${algMember(alg)}
             "x": "${base64Url(key.w.affineX, size)}", "y": "${base64Url(key.w.affineY, size)}"}
        """.trimIndent()
    }

    /** [keys] signing with RSASSA-PKCS1 over SHA-[bits]. */
    private fun rsaSigner(keys: KeyPair, bits: Int): Algorithm {
        val public: RSAPublicKey = keys.public as RSAPublicKey
        val private: RSAPrivateKey = keys.private as RSAPrivateKey
        return when (bits) {
            256 -> Algorithm.RSA256(public, private)
            384 -> Algorithm.RSA384(public, private)
            else -> Algorithm.RSA512(public, private)
        }
    }

    /** [keys] signing with ECDSA over SHA-[bits]. */
    private fun ecSigner(keys: KeyPair, bits: Int): Algorithm {
        val public: ECPublicKey = keys.public as ECPublicKey
        val private: ECPrivateKey = keys.private as ECPrivateKey
        return when (bits) {
            256 -> Algorithm.ECDSA256(public, private)
            384 -> Algorithm.ECDSA384(public, private)
            else -> Algorithm.ECDSA512(public, private)
        }
    }

    private fun algMember(alg: String?): String = if (alg == null) "" else " \"alg\": \"$alg\","

    /** [value] as unsigned big-endian bytes, left-padded to [length], in unpadded base64url. */
    private fun base64Url(value: BigInteger, length: Int = 0): String {
        val unsigned: ByteArray = value.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        val padded: ByteArray = ByteArray(maxOf(0, length - unsigned.size)) + unsigned
        return Base64.getUrlEncoder().withoutPadding().encodeToString(padded)
    }

    private companion object {
        const val REALM: String = "firmware updates"
        const val AUDIENCE: String = "firmware-update"
        const val FACTORY_ISSUER: String = "https://factory.example"
        const val FIELD_ISSUER: String = "https://field.example"
        const val OPS_RS384_KID: String = "ops-rs384"
        const val OPS_RS512_KID: String = "ops-rs512"
        const val EDGE_ES384_KID: String = "edge-es384"
        const val EDGE_ES512_KID: String = "edge-es512"
        const val LEGACY_RSA_KID: String = "legacy-rsa"
        const val LEGACY_EC_KID: String = "legacy-ec"
        const val LIFETIME_MILLIS: Long = 3_600_000

        val OPS_RS384: KeyPair = rsaKeys()
        val OPS_RS512: KeyPair = rsaKeys()
        val LEGACY_RSA: KeyPair = rsaKeys()
        val EDGE_ES384: KeyPair = ecKeys("secp384r1")
        val EDGE_ES512: KeyPair = ecKeys("secp521r1")
        val LEGACY_EC: KeyPair = ecKeys("secp384r1")

        fun rsaKeys(): KeyPair = KeyPairGenerator.getInstance("RSA")
            .apply { initialize(2048) }
            .generateKeyPair()

        fun ecKeys(curve: String): KeyPair = KeyPairGenerator.getInstance("EC")
            .apply { initialize(ECGenParameterSpec(curve)) }
            .generateKeyPair()
    }
}
