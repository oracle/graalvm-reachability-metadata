/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_serialization_jvm

import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.http.withCharset
import io.ktor.serialization.Configuration
import io.ktor.serialization.ContentConvertException
import io.ktor.serialization.ContentConverter
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.WebsocketContentConvertException
import io.ktor.serialization.WebsocketContentConverter
import io.ktor.serialization.WebsocketConverterNotFoundException
import io.ktor.serialization.WebsocketDeserializeException
import io.ktor.serialization.deserialize
import io.ktor.serialization.serialize
import io.ktor.serialization.suitableCharset
import io.ktor.serialization.suitableCharsetOrNull
import io.ktor.util.reflect.TypeInfo
import io.ktor.util.reflect.typeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.toByteArray
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlin.reflect.KClass

public class KtorSerializationJvmTest {
    @Test
    public fun suitableCharsetChoosesHighestQualitySupportedCharset(): Unit {
        val headers = acceptCharsetHeaders("UTF-16; q=0.4, UTF-8; q=0.9, ISO-8859-1; q=0.1")

        assertThat(headers.suitableCharset(StandardCharsets.ISO_8859_1)).isEqualTo(StandardCharsets.UTF_8)
        assertThat(headers.suitableCharsetOrNull()).isEqualTo(StandardCharsets.UTF_8)
    }

    @Test
    public fun suitableCharsetHandlesWildcardUnsupportedValuesAndFallbacks(): Unit {
        val wildcardHeaders = acceptCharsetHeaders("unknown-charset; q=1.0, *; q=0.5")
        val unsupportedHeaders = acceptCharsetHeaders("not-a-real-charset; q=1.0")
        val missingHeaders = Headers.Empty

        assertThat(wildcardHeaders.suitableCharset(StandardCharsets.UTF_16)).isEqualTo(StandardCharsets.UTF_16)
        assertThat(unsupportedHeaders.suitableCharset(StandardCharsets.ISO_8859_1)).isEqualTo(StandardCharsets.ISO_8859_1)
        assertThat(unsupportedHeaders.suitableCharsetOrNull()).isNull()
        assertThat(missingHeaders.suitableCharset()).isEqualTo(StandardCharsets.UTF_8)
        assertThat(missingHeaders.suitableCharsetOrNull()).isNull()
    }

    @Test
    public fun configurationRegisterStoresConverterAndAppliesConfigurationLambda(): Unit {
        val configuration = RecordingConfiguration()
        val converter = ConfigurableContentConverter()
        val customType = ContentType("application", "x-ktor-message")

        configuration.register(customType, converter) {
            prefix = "configured"
        }

        val registration = configuration.singleRegistration()
        assertThat(registration.contentType).isEqualTo(customType)
        assertThat(registration.converter).isSameAs(converter)
        assertThat(converter.prefix).isEqualTo("configured")
    }

    @Test
    public fun contentConverterSerializesTextContentWithRequestedContentTypeAndCharset(): Unit = runSuspendTest {
        val converter = MessageContentConverter()
        val contentType = ContentType("application", "x-message")

        val outgoingContent = converter.serialize(
            contentType = contentType,
            charset = StandardCharsets.UTF_16,
            typeInfo = typeInfo<Message>(),
            value = Message("encoded", 7),
        )

        assertThat(outgoingContent).isInstanceOf(TextContent::class.java)
        val textContent = outgoingContent as TextContent
        assertThat(textContent.contentType).isEqualTo(contentType.withCharset(StandardCharsets.UTF_16))
        assertThat(textContent.text).isEqualTo("name=encoded;count=7")
        assertThat(String(textContent.bytes(), StandardCharsets.UTF_16)).isEqualTo("name=encoded;count=7")
        assertThat(converter.serializedTypes).containsExactly(Message::class)
        assertThat(converter.serializationCharsets).containsExactly(StandardCharsets.UTF_16)
    }

