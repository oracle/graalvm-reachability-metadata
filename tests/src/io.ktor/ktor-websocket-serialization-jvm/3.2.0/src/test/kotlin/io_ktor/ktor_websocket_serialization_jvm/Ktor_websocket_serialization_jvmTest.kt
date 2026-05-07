/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_websocket_serialization_jvm

import io.ktor.serialization.WebsocketContentConverter
import io.ktor.serialization.WebsocketDeserializeException
import io.ktor.util.reflect.TypeInfo
import io.ktor.util.reflect.typeInfo
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketExtension
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.serialization.receiveDeserializedBase
import io.ktor.websocket.serialization.sendSerializedBase
import io.ktor.utils.io.InternalAPI
import java.nio.charset.Charset
import java.util.function.Consumer
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

@OptIn(InternalAPI::class)
public class Ktor_websocket_serialization_jvmTest {
    @Test
    fun `sendSerializedBase sends converter-produced frame and forwards conversion context`(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val session: TestWebSocketSession = TestWebSocketSession()
            try {
                val serializedFrame: Frame = Frame.Text("encoded-message")
                val converter: RecordingWebsocketContentConverter = RecordingWebsocketContentConverter(
                    serializedFrame = serializedFrame,
                )

                session.sendSerializedBase("message", typeInfo<String>(), converter, Charsets.UTF_16)

                assertThat(session.receiveOutgoing()).isSameAs(serializedFrame)
                assertThat(converter.serializeCalls).hasSize(1)
                val call: SerializeCall = converter.serializeCalls.single()
                assertThat(call.charset).isEqualTo(Charsets.UTF_16)
                assertThat(call.typeInfo.type).isEqualTo(String::class)
                assertThat(call.value).isEqualTo("message")
            } finally {
                session.close()
            }
        }
    }

    @Test
    fun `reified sendSerializedBase supplies the requested runtime type to the converter`(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val session: TestWebSocketSession = TestWebSocketSession()
            try {
                val serializedFrame: Frame = Frame.Text("encoded-number")
                val converter: RecordingWebsocketContentConverter = RecordingWebsocketContentConverter(
                    serializedFrame = serializedFrame,
                )

                session.sendSerializedBase<Int>(42, converter, Charsets.UTF_8)

                assertThat(session.receiveOutgoing()).isSameAs(serializedFrame)
                assertThat(converter.serializeCalls).hasSize(1)
                val call: SerializeCall = converter.serializeCalls.single()
                assertThat(call.typeInfo.type).isEqualTo(Int::class)
                assertThat(call.value).isEqualTo(42)
            } finally {
                session.close()
            }
        }
    }

    @Test
    fun `receiveDeserializedBase returns converter value for applicable frames`(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val session: TestWebSocketSession = TestWebSocketSession()
            try {
                val incomingFrame: Frame = Frame.Text("wire-payload")
                val converter: RecordingWebsocketContentConverter = RecordingWebsocketContentConverter(
                    deserializedValue = "domain-payload",
                )
                session.queueIncoming(incomingFrame)

                val value: String = session.receiveDeserializedBase(
                    typeInfo<String>(),
                    converter,
                    Charsets.ISO_8859_1,
                ) as String

                assertThat(value).isEqualTo("domain-payload")
                assertThat(converter.deserializeCalls).hasSize(1)
                val call: DeserializeCall = converter.deserializeCalls.single()
                assertThat(call.charset).isEqualTo(Charsets.ISO_8859_1)
                assertThat(call.typeInfo.type).isEqualTo(String::class)
                assertThat(call.frame).isSameAs(incomingFrame)
            } finally {
                session.close()
            }
        }
    }

    @Test
    fun `reified receiveDeserializedBase supplies the requested runtime type to the converter`(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val session: TestWebSocketSession = TestWebSocketSession()
            try {
                val converter: RecordingWebsocketContentConverter = RecordingWebsocketContentConverter(
                    deserializedValue = 123,
                )
                session.queueIncoming(Frame.Text("123"))

                val value: Int = session.receiveDeserializedBase<Int>(converter, Charsets.UTF_8) as Int

                assertThat(value).isEqualTo(123)
                assertThat(converter.deserializeCalls).hasSize(1)
                assertThat(converter.deserializeCalls.single().typeInfo.type).isEqualTo(Int::class)
            } finally {
                session.close()
            }
        }
    }

    @Test
    fun `receiveDeserializedBase rejects frames that the converter declares unsupported`(): Unit {
        val session: TestWebSocketSession = TestWebSocketSession()
        val pingFrame: Frame = Frame.Ping(byteArrayOf(1, 2, 3))
        val converter: RecordingWebsocketContentConverter = RecordingWebsocketContentConverter(
            applicable = false,
            deserializedValue = "unused",
        )

        assertThatThrownBy {
            runBlocking {
                withTimeout(TEST_TIMEOUT_MILLIS) {
                    try {
                        session.queueIncoming(pingFrame)
                        session.receiveDeserializedBase(typeInfo<String>(), converter, Charsets.UTF_8)
                    } finally {
                        session.close()
                    }
                }
            }
        }
            .isInstanceOf(WebsocketDeserializeException::class.java)
            .hasMessageContaining("Converter doesn't support frame type")
            .satisfies(Consumer { error: Throwable ->
                val exception: WebsocketDeserializeException = error as WebsocketDeserializeException
                assertThat(exception.frame).isSameAs(pingFrame)
                assertThat(converter.deserializeCalls).isEmpty()
            })
    }

    @Test
    fun `receiveDeserializedBase accepts null only for nullable target types`(): Unit {
        val nullableSession: TestWebSocketSession = TestWebSocketSession()
        val nullableConverter: RecordingWebsocketContentConverter = RecordingWebsocketContentConverter(
            deserializedValue = null,
        )
        runBlocking {
            withTimeout(TEST_TIMEOUT_MILLIS) {
                try {
                    nullableSession.queueIncoming(Frame.Text("null"))
                    val nullableValue: Any? = nullableSession.receiveDeserializedBase(
                        typeInfo<String?>(),
                        nullableConverter,
                        Charsets.UTF_8,
                    )
                    assertThat(nullableValue).isNull()
                } finally {
                    nullableSession.close()
                }
            }
        }

        val nonNullableSession: TestWebSocketSession = TestWebSocketSession()
        val nonNullableConverter: RecordingWebsocketContentConverter = RecordingWebsocketContentConverter(
            deserializedValue = null,
        )
        assertThatThrownBy {
            runBlocking {
                withTimeout(TEST_TIMEOUT_MILLIS) {
                    try {
                        nonNullableSession.queueIncoming(Frame.Text("null"))
                        nonNullableSession.receiveDeserializedBase(
                            typeInfo<String>(),
                            nonNullableConverter,
                            Charsets.UTF_8,
                        )
                    } finally {
                        nonNullableSession.close()
                    }
                }
            }
        }
            .isInstanceOf(WebsocketDeserializeException::class.java)
            .hasMessageContaining("Frame has null content")
    }

    @Test
    fun `receiveDeserializedBase rejects converter results with the wrong runtime type`(): Unit {
        val session: TestWebSocketSession = TestWebSocketSession()
        val converter: RecordingWebsocketContentConverter = RecordingWebsocketContentConverter(
            deserializedValue = 123,
        )

        assertThatThrownBy {
            runBlocking {
                withTimeout(TEST_TIMEOUT_MILLIS) {
                    try {
                        session.queueIncoming(Frame.Text("123"))
                        session.receiveDeserializedBase(typeInfo<String>(), converter, Charsets.UTF_8)
                    } finally {
                        session.close()
                    }
                }
            }
        }
            .isInstanceOf(WebsocketDeserializeException::class.java)
            .hasMessageContaining("expected value of type")
            .hasMessageContaining("String")
            .hasMessageContaining("Int")
    }

    private companion object {
        private const val TEST_TIMEOUT_MILLIS: Long = 5_000
    }
}

