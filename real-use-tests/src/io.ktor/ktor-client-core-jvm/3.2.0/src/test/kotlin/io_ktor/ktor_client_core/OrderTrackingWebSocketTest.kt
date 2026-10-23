/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_core

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
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
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.websocket.WebSockets as ServerWebSockets

/** Exercises client WebSocket sessions by subscribing to live order updates and acknowledging delivery. */
class OrderTrackingWebSocketTest {
    @Test
    @Timeout(60)
    fun followsAnOrderUntilItsTrackingSessionClosesNormally(): Unit = runBlocking {
        withTimeout(45_000) {
            val server = embeddedServer(ServerCIO, host = "127.0.0.1", port = 0) {
                install(ServerWebSockets)
                routing {
                    webSocket("/orders/tracking") {
                        val subscription: Frame = incoming.receive()
                        if (subscription !is Frame.Text || subscription.readText() != "SUBSCRIBE order-42") {
                            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Order subscription required"))
                            return@webSocket
                        }
                        send("order-42: packed")
                        val acknowledgement: Frame = incoming.receive()
                        if (acknowledgement !is Frame.Text || acknowledgement.readText() != "ACK order-42") {
                            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Acknowledgement required"))
                            return@webSocket
                        }
                        send("order-42: dispatched; tracking=parcel-17")
                        close(CloseReason(CloseReason.Codes.NORMAL, "Tracking complete"))
                    }
                }
            }
            try {
                server.start(wait = false)
                val port: Int = server.engine.resolvedConnectors().single().port
                HttpClient(CIO) {
                    install(WebSockets)
                    install(HttpTimeout) {
                        requestTimeoutMillis = 15_000
                        connectTimeoutMillis = 15_000
                        socketTimeoutMillis = 15_000
                    }
                }.use { client: HttpClient ->
                    client.webSocket(urlString = "ws://127.0.0.1:$port/orders/tracking") {
                        send("SUBSCRIBE order-42")
                        val packed: Frame.Text = incoming.receive() as Frame.Text
                        assertEquals("order-42: packed", packed.readText())

                        send("ACK order-42")
                        val dispatched: Frame.Text = incoming.receive() as Frame.Text
                        assertEquals("order-42: dispatched; tracking=parcel-17", dispatched.readText())

                        val closure: CloseReason = requireNotNull(closeReason.await())
                        assertEquals(CloseReason.Codes.NORMAL.code, closure.code)
                        assertEquals("Tracking complete", closure.message)
                    }
                }
            } finally {
                server.stop(0, 10_000)
            }
        }
    }
}
