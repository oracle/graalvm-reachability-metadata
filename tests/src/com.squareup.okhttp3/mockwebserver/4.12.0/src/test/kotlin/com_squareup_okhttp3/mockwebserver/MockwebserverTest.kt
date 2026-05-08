/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.mockwebserver

import java.net.HttpURLConnection
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import okhttp3.Headers.Companion.headersOf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.PushPromise
import okhttp3.mockwebserver.QueueDispatcher
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class MockwebserverTest {
    @Test
    fun recordsHttpRequestAndServesQueuedResponse(): Unit {
        val client: OkHttpClient = newClient()
        try {
            MockWebServer().use { server: MockWebServer ->
                server.enqueue(
                    MockResponse()
                        .setResponseCode(HttpURLConnection.HTTP_CREATED)
                        .setHeader("Content-Type", "text/plain; charset=utf-8")
                        .addHeader("X-Server", "mock")
                        .setBody("created"),
                )

                val request: Request = Request.Builder()
                    .url(server.url("/users?active=true"))
                    .header("X-Client", "integration-test")
                    .post("name=alice".toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response: Response ->
                    assertThat(response.code).isEqualTo(HttpURLConnection.HTTP_CREATED)
                    assertThat(response.header("X-Server")).isEqualTo("mock")
                    assertThat(response.body!!.string()).isEqualTo("created")
                }

                val recorded: RecordedRequest = server.awaitRequest()
                assertThat(recorded.requestLine).isEqualTo("POST /users?active=true HTTP/1.1")
                assertThat(recorded.method).isEqualTo("POST")
                assertThat(recorded.path).isEqualTo("/users?active=true")
                assertThat(recorded.requestUrl!!.encodedPath).isEqualTo("/users")
                assertThat(recorded.requestUrl!!.queryParameter("active")).isEqualTo("true")
                assertThat(recorded.getHeader("X-Client")).isEqualTo("integration-test")
                assertThat(recorded.bodySize).isEqualTo("name=alice".length.toLong())
                assertThat(recorded.body.readUtf8()).isEqualTo("name=alice")
                assertThat(server.requestCount).isEqualTo(1)
                assertThat(server.port).isPositive()
                assertThat(server.toProxyAddress().type()).isEqualTo(java.net.Proxy.Type.HTTP)
            }
        } finally {
            client.closeResources()
        }
    }

    @Test
    fun truncatesRecordedRequestBodyAndStreamsChunkedResponseWithTrailers(): Unit {
        val client: OkHttpClient = newClient()
        try {
            MockWebServer().use { server: MockWebServer ->
                server.bodyLimit = 5L
                server.enqueue(
                    MockResponse()
                        .setChunkedBody("chunk-one chunk-two", 4)
                        .setTrailers(headersOf("X-Trailer", "done")),
                )

                val request: Request = Request.Builder()
                    .url(server.url("/upload"))
                    .post("abcdefghij".toRequestBody("text/plain".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response: Response ->
                    assertThat(response.code).isEqualTo(HttpURLConnection.HTTP_OK)
                    assertThat(response.header("Transfer-encoding")).isEqualTo("chunked")
                    assertThat(response.body!!.string()).isEqualTo("chunk-one chunk-two")
                    assertThat(response.trailers()["X-Trailer"]).isEqualTo("done")
                }

                val recorded: RecordedRequest = server.awaitRequest()
                assertThat(recorded.bodySize).isEqualTo(10L)
                assertThat(recorded.body.readUtf8()).isEqualTo("abcde")
                assertThat(recorded.chunkSizes).isEmpty()
            }
        } finally {
            client.closeResources()
        }
    }

    @Test
    fun customDispatcherRoutesRequestsFromRecordedMetadata(): Unit {
        val client: OkHttpClient = newClient()
        val shutdownCalled: CountDownLatch = CountDownLatch(1)
        try {
            MockWebServer().use { server: MockWebServer ->
                server.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        return when (request.path) {
                            "/json" -> MockResponse()
                                .setHeader("Content-Type", "application/json")
                                .setBody("""{"ok":true,"method":"${request.method}"}""")

                            "/missing" -> MockResponse()
                                .setResponseCode(HttpURLConnection.HTTP_NOT_FOUND)
                                .setBody("missing")

                            else -> MockResponse()
                                .setResponseCode(HttpURLConnection.HTTP_BAD_REQUEST)
                                .setBody("unexpected")
                        }
                    }

                    override fun peek(): MockResponse = MockResponse().apply {
                        socketPolicy = SocketPolicy.KEEP_OPEN
                    }

                    override fun shutdown(): Unit {
                        shutdownCalled.countDown()
                    }
                }

                val jsonRequest: Request = Request.Builder().url(server.url("/json")).build()
                client.newCall(jsonRequest).execute().use { response: Response ->
                    assertThat(response.code).isEqualTo(HttpURLConnection.HTTP_OK)
                    assertThat(response.header("Content-Type")).isEqualTo("application/json")
                    assertThat(response.body!!.string()).isEqualTo("""{"ok":true,"method":"GET"}""")
                }

                val missingRequest: Request = Request.Builder().url(server.url("/missing")).build()
                client.newCall(missingRequest).execute().use { response: Response ->
                    assertThat(response.code).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND)
                    assertThat(response.body!!.string()).isEqualTo("missing")
                }

                assertThat(server.awaitRequest().path).isEqualTo("/json")
                assertThat(server.awaitRequest().path).isEqualTo("/missing")
            }

            assertThat(shutdownCalled.await(2, TimeUnit.SECONDS)).isTrue()
        } finally {
            client.closeResources()
        }
    }

    @Test
    fun queueDispatcherCanFailFastWhenNoScriptedResponsesRemain(): Unit {
        val client: OkHttpClient = newClient()
        try {
            MockWebServer().use { server: MockWebServer ->
                val dispatcher: QueueDispatcher = QueueDispatcher().apply {
                    setFailFast(
                        MockResponse()
                            .setResponseCode(429)
                            .setBody("try later"),
                    )
                }
                server.dispatcher = dispatcher

                val request: Request = Request.Builder().url(server.url("/unmatched")).build()
                client.newCall(request).execute().use { response: Response ->
                    assertThat(response.code).isEqualTo(429)
                    assertThat(response.body!!.string()).isEqualTo("try later")
                }

                assertThat(server.awaitRequest().path).isEqualTo("/unmatched")
            }
        } finally {
            client.closeResources()
        }
    }

    @Test
    fun upgradesHttpRequestToWebSocketAndRecordsHandshake(): Unit {
        val client: OkHttpClient = newClient()
        val opened: CountDownLatch = CountDownLatch(1)
        val echoed: CountDownLatch = CountDownLatch(1)
        val closed: CountDownLatch = CountDownLatch(1)
        val echoedMessage: AtomicReference<String> = AtomicReference()
        val failure: AtomicReference<Throwable> = AtomicReference()
        var webSocket: WebSocket? = null

        try {
            MockWebServer().use { server: MockWebServer ->
                server.enqueue(
                    MockResponse().withWebSocketUpgrade(
                        object : WebSocketListener() {
                            override fun onMessage(webSocket: WebSocket, text: String): Unit {
                                webSocket.send("echo:$text")
                                webSocket.close(1000, "server done")
                            }
                        },
                    ),
                )

                val request: Request = Request.Builder().url(server.url("/socket")).build()
                webSocket = client.newWebSocket(
                    request,
                    object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response): Unit {
                            opened.countDown()
                            webSocket.send("hello")
                        }

                        override fun onMessage(webSocket: WebSocket, text: String): Unit {
                            echoedMessage.set(text)
                            echoed.countDown()
                        }

                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String): Unit {
                            webSocket.close(code, reason)
                            closed.countDown()
                        }

                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String): Unit {
                            closed.countDown()
                        }

                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?): Unit {
                            failure.set(t)
                            opened.countDown()
                            echoed.countDown()
                            closed.countDown()
                        }
                    },
                )

                assertThat(opened.await(2, TimeUnit.SECONDS)).isTrue()
                assertThat(echoed.await(2, TimeUnit.SECONDS)).isTrue()
                assertThat(closed.await(2, TimeUnit.SECONDS)).isTrue()
                assertThat(failure.get()).isNull()
                assertThat(echoedMessage.get()).isEqualTo("echo:hello")

                val recorded: RecordedRequest = server.awaitRequest()
                assertThat(recorded.path).isEqualTo("/socket")
                assertThat(recorded.getHeader("Upgrade")).isEqualTo("websocket")
                assertThat(recorded.getHeader("Connection")!!.lowercase()).contains("upgrade")
            }
        } finally {
            webSocket?.cancel()
            client.closeResources()
        }
    }

    @Test
    fun mockResponseConfigurationIsDefensivelyCopiedAndInspectable(): Unit {
        val pushPromise: PushPromise = PushPromise(
            "GET",
            "/pushed-resource",
            headersOf("Accept", "text/plain"),
            MockResponse().setBody("pushed"),
        )
        val original: MockResponse = MockResponse()
            .setStatus("HTTP/1.1 202 Accepted")
            .clearHeaders()
            .addHeader("X-Multi", "one")
            .addHeader("X-Multi", "two")
            .addHeaderLenient("X-Lenient", " value")
            .setHeader("X-Replace", 1)
            .setBody(Buffer().writeUtf8("payload"))
            .throttleBody(3L, 250L, TimeUnit.MILLISECONDS)
            .setHeadersDelay(10L, TimeUnit.MILLISECONDS)
            .setBodyDelay(20L, TimeUnit.MILLISECONDS)
            .withPush(pushPromise)
            .apply {
                socketPolicy = SocketPolicy.DISCONNECT_AT_END
                http2ErrorCode = 8
            }

        val cloned: MockResponse = original.clone()
        original.removeHeader("X-Replace").setBody("changed")

        assertThat(cloned.status).isEqualTo("HTTP/1.1 202 Accepted")
        assertThat(cloned.toString()).isEqualTo("HTTP/1.1 202 Accepted")
        assertThat(cloned.headers.values("X-Multi")).containsExactly("one", "two")
        assertThat(cloned.headers["X-Lenient"]).isEqualTo("value")
        assertThat(cloned.headers["X-Replace"]).isEqualTo("1")
        assertThat(cloned.getBody()!!.readUtf8()).isEqualTo("payload")
        assertThat(cloned.throttleBytesPerPeriod).isEqualTo(3L)
        assertThat(cloned.getThrottlePeriod(TimeUnit.MILLISECONDS)).isEqualTo(250L)
        assertThat(cloned.getHeadersDelay(TimeUnit.MILLISECONDS)).isEqualTo(10L)
        assertThat(cloned.getBodyDelay(TimeUnit.MILLISECONDS)).isEqualTo(20L)
        assertThat(cloned.socketPolicy).isEqualTo(SocketPolicy.DISCONNECT_AT_END)
        assertThat(cloned.http2ErrorCode).isEqualTo(8)
        assertThat(cloned.pushPromises).containsExactly(pushPromise)
        assertThat(pushPromise.method).isEqualTo("GET")
        assertThat(pushPromise.path).isEqualTo("/pushed-resource")
        assertThat(pushPromise.headers["Accept"]).isEqualTo("text/plain")
        assertThat(pushPromise.response.getBody()!!.readUtf8()).isEqualTo("pushed")
    }
}

private fun newClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(2, TimeUnit.SECONDS)
    .readTimeout(2, TimeUnit.SECONDS)
    .writeTimeout(2, TimeUnit.SECONDS)
    .callTimeout(5, TimeUnit.SECONDS)
    .build()

private fun OkHttpClient.closeResources(): Unit {
    dispatcher.executorService.shutdown()
    connectionPool.evictAll()
    cache?.close()
}

private fun MockWebServer.awaitRequest(): RecordedRequest =
    takeRequest(2, TimeUnit.SECONDS) ?: throw AssertionError("Expected MockWebServer to record a request")
