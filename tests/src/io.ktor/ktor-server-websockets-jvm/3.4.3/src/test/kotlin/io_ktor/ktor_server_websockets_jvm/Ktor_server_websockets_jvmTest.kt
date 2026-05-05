/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_websockets_jvm

import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.server.websocket.pingInterval
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.server.websocket.webSocketRaw
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

public class Ktor_server_websockets_jvmTest {
    @Test
    fun defaultWebSocketSessionEchoesTextAndExposesApplicationCall(): Unit = testApplication {
        val observedRoom: AtomicReference<String> = AtomicReference()
        val observedSessionConfiguration: AtomicReference<String> = AtomicReference()

        install(ServerWebSockets) {
            pingPeriod = null
            timeout = 2.seconds
            maxFrameSize = 64 * 1024
            masking = false
        }
        routing {
            webSocket("/rooms/{room}") {
                observedRoom.set(call.parameters["room"])
                observedSessionConfiguration.set("$pingIntervalMillis:$timeoutMillis:$maxFrameSize:$masking")

                val request: Frame = withTimeout(RequestTimeout) { incoming.receive() }
                assertThat(request).isInstanceOf(Frame.Text::class.java)

                send(Frame.Text("room=${call.parameters["room"]};message=${(request as Frame.Text).readText()}"))
            }
        }

        val client = createClient {
            install(ClientWebSockets)
        }
        client.webSocket("/rooms/blue") {
            send(Frame.Text("hello"))

            val response: Frame = withTimeout(RequestTimeout) { incoming.receive() }
            assertThat(response).isInstanceOf(Frame.Text::class.java)
            assertThat((response as Frame.Text).readText()).isEqualTo("room=blue;message=hello")
        }

        assertThat(observedRoom.get()).isEqualTo("blue")
        assertThat(observedSessionConfiguration.get()).isEqualTo("0:2000:65536:false")
    }

    @Test
    fun rawWebSocketEndpointReceivesLowLevelFrames(): Unit = testApplication {
        val observedUpgradeHeader: AtomicReference<String> = AtomicReference()

        install(ServerWebSockets) {
            pingPeriod = null
            timeout = 2.seconds
            maxFrameSize = 8 * 1024
        }
        routing {
            webSocketRaw("/raw") {
                observedUpgradeHeader.set(call.request.headers[HttpHeaders.Upgrade])

                val request: Frame = withTimeout(RequestTimeout) { incoming.receive() }
                assertThat(request).isInstanceOf(Frame.Text::class.java)

                send(Frame.Text("raw:${(request as Frame.Text).readText().uppercase()}"))
                flush()
            }
        }

        val client = createClient {
            install(ClientWebSockets)
        }
        client.webSocket("/raw") {
            send(Frame.Text("payload"))

            val response: Frame = withTimeout(RequestTimeout) { incoming.receive() }
            assertThat(response).isInstanceOf(Frame.Text::class.java)
            assertThat((response as Frame.Text).readText()).isEqualTo("raw:PAYLOAD")
        }

        assertThat(observedUpgradeHeader.get()).isEqualTo("websocket")
    }

    @Test
    fun defaultWebSocketSessionExposesPeerCloseReason(): Unit = testApplication {
        val observedCloseReason: CompletableDeferred<CloseReason?> = CompletableDeferred()

        install(ServerWebSockets) {
            pingPeriod = null
            timeout = 2.seconds
        }
        routing {
            webSocket("/close-reason") {
                val reason: CloseReason? = withTimeout(RequestTimeout) { closeReason.await() }
                observedCloseReason.complete(reason)
            }
        }

        val client = createClient {
            install(ClientWebSockets)
        }
        client.webSocket("/close-reason") {
            withTimeout(RequestTimeout) {
                close(CloseReason(CloseReason.Codes.NORMAL, "client completed"))
            }
        }

        val reason: CloseReason? = withTimeout(RequestTimeout) { observedCloseReason.await() }
        assertThat(reason?.knownReason).isEqualTo(CloseReason.Codes.NORMAL)
        assertThat(reason?.message).isEqualTo("client completed")
    }

    @Test
    fun websocketRouteCanRequireSubprotocol(): Unit = testApplication {
        val observedProtocolHeader: AtomicReference<String> = AtomicReference()

        install(ServerWebSockets) {
            pingPeriod = null
            timeout = 2.seconds
        }
        routing {
            webSocket("/protocol", protocol = "chat.v1") {
                observedProtocolHeader.set(call.request.headers[HttpHeaders.SecWebSocketProtocol])
                send(Frame.Text("protocol accepted"))
            }
        }

        val client = createClient {
            install(ClientWebSockets)
        }
        client.webSocket("/protocol", request = {
            header(HttpHeaders.SecWebSocketProtocol, "fallback, chat.v1")
        }) {
            val response: Frame = withTimeout(RequestTimeout) { incoming.receive() }
            assertThat(response).isInstanceOf(Frame.Text::class.java)
            assertThat((response as Frame.Text).readText()).isEqualTo("protocol accepted")
        }

        assertThat(observedProtocolHeader.get()).isEqualTo("fallback, chat.v1")
    }

    @Test
    fun publicPluginConfigurationKeepsDurationAndFrameSettings(): Unit {
        val options = ServerWebSockets.WebSocketOptions().apply {
            pingPeriod = 125.milliseconds
            timeout = 3.seconds
            maxFrameSize = 4096
            masking = true
        }
        val plugin = ServerWebSockets(
            pingInterval = options.pingPeriod,
            timeout = options.timeout,
            maxFrameSize = options.maxFrameSize,
            masking = options.masking,
        )

        assertThat(options.pingPeriod).isEqualTo(125.milliseconds)
        assertThat(options.timeout).isEqualTo(3.seconds)
        assertThat(plugin.pingInterval).isEqualTo(125.milliseconds)
        assertThat(plugin.timeout).isEqualTo(3.seconds)
        assertThat(plugin.maxFrameSize).isEqualTo(4096)
        assertThat(plugin.masking).isTrue()
        assertThat(plugin.coroutineContext).isNotNull
    }

    private companion object {
        const val RequestTimeout: Long = 5_000L
    }
}
