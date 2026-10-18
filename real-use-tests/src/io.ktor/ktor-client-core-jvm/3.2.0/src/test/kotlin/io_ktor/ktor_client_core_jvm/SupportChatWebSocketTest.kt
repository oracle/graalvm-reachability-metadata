/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_core_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.application.install
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.server.websocket.receiveDeserialized as serverReceiveDeserialized
import io.ktor.server.websocket.sendSerialized as serverSendSerialized
import io.ktor.server.websocket.webSocket as serverWebSocket

/**
 * The client `WebSockets` plugin holding a two-way session with serialized messages.
 *
 * A help-desk widget opens a chat with a support bot. The server greets the customer with a
 * plain text frame, then answers each JSON question with a JSON reply and closes the session
 * with a reason once the customer says goodbye. The test checks that the client reads text
 * frames, round-trips `@Serializable` messages through the plugin's content converter, sends
 * request headers on the upgrade, and observes the close reason the server chose.
 */
class SupportChatWebSocketTest {

    /** A question the customer types into the widget. */
    @Serializable
    data class Question(val ticket: String, val text: String)

    /** The bot's answer to one [Question]. */
    @Serializable
    data class Answer(val ticket: String, val reply: String, val escalated: Boolean)

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(ServerWebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(Json)
            }
            routing {
                serverWebSocket("/support/chat") {
                    val customer: String = call.request.headers["X-Customer"] ?: "guest"
                    send(Frame.Text("Hello $customer, how can we help?"))
                    while (true) {
                        val question: Question = serverReceiveDeserialized<Question>()
                        if (question.text == "bye") {
                            close(CloseReason(CloseReason.Codes.NORMAL, "ticket ${question.ticket} closed"))
                            break
                        }
                        serverSendSerialized(answer(question))
                    }
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stop() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun chatsWithTheSupportBotOverAWebSocket() {
        val client = HttpClient(CIO) {
            install(WebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(Json)
            }
        }

        var greeting: String? = null
        val answers: MutableList<Answer> = mutableListOf()
        var closeReason: CloseReason? = null

        client.use {
            runBlocking {
                withTimeout(30_000) {
                    it.webSocket(
                        host = "127.0.0.1",
                        port = port,
                        path = "/support/chat",
                        request = { header("X-Customer", "Ada") },
                    ) {
                        greeting = (incoming.receive() as Frame.Text).readText()
                        sendSerialized(Question("T-9", "Where is my refund?"))
                        answers += receiveDeserialized<Answer>()
                        sendSerialized(Question("T-9", "I want to talk to a person"))
                        answers += receiveDeserialized<Answer>()
                        sendSerialized(Question("T-9", "bye"))
                        closeReason = this.closeReason.await()
                    }
                }
            }
        }

        assertEquals("Hello Ada, how can we help?", greeting)
        assertEquals(
            listOf(
                Answer("T-9", "Refunds reach your card within 5 business days.", false),
                Answer("T-9", "Connecting you to an agent.", true),
            ),
            answers,
        )
        assertEquals(CloseReason(CloseReason.Codes.NORMAL, "ticket T-9 closed"), closeReason)
    }

    private companion object {
        /** The bot's canned reply to [question]. */
        fun answer(question: Question): Answer = when {
            "refund" in question.text -> Answer(question.ticket, "Refunds reach your card within 5 business days.", false)
            else -> Answer(question.ticket, "Connecting you to an agent.", true)
        }
    }
}