    @Test
    public fun contentConverterDeserializesChannelWithRequestedCharset(): Unit = runSuspendTest {
        val converter = MessageContentConverter()
        val payload = "name=decoded;count=11"

        val decoded = converter.deserialize(
            charset = StandardCharsets.UTF_16,
            typeInfo = typeInfo<Message>(),
            content = ByteReadChannel(payload.toByteArray(StandardCharsets.UTF_16)),
        )

        assertThat(decoded).isEqualTo(Message("decoded", 11))
        assertThat(converter.deserializedTypes).containsExactly(Message::class)
        assertThat(converter.deserializationCharsets).containsExactly(StandardCharsets.UTF_16)
    }

    @Test
    public fun websocketConverterReifiedExtensionsRoundTripTextFrame(): Unit = runSuspendTest {
        val converter = MessageWebsocketConverter()
        val original = Message("socket", 19)

        val frame = converter.serialize(original)
        val decoded = converter.deserialize<Message>(frame)

        assertThat(frame).isInstanceOf(Frame.Text::class.java)
        assertThat((frame as Frame.Text).readText()).isEqualTo("name=socket;count=19")
        assertThat(decoded).isEqualTo(original)
        assertThat(converter.serializedTypes).containsExactly(Message::class)
        assertThat(converter.deserializedTypes).containsExactly(Message::class)
    }

    @Test
    public fun websocketConverterHandlesBinaryFramesThroughPublicInterface(): Unit = runSuspendTest {
        val converter = MessageWebsocketConverter()
        val payload = "binary-payload".toByteArray(StandardCharsets.UTF_8)
        val frame = Frame.Binary(fin = true, data = payload)

        val decoded = converter.deserialize(
            charset = StandardCharsets.UTF_8,
            typeInfo = typeInfo<ByteArray>(),
            content = frame,
        )

        assertThat(converter.isApplicable(frame)).isTrue()
        assertThat(decoded).isInstanceOf(ByteArray::class.java)
        assertThat(decoded as ByteArray).containsExactly(*payload)
        assertThat(converter.deserializedTypes).containsExactly(ByteArray::class)
    }

    @Test
    public fun websocketConverterReportsApplicabilityByFrameType(): Unit {
        val converter = MessageWebsocketConverter()

        assertThat(converter.isApplicable(Frame.Text("text"))).isTrue()
        assertThat(converter.isApplicable(Frame.Binary(fin = true, data = byteArrayOf(1, 2, 3)))).isTrue()
        assertThat(converter.isApplicable(Frame.Ping(byteArrayOf(4, 5, 6)))).isFalse()
    }

    @Test
    public fun websocketConverterThrowsSpecificExceptionForUnsupportedValuesAndFrames(): Unit {
        val converter = MessageWebsocketConverter()

        assertThatThrownBy {
            runBlocking {
                withTimeout(TEST_TIMEOUT_MILLIS) {
                    converter.serialize(UnsupportedWebsocketValue("not-convertible"))
                }
            }
        }.isInstanceOf(WebsocketConverterNotFoundException::class.java)
            .isInstanceOf(WebsocketContentConvertException::class.java)
            .isInstanceOf(ContentConvertException::class.java)
            .hasMessageContaining("Unsupported websocket value")

        assertThatThrownBy {
            runBlocking {
                withTimeout(TEST_TIMEOUT_MILLIS) {
                    converter.deserialize<Message>(Frame.Ping(byteArrayOf(1)))
                }
            }
        }.isInstanceOf(WebsocketConverterNotFoundException::class.java)
            .hasMessageContaining("Unsupported websocket frame")
    }

    @Test
    public fun conversionExceptionsPreserveMessagesCausesAndFrames(): Unit {
        val cause = IllegalArgumentException("invalid payload")
        val frame = Frame.Text("bad-message")
        val jsonException = JsonConvertException("json failed", cause)
        val websocketException = WebsocketDeserializeException("websocket failed", cause, frame)

        assertThat(jsonException).isInstanceOf(ContentConvertException::class.java)
        assertThat(jsonException).hasMessage("json failed")
        assertThat(jsonException).hasCause(cause)
        assertThat(websocketException).isInstanceOf(WebsocketContentConvertException::class.java)
        assertThat(websocketException).hasMessage("websocket failed")
        assertThat(websocketException).hasCause(cause)
        assertThat(websocketException.frame).isSameAs(frame)
    }

