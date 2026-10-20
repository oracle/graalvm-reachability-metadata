/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_sessions_jvm

import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.CookieEncoding
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.decodeCookieValue
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sessions.SessionTransportTransformerEncrypt
import io.ktor.server.sessions.SessionTransportTransformerMessageAuthentication
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.testApplication
import io.ktor.util.hex
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Session transport transformers that sign and encrypt cookie values.
 *
 * A banking portal keeps display preferences in an HMAC-signed `PREFS` cookie the browser may
 * read but not alter, and the signed-in account in an AES-encrypted `AUTH` cookie the browser
 * can neither read nor forge. Tampered cookies are rejected and read back as no session.
 */
class SignedAndEncryptedCookieSessionTest {

    @Serializable
    data class Preferences(val theme: String, val locale: String)

    @Serializable
    data class AuthSession(val accountId: String, val role: String)

    @Test
    fun signedAndEncryptedCookiesRoundTripAndRejectTampering() = testApplication {
        val signKey: ByteArray = hex("6819b57a326945c1968f45236589")
        val encryptKey: ByteArray = hex("00112233445566778899aabbccddeeff")

        application {
            install(Sessions) {
                cookie<Preferences>("PREFS") {
                    cookie.path = "/"
                    transform(SessionTransportTransformerMessageAuthentication(signKey))
                }
                cookie<AuthSession>("AUTH") {
                    cookie.path = "/"
                    cookie.httpOnly = true
                    transform(SessionTransportTransformerEncrypt(encryptKey, signKey))
                }
            }
            routing {
                post("/preferences") {
                    call.sessions.set(Preferences(theme = "dark", locale = "de-CH"))
                    call.respondText("saved")
                }
                post("/login") {
                    call.sessions.set(AuthSession(accountId = "CH93-0076-2011-6238-5295-7", role = "owner"))
                    call.respondText("welcome")
                }
                get("/dashboard") {
                    val prefs: Preferences? = call.sessions.get<Preferences>()
                    val auth: AuthSession? = call.sessions.get<AuthSession>()
                    if (auth == null) {
                        call.respondText("login required", status = HttpStatusCode.Unauthorized)
                    } else {
                        val look: String = prefs?.let { "${it.theme}/${it.locale}" } ?: "default"
                        call.respondText("${auth.accountId}|${auth.role}|$look")
                    }
                }
                get("/preferences") {
                    val prefs: Preferences? = call.sessions.get<Preferences>()
                    call.respondText(prefs?.theme ?: "none")
                }
            }
        }

        val browser = createClient { install(HttpCookies) }

        val savedPrefs: HttpResponse = browser.post("/preferences")
        val prefsCookie: String = savedPrefs.headers[HttpHeaders.SetCookie]!!
        val prefsValue: String = prefsCookie.substringAfter("PREFS=").substringBefore(";")
        assertTrue(prefsValue.contains("dark"), "signed cookie stays readable: $prefsValue")
        val decodedPrefs: String = decodeCookieValue(prefsValue, CookieEncoding.URI_ENCODING)
        assertTrue(decodedPrefs.startsWith("{\"theme\":\"dark\""), decodedPrefs)
        val signature: String = decodedPrefs.substringAfterLast("/")
        assertEquals(64, signature.length, "HmacSHA256 signature is hex-encoded: $prefsValue")

        val login: HttpResponse = browser.post("/login")
        assertEquals("welcome", login.bodyAsText())
        val authCookie: String = login.headers[HttpHeaders.SetCookie]!!
        val authValue: String = authCookie.substringAfter("AUTH=").substringBefore(";")
        assertFalse(authValue.contains("CH93"), "encrypted cookie hides the account: $authValue")
        assertFalse(authValue.contains("owner"), "encrypted cookie hides the role: $authValue")
        assertTrue(authCookie.contains("HttpOnly"), authCookie)

        assertEquals("CH93-0076-2011-6238-5295-7|owner|dark/de-CH", browser.get("/dashboard").bodyAsText())

        val secondLogin: String = browser.post("/login").headers[HttpHeaders.SetCookie]!!
        val secondAuthValue: String = secondLogin.substringAfter("AUTH=").substringBefore(";")
        assertNotEquals(authValue, secondAuthValue, "a fresh IV makes every encryption distinct")

        val attacker = createClient { }
        val forgedPrefs: String = prefsValue.replace("dark", "lite")
        val forgedPrefsResponse: HttpResponse = attacker.get("/preferences") {
            header(HttpHeaders.Cookie, "PREFS=$forgedPrefs")
        }
        assertEquals("none", forgedPrefsResponse.bodyAsText())
        val replayedPrefs: HttpResponse = attacker.get("/preferences") {
            header(HttpHeaders.Cookie, "PREFS=$prefsValue")
        }
        assertEquals("dark", replayedPrefs.bodyAsText())

        val lastHex: Char = authValue.last()
        val flippedAuth: String = authValue.dropLast(1) + (if (lastHex == '0') '1' else '0')
        val forgedAuth: HttpResponse = attacker.get("/dashboard") {
            header(HttpHeaders.Cookie, "AUTH=$flippedAuth")
        }
        assertEquals(HttpStatusCode.Unauthorized, forgedAuth.status)
        val replayedAuth: HttpResponse = attacker.get("/dashboard") {
            header(HttpHeaders.Cookie, "AUTH=$secondAuthValue")
        }
        assertEquals("CH93-0076-2011-6238-5295-7|owner|default", replayedAuth.bodyAsText())
    }
}
