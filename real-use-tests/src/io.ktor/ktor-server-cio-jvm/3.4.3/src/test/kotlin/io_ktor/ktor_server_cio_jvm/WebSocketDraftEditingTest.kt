/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises CIO WebSocket upgrades and bidirectional messaging through an interactive draft editor.
 */
class WebSocketDraftEditingTest {
    @Test
    @Timeout(60)
    fun `acknowledges draft revisions and closes the editing session after saving`(): Unit = runBlocking {
        val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            install(WebSockets)
            routing {
                webSocket("/drafts/{id}") {
                    val draftId: String = call.parameters["id"]!!
                    val draft: StringBuilder = StringBuilder()
                    var revision: Int = 0
                    send("opened:$draftId:0")
                    for (frame: Frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val command: String = frame.readText()
                        when {
                            command.startsWith("append:") -> {
                                draft.append(command.removePrefix("append:"))
                                revision++
                                send("revision:$revision:$draft")
                            }
                            command == "save" -> {
                                send("saved:$draftId:$revision:$draft")
                                close(CloseReason(CloseReason.Codes.NORMAL, "draft saved"))
                                break
                            }
                        }
                    }
                }
            }
        }
        try {
            withTimeout(40_000) {
                server.start(wait = false)
                val port: Int = server.engine.resolvedConnectors().single().port
                HttpClient(ClientCIO) {
                    install(ClientWebSockets)
                    install(HttpTimeout) {
                        connectTimeoutMillis = 15_000
                        requestTimeoutMillis = 15_000
                        socketTimeoutMillis = 15_000
                    }
                }.use { client: HttpClient ->
                    client.webSocket("ws://127.0.0.1:$port/drafts/release-notes") {
                        assertEquals("opened:release-notes:0", (incoming.receive() as Frame.Text).readText())

                        send("append:Release ready")
                        assertEquals("revision:1:Release ready", (incoming.receive() as Frame.Text).readText())

                        send("append: for review.")
                        assertEquals(
                            "revision:2:Release ready for review.",
                            (incoming.receive() as Frame.Text).readText(),
                        )

                        send("save")
                        assertEquals(
                            "saved:release-notes:2:Release ready for review.",
                            (incoming.receive() as Frame.Text).readText(),
                        )
                        val reason: CloseReason? = closeReason.await()
                        assertEquals(CloseReason.Codes.NORMAL.code, reason?.code)
                        assertEquals("draft saved", reason?.message)
                    }
                }
            }
        } finally {
            server.stop(0, 10_000)
        }
    }
}
