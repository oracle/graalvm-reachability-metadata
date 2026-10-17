/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_websockets_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
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
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets

/**
 * Covers the upgrade-header route selection of the plugin's `webSocket` routes: a stock ticker
 * serves streaming subscribers and plain HTTP pollers on one path, where only requests carrying
 * the `Connection: Upgrade` and `Upgrade: websocket` headers reach the WebSocket handler, others
 * fall through to the sibling `get` route, and a WebSocket-only path never answers a plain poll.
 */
class UpgradeHeaderRouteFallbackTest {

    @Test
    fun streamsQuotesToAWebSocketSubscriber(): Unit = testApplication {
        installTicker()
        val client: HttpClient = createClient {
            install(ClientWebSockets)
        }
        val received: MutableList<String> = mutableListOf()

        client.webSocket("/quotes") {
            send(Frame.Text("ACME,GLOBEX"))
            repeat(2) {
                received += (incoming.receive() as Frame.Text).readText()
            }
            close(CloseReason(CloseReason.Codes.NORMAL, "done"))
        }

        assertEquals(listOf("live ACME=101.5", "live GLOBEX=42.25"), received)
    }

    @Test
    fun answersAPlainPollFromTheSiblingHttpRoute(): Unit = testApplication {
        installTicker()

        val response: HttpResponse = client.get("/quotes")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("snapshot ACME=101.5;GLOBEX=42.25", response.bodyAsText())
    }

    @Test
    fun neverAnswersAPlainPollOnAWebSocketOnlyPath(): Unit = testApplication {
        installTicker()

        val response: HttpResponse = client.get("/alerts")

        assertNotEquals(HttpStatusCode.OK, response.status)
        assertNotEquals(HttpStatusCode.SwitchingProtocols, response.status)
        assertTrue(response.status.value in 400..499, "a plain poll should be refused, got ${response.status}")
    }

    private fun ApplicationTestBuilder.installTicker() {
        application {
            install(WebSockets)
            routing {
                webSocket("/quotes") {
                    val symbols: List<String> = (incoming.receive() as Frame.Text).readText().split(",")
                    for (symbol: String in symbols) {
                        send(Frame.Text("live $symbol=${PRICES.getValue(symbol)}"))
                    }
                    incoming.receiveCatching()
                }
                get("/quotes") {
                    val snapshot: String = PRICES.entries.joinToString(";") { (symbol: String, price: Double) ->
                        "$symbol=$price"
                    }
                    call.respondText("snapshot $snapshot")
                }
                webSocket("/alerts") {
                    send(Frame.Text("no alerts"))
                }
            }
        }
    }

    private companion object {

        val PRICES: Map<String, Double> = linkedMapOf("ACME" to 101.5, "GLOBEX" to 42.25)
    }
}
