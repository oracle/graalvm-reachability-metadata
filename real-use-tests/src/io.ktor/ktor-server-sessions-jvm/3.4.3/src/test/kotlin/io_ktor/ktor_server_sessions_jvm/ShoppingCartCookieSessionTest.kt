/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_sessions_jvm

import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Client-side cookie sessions encoded by the default kotlinx-serialization session serializer.
 *
 * A storefront keeps each visitor's shopping cart entirely in a `CART` cookie. Items are added
 * over several requests by a browser-like client that replays cookies, the cart total is read
 * back from the decoded session, and checkout clears the session so the cookie expires.
 */
class ShoppingCartCookieSessionTest {

    @Serializable
    data class CartLine(val sku: String, val quantity: Int, val unitPriceCents: Long)

    @Serializable
    data class CartSession(val customer: String, val lines: List<CartLine> = emptyList(), val couponCode: String? = null) {
        val totalCents: Long get() = lines.sumOf { it.quantity * it.unitPriceCents }
    }

    @Test
    fun cartSurvivesAcrossRequestsAndIsClearedAtCheckout() = testApplication {
        val prices: Map<String, Long> = mapOf("MUG-01" to 1250L, "TEE-XL" to 2400L)

        application {
            install(Sessions) {
                cookie<CartSession>("CART") {
                    cookie.path = "/"
                    cookie.maxAgeInSeconds = 3600
                    cookie.httpOnly = true
                    cookie.extensions["SameSite"] = "lax"
                }
            }
            routing {
                post("/cart/{sku}") {
                    val sku: String = call.parameters["sku"]!!
                    val quantity: Int = call.request.queryParameters["qty"]?.toInt() ?: 1
                    val cart: CartSession = call.sessions.get<CartSession>() ?: CartSession(customer = "guest-42")
                    val line = CartLine(sku, quantity, prices.getValue(sku))
                    call.sessions.set(cart.copy(lines = cart.lines + line))
                    call.respondText("added $sku")
                }
                post("/cart/coupon/{code}") {
                    val cart: CartSession? = call.sessions.get<CartSession>()
                    if (cart == null) {
                        call.respondText("no cart", status = HttpStatusCode.NotFound)
                    } else {
                        call.sessions.set(cart.copy(couponCode = call.parameters["code"]))
                        call.respondText("coupon applied")
                    }
                }
                get("/cart") {
                    val cart: CartSession? = call.sessions.get<CartSession>()
                    if (cart == null) {
                        call.respondText("empty", status = HttpStatusCode.NotFound)
                    } else {
                        val skus: String = cart.lines.joinToString(",") { "${it.sku}x${it.quantity}" }
                        call.respondText("${cart.customer}|$skus|${cart.totalCents}|${cart.couponCode ?: "-"}")
                    }
                }
                delete("/cart") {
                    val cart: CartSession = call.sessions.get<CartSession>()!!
                    call.sessions.clear<CartSession>()
                    call.respondText("charged ${cart.totalCents}")
                }
            }
        }

        val browser = createClient { install(HttpCookies) }

        assertEquals(HttpStatusCode.NotFound, browser.get("/cart").status)

        val firstAdd: HttpResponse = browser.post("/cart/MUG-01?qty=2")
        assertEquals("added MUG-01", firstAdd.bodyAsText())
        val setCookie: String? = firstAdd.headers[HttpHeaders.SetCookie]
        assertNotNull(setCookie)
        assertTrue(setCookie!!.startsWith("CART="), setCookie)
        assertTrue(setCookie.contains("Max-Age=3600"), setCookie)
        assertTrue(setCookie.contains("Path=/"), setCookie)
        assertTrue(setCookie.contains("HttpOnly"), setCookie)
        assertTrue(setCookie.contains("SameSite=lax"), setCookie)
        assertTrue(setCookie.contains("guest-42"), "client-side session carries the value itself: $setCookie")

        browser.post("/cart/TEE-XL")
        assertEquals("coupon applied", browser.post("/cart/coupon/AUTUMN10").bodyAsText())
        assertEquals("guest-42|MUG-01x2,TEE-XLx1|4900|AUTUMN10", browser.get("/cart").bodyAsText())

        val checkout: HttpResponse = browser.delete("/cart")
        assertEquals("charged 4900", checkout.bodyAsText())
        val expired: String = checkout.headers[HttpHeaders.SetCookie]!!
        assertTrue(expired.startsWith("CART=;"), expired)
        assertTrue(expired.contains("Max-Age=0"), expired)

        assertEquals(HttpStatusCode.NotFound, browser.get("/cart").status)

        val anonymous = createClient { }
        assertEquals("no cart", anonymous.post("/cart/coupon/AUTUMN10").bodyAsText())
    }
}
