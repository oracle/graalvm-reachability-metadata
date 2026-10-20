/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_sessions_jvm

import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sessions.SameSite
import io.ktor.server.sessions.SessionNotYetConfiguredException
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.TooLateSessionSetException
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.get
import io.ktor.server.sessions.getOrSet
import io.ktor.server.sessions.sameSite
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.testApplication
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Session access guards of the Sessions plugin: reads before the session is loaded, writes after
 * the response is committed, and lookups of session types that were never registered.
 *
 * A news site keeps a `VISIT` cookie holding the visitor's page-view counter, created lazily with
 * `getOrSet`. An audit plugin installed ahead of Sessions tries to read the session too early, a
 * handler tries to bump the counter after it has already responded, and a legacy endpoint asks for
 * a session type nobody configured. The application records each refusal and keeps serving.
 */
class SessionMisuseGuardsTest {

    @Serializable
    data class VisitSession(val visitorId: String, val pageViews: Int)

    @Serializable
    data class LegacyPreferences(val theme: String)

    @Test
    fun misusedSessionAccessIsRefusedWhileTheVisitCounterKeepsWorking() = testApplication {
        val auditFailures: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()
        val lateWriteFailures: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()
        var nextVisitor = 0

        application {
            val earlyAudit = createApplicationPlugin("EarlySessionAudit") {
                onCall { call ->
                    try {
                        call.sessions.get<VisitSession>()
                        auditFailures.add("read succeeded")
                    } catch (e: SessionNotYetConfiguredException) {
                        auditFailures.add(call.request.local.uri)
                    }
                }
            }
            install(earlyAudit)
            install(Sessions) {
                cookie<VisitSession>("VISIT") {
                    cookie.path = "/"
                    cookie.httpOnly = true
                    cookie.sameSite = SameSite.Strict
                }
            }
            routing {
                get("/article/{slug}") {
                    val visit: VisitSession = call.sessions.getOrSet { VisitSession("v-${++nextVisitor}", 0) }
                    val updated: VisitSession = visit.copy(pageViews = visit.pageViews + 1)
                    call.sessions.set(updated)
                    call.respondText("${updated.visitorId} read ${call.parameters["slug"]} (${updated.pageViews})")
                }
                get("/teaser") {
                    val visit: VisitSession? = call.sessions.get<VisitSession>()
                    call.respondText("teaser for ${visit?.visitorId ?: "anonymous"}")
                    try {
                        call.sessions.set(VisitSession(visit?.visitorId ?: "late", 999))
                    } catch (e: TooLateSessionSetException) {
                        lateWriteFailures.add(e.message ?: "")
                    }
                }
                get("/legacy/theme") {
                    val outcome: String = try {
                        call.sessions.get<LegacyPreferences>()?.theme ?: "none"
                    } catch (e: IllegalArgumentException) {
                        "unregistered: ${e.message}"
                    }
                    call.respondText(outcome)
                }
            }
        }

        val browser = createClient { install(HttpCookies) }

        val first = browser.get("/article/elections")
        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals("v-1 read elections (1)", first.bodyAsText())
        val setCookie: String? = first.headers[HttpHeaders.SetCookie]
        assertNotNull(setCookie)
        assertTrue(setCookie!!.startsWith("VISIT="), setCookie)
        assertTrue(setCookie.contains("SameSite=Strict"), setCookie)
        assertTrue(setCookie.contains("HttpOnly"), setCookie)

        assertEquals("v-1 read weather (2)", browser.get("/article/weather").bodyAsText())

        val teaser = browser.get("/teaser")
        assertEquals(HttpStatusCode.OK, teaser.status)
        assertEquals("teaser for v-1", teaser.bodyAsText())
        assertNull(teaser.headers[HttpHeaders.SetCookie], "a write after responding must not reach the client")
        assertEquals(1, lateWriteFailures.size, "the late write is refused exactly once")
        assertTrue(lateWriteFailures.single().isNotEmpty())

        assertEquals("v-1 read sports (3)", browser.get("/article/sports").bodyAsText(), "the late write was discarded")

        val legacy = browser.get("/legacy/theme").bodyAsText()
        assertTrue(legacy.startsWith("unregistered: "), legacy)
        assertTrue(legacy.contains("LegacyPreferences"), legacy)

        assertEquals(
            listOf("/article/elections", "/article/weather", "/teaser", "/article/sports", "/legacy/theme"),
            auditFailures.toList(),
            "every early read is refused because Sessions has not loaded the call yet"
        )
    }
}