    private fun acceptCharsetHeaders(value: String): Headers = headersOf(HttpHeaders.AcceptCharset, value)

    private fun runSuspendTest(block: suspend () -> Unit): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            block()
        }
    }

    private companion object {
        private const val TEST_TIMEOUT_MILLIS: Long = 5_000
    }
}

private data class Message(
    val name: String,
    val count: Int,
)

private data class UnsupportedWebsocketValue(
    val value: String,
)

private data class ConverterRegistration(
    val contentType: ContentType,
    val converter: ContentConverter,
)

private class RecordingConfiguration : Configuration {
    private val registrations = mutableListOf<ConverterRegistration>()

    override fun <T : ContentConverter> register(
        contentType: ContentType,
        converter: T,
        configuration: T.() -> Unit,
    ) {
        converter.configuration()
        registrations += ConverterRegistration(contentType, converter)
    }

    fun singleRegistration(): ConverterRegistration {
        assertThat(registrations).hasSize(1)
        return registrations.single()
    }
}

private class ConfigurableContentConverter : ContentConverter {
    var prefix: String = "unset"

    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?,
    ): OutgoingContent? = null

    override suspend fun deserialize(
        charset: Charset,
        typeInfo: TypeInfo,
        content: ByteReadChannel,
    ): Any? = null
}

private class MessageContentConverter : ContentConverter {
    val serializedTypes: MutableList<KClass<*>> = mutableListOf()
    val deserializedTypes: MutableList<KClass<*>> = mutableListOf()
    val serializationCharsets: MutableList<Charset> = mutableListOf()
    val deserializationCharsets: MutableList<Charset> = mutableListOf()

    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?,
    ): OutgoingContent? {
        serializedTypes += typeInfo.type
        serializationCharsets += charset
        return when (value) {
            is Message -> TextContent(encodeMessage(value), contentType.withCharset(charset))
            else -> null
        }
    }

    override suspend fun deserialize(
        charset: Charset,
        typeInfo: TypeInfo,
        content: ByteReadChannel,
    ): Any? {
        deserializedTypes += typeInfo.type
        deserializationCharsets += charset
        val text = String(content.toByteArray(), charset)
        return if (typeInfo.type == Message::class) decodeMessage(text) else null
    }
}

private class MessageWebsocketConverter : WebsocketContentConverter {
    val serializedTypes: MutableList<KClass<*>> = mutableListOf()
    val deserializedTypes: MutableList<KClass<*>> = mutableListOf()

    override suspend fun serialize(
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?,
    ): Frame {
        serializedTypes += typeInfo.type
        return when (value) {
            is Message -> Frame.Text(encodeMessage(value))
            is ByteArray -> Frame.Binary(fin = true, data = value)
            else -> throw WebsocketConverterNotFoundException("Unsupported websocket value for $typeInfo")
        }
    }

    override suspend fun deserialize(
        charset: Charset,
        typeInfo: TypeInfo,
        content: Frame,
    ): Any? {
        deserializedTypes += typeInfo.type
        if (!isApplicable(content)) {
            throw WebsocketConverterNotFoundException("Unsupported websocket frame: ${content.frameType}")
        }
        return when {
            content is Frame.Text && typeInfo.type == Message::class -> decodeMessage(content.readText())
            content is Frame.Binary && typeInfo.type == ByteArray::class -> content.data
            else -> throw WebsocketConverterNotFoundException("Unsupported websocket target type: $typeInfo")
        }
    }

    override fun isApplicable(frame: Frame): Boolean = frame is Frame.Text || frame is Frame.Binary
}

private fun encodeMessage(message: Message): String = "name=${message.name};count=${message.count}"

private fun decodeMessage(text: String): Message {
    val parts = text.split(';').associate { part ->
        val key = part.substringBefore('=')
        val value = part.substringAfter('=')
        key to value
    }
    return Message(
        name = requireNotNull(parts["name"]) { "Missing name in $text" },
        count = requireNotNull(parts["count"]) { "Missing count in $text" }.toInt(),
    )
}
