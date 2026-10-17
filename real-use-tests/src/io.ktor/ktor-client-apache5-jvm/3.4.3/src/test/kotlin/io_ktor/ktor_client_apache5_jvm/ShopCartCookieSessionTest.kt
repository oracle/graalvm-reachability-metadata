/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_apache5_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.cookies.cookies
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Cookie-based session management with the `HttpCookies` plugin on the `Apache5` engine.
 *
 * A storefront client signs in, receives a session cookie and a path-scoped checkout cookie
 * from the shop, fills a cart across several requests that the shop recognises only through
 * the cookies the client sends back, and signs out, after which the shop's expiring
 * `Set-Cookie` removes the session from the client's cookie storage. A preference cookie the
 * application seeds into the storage itself reaches the shop as well.
 */
class ShopCartCookieSessionTest {

    /** The shop's server-side sessions, keyed by the session id it hands out in a cookie. */
    class SessionRegistry {
        private val carts: MutableMap<String, MutableList<String>> = ConcurrentHashMap()
        private val nextId: AtomicInteger = AtomicInteger(1)

        fun open(user: String): String {
            val id: String = "$user-${nextId.getAndIncrement()}"
            carts[id] = mutableListOf()
            return id
        }

        fun cart(id: String?): MutableList<String>? = id?.let { carts[it] }

        fun close(id: String?): Boolean = id != null && carts.remove(id) != null
    }

    private val sessions: SessionRegistry = SessionRegistry()
    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                post("/login") {
                    val user: String = call.request.queryParameters["user"]!!
                    val sessionId: String = sessions.open(user)
                    call.response.cookies.append(Cookie("SHOP_SESSION", sessionId, path = "/", httpOnly = true))
                    call.response.cookies.append(Cookie("CHECKOUT_STEP", "cart", path = "/checkout"))
                    call.respondText("welcome $user")
                }
                post("/cart/items") {
                    val cart: MutableList<String>? = sessions.cart(call.request.cookies["SHOP_SESSION"])
                    if (cart == null) {
                        call.respondText("sign in first", status = HttpStatusCode.Unauthorized)
                    } else {
                        cart.add(call.request.queryParameters["sku"]!!)
                        call.respondText("${cart.size}", status = HttpStatusCode.Created)
                    }
                }
                get("/cart") {
                    val cart: List<String>? = sessions.cart(call.request.cookies["SHOP_SESSION"])
                    val currency: String = call.request.cookies["CURRENCY"] ?: "USD"
                    if (cart == null) {
                        call.respondText("sign in first", status = HttpStatusCode.Unauthorized)
                    } else {
                        call.respondText("${cart.joinToString(",")} in $currency")
                    }
                }
                get("/checkout/summary") {
                    val step: String = call.request.cookies["CHECKOUT_STEP"] ?: "none"
                    val items: Int = sessions.cart(call.request.cookies["SHOP_SESSION"])?.size ?: -1
                    call.respondText("step=$step items=$items")
                }
                post("/logout") {
                    sessions.close(call.request.cookies["SHOP_SESSION"])
                    call.response.cookies.append(Cookie("SHOP_SESSION", "", path = "/", maxAge = 0))
                    call.respondText("bye")
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stopServer() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun cartSurvivesAcrossRequestsThroughSessionCookieUntilLogout() = runBlocking {
        val storage: AcceptAllCookiesStorage = AcceptAllCookiesStorage()
        val shop: String = "http://127.0.0.1:$port"
        HttpClient(Apache5) {
            install(HttpCookies) {
                this.storage = storage
            }
        }.use { client: HttpClient ->
            val anonymous: HttpResponse = client.get("$shop/cart")
            assertEquals(HttpStatusCode.Unauthorized, anonymous.status)

            assertEquals("welcome alice", client.post("$shop/login?user=alice").bodyAsText())
            val rootCookies: List<Cookie> = client.cookies("$shop/cart")
            val session: Cookie = rootCookies.single { it.name == "SHOP_SESSION" }
            assertEquals("alice-1", session.value)
            assertTrue(session.httpOnly)
            assertNull(rootCookies.firstOrNull { it.name == "CHECKOUT_STEP" })

            assertEquals("1", client.post("$shop/cart/items?sku=TEA-42").bodyAsText())
            assertEquals("2", client.post("$shop/cart/items?sku=MUG-07").bodyAsText())

            storage.addCookie(Url("$shop/"), Cookie("CURRENCY", "EUR", path = "/"))
            assertEquals("TEA-42,MUG-07 in EUR", client.get("$shop/cart").bodyAsText())
            assertEquals("step=cart items=2", client.get("$shop/checkout/summary").bodyAsText())

            assertEquals("bye", client.post("$shop/logout").bodyAsText())
            // The storage drops a `maxAge = 0` cookie only once its millisecond clock has moved on.
            val loggedOutAt: Long = System.currentTimeMillis()
            while (System.currentTimeMillis() <= loggedOutAt) {
                delay(1)
            }
            val remaining: List<Cookie> = client.cookies("$shop/cart")
            assertNull(remaining.firstOrNull { it.name == "SHOP_SESSION" })
            assertEquals("EUR", remaining.single { it.name == "CURRENCY" }.value)

            val afterLogout: HttpResponse = client.get("$shop/cart")
            assertEquals(HttpStatusCode.Unauthorized, afterLogout.status)
            assertEquals("sign in first", afterLogout.bodyAsText())
        }
    }

    @Test
    fun separateClientsKeepIndependentSessions() = runBlocking {
        val shop: String = "http://127.0.0.1:$port"
        HttpClient(Apache5) { install(HttpCookies) }.use { first: HttpClient ->
            HttpClient(Apache5) { install(HttpCookies) }.use { second: HttpClient ->
                first.post("$shop/login?user=bob")
                second.post("$shop/login?user=carol")

                first.post("$shop/cart/items?sku=BOOK-1")
                second.post("$shop/cart/items?sku=PEN-9")
                second.post("$shop/cart/items?sku=INK-3")

                assertEquals("BOOK-1 in USD", first.get("$shop/cart").bodyAsText())
                assertEquals("PEN-9,INK-3 in USD", second.get("$shop/cart").bodyAsText())
                assertEquals("bob-1", first.cookies("$shop/").single { it.name == "SHOP_SESSION" }.value)
                assertEquals("carol-2", second.cookies("$shop/").single { it.name == "SHOP_SESSION" }.value)
            }
        }
    }
}
