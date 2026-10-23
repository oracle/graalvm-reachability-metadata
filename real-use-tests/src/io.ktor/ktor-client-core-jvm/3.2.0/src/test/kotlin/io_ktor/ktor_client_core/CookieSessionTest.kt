/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_core

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import io.ktor.server.cio.CIO as ServerCIO

/** Exercises client cookie sessions by retaining a login cookie and respecting its request path. */
class CookieSessionTest {
    @Test
    @Timeout(60)
    fun retainsSessionOnlyForAccountRequests(): Unit = runBlocking {
        withTimeout(45_000) {
            val server = embeddedServer(ServerCIO, host = "127.0.0.1", port = 0) {
                routing {
                    post("/login") {
                        call.response.cookies.append(
                            Cookie("session", "customer-42", path = "/account", httpOnly = true)
                        )
                        call.respondText("Signed in")
                    }
                    get("/account/profile") {
                        if (call.request.cookies["session"] == "customer-42") {
                            call.respondText("Account: customer-42")
                        } else {
                            call.respondText("Sign in required", status = HttpStatusCode.Unauthorized)
                        }
                    }
                    get("/public") {
                        call.respondText(call.request.cookies["session"] ?: "Anonymous visitor")
                    }
                }
            }
            try {
                server.start(wait = false)
                val port: Int = server.engine.resolvedConnectors().single().port
                val baseUrl: String = "http://127.0.0.1:$port"
                HttpClient(CIO) {
                    install(HttpCookies)
                    install(HttpTimeout) {
                        requestTimeoutMillis = 15_000
                        connectTimeoutMillis = 15_000
                        socketTimeoutMillis = 15_000
                    }
                }.use { client: HttpClient ->
                    val anonymousProfile: HttpResponse = client.get("$baseUrl/account/profile")
                    assertEquals(HttpStatusCode.Unauthorized, anonymousProfile.status)
                    assertEquals("Sign in required", anonymousProfile.bodyAsText())

                    val login: HttpResponse = client.post("$baseUrl/login")
                    assertEquals(HttpStatusCode.OK, login.status)
                    assertEquals("Signed in", login.bodyAsText())

                    val profile: HttpResponse = client.get("$baseUrl/account/profile")
                    assertEquals(HttpStatusCode.OK, profile.status)
                    assertEquals("Account: customer-42", profile.bodyAsText())

                    val publicPage: HttpResponse = client.get("$baseUrl/public")
                    assertEquals(HttpStatusCode.OK, publicPage.status)
                    assertEquals("Anonymous visitor", publicPage.bodyAsText())

                    assertEquals("Account: customer-42", client.get("$baseUrl/account/profile").bodyAsText())
                }
            } finally {
                server.stop(0, 10_000)
            }
        }
    }
}
