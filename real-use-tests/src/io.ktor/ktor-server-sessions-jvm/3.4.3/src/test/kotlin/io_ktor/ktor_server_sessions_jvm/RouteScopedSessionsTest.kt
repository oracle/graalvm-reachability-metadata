/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_sessions_jvm

import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The Sessions plugin installed as a route-scoped plugin on separate route subtrees.
 *
 * A shop serves its storefront and its back office from one application. Each subtree installs
 * its own Sessions plugin with its own session type and cookie, so the back-office login and the
 * storefront basket live side by side, and signing out of one leaves the other untouched.
 */
class RouteScopedSessionsTest {

    @Serializable
    data class StaffSession(val username: String, val permissions: List<String>)

    @Serializable
    data class BasketSession(val items: List<String>)

    @Test
    fun eachRouteSubtreeKeepsItsOwnSession() = testApplication {
        application {
            routing {
                route("/backoffice") {
                    install(Sessions) {
                        cookie<StaffSession>("STAFF") {
                            cookie.path = "/backoffice"
                            cookie.httpOnly = true
                        }
                    }
                    post("/login/{user}") {
                        val user: String = call.parameters["user"]!!
                        call.sessions.set(StaffSession(user, listOf("orders:read", "orders:refund")))
                        call.respondText("welcome $user")
                    }
                    get("/orders") {
                        val staff: StaffSession? = call.sessions.get<StaffSession>()
                        if (staff == null) {
                            call.respondText("sign in first", status = HttpStatusCode.Unauthorized)
                        } else {
                            call.respondText("${staff.username} may ${staff.permissions.joinToString("+")}")
                        }
                    }
                    post("/logout") {
                        call.sessions.clear<StaffSession>()
                        call.respondText("signed out")
                    }
                }
                route("/store") {
                    install(Sessions) {
                        cookie<BasketSession>("BASKET") {
                            cookie.path = "/store"
                            cookie.maxAgeInSeconds = 86400
                        }
                    }
                    post("/basket/{sku}") {
                        val basket: BasketSession = call.sessions.get<BasketSession>() ?: BasketSession(emptyList())
                        val updated = BasketSession(basket.items + call.parameters["sku"]!!)
                        call.sessions.set(updated)
                        call.respondText("${updated.items.size} item(s)")
                    }
                    get("/basket") {
                        val basket: BasketSession? = call.sessions.get<BasketSession>()
                        call.respondText(basket?.items?.joinToString(",") ?: "empty")
                    }
                }
            }
        }

        val browser = createClient { install(HttpCookies) }

        assertEquals(HttpStatusCode.Unauthorized, browser.get("/backoffice/orders").status)

        val login: HttpResponse = browser.post("/backoffice/login/alice")
        assertEquals("welcome alice", login.bodyAsText())
        val staffCookie: String = login.headers[HttpHeaders.SetCookie]!!
        assertTrue(staffCookie.startsWith("STAFF="), staffCookie)
        assertTrue(staffCookie.contains("Path=/backoffice"), staffCookie)
        assertTrue(staffCookie.contains("HttpOnly"), staffCookie)
        assertEquals("alice may orders:read+orders:refund", browser.get("/backoffice/orders").bodyAsText())

        val firstItem: HttpResponse = browser.post("/store/basket/KETTLE")
        assertEquals("1 item(s)", firstItem.bodyAsText())
        val basketCookie: String = firstItem.headers[HttpHeaders.SetCookie]!!
        assertTrue(basketCookie.startsWith("BASKET="), basketCookie)
        assertTrue(basketCookie.contains("Path=/store"), basketCookie)
        assertTrue(basketCookie.contains("Max-Age=86400"), basketCookie)
        assertFalse(staffCookie.contains("Max-Age=86400"), "each subtree applies its own cookie settings")
        assertEquals("2 item(s)", browser.post("/store/basket/TEAPOT").bodyAsText())
        assertEquals("KETTLE,TEAPOT", browser.get("/store/basket").bodyAsText())

        val logout: HttpResponse = browser.post("/backoffice/logout")
        assertEquals("signed out", logout.bodyAsText())
        val expired: String = logout.headers[HttpHeaders.SetCookie]!!
        assertTrue(expired.startsWith("STAFF=;"), expired)
        assertTrue(expired.contains("Max-Age=0"), expired)
        assertEquals(HttpStatusCode.Unauthorized, browser.get("/backoffice/orders").status)

        assertEquals("KETTLE,TEAPOT", browser.get("/store/basket").bodyAsText(), "the basket outlives the logout")
    }
}
