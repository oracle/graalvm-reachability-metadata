/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_sessions_jvm

import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
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
import io.ktor.server.sessions.SessionStorageMemory
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.testApplication
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Session-backed authentication, where the `ktor-server-auth` session provider reads the login.
 *
 * A back-office app checks a login form once, stores the signed-in user in a server-side
 * session cookie, and guards its dashboard with the session provider. Requests without a
 * session are redirected to the login page, a session whose account was disabled fails
 * validation, and logging out clears the session so the dashboard is locked again.
 */
class SessionAuthenticationLoginTest {

    @Serializable
    data class StaffSession(val username: String, val role: String)

    @Test
    fun loginStoresSessionThatGuardsTheDashboard() = testApplication {
        val passwords: Map<String, String> = mapOf("alice" to "s3cret", "bob" to "hunter2")
        val roles: Map<String, String> = mapOf("alice" to "admin", "bob" to "clerk")
        val disabledAccounts: MutableSet<String> = ConcurrentHashMap.newKeySet()

        application {
            install(Sessions) {
                cookie<StaffSession>("STAFF_SESSION", SessionStorageMemory()) {
                    cookie.path = "/"
                    cookie.httpOnly = true
                }
            }
            install(Authentication) {
                form("login-form") {
                    userParamName = "username"
                    passwordParamName = "password"
                    validate { credentials ->
                        if (passwords[credentials.name] == credentials.password) {
                            UserIdPrincipal(credentials.name)
                        } else {
                            null
                        }
                    }
                    challenge { call.respondRedirect("/login?failed") }
                }
                session<StaffSession>("staff-session") {
                    validate { staff -> staff.takeIf { it.username !in disabledAccounts } }
                    challenge { call.respondRedirect("/login") }
                }
            }
            routing {
                get("/login") { call.respondText("login page") }
                authenticate("login-form") {
                    post("/login") {
                        val user: UserIdPrincipal = call.principal<UserIdPrincipal>()!!
                        call.sessions.set(StaffSession(user.name, roles.getValue(user.name)))
                        call.respondRedirect("/dashboard")
                    }
                }
                authenticate("staff-session") {
                    get("/dashboard") {
                        val staff: StaffSession = call.principal<StaffSession>()!!
                        call.respondText("welcome ${staff.username} (${staff.role})")
                    }
                    post("/logout") {
                        call.sessions.clear<StaffSession>()
                        call.respondRedirect("/login")
                    }
                }
            }
        }

        val alice = createClient {
            install(HttpCookies)
            followRedirects = false
        }

        val anonymous: HttpResponse = alice.get("/dashboard")
        assertEquals(HttpStatusCode.Found, anonymous.status)
        assertEquals("/login", anonymous.headers[HttpHeaders.Location])

        val wrongPassword: HttpResponse = alice.submitForm(
            url = "/login",
            formParameters = parameters {
                append("username", "alice")
                append("password", "guess")
            },
        )
        assertEquals("/login?failed", wrongPassword.headers[HttpHeaders.Location])
        assertEquals(null, wrongPassword.headers[HttpHeaders.SetCookie])

        val login: HttpResponse = alice.submitForm(
            url = "/login",
            formParameters = parameters {
                append("username", "alice")
                append("password", "s3cret")
            },
        )
        assertEquals(HttpStatusCode.Found, login.status)
        assertEquals("/dashboard", login.headers[HttpHeaders.Location])
        val setCookie: String = login.headers[HttpHeaders.SetCookie]!!
        assertTrue(setCookie.startsWith("STAFF_SESSION="), setCookie)
        assertTrue(setCookie.contains("HttpOnly"), setCookie)
        assertTrue(!setCookie.contains("alice"), "the cookie carries only the server-side ID")

        val dashboard: HttpResponse = alice.get("/dashboard")
        assertEquals(HttpStatusCode.OK, dashboard.status)
        assertEquals("welcome alice (admin)", dashboard.bodyAsText())

        val logout: HttpResponse = alice.post("/logout")
        assertEquals("/login", logout.headers[HttpHeaders.Location])
        val afterLogout: HttpResponse = alice.get("/dashboard")
        assertEquals(HttpStatusCode.Found, afterLogout.status)
        assertEquals("/login", afterLogout.headers[HttpHeaders.Location])

        val bob = createClient {
            install(HttpCookies)
            followRedirects = false
        }
        bob.submitForm(
            url = "/login",
            formParameters = parameters {
                append("username", "bob")
                append("password", "hunter2")
            },
        )
        assertEquals("welcome bob (clerk)", bob.get("/dashboard").bodyAsText())

        disabledAccounts.add("bob")
        val disabled: HttpResponse = bob.get("/dashboard")
        assertEquals(HttpStatusCode.Found, disabled.status)
        assertEquals("/login", disabled.headers[HttpHeaders.Location])
    }
}
