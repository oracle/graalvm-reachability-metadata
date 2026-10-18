/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_core_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
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
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.ktor.server.cio.CIO as ServerCIO

/**
 * The client `HttpCookies` plugin backed by an `AcceptAllCookiesStorage`.
 *
 * A member portal client logs in, receives a path-scoped session cookie and a site-wide
 * preference cookie, and keeps a locale cookie the application seeded into the storage
 * itself. The test checks that the plugin replays each cookie only on the paths it is
 * scoped to, that the storage exposes what it holds for a URL, and that the expired
 * cookie the server sends on logout removes the session so the account is locked again.
 */
class MemberPortalCookieSessionTest {

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                post("/login") {
                    call.response.cookies.append(Cookie(SESSION_COOKIE, SESSION_ID, path = "/account", httpOnly = true))
                    call.response.cookies.append(Cookie("theme", "dark", path = "/"))
                    call.respondText("welcome")
                }
                get("/account/profile") {
                    if (call.request.cookies[SESSION_COOKIE] != SESSION_ID) {
                        call.respondText("login required", status = HttpStatusCode.Unauthorized)
                        return@get
                    }
                    val theme: String = call.request.cookies["theme"] ?: "none"
                    val locale: String = call.request.cookies["locale"] ?: "none"
                    call.respondText("member=ada theme=$theme locale=$locale")
                }
                post("/account/logout") {
                    call.response.cookies.append(Cookie(SESSION_COOKIE, "", path = "/account", expires = GMTDate.START))
                    call.respondText("bye")
                }
                get("/news") {
                    val names: String = call.request.cookies.rawCookies.keys.sorted().joinToString(",")
                    call.respondText(names)
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stop() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun cookiesFollowTheirScopeAcrossLoginAndLogout() {
        val storage = AcceptAllCookiesStorage()
        val base: String = "http://127.0.0.1:$port"
        val client = HttpClient(CIO) {
            install(HttpCookies) {
                this.storage = storage
            }
        }

        client.use {
            runBlocking {
                storage.addCookie(Url("$base/"), Cookie("locale", "en-GB", path = "/"))

                val anonymous: HttpResponse = it.get("$base/account/profile")
                assertEquals(HttpStatusCode.Unauthorized, anonymous.status)

                assertEquals("welcome", it.post("$base/login").bodyAsText())

                val held: List<String> = it.cookies("$base/account/profile").map { c: Cookie -> c.name }.sorted()
                assertEquals(listOf(SESSION_COOKIE, "locale", "theme"), held)
                val publicOnly: List<String> = it.cookies("$base/news").map { c: Cookie -> c.name }.sorted()
                assertEquals(listOf("locale", "theme"), publicOnly)
                val session: Cookie = it.cookies("$base/account/profile").first { c: Cookie -> c.name == SESSION_COOKIE }
                assertTrue(session.httpOnly, "the session cookie keeps its HttpOnly attribute")
                assertEquals("/account", session.path)

                val profile: HttpResponse = it.get("$base/account/profile")
                assertEquals(HttpStatusCode.OK, profile.status)
                assertEquals("member=ada theme=dark locale=en-GB", profile.bodyAsText())

                assertEquals("locale,theme", it.get("$base/news").bodyAsText())

                assertEquals("bye", it.post("$base/account/logout").bodyAsText())
                val afterLogout: List<String> = it.cookies("$base/account/profile").map { c: Cookie -> c.name }.sorted()
                assertEquals(listOf("locale", "theme"), afterLogout)
                assertEquals(HttpStatusCode.Unauthorized, it.get("$base/account/profile").status)
            }
        }
    }

    private companion object {
        const val SESSION_COOKIE: String = "SESSION"
        const val SESSION_ID: String = "s-4f9c2e"
    }
}
