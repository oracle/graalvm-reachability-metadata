/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_websockets_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets

/**
 * Covers fan-out across concurrent server sessions: a chat hub keeps every open
 * `DefaultWebSocketServerSession` in a per-room registry keyed by route parameters, broadcasts
 * each member's text frames to the whole room, and announces departures once a member's
 * `closeReason` resolves.
 */
class SessionBroadcastHubTest {

    /** The open sessions of every room, plus the close reason each departed member sent. */
    private class ChatHub {

        private val lock = Mutex()

        private val rooms: MutableMap<String, MutableMap<String, DefaultWebSocketServerSession>> = mutableMapOf()

        val departures: MutableMap<String, CompletableDeferred<CloseReason?>> = mutableMapOf()

        suspend fun join(room: String, member: String, session: DefaultWebSocketServerSession): List<String> =
            lock.withLock {
                val members: MutableMap<String, DefaultWebSocketServerSession> = rooms.getOrPut(room) { mutableMapOf() }
                members[member] = session
                departures.getOrPut(member) { CompletableDeferred() }
                members.keys.sorted()
            }

        suspend fun leave(room: String, member: String, reason: CloseReason?) {
            lock.withLock {
                rooms[room]?.remove(member)
                departures.getOrPut(member) { CompletableDeferred() }.complete(reason)
            }
        }

        /** Sends [text] to every member of [room] except [skip], in member-name order. */
        suspend fun broadcast(room: String, text: String, skip: String? = null) {
            val targets: List<DefaultWebSocketServerSession> = lock.withLock {
                rooms[room].orEmpty().toSortedMap().filterKeys { it != skip }.values.toList()
            }
            for (target: DefaultWebSocketServerSession in targets) {
                target.send(text)
            }
        }
    }

    @Test
    fun broadcastsMessagesAndDeparturesToTheWholeRoom(): Unit = testApplication {
        val hub = ChatHub()
        application { chatHub(hub) }
        val client: HttpClient = createClient { install(ClientWebSockets) }

        val alice: DefaultClientWebSocketSession = client.webSocketSession("/rooms/general/alice")
        assertEquals("welcome alice, online: alice", alice.nextText())

        val bob: DefaultClientWebSocketSession = client.webSocketSession("/rooms/general/bob")
        assertEquals("welcome bob, online: alice, bob", bob.nextText())
        assertEquals("bob joined", alice.nextText())

        alice.send("lunch at noon?")
        assertEquals("[alice] lunch at noon?", alice.nextText())
        assertEquals("[alice] lunch at noon?", bob.nextText())

        bob.send("sure")
        assertEquals("[bob] sure", alice.nextText())
        assertEquals("[bob] sure", bob.nextText())

        bob.close(CloseReason(CloseReason.Codes.NORMAL, "heading out"))
        assertEquals("bob left: heading out", alice.nextText())
        val bobReason: CloseReason? = withTimeout(AWAIT_MILLIS) { hub.departures.getValue("bob").await() }
        assertEquals(CloseReason.Codes.NORMAL, bobReason?.knownReason)
        assertEquals("heading out", bobReason?.message)

        alice.close(CloseReason(CloseReason.Codes.GOING_AWAY, "done"))
        val aliceReason: CloseReason? = withTimeout(AWAIT_MILLIS) { hub.departures.getValue("alice").await() }
        assertEquals(CloseReason.Codes.GOING_AWAY, aliceReason?.knownReason)
    }

    @Test
    fun keepsEachRoomsTrafficApart(): Unit = testApplication {
        val hub = ChatHub()
        application { chatHub(hub) }
        val client: HttpClient = createClient { install(ClientWebSockets) }

        val carol: DefaultClientWebSocketSession = client.webSocketSession("/rooms/ops/carol")
        assertEquals("welcome carol, online: carol", carol.nextText())
        val dave: DefaultClientWebSocketSession = client.webSocketSession("/rooms/general/dave")
        assertEquals("welcome dave, online: dave", dave.nextText())

        dave.send("anyone around?")
        assertEquals("[dave] anyone around?", dave.nextText())

        val erin: DefaultClientWebSocketSession = client.webSocketSession("/rooms/ops/erin")
        assertEquals("welcome erin, online: carol, erin", erin.nextText())
        // Carol's next frame is Erin's arrival: nothing from the general room reached her first.
        assertEquals("erin joined", carol.nextText())

        erin.send("deploy is green")
        assertEquals("[erin] deploy is green", carol.nextText())
        assertEquals("[erin] deploy is green", erin.nextText())

        dave.close(CloseReason(CloseReason.Codes.NORMAL, "bye"))
        withTimeout(AWAIT_MILLIS) { hub.departures.getValue("dave").await() }
        erin.send("still here")
        // Dave's departure was announced only in his own room, so ops sees Erin's line next.
        assertEquals("[erin] still here", carol.nextText())
        assertEquals("[erin] still here", erin.nextText())

        carol.close(CloseReason(CloseReason.Codes.NORMAL, "bye"))
        assertEquals("carol left: bye", erin.nextText())
        erin.close(CloseReason(CloseReason.Codes.NORMAL, "bye"))
        withTimeout(AWAIT_MILLIS) { hub.departures.getValue("erin").await() }
    }

    private suspend fun DefaultClientWebSocketSession.nextText(): String =
        withTimeout(AWAIT_MILLIS) { (incoming.receive() as Frame.Text).readText() }

    private fun Application.chatHub(hub: ChatHub) {
        install(WebSockets)
        routing {
            webSocket("/rooms/{room}/{member}") {
                val room: String = call.parameters["room"]!!
                val member: String = call.parameters["member"]!!
                val online: List<String> = hub.join(room, member, this)
                send("welcome $member, online: ${online.joinToString()}")
                hub.broadcast(room, "$member joined", skip = member)
                try {
                    for (frame: Frame in incoming) {
                        if (frame is Frame.Text) {
                            hub.broadcast(room, "[$member] ${frame.readText()}")
                        }
                    }
                } finally {
                    val reason: CloseReason? = closeReason.await()
                    hub.leave(room, member, reason)
                    hub.broadcast(room, "$member left: ${reason?.message}")
                }
            }
        }
    }

    private companion object {

        const val AWAIT_MILLIS: Long = 10_000L
    }
}
