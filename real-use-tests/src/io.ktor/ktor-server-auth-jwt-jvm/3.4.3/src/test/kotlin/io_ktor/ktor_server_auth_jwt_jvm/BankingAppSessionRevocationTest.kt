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
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Server-side session checks inside `validate`, where a mobile banking API
 * resolves each verified token against its account store before trusting it.
 *
 * The suspending `validate` block reads the calling device from the request,
 * rejects tokens bound to another device, revoked token ids and disabled
 * accounts, and maps the rest to an application session; logging out revokes
 * only the token used for it. When the account store itself fails, the
 * provider reports an authentication error instead of the bearer challenge.
 */
public class BankingAppSessionRevocationTest {
    /** One customer of the bank, as the account store keeps it. */
    private data class Account(val id: String, val name: String, val balance: String, val disabled: Boolean)

    /** The session `validate` derives from a verified token and the account behind it. */
    private data class MobileSession(val account: Account, val tokenId: String, val device: String)

    /** Raised when the account store cannot be reached. */
    private class StoreUnavailableException : RuntimeException("account store unavailable")

    /** The bank's account and revocation store, read off the request thread like a database. */
    private class AccountStore {
        private val accounts: Map<String, Account> = mapOf(
            "ana" to Account("ana", "Ana Lima", "120.50 EUR", disabled = false),
            "milan" to Account("milan", "Milan Novak", "8.00 EUR", disabled = true),
        )
        private val revokedTokenIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

        @Volatile
        var available: Boolean = true

        suspend fun find(id: String): Account? = withContext(Dispatchers.IO) {
            if (!available) {
                throw StoreUnavailableException()
            }
            accounts[id]
        }

        suspend fun isRevoked(tokenId: String): Boolean = withContext(Dispatchers.IO) { tokenId in revokedTokenIds }

        suspend fun revoke(tokenId: String) {
            withContext(Dispatchers.IO) { revokedTokenIds.add(tokenId) }
        }
    }

    private val store: AccountStore = AccountStore()

    /** The banking API: the `mobile` provider and the account routes it protects. */
    private fun ApplicationTestBuilder.bank() {
        application {
            install(Authentication) {
                jwt("mobile") {
                    realm = REALM
                    verifier(
                        JWT.require(Algorithm.HMAC256(SECRET))
                            .withIssuer(ISSUER)
                            .withAudience(AUDIENCE)
                            .build(),
                    )
                    validate { credential ->
                        val tokenId: String = credential.jwtId ?: return@validate null
                        val device: String = request.headers[DEVICE_HEADER] ?: return@validate null
                        if (credential["device"] != device || store.isRevoked(tokenId)) {
                            return@validate null
                        }
                        val account: Account? = store.find(credential.subject!!)
                        if (account == null || account.disabled) null else MobileSession(account, tokenId, device)
                    }
                }
            }
            routing {
                authenticate("mobile") {
                    get("/balance") {
                        val session: MobileSession = call.principal<MobileSession>()!!
                        call.respondText("${session.account.name} on ${session.device}: ${session.account.balance}")
                    }
                    post("/logout") {
                        val session: MobileSession = call.principal<MobileSession>()!!
                        store.revoke(session.tokenId)
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }
        }
    }

    /** A token the bank issued for [subject] on [device] as [tokenId], before [customize] adjusts it. */
    private fun token(
        subject: String,
        tokenId: String,
        device: String = PHONE,
        customize: JWTCreator.Builder.() -> Unit = {},
    ): String {
        val builder: JWTCreator.Builder = JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withSubject(subject)
            .withJWTId(tokenId)
            .withClaim("device", device)
            .withExpiresAt(Date(System.currentTimeMillis() + LIFETIME_MILLIS))
        builder.customize()
        return builder.sign(Algorithm.HMAC256(SECRET))
    }

    private suspend fun ApplicationTestBuilder.balance(issued: String, device: String? = PHONE): HttpResponse =
        client.get("/balance") {
            bearerAuth(issued)
            if (device != null) {
                header(DEVICE_HEADER, device)
            }
        }

    @Test
    fun tokenFromItsOwnDeviceReadsTheBalance() = testApplication {
        bank()

        val response: HttpResponse = balance(token("ana", "session-1"))

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Ana Lima on $PHONE: 120.50 EUR", response.bodyAsText())
    }

    @Test
    fun tokenReplayedFromAnotherDeviceIsRejected() = testApplication {
        bank()
        val issued: String = token("ana", "session-1")

        assertChallenged(balance(issued, device = "stolen-laptop"))
        assertChallenged(balance(issued, device = null))
    }

    @Test
    fun logoutRevokesOnlyTheSessionItWasCalledWith() = testApplication {
        bank()
        val phoneSession: String = token("ana", "session-phone")
        val tabletSession: String = token("ana", "session-tablet", device = TABLET)

        val logout: HttpResponse = client.post("/logout") {
            bearerAuth(phoneSession)
            header(DEVICE_HEADER, PHONE)
        }

        assertEquals(HttpStatusCode.NoContent, logout.status)
        assertChallenged(balance(phoneSession))
        val tablet: HttpResponse = balance(tabletSession, device = TABLET)
        assertEquals(HttpStatusCode.OK, tablet.status)
        assertEquals("Ana Lima on $TABLET: 120.50 EUR", tablet.bodyAsText())
    }

    @Test
    fun tokensForUnusableAccountsAreRejected() = testApplication {
        bank()
        val rejected: List<String> = listOf(
            token("milan", "session-2"),
            token("ghost", "session-3"),
            JWT.create()
                .withIssuer(ISSUER)
                .withAudience(AUDIENCE)
                .withSubject("ana")
                .withClaim("device", PHONE)
                .withExpiresAt(Date(System.currentTimeMillis() + LIFETIME_MILLIS))
                .sign(Algorithm.HMAC256(SECRET)),
        )

        for (issued in rejected) {
            assertChallenged(balance(issued))
        }
    }

    @Test
    fun accountStoreOutageIsAnAuthenticationErrorNotAChallenge() = testApplication {
        bank()
        store.available = false

        val response: HttpResponse = balance(token("ana", "session-1"))

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertNull(response.headers[HttpHeaders.WWWAuthenticate])
    }

    private fun assertChallenged(response: HttpResponse) {
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("Bearer realm=\"$REALM\"", response.headers[HttpHeaders.WWWAuthenticate])
    }

    private companion object {
        const val SECRET: String = "mobile-banking-signing-secret"
        const val ISSUER: String = "https://login.bank.example"
        const val AUDIENCE: String = "mobile-banking"
        const val REALM: String = "mobile banking"
        const val DEVICE_HEADER: String = "X-Device-Id"
        const val PHONE: String = "pixel-8-ana"
        const val TABLET: String = "ipad-ana"
        const val LIFETIME_MILLIS: Long = 3_600_000
    }
}
