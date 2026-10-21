/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_auth_jvm

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.auth.parseAuthorizationHeader
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.DigestCredential
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.digest
import io.ktor.server.auth.principal
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.util.NonceManager
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * HTTP Digest authentication with MD5 response hashes and an application-owned single-use nonce manager.
 *
 * A ledger archive stores only `MD5(user:realm:password)` digests, challenges every anonymous
 * request with a fresh nonce, and accepts each nonce exactly once, so a captured `Authorization`
 * header cannot be replayed. Clients answer the challenge both with `qop=auth` and in the legacy
 * form without `qop`; wrong passwords, unknown users, and a header signed for another method are
 * all refused with a new challenge.
 */
class DigestAuthSingleUseNonceTest {

    /** The principal the archive attaches to an authenticated call. */
    data class Archivist(val name: String, val realm: String)

    /** Issues numbered nonces and forgets each one the first time a client presents it. */
    class SingleUseNonceManager : NonceManager {
        private val counter: AtomicInteger = AtomicInteger()
        private val outstanding: MutableSet<String> = ConcurrentHashMap.newKeySet()

        override suspend fun newNonce(): String {
            val nonce: String = "ledger-nonce-%04d".format(counter.incrementAndGet())
            outstanding.add(nonce)
            return nonce
        }

        override suspend fun verifyNonce(nonce: String): Boolean = outstanding.remove(nonce)

        fun isOutstanding(nonce: String): Boolean = nonce in outstanding
    }

    private fun md5Hex(text: String): String =
        MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.ISO_8859_1))
            .joinToString("") { byte: Byte -> "%02x".format(byte) }

    private fun Application.archiveModule(nonces: SingleUseNonceManager) {
        val passwordDigests: Map<String, ByteArray> = mapOf(
            "ada" to MessageDigest.getInstance("MD5").digest("ada:$REALM:analytical-engine".toByteArray()),
            "charles" to MessageDigest.getInstance("MD5").digest("charles:$REALM:difference-engine".toByteArray()),
        )
        install(Authentication) {
            digest("ledger") {
                realm = REALM
                algorithmName = "MD5"
                nonceManager = nonces
                digestProvider { userName: String, _: String -> passwordDigests[userName] }
                validate { credentials: DigestCredential -> Archivist(credentials.userName, credentials.realm) }
            }
        }
        routing {
            authenticate("ledger") {
                get("/ledger/1843") {
                    val archivist: Archivist = call.principal<Archivist>()!!
                    call.respondText("ledger 1843 opened by ${archivist.name} in ${archivist.realm}")
                }
                post("/ledger/1843/notes") {
                    val archivist: Archivist = call.principal<Archivist>()!!
                    call.respondText("note filed by ${archivist.name}")
                }
            }
        }
    }

    /** Requests the resource anonymously and returns the parsed Digest challenge. */
    private suspend fun challenge(client: HttpClient): HttpAuthHeader.Parameterized {
        val anonymous: HttpResponse = client.get(LEDGER)
        assertEquals(HttpStatusCode.Unauthorized, anonymous.status)
        return digestChallengeOf(anonymous)
    }

    private fun digestChallengeOf(response: HttpResponse): HttpAuthHeader.Parameterized {
        val header: HttpAuthHeader = parseAuthorizationHeader(response.headers[HttpHeaders.WWWAuthenticate]!!)!!
        assertTrue(header is HttpAuthHeader.Parameterized, "the challenge carries parameters: $header")
        assertEquals("Digest", header.authScheme)
        return header as HttpAuthHeader.Parameterized
    }

    /** Answers a challenge the way an RFC 7616 client does, with `qop=auth` when [cnonce] is given. */
    private fun digestAuthorization(
        challenge: HttpAuthHeader.Parameterized,
        method: HttpMethod,
        uri: String,
        user: String,
        password: String,
        cnonce: String? = null,
    ): String {
        val realm: String = challenge.parameter("realm")!!
        val nonce: String = challenge.parameter("nonce")!!
        val ha1: String = md5Hex("$user:$realm:$password")
        val ha2: String = md5Hex("${method.value}:$uri")
        val prefix: String = "Digest username=\"$user\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$uri\", algorithm=MD5"
        return if (cnonce == null) {
            "$prefix, response=\"${md5Hex("$ha1:$nonce:$ha2")}\""
        } else {
            val nc: String = "00000001"
            val response: String = md5Hex("$ha1:$nonce:$nc:$cnonce:auth:$ha2")
            "$prefix, qop=auth, nc=$nc, cnonce=\"$cnonce\", response=\"$response\""
        }
    }

    @Test
    fun digestCredentialsOpenTheLedgerOncePerNonce() = testApplication {
        val nonces: SingleUseNonceManager = SingleUseNonceManager()
        application { archiveModule(nonces) }

        val first: HttpAuthHeader.Parameterized = challenge(client)
        assertEquals(REALM, first.parameter("realm"))
        assertEquals("MD5", first.parameter("algorithm"))
        val firstNonce: String = first.parameter("nonce")!!
        assertTrue(nonces.isOutstanding(firstNonce), "the challenge nonce came from the application's manager")

        val adaHeader: String = digestAuthorization(
            first, HttpMethod.Get, LEDGER, "ada", "analytical-engine", cnonce = "0a4f113b",
        )
        val ada: HttpResponse = client.get(LEDGER) { header(HttpHeaders.Authorization, adaHeader) }
        assertEquals(HttpStatusCode.OK, ada.status)
        assertEquals("ledger 1843 opened by ada in $REALM", ada.bodyAsText())
        assertFalse(nonces.isOutstanding(firstNonce), "a successful login spends its nonce")

        val replay: HttpResponse = client.get(LEDGER) { header(HttpHeaders.Authorization, adaHeader) }
        assertEquals(HttpStatusCode.Unauthorized, replay.status, "a replayed header is refused")
        val fresh: HttpAuthHeader.Parameterized = digestChallengeOf(replay)
        assertNotEquals(firstNonce, fresh.parameter("nonce"), "the refusal issues a new nonce")

        val charles: HttpResponse = client.post(NOTES) {
            header(
                HttpHeaders.Authorization,
                digestAuthorization(fresh, HttpMethod.Post, NOTES, "charles", "difference-engine"),
            )
        }
        assertEquals(HttpStatusCode.OK, charles.status, "the legacy form without qop is still accepted")
        assertEquals("note filed by charles", charles.bodyAsText())

        val wrongPassword: String = digestAuthorization(
            challenge(client), HttpMethod.Get, LEDGER, "ada", "difference-engine", cnonce = "77aa",
        )
        val unknownUser: String = digestAuthorization(
            challenge(client), HttpMethod.Get, LEDGER, "grace", "cobol", cnonce = "88bb",
        )
        val signedForGet: String = digestAuthorization(
            challenge(client), HttpMethod.Get, NOTES, "charles", "difference-engine",
        )
        val refusals: List<HttpResponse> = listOf(
            client.get(LEDGER) { header(HttpHeaders.Authorization, wrongPassword) },
            client.get(LEDGER) { header(HttpHeaders.Authorization, unknownUser) },
            client.post(NOTES) { header(HttpHeaders.Authorization, signedForGet) },
        )
        for (refused: HttpResponse in refusals) {
            assertEquals(HttpStatusCode.Unauthorized, refused.status)
            assertEquals(REALM, digestChallengeOf(refused).parameter("realm"))
        }
    }

    private companion object {
        const val REALM: String = "Ledger Archive"
        const val LEDGER: String = "/ledger/1843"
        const val NOTES: String = "/ledger/1843/notes"
    }
}
