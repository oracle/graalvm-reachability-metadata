/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_websockets_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets

/**
 * Covers how the plugin ends a default server session when its handler stops: an order desk
 * whose handler throws on a malformed command has the plugin log the failure and drop the
 * connection without a close frame, while a handler that calls `close` with an application code
 * ends the session with that reason.
 */
class HandlerFailureCloseTest {

    /** What the order desk recorded about one session; read it once [ended] completes. */
    private class DeskLog {

        val accepted: MutableList<String> = mutableListOf()

        val failure: CompletableDeferred<Throwable> = CompletableDeferred()

        val ended: CompletableDeferred<Unit> = CompletableDeferred()
    }

    /** What the client observed before the server ended the session. */
    private data class ClientView(val replies: List<String>, val closeReason: CloseReason?, val incomingDrained: Boolean)

    @Test
    fun aThrowingHandlerDropsTheConnectionWithoutACloseFrame(): Unit = testApplication {
        val log = DeskLog()
        installOrderDesk(log)

        val view: ClientView = placeOrders(listOf("order 2 apples", "refund everything"))

        assertEquals(listOf("accepted 2 apples"), view.replies)
        assertEquals(ABNORMAL_CLOSURE, view.closeReason?.code)
        assertTrue(view.incomingDrained)
        val failure: Throwable = withTimeout(AWAIT_MILLIS) { log.failure.await() }
        assertTrue(failure is IllegalStateException)
        assertEquals("malformed command: refund everything", failure.message)
        withTimeout(AWAIT_MILLIS) { log.ended.await() }
        assertEquals(listOf("2 apples"), log.accepted)
    }

    @Test
    fun aHandlerCloseCarriesTheApplicationCloseCode(): Unit = testApplication {
        val log = DeskLog()
        installOrderDesk(log)

        val view: ClientView = placeOrders(listOf("order 1 pear", "order 3 plums", "order 5 figs", "order 9 kiwis"))

        assertEquals(listOf("accepted 1 pear", "accepted 3 plums", "accepted 5 figs"), view.replies)
        assertEquals(CloseReason.Codes.VIOLATED_POLICY, view.closeReason?.knownReason)
        assertEquals("order limit of $ORDER_LIMIT reached", view.closeReason?.message)
        assertTrue(view.incomingDrained)
        withTimeout(AWAIT_MILLIS) { log.ended.await() }
        assertEquals(listOf("1 pear", "3 plums", "5 figs"), log.accepted)
        assertTrue(!log.failure.isCompleted)
    }

    /** Sends each command, collects text replies until the server closes, and reads its reason. */
    private suspend fun ApplicationTestBuilder.placeOrders(commands: List<String>): ClientView {
        val client: HttpClient = createClient {
            install(ClientWebSockets)
        }
        val replies: MutableList<String> = mutableListOf()
        var serverClose: CloseReason? = null
        var incomingDrained = false

        client.webSocket("/orders") {
            for (command: String in commands) {
                send(Frame.Text(command))
            }
            try {
                while (true) {
                    val frame: Frame = incoming.receive()
                    if (frame is Frame.Text) {
                        replies += frame.readText()
                    }
                }
            } catch (closed: ClosedReceiveChannelException) {
                incomingDrained = true
            }
            serverClose = withTimeout(AWAIT_MILLIS) { closeReason.await() }
        }
        client.close()
        return ClientView(replies, serverClose, incomingDrained)
    }

    private fun ApplicationTestBuilder.installOrderDesk(log: DeskLog) {
        application {
            install(WebSockets)
            routing {
                webSocket("/orders") {
                    try {
                        takeOrders(this, log)
                    } catch (failure: IllegalStateException) {
                        log.failure.complete(failure)
                        throw failure
                    } finally {
                        log.ended.complete(Unit)
                    }
                }
            }
        }
    }

    /** Accepts `order` commands up to [ORDER_LIMIT], then closes; anything else is a handler bug. */
    private suspend fun takeOrders(session: DefaultWebSocketServerSession, log: DeskLog) {
        for (frame: Frame in session.incoming) {
            if (frame !is Frame.Text) {
                continue
            }
            val command: String = frame.readText()
            check(command.startsWith("order ")) { "malformed command: $command" }
            val item: String = command.removePrefix("order ")
            log.accepted += item
            session.send(Frame.Text("accepted $item"))
            if (log.accepted.size == ORDER_LIMIT) {
                session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "order limit of $ORDER_LIMIT reached"))
                return
            }
        }
    }

    private companion object {

        const val AWAIT_MILLIS: Long = 10_000L

        const val ORDER_LIMIT: Int = 3

        /** RFC 6455 code a client reports when the connection ends without a close frame. */
        const val ABNORMAL_CLOSURE: Short = 1006
    }
}
