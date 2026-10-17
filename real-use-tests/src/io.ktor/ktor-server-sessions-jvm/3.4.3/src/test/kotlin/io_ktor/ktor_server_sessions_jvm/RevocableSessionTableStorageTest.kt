/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_sessions_jvm

import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.setCookie
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sessions.SessionStorage
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Application-implemented [SessionStorage] backend plugged into a cookie session by ID.
 *
 * A support desk keeps each agent's console state in its own session table, a stand-in for a
 * database the application owns, and lets an administrator revoke a session out of band. The
 * test checks what the plugin writes to and reads from the table, that a revoked or forged
 * session ID reads as no session, and that logging out invalidates the row.
 */
class RevocableSessionTableStorageTest {

    @Serializable
    data class ConsoleSession(val agent: String, val openTickets: List<Int> = emptyList())

    /** A session table that records which rows exist and how often the plugin touched them. */
    class SessionTable : SessionStorage {
        private val mutex: Mutex = Mutex()
        private val rows: MutableMap<String, String> = linkedMapOf()
        val audit: MutableList<String> = mutableListOf()

        override suspend fun write(id: String, value: String): Unit = mutex.withLock {
            rows[id] = value
            audit += "write:$id"
        }

        override suspend fun read(id: String): String = mutex.withLock {
            audit += "read:$id"
            rows[id] ?: throw NoSuchElementException("Session $id not found")
        }

        override suspend fun invalidate(id: String): Unit = mutex.withLock {
            rows.remove(id)
            audit += "invalidate:$id"
        }

        suspend fun revoke(id: String): Boolean = mutex.withLock { rows.remove(id) != null }

        suspend fun ids(): Set<String> = mutex.withLock { rows.keys.toSet() }

        suspend fun storedValue(id: String): String? = mutex.withLock { rows[id] }
    }

    @Test
    fun customStorageBacksSessionsAndHonoursRevocation() = testApplication {
        val table = SessionTable()
        var nextId = 0

        application {
            install(Sessions) {
                cookie<ConsoleSession>("DESK_SESSION", table) {
                    cookie.path = "/"
                    cookie.httpOnly = true
                    identity { "desk-${++nextId}" }
                }
            }
            routing {
                post("/login/{agent}") {
                    call.sessions.set(ConsoleSession(agent = call.parameters["agent"]!!))
                    call.respondText("welcome")
                }
                post("/tickets/{number}") {
                    val session: ConsoleSession? = call.sessions.get<ConsoleSession>()
                    if (session == null) {
                        call.respondText("login required", status = HttpStatusCode.Unauthorized)
                    } else {
                        val ticket: Int = call.parameters["number"]!!.toInt()
                        call.sessions.set(session.copy(openTickets = session.openTickets + ticket))
                        call.respondText("claimed $ticket")
                    }
                }
                get("/console") {
                    val session: ConsoleSession? = call.sessions.get<ConsoleSession>()
                    if (session == null) {
                        call.respondText("login required", status = HttpStatusCode.Unauthorized)
                    } else {
                        call.respondText("${session.agent}:${session.openTickets.joinToString(",")}")
                    }
                }
                post("/logout") {
                    call.sessions.clear<ConsoleSession>()
                    call.respondText("bye")
                }
            }
        }

        val alice = createClient { install(HttpCookies) }
        val bob = createClient { install(HttpCookies) }

        val aliceLogin: HttpResponse = alice.post("/login/alice")
        assertEquals("welcome", aliceLogin.bodyAsText())
        val aliceCookie = aliceLogin.setCookie().single { it.name == "DESK_SESSION" }
        assertEquals("desk-1", aliceCookie.value, "the cookie carries only the application-issued ID")
        assertTrue(aliceCookie.httpOnly)
        bob.post("/login/bob")

        assertEquals("claimed 101", alice.post("/tickets/101").bodyAsText())
        assertEquals("claimed 205", alice.post("/tickets/205").bodyAsText())
        assertEquals("claimed 300", bob.post("/tickets/300").bodyAsText())
        assertEquals("alice:101,205", alice.get("/console").bodyAsText())
        assertEquals("bob:300", bob.get("/console").bodyAsText())

        assertEquals(setOf("desk-1", "desk-2"), table.ids())
        val aliceRow: String = table.storedValue("desk-1")!!
        assertTrue(aliceRow.contains("alice") && aliceRow.contains("205"), aliceRow)
        assertTrue(table.audit.contains("write:desk-1") && table.audit.contains("read:desk-1"), "${table.audit}")

        val forged = createClient { }
        val forgedResponse: HttpResponse = forged.get("/console") { cookie("DESK_SESSION", "desk-999") }
        assertEquals(HttpStatusCode.Unauthorized, forgedResponse.status)
        assertTrue(table.audit.contains("read:desk-999"), "a forged ID is looked up and rejected")

        assertTrue(table.revoke("desk-2"), "the administrator revokes bob's session")
        assertEquals(HttpStatusCode.Unauthorized, bob.get("/console").status)
        assertEquals("login required", bob.post("/tickets/301").bodyAsText())
        assertEquals("alice:101,205", alice.get("/console").bodyAsText())

        val logout: HttpResponse = alice.post("/logout")
        assertEquals("bye", logout.bodyAsText())
        assertTrue(table.audit.contains("invalidate:desk-1"), "${table.audit}")
        val expired = logout.setCookie().single { it.name == "DESK_SESSION" }
        assertEquals("", expired.value)
        assertEquals(0, expired.maxAge)
        assertFalse(table.ids().contains("desk-1"))
        assertEquals(HttpStatusCode.Unauthorized, alice.get("/console").status)
        assertTrue(table.ids().isEmpty())
    }
}
