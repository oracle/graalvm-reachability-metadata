/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_sse_jvm

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.ktor.client.engine.cio.CIO as ClientCIO

/**
 * Encodes server-sent events into the `text/event-stream` wire format.
 *
 * A notification service streams its feed over a Ktor SSE route, and a plain
 * HTTP client reads the raw response body to check the frames the browser
 * would receive.
 */
public class NotificationFeedWireFormatTest {

    /** One entry of the notification feed the service publishes. */
    private data class Notification(
        val id: String,
        val kind: String,
        val message: String,
    )

    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var client: HttpClient
    private var port: Int = 0

    private val feed: List<Notification> = listOf(
        Notification(id = "n-1", kind = "order-shipped", message = "Order 1001 shipped"),
        Notification(id = "n-2", kind = "order-delayed", message = "Order 1002 delayed\nNew ETA: Friday"),
    )

    @BeforeEach
    fun startServer() {
        server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(SSE)
            routing {
                sse("/notifications") {
                    send(ServerSentEvent(retry = 5_000L, comments = "notification feed v1"))
                    for (notification: Notification in feed) {
                        send(
                            ServerSentEvent(
                                data = notification.message,
                                event = notification.kind,
                                id = notification.id,
                            ),
                        )
                    }
                    send(ServerSentEvent(data = "end of feed", event = "close"))
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
        client = HttpClient(ClientCIO)
    }

    @AfterEach
    fun stopServer() {
        client.close()
        server.stop(gracePeriodMillis = 0L, timeoutMillis = 10_000L)
    }

    @Test
    fun `feed is delivered as text event stream frames`() = runBlocking {
        val response = client.get("http://127.0.0.1:$port/notifications") {
            header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Text.EventStream, response.contentType()?.withoutParameters())
        assertEquals(
            "retry: 5000\r\n" +
                ": notification feed v1\r\n" +
                "\r\n" +
                "data: Order 1001 shipped\r\n" +
                "event: order-shipped\r\n" +
                "id: n-1\r\n" +
                "\r\n" +
                "data: Order 1002 delayed\r\n" +
                "data: New ETA: Friday\r\n" +
                "event: order-delayed\r\n" +
                "id: n-2\r\n" +
                "\r\n" +
                "data: end of feed\r\n" +
                "event: close\r\n" +
                "\r\n",
            response.bodyAsText(),
        )
    }

    @Test
    fun `event text is split on every line terminator style`() {
        val event = ServerSentEvent(
            data = "line one\r\nline two\rline three\nline four",
            comments = "first remark\nsecond remark",
        )

        assertEquals(
            "data: line one\r\n" +
                "data: line two\r\n" +
                "data: line three\r\n" +
                "data: line four\r\n" +
                ": first remark\r\n" +
                ": second remark\r\n",
            event.toString(),
        )
    }
}
