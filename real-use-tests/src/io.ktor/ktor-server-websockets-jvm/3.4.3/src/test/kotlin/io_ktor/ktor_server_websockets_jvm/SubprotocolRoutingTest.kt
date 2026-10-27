/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_websockets_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets

/**
 * Covers subprotocol-based route selection in the plugin's routing: a chat endpoint serves two
 * wire formats on one path, picks the handler from the `Sec-WebSocket-Protocol` the client
 * offers, echoes the chosen protocol in the handshake, and refuses an upgrade offering none it serves.
 */
class SubprotocolRoutingTest {

    /** What one client session observed: the negotiated protocol and the replies it received. */
    private data class ChatSession(val protocol: String?, val replies: List<String>)

    @Test
    fun selectsTheLegacyHandlerForAV1Client(): Unit = testApplication {
        installChat()
        val session: ChatSession = chat(offeredProtocols = PROTOCOL_V1, messages = listOf("hello", "bye"))

        assertEquals(PROTOCOL_V1, session.protocol)
        assertEquals(listOf("ada: hello", "ada: bye"), session.replies)
    }

    @Test
    fun selectsTheStructuredHandlerForAV2Client(): Unit = testApplication {
        installChat()
        val session: ChatSession = chat(offeredProtocols = PROTOCOL_V2, messages = listOf("hello", "bye"))

        assertEquals(PROTOCOL_V2, session.protocol)
        assertEquals(
            listOf(
                """{"seq":1,"from":"ada","text":"hello"}""",
                """{"seq":2,"from":"ada","text":"bye"}""",
            ),
            session.replies,
        )
    }

    @Test
    fun picksTheSupportedProtocolAmongSeveralOffered(): Unit = testApplication {
        installChat()
        val session: ChatSession = chat(offeredProtocols = "chat.v3, $PROTOCOL_V1", messages = listOf("ping"))

        assertEquals(PROTOCOL_V1, session.protocol)
        assertEquals(listOf("ada: ping"), session.replies)
    }

    @Test
    fun refusesTheUpgradeWhenNoOfferedProtocolIsServed(): Unit = testApplication {
        installChat()
        val client: HttpClient = createClient {
            install(ClientWebSockets)
        }
        var opened = false

        val outcome: Result<Unit> = runCatching {
            client.webSocket("/chat", request = { header(HttpHeaders.SecWebSocketProtocol, "chat.v3") }) {
                opened = true
            }
        }

        val failure: Throwable? = outcome.exceptionOrNull()
        assertNotNull(failure, "the upgrade should be refused")
        assertEquals("WebSocket connection failed", failure!!.message)
        assertFalse(opened, "no session should open without a matching subprotocol")
    }

    private fun ApplicationTestBuilder.installChat() {
        application {
            install(WebSockets)
            routing {
                webSocket("/chat", protocol = PROTOCOL_V1) {
                    for (frame: Frame in incoming) {
                        send(Frame.Text("$USER: ${(frame as Frame.Text).readText()}"))
                    }
                }
                webSocket("/chat", protocol = PROTOCOL_V2) {
                    var seq = 0
                    for (frame: Frame in incoming) {
                        seq += 1
                        val text: String = (frame as Frame.Text).readText()
                        send(Frame.Text("""{"seq":$seq,"from":"$USER","text":"$text"}"""))
                    }
                }
            }
        }
    }

    /** Opens `/chat` offering [offeredProtocols], sends each message, and collects one reply per message. */
    private suspend fun ApplicationTestBuilder.chat(offeredProtocols: String, messages: List<String>): ChatSession {
        val client: HttpClient = createClient {
            install(ClientWebSockets)
        }
        var protocol: String? = null
        val replies: MutableList<String> = mutableListOf()

        client.webSocket("/chat", request = { header(HttpHeaders.SecWebSocketProtocol, offeredProtocols) }) {
            protocol = call.response.headers[HttpHeaders.SecWebSocketProtocol]
            for (message: String in messages) {
                send(Frame.Text(message))
                replies += (incoming.receive() as Frame.Text).readText()
            }
            close(CloseReason(CloseReason.Codes.NORMAL, "left the room"))
        }
        return ChatSession(protocol, replies)
    }

    private companion object {

        const val PROTOCOL_V1: String = "chat.v1"

        const val PROTOCOL_V2: String = "chat.v2"

        const val USER: String = "ada"
    }
}
