/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_sessions_jvm

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.server.sessions.SessionStorageMemory
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.get
import io.ktor.server.sessions.header
import io.ktor.server.sessions.sessionId
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.testApplication
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Server-side session storage with session IDs carried in an HTTP header.
 *
 * A mobile API keeps each device's draft order in a [SessionStorageMemory] on the server and
 * hands the app only an opaque ID in the `X-Order-Session` header. The app replays that header
 * on later calls, the server resolves the ID back to the stored draft, and checkout
 * invalidates the stored entry so the old ID no longer resolves.
 */
class HeaderSessionServerStorageTest {

    @Serializable
    data class DraftOrder(val deviceId: String, val items: List<String>)

    @Test
    fun headerCarriesIdWhileDraftLivesInServerStorage() = testApplication {
        val storage = SessionStorageMemory()
        val issuedIds = AtomicInteger()

        application {
            install(Sessions) {
                header<DraftOrder>("X-Order-Session", storage) {
                    identity { "order-${issuedIds.incrementAndGet()}" }
                }
            }
            routing {
                post("/orders") {
                    call.sessions.set(DraftOrder(deviceId = call.receiveText(), items = emptyList()))
                    call.respondText("draft opened", status = HttpStatusCode.Created)
                }
                put("/orders/items") {
                    val draft: DraftOrder? = call.sessions.get<DraftOrder>()
                    if (draft == null) {
                        call.respondText("no draft", status = HttpStatusCode.NotFound)
                    } else {
                        call.sessions.set(draft.copy(items = draft.items + call.receiveText()))
                        call.respondText("${call.sessionId<DraftOrder>()}:${draft.items.size + 1}")
                    }
                }
                get("/orders") {
                    val draft: DraftOrder? = call.sessions.get<DraftOrder>()
                    call.respondText(draft?.let { "${it.deviceId}=${it.items.joinToString(",")}" } ?: "none")
                }
                delete("/orders") {
                    val draft: DraftOrder? = call.sessions.get<DraftOrder>()
                    call.sessions.clear<DraftOrder>()
                    call.respondText("checked out ${draft?.items?.size ?: 0} items")
                }
            }
        }

        val opened: HttpResponse = client.post("/orders") { setBody("pixel-9") }
        assertEquals(HttpStatusCode.Created, opened.status)
        val sessionId: String = opened.headers["X-Order-Session"]!!
        assertEquals("order-1", sessionId, "the header carries only the generated ID")

        val stored: String = storage.read(sessionId)
        assertTrue(stored.contains("pixel-9"), "the draft itself lives on the server: $stored")

        val firstItem: HttpResponse = client.put("/orders/items") {
            header("X-Order-Session", sessionId)
            setBody("espresso")
        }
        assertEquals("order-1:1", firstItem.bodyAsText())
        assertEquals(sessionId, firstItem.headers["X-Order-Session"], "updates keep the same ID")
        val secondItem: HttpResponse = client.put("/orders/items") {
            header("X-Order-Session", sessionId)
            setBody("croissant")
        }
        assertEquals("order-1:2", secondItem.bodyAsText())
        assertTrue(storage.read(sessionId).contains("croissant"), "storage holds the updated draft")

        val otherDevice: HttpResponse = client.post("/orders") { setBody("galaxy-s25") }
        val otherId: String = otherDevice.headers["X-Order-Session"]!!
        assertEquals("order-2", otherId)
        val otherView: String = client.get("/orders") { header("X-Order-Session", otherId) }.bodyAsText()
        assertEquals("galaxy-s25=", otherView, "each ID resolves to its own draft")

        val view: String = client.get("/orders") { header("X-Order-Session", sessionId) }.bodyAsText()
        assertEquals("pixel-9=espresso,croissant", view)
        assertEquals("none", client.get("/orders").bodyAsText(), "no header means no session")
        assertEquals("none", client.get("/orders") { header("X-Order-Session", "order-99") }.bodyAsText())

        val checkout: HttpResponse = client.delete("/orders") { header("X-Order-Session", sessionId) }
        assertEquals("checked out 2 items", checkout.bodyAsText())
        assertThrows<NoSuchElementException> { storage.read(sessionId) }
        assertEquals("none", client.get("/orders") { header("X-Order-Session", sessionId) }.bodyAsText())
        val staleItem: HttpResponse = client.put("/orders/items") {
            header("X-Order-Session", sessionId)
            setBody("muffin")
        }
        assertEquals(HttpStatusCode.NotFound, staleItem.status)
        assertNull(staleItem.headers["X-Order-Session"], "a stale ID is not reissued")

        assertTrue(storage.read(otherId).contains("galaxy-s25"), "other drafts survive the checkout")
        assertEquals(2, issuedIds.get())
    }
}
