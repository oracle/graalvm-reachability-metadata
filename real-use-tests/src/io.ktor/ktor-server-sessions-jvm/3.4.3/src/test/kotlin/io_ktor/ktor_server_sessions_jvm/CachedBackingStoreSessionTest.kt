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
import io.ktor.http.HttpStatusCode
import io.ktor.http.setCookie
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sessions.CacheStorage
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
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * In-process [CacheStorage] layered over a slower backing [SessionStorage].
 *
 * A conference ticketing site keeps each attendee's seat selection in a remote session store,
 * here a stand-in that logs every call, and fronts it with a [CacheStorage] so page views do
 * not each pay a round trip. The test checks that new and changed sessions are written through
 * to the store, that re-saving an unchanged session never reaches it, and that logging out
 * invalidates the stored row.
 */
class CachedBackingStoreSessionTest {

    @Serializable
    data class SeatSelection(val attendee: String, val seats: List<String> = emptyList())

    /** A remote session store that records every write and invalidation it receives. */
    class RemoteSessionStore : SessionStorage {
        private val mutex: Mutex = Mutex()
        private val rows: MutableMap<String, String> = linkedMapOf()
        private val changes: MutableList<String> = mutableListOf()

        override suspend fun write(id: String, value: String): Unit = mutex.withLock {
            rows[id] = value
            changes += "write:$id"
        }

        override suspend fun read(id: String): String = mutex.withLock {
            rows[id] ?: throw NoSuchElementException("Session $id not found")
        }

        override suspend fun invalidate(id: String): Unit = mutex.withLock {
            rows.remove(id)
            changes += "invalidate:$id"
        }

        suspend fun changeLog(): List<String> = mutex.withLock { changes.toList() }

        suspend fun storedValue(id: String): String? = mutex.withLock { rows[id] }
    }

    @Test
    fun cacheWritesThroughOnlyWhenTheSessionChanges() = testApplication {
        val remote = RemoteSessionStore()
        val cached = CacheStorage(remote, 60_000L)
        assertSame(remote, cached.delegate)
        var nextId = 0

        application {
            install(Sessions) {
                cookie<SeatSelection>("SEATS", cached) {
                    cookie.path = "/"
                    identity { "attendee-${++nextId}" }
                }
            }
            routing {
                post("/register/{attendee}") {
                    call.sessions.set(SeatSelection(attendee = call.parameters["attendee"]!!))
                    call.respondText("registered")
                }
                post("/seats/{seat}") {
                    val selection: SeatSelection? = call.sessions.get<SeatSelection>()
                    if (selection == null) {
                        call.respondText("register first", status = HttpStatusCode.Unauthorized)
                    } else {
                        val seat: String = call.parameters["seat"]!!
                        val seats: List<String> = (selection.seats + seat).distinct()
                        call.sessions.set(selection.copy(seats = seats))
                        call.respondText("holding ${seats.joinToString(",")}")
                    }
                }
                get("/basket") {
                    val selection: SeatSelection? = call.sessions.get<SeatSelection>()
                    if (selection == null) {
                        call.respondText("register first", status = HttpStatusCode.Unauthorized)
                    } else {
                        call.respondText("${selection.attendee}:${selection.seats.joinToString(",")}")
                    }
                }
                post("/checkout") {
                    call.sessions.clear<SeatSelection>()
                    call.respondText("tickets issued")
                }
            }
        }

        val attendee = createClient { install(HttpCookies) }

        val registration: HttpResponse = attendee.post("/register/dana")
        assertEquals("registered", registration.bodyAsText())
        assertEquals("attendee-1", registration.setCookie().single { it.name == "SEATS" }.value)
        assertEquals(listOf("write:attendee-1"), remote.changeLog())

        assertEquals("holding A12", attendee.post("/seats/A12").bodyAsText())
        assertEquals("holding A12,A13", attendee.post("/seats/A13").bodyAsText())
        assertEquals(listOf("write:attendee-1", "write:attendee-1", "write:attendee-1"), remote.changeLog())
        val stored: String = remote.storedValue("attendee-1")!!
        assertTrue(stored.contains("dana") && stored.contains("A13"), stored)

        val writesBeforeUnchanged: List<String> = remote.changeLog()
        assertEquals("holding A12,A13", attendee.post("/seats/A12").bodyAsText())
        repeat(3) { assertEquals("dana:A12,A13", attendee.get("/basket").bodyAsText()) }
        assertEquals(writesBeforeUnchanged, remote.changeLog(), "an unchanged session is not written again")
        assertEquals(stored, remote.storedValue("attendee-1"))

        val checkout: HttpResponse = attendee.post("/checkout")
        assertEquals("tickets issued", checkout.bodyAsText())
        assertEquals("invalidate:attendee-1", remote.changeLog().last())
        assertEquals(null, remote.storedValue("attendee-1"))
        assertEquals(HttpStatusCode.Unauthorized, attendee.get("/basket").status)
    }
}
