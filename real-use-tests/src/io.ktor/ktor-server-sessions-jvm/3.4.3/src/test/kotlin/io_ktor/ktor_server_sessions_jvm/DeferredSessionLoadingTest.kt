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
import io.ktor.http.setCookie
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sessions.SessionStorage
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Deferred session loading switched on by the `io.ktor.server.sessions.deferred` flag.
 *
 * A news site keeps reader profiles in a remote profile store that is expensive to query, so it
 * starts with deferred sessions: the plugin only loads a session when a route asks for it. The
 * test counts store reads to show that routes which never touch the session, such as the health
 * check and the public front page, cost no lookup and send no cookie, while the profile page
 * still sees the reader's session and its updates.
 */
class DeferredSessionLoadingTest {

    companion object {
        /** The system property an application sets to make the Sessions plugin load lazily. */
        private const val DEFERRED_SESSIONS_PROPERTY: String = "io.ktor.server.sessions.deferred"
    }

    @Serializable
    data class ReaderSession(val reader: String, val savedArticles: List<String> = emptyList())

    /** A remote profile store stand-in that counts every lookup the plugin makes. */
    class ProfileStore : SessionStorage {
        private val mutex: Mutex = Mutex()
        private val rows: MutableMap<String, String> = mutableMapOf()
        private var readCount: Int = 0
        private var writeCount: Int = 0

        override suspend fun write(id: String, value: String): Unit = mutex.withLock {
            rows[id] = value
            writeCount++
        }

        override suspend fun read(id: String): String = mutex.withLock {
            readCount++
            rows[id] ?: throw NoSuchElementException("Profile session $id not found")
        }

        override suspend fun invalidate(id: String): Unit = mutex.withLock {
            rows.remove(id)
        }

        suspend fun reads(): Int = mutex.withLock { readCount }

        suspend fun writes(): Int = mutex.withLock { writeCount }
    }

    @Test
    fun sessionsAreLoadedOnlyByRoutesThatReadThem() {
        val previousFlag: String? = System.setProperty(DEFERRED_SESSIONS_PROPERTY, "true")
        try {
            readerJourney()
        } finally {
            if (previousFlag == null) {
                System.clearProperty(DEFERRED_SESSIONS_PROPERTY)
            } else {
                System.setProperty(DEFERRED_SESSIONS_PROPERTY, previousFlag)
            }
        }
    }

    private fun readerJourney() = testApplication {
        val store = ProfileStore()
        var nextId = 0

        application {
            install(Sessions) {
                cookie<ReaderSession>("READER", store) {
                    cookie.path = "/"
                    identity { "reader-${++nextId}" }
                }
            }
            routing {
                get("/health") {
                    call.respondText("ok")
                }
                get("/") {
                    call.respondText("front page")
                }
                post("/sign-in/{reader}") {
                    call.sessions.set(ReaderSession(reader = call.parameters["reader"]!!))
                    call.respondText("signed in")
                }
                post("/save/{article}") {
                    val session: ReaderSession? = call.sessions.get<ReaderSession>()
                    if (session == null) {
                        call.respondText("sign in first", status = HttpStatusCode.Unauthorized)
                    } else {
                        val article: String = call.parameters["article"]!!
                        call.sessions.set(session.copy(savedArticles = session.savedArticles + article))
                        call.respondText("saved $article")
                    }
                }
                get("/profile") {
                    val session: ReaderSession? = call.sessions.get<ReaderSession>()
                    if (session == null) {
                        call.respondText("sign in first", status = HttpStatusCode.Unauthorized)
                    } else {
                        call.respondText("${session.reader}:${session.savedArticles.joinToString(",")}")
                    }
                }
            }
        }

        val browser = createClient { install(HttpCookies) }

        val signIn: HttpResponse = browser.post("/sign-in/ada")
        assertEquals("signed in", signIn.bodyAsText())
        assertEquals("reader-1", signIn.setCookie().single { it.name == "READER" }.value)
        assertEquals(1, store.writes())
        assertEquals(0, store.reads(), "setting a session never loads the previous one")

        val health: HttpResponse = browser.get("/health")
        assertEquals("ok", health.bodyAsText())
        assertNull(health.headers[HttpHeaders.SetCookie], "an untouched session is not sent back")
        assertEquals("front page", browser.get("/").bodyAsText())
        assertEquals(0, store.reads(), "routes that ignore the session cost no store lookup")
        assertEquals(1, store.writes())

        assertEquals("ada:", browser.get("/profile").bodyAsText())
        assertEquals(1, store.reads(), "the profile page loads the session on first access")

        assertEquals("saved deep-sea-mining", browser.post("/save/deep-sea-mining").bodyAsText())
        assertEquals("saved tidal-power", browser.post("/save/tidal-power").bodyAsText())
        assertEquals(3, store.reads())
        assertEquals(3, store.writes(), "each update is written back to the store")

        repeat(3) { assertEquals("ok", browser.get("/health").bodyAsText()) }
        assertEquals(3, store.reads(), "health checks between visits still skip the store")

        assertEquals("ada:deep-sea-mining,tidal-power", browser.get("/profile").bodyAsText())
        assertEquals(4, store.reads())

        val anonymous = createClient { }
        assertEquals(HttpStatusCode.Unauthorized, anonymous.get("/profile").status)
        assertEquals(4, store.reads(), "a request without a session cookie never reaches the store")
    }
}