private data class SerializeCall(
    val charset: Charset,
    val typeInfo: TypeInfo,
    val value: Any?,
)

private data class DeserializeCall(
    val charset: Charset,
    val typeInfo: TypeInfo,
    val frame: Frame,
)

private class RecordingWebsocketContentConverter(
    private val serializedFrame: Frame = Frame.Text("serialized"),
    private val deserializedValue: Any? = null,
    private val applicable: Boolean = true,
) : WebsocketContentConverter {
    val serializeCalls: MutableList<SerializeCall> = mutableListOf()
    val deserializeCalls: MutableList<DeserializeCall> = mutableListOf()

    override suspend fun serialize(charset: Charset, typeInfo: TypeInfo, value: Any?): Frame {
        serializeCalls += SerializeCall(charset, typeInfo, value)
        return serializedFrame
    }

    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: Frame): Any? {
        deserializeCalls += DeserializeCall(charset, typeInfo, content)
        return deserializedValue
    }

    override fun isApplicable(frame: Frame): Boolean = applicable
}

private class TestWebSocketSession : WebSocketSession {
    private val job: Job = Job()
    private val incomingChannel: Channel<Frame> = Channel(Channel.UNLIMITED)
    private val outgoingChannel: Channel<Frame> = Channel(Channel.UNLIMITED)

    override val coroutineContext: CoroutineContext = job
    override var masking: Boolean = false
    override var maxFrameSize: Long = Long.MAX_VALUE
    override val incoming: ReceiveChannel<Frame> = incomingChannel
    override val outgoing: SendChannel<Frame> = outgoingChannel
    override val extensions: List<WebSocketExtension<*>> = emptyList()

    suspend fun queueIncoming(frame: Frame): Unit {
        incomingChannel.send(frame)
    }

    suspend fun receiveOutgoing(): Frame = outgoingChannel.receive()

    override suspend fun flush(): Unit = Unit

    override fun terminate(): Unit {
        close()
    }

    fun close(): Unit {
        incomingChannel.close()
        outgoingChannel.close()
        coroutineContext.cancel()
    }
}
