/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_websocket_serialization_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.websocket.WebSockets as ServerWebSockets

/**
 * Typed JSON messaging over WebSockets, where a CIO chat server and a CIO client
 * exchange kotlinx-serialization classes through `sendSerialized` and
 * `receiveDeserialized` with the kotlinx JSON WebSocket converter.
 *
 * The server numbers every post it receives, answers with a sealed `ChatEvent`,
 * and says farewell with a summary when a client posts `/quit`.
 */
public class JsonChatRoomTest {
    /** A post a chat client sends to the room. */
    @Serializable
    data class ChatPost(val author: String, val text: String, val tags: List<String> = emptyList())

    /** Everything the room sends back, told apart by the `event` discriminator. */
    @Serializable
    sealed class ChatEvent {
        @Serializable
        @SerialName("delivered")
        data class Delivered(val sequence: Int, val post: ChatPost, val wordCount: Int) : ChatEvent()

        @Serializable
        @SerialName("farewell")
        data class Farewell(val author: String, val postsDelivered: Int, val authors: Set<String>) : ChatEvent()
    }

    private lateinit var server: EmbeddedServer<*, *>
    private var port: Int = 0

    @BeforeEach
    fun startChatServer() {
        server = embeddedServer(ServerCIO, port = 0, host = HOST) {
            install(ServerWebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(CHAT_JSON)
            }
            routing {
                webSocket("/chat") {
                    val authors: MutableSet<String> = linkedSetOf()
                    var sequence: Int = 0
                    while (true) {
                        val post: ChatPost = receiveDeserialized<ChatPost>()
                        if (post.text == QUIT_COMMAND) {
                            val farewell: ChatEvent = ChatEvent.Farewell(post.author, sequence, authors)
                            sendSerialized(farewell)
                            close(CloseReason(CloseReason.Codes.NORMAL, "bye ${post.author}"))
                            break
                        }
                        sequence++
                        authors += post.author
                        val wordCount: Int = post.text.split(' ').count { it.isNotBlank() }
                        val delivered: ChatEvent = ChatEvent.Delivered(sequence, post, wordCount)
                        sendSerialized(delivered)
                    }
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().single().port }
    }

    @AfterEach
    fun stopChatServer() {
        server.stop(gracePeriodMillis = 100, timeoutMillis = 5_000)
    }

    @Test
    fun `chat client receives numbered deliveries and a farewell summary`(): Unit = runBlocking {
        val posts: List<ChatPost> = listOf(
            ChatPost("alice", "hello room", listOf("greeting")),
            ChatPost("bob", "hi alice, how are you today"),
            ChatPost("alice", "great"),
        )
        val events: MutableList<ChatEvent> = mutableListOf()
        var observedClose: CloseReason? = null

        withTimeout(TIMEOUT_MILLIS) {
            chatClient().use { client: HttpClient ->
                client.webSocket(host = HOST, port = port, path = "/chat") {
                    for (post: ChatPost in posts) {
                        sendSerialized(post)
                        events += receiveDeserialized<ChatEvent>()
                    }
                    sendSerialized(ChatPost("alice", QUIT_COMMAND))
                    events += receiveDeserialized<ChatEvent>()
                    observedClose = closeReason.await()
                }
            }
        }

        assertEquals(
            listOf(
                ChatEvent.Delivered(1, posts[0], 2),
                ChatEvent.Delivered(2, posts[1], 6),
                ChatEvent.Delivered(3, posts[2], 1),
                ChatEvent.Farewell("alice", 3, setOf("alice", "bob")),
            ),
            events,
        )
        assertEquals(CloseReason(CloseReason.Codes.NORMAL, "bye alice"), observedClose)
    }

    @Test
    fun `browser client sending raw JSON text sees the event discriminator on the wire`(): Unit = runBlocking {
        var deliveredJson: JsonObject? = null

        withTimeout(TIMEOUT_MILLIS) {
            chatClient().use { client: HttpClient ->
                client.webSocket(host = HOST, port = port, path = "/chat") {
                    send(Frame.Text("""{"author":"carol","text":"plain   javascript client"}"""))
                    val reply: Frame = incoming.receive()
                    val text: String = assertInstanceOf(Frame.Text::class.java, reply).readText()
                    deliveredJson = Json.parseToJsonElement(text).jsonObject
                    sendSerialized(ChatPost("carol", QUIT_COMMAND))
                    assertInstanceOf(ChatEvent.Farewell::class.java, receiveDeserialized<ChatEvent>())
                }
            }
        }

        val delivered: JsonObject = deliveredJson!!
        assertEquals("delivered", delivered.getValue("event").jsonPrimitive.content)
        assertEquals("1", delivered.getValue("sequence").jsonPrimitive.content)
        assertEquals("3", delivered.getValue("wordCount").jsonPrimitive.content)
        val post: JsonObject = delivered.getValue("post").jsonObject
        assertEquals("carol", post.getValue("author").jsonPrimitive.content)
        assertEquals("[]", post.getValue("tags").toString())
    }

    private fun chatClient(): HttpClient = HttpClient(ClientCIO) {
        install(ClientWebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(CHAT_JSON)
        }
    }

    private companion object {
        const val HOST: String = "127.0.0.1"
        const val QUIT_COMMAND: String = "/quit"
        const val TIMEOUT_MILLIS: Long = 30_000

        val CHAT_JSON: Json = Json {
            classDiscriminator = "event"
            encodeDefaults = true
        }
    }
}
