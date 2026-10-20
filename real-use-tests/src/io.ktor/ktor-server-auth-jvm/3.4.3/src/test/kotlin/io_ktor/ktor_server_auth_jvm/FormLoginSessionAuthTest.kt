/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_auth_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.form
import io.ktor.server.auth.principal
import io.ktor.server.auth.session
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * HTTP form authentication of a login page that hands the reader over to cookie session authentication.
 *
 * A library portal checks the username and password posted from its login form, stores a
 * serializable session for the accepted reader, and protects the loans page with session
 * authentication that re-validates the stored reader and counts visits. Wrong passwords and
 * missing sessions are redirected back to the login page, and logging out drops the session.
 */
class FormLoginSessionAuthTest {

    /** Session the portal keeps in a cookie once a reader has logged in. */
    @Serializable
    data class ReaderSession(val reader: String, val visits: Int)

    /** Reader accounts and their loans an application would normally keep in a database. */
    class LibraryAccounts(private val passwords: Map<String, String>, private val loans: Map<String, List<String>>) {
        fun check(reader: String, password: String): Boolean = passwords[reader] == password

        fun isActive(reader: String): Boolean = reader in passwords

        fun loansOf(reader: String): List<String> = loans[reader].orEmpty()
    }

    private fun Application.portalModule(accounts: LibraryAccounts) {
        install(Sessions) {
            cookie<ReaderSession>("LIBRARY_SESSION") {
                cookie.path = "/"
                cookie.httpOnly = true
            }
        }
        install(Authentication) {
            form("login-form") {
                userParamName = "reader"
                passwordParamName = "secret"
                validate { credentials ->
                    if (accounts.check(credentials.name, credentials.password)) UserIdPrincipal(credentials.name) else null
                }
                challenge { call.respondRedirect("/login?error=credentials") }
            }
            session<ReaderSession>("reader-session") {
                validate { session -> if (accounts.isActive(session.reader)) session else null }
                challenge { call.respondRedirect("/login") }
            }
        }
        routing {
            get("/login") {
                call.respondText("login page")
            }
            authenticate("login-form") {
                post("/login") {
                    val reader: UserIdPrincipal = call.principal<UserIdPrincipal>()!!
                    call.sessions.set(ReaderSession(reader.name, 0))
                    call.respondRedirect("/loans")
                }
            }
            authenticate("reader-session") {
                get("/loans") {
                    val session: ReaderSession = call.principal<ReaderSession>()!!
                    val visit: ReaderSession = session.copy(visits = session.visits + 1)
                    call.sessions.set(visit)
                    val titles: String = accounts.loansOf(visit.reader).joinToString(", ")
                    call.respondText("${visit.reader} visit ${visit.visits}: $titles")
                }
                post("/logout") {
                    call.sessions.clear<ReaderSession>()
                    call.respondRedirect("/login")
                }
            }
        }
    }

    private suspend fun login(browser: HttpClient, reader: String, secret: String): HttpResponse =
        browser.submitForm(
            url = "/login",
            formParameters = parameters {
                append("reader", reader)
                append("secret", secret)
            },
        )

    private fun assertRedirect(response: HttpResponse, location: String) {
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals(location, response.headers[HttpHeaders.Location])
    }

    @Test
    fun formLoginStartsSessionThatGuardsLoans() = testApplication {
        val accounts: LibraryAccounts = LibraryAccounts(
            passwords = mapOf("ada" to "engine-1843", "grace" to "cobol-1959"),
            loans = mapOf("ada" to listOf("Sketch of the Analytical Engine", "Elements of Algebra")),
        )
        application { portalModule(accounts) }
        val browser: HttpClient = createClient {
            followRedirects = false
            install(HttpCookies)
        }

        assertRedirect(browser.get("/loans"), "/login")

        val rejected: HttpResponse = login(browser, "ada", "wrong-password")
        assertRedirect(rejected, "/login?error=credentials")
        assertTrue(rejected.headers.getAll(HttpHeaders.SetCookie).orEmpty().isEmpty(), "no session for a failed login")
        assertRedirect(browser.get("/loans"), "/login")

        val accepted: HttpResponse = login(browser, "ada", "engine-1843")
        assertRedirect(accepted, "/loans")
        val sessionCookie: String? = accepted.headers.getAll(HttpHeaders.SetCookie)?.firstOrNull { it.startsWith("LIBRARY_SESSION=") }
        assertNotNull(sessionCookie, "the login sets the session cookie")
        assertTrue(sessionCookie!!.contains("HttpOnly", ignoreCase = true), "the session cookie is HttpOnly: $sessionCookie")

        val first: HttpResponse = browser.get("/loans")
        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals("ada visit 1: Sketch of the Analytical Engine, Elements of Algebra", first.bodyAsText())
        assertEquals("ada visit 2: Sketch of the Analytical Engine, Elements of Algebra", browser.get("/loans").bodyAsText())

        assertRedirect(browser.post("/logout"), "/login")
        assertRedirect(browser.get("/loans"), "/login")

        val other: HttpClient = createClient {
            followRedirects = false
            install(HttpCookies)
        }
        assertRedirect(login(other, "grace", "cobol-1959"), "/loans")
        assertEquals("grace visit 1: ", other.get("/loans").bodyAsText(), "a reader without loans sees an empty list")
    }
}
