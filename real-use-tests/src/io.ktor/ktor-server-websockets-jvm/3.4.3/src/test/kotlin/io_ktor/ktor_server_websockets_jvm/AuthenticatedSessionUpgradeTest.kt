/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_websockets_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.basicAuth
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.auth.principal
import io.ktor.server.auth.session
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets

/**
 * Covers WebSocket upgrades guarded by the authentication and sessions plugins: a support desk
 * logs agents in over basic auth into a session cookie, admits only upgrades carrying that
 * session, and greets each agent from the session principal it reads off the upgraded call.
 */
class AuthenticatedSessionUpgradeTest {

    /** The desk session an agent receives at login: who they are and which ticket queue they work. */
    @Serializable
    data class DeskSession(val agent: String, val queue: String)

    @Test
    fun admitsALoggedInAgentAndServesTheirQueue(): Unit = testApplication {
        installDesk()
        val client: HttpClient = deskClient()

        val login: HttpResponse = client.post("/login") {
            basicAuth("alice", PASSWORD)
            parameter("queue", "billing")
        }
        assertEquals(HttpStatusCode.NoContent, login.status)

        val replies: MutableList<String> = mutableListOf()
        client.webSocket("/desk") {
            replies += (incoming.receive() as Frame.Text).readText()
            for (ticket: String in listOf("T-17", "T-42")) {
                send(Frame.Text(ticket))
                replies += (incoming.receive() as Frame.Text).readText()
            }
            close(CloseReason(CloseReason.Codes.NORMAL, "shift over"))
        }

        assertEquals(
            listOf("welcome alice to billing", "alice took T-17 from billing", "alice took T-42 from billing"),
            replies,
        )
    }

    @Test
    fun keepsEachAgentsSessionApart(): Unit = testApplication {
        installDesk()
        val alice: HttpClient = deskClient()
        val bob: HttpClient = deskClient()
        alice.post("/login") {
            basicAuth("alice", PASSWORD)
            parameter("queue", "billing")
        }
        bob.post("/login") {
            basicAuth("bob", PASSWORD)
            parameter("queue", "shipping")
        }

        assertEquals("welcome bob to shipping", greeting(bob))
        assertEquals("welcome alice to billing", greeting(alice))
    }

    @Test
    fun refusesAnUpgradeWithoutASession(): Unit = testApplication {
        installDesk()

        assertUpgradeRefused(deskClient())
    }

    @Test
    fun refusesAnUpgradeAfterAFailedLogin(): Unit = testApplication {
        installDesk()
        val client: HttpClient = deskClient()

        val login: HttpResponse = client.post("/login") {
            basicAuth("alice", "wrong")
            parameter("queue", "billing")
        }

        assertEquals(HttpStatusCode.Unauthorized, login.status)
        assertUpgradeRefused(client)
    }

    private fun ApplicationTestBuilder.installDesk() {
        application {
            install(Sessions) {
                cookie<DeskSession>(SESSION_COOKIE)
            }
            install(Authentication) {
                basic(LOGIN_AUTH) {
                    validate { credentials ->
                        if (credentials.password == PASSWORD) UserIdPrincipal(credentials.name) else null
                    }
                }
                session<DeskSession>(DESK_AUTH) {
                    validate { session -> session }
                }
            }
            install(WebSockets)
            routing {
                authenticate(LOGIN_AUTH) {
                    post("/login") {
                        val agent: String = call.principal<UserIdPrincipal>()!!.name
                        val queue: String = call.request.queryParameters["queue"]!!
                        call.sessions.set(DeskSession(agent, queue))
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
                authenticate(DESK_AUTH) {
                    webSocket("/desk") {
                        val desk: DeskSession = call.principal<DeskSession>()!!
                        send(Frame.Text("welcome ${desk.agent} to ${desk.queue}"))
                        for (frame: Frame in incoming) {
                            val ticket: String = (frame as Frame.Text).readText()
                            send(Frame.Text("${desk.agent} took $ticket from ${desk.queue}"))
                        }
                    }
                }
            }
        }
    }

    private fun ApplicationTestBuilder.deskClient(): HttpClient = createClient {
        install(HttpCookies)
        install(ClientWebSockets)
    }

    /** Opens `/desk` with [client] and returns the greeting the desk sends first. */
    private suspend fun greeting(client: HttpClient): String {
        var greeting: String? = null
        client.webSocket("/desk") {
            greeting = (incoming.receive() as Frame.Text).readText()
            close(CloseReason(CloseReason.Codes.NORMAL, "checked in"))
        }
        return greeting!!
    }

    private suspend fun assertUpgradeRefused(client: HttpClient) {
        var opened = false
        val outcome: Result<Unit> = runCatching {
            client.webSocket("/desk") {
                opened = true
            }
        }

        assertNotNull(outcome.exceptionOrNull(), "the upgrade should be refused")
        assertFalse(opened, "no session should open without an authenticated desk session")
    }

    private companion object {

        const val PASSWORD: String = "s3cret"

        const val SESSION_COOKIE: String = "DESK_SESSION"

        const val LOGIN_AUTH: String = "agent-login"

        const val DESK_AUTH: String = "desk-session"
    }
}
