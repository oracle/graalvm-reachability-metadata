/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_sse_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.SSEServerContent
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.sse.SSE as ClientSSE

/**
 * Streams a POST-initiated SSE response built with SSEServerContent from an ordinary route.
 *
 * A chat assistant accepts a JSON prompt in the body of a POST request, the way
 * completion APIs do, and answers on the same call with a `text/event-stream`
 * response: each `chunk` event carries one JSON-encoded completion fragment and
 * the stream ends with a `[DONE]` sentinel. The client opens the stream with a
 * POST request and a body, stops reading at the sentinel, and reassembles the
 * reply; the server sees the prompt and the stream headers the client sent.
 */
public class ChatCompletionPostStreamTest {

    /** The prompt the client posts. */
    @Serializable
    private data class CompletionRequest(
        val conversation: String,
        val prompt: String,
    )

    /** One fragment of the streamed reply. */
    @Serializable
    private data class CompletionChunk(
        val index: Int,
        val delta: String,
    )

    /** What the server observed about one completion request. */
    private data class ObservedRequest(
        val method: String,
        val accept: String?,
        val request: CompletionRequest,
    )

    private val json: Json = Json
    private val observed: ConcurrentLinkedQueue<ObservedRequest> = ConcurrentLinkedQueue()
    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var client: HttpClient
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(SSE)
            routing {
                post("/v1/chat/completions") {
                    val request: CompletionRequest = json.decodeFromString(call.receiveText())
                    observed.add(
                        ObservedRequest(
                            method = call.request.local.method.value,
                            accept = call.request.headers[HttpHeaders.Accept],
                            request = request,
                        ),
                    )
                    val words: List<String> = "You asked: ${request.prompt}".split(" ")
                    call.respond(
                        SSEServerContent(call) {
                            words.forEachIndexed { index: Int, word: String ->
                                val delta: String = if (index == 0) word else " $word"
                                send(
                                    ServerSentEvent(
                                        data = json.encodeToString(CompletionChunk(index, delta)),
                                        event = "chunk",
                                        id = "${request.conversation}-$index",
                                    ),
                                )
                            }
                            send(ServerSentEvent(data = "[DONE]", event = "chunk"))
                            send(ServerSentEvent(data = "ignored after the sentinel", event = "chunk"))
                        },
                    )
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
        client = HttpClient(ClientCIO) {
            install(ClientSSE)
        }
    }

    @AfterEach
    fun stopServer() {
        client.close()
        server.stop(gracePeriodMillis = 0L, timeoutMillis = 10_000L)
    }

    /** Posts [request] and collects the streamed chunks up to the `[DONE]` sentinel. */
    private suspend fun complete(request: CompletionRequest): List<ServerSentEvent> = withTimeout(10_000L) {
        var events: List<ServerSentEvent> = emptyList()
        client.sse(
            request = {
                url("http://127.0.0.1:$port/v1/chat/completions")
                method = HttpMethod.Post
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(request))
            },
        ) {
            events = incoming.takeWhile { event: ServerSentEvent -> event.data != "[DONE]" }.toList()
        }
        events
    }

    @Test
    fun `a posted prompt is answered with a streamed completion`() = runBlocking {
        val request = CompletionRequest(conversation = "conv-7", prompt = "when does the train leave")
        val events: List<ServerSentEvent> = complete(request)

        val chunks: List<CompletionChunk> = events.map { json.decodeFromString<CompletionChunk>(it.data!!) }
        assertEquals((0..6).toList(), chunks.map { it.index })
        assertEquals("You asked: when does the train leave", chunks.joinToString("") { it.delta })
        assertEquals(List(7) { "chunk" }, events.map { it.event })
        assertEquals((0..6).map { "conv-7-$it" }, events.map { it.id })

        val seen: ObservedRequest = observed.single()
        assertEquals("POST", seen.method)
        assertEquals(request, seen.request)
        assertTrue(seen.accept.orEmpty().contains(ContentType.Text.EventStream.toString()), seen.accept)
    }

    @Test
    fun `each posted prompt opens its own stream`() = runBlocking {
        val replies: List<String> = listOf("first", "second question").map { prompt: String ->
            complete(CompletionRequest(conversation = "conv-$prompt", prompt = prompt))
                .map { json.decodeFromString<CompletionChunk>(it.data!!).delta }
                .joinToString("")
        }

        assertEquals(listOf("You asked: first", "You asked: second question"), replies)
        assertEquals(listOf("first", "second question"), observed.map { it.request.prompt })
    }
}
