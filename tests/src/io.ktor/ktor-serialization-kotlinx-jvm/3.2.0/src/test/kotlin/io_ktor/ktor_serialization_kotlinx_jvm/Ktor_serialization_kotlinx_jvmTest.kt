/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_serialization_kotlinx_jvm

import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.Configuration
import io.ktor.serialization.ContentConverter
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.WebsocketConverterNotFoundException
import io.ktor.serialization.WebsocketDeserializeException
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.serialization
import io.ktor.util.reflect.TypeInfo
import io.ktor.util.reflect.typeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.websocket.Frame
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.StringFormat
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

public class KtorSerializationKotlinxJvmTest {
    @Test
    public fun stringFormatRegistersKotlinxContentConverterForRequestedContentType(): Unit {
        val contentType = ContentType.parse("application/vnd.example.text-format")
        val format = RecordingStringFormat()
        val configuration = RecordingConfiguration()

        configuration.serialization(contentType, format)

        val registration = configuration.singleRegistration()
        assertThat(registration.contentType).isEqualTo(contentType)
        assertThat(registration.converter).isInstanceOf(KotlinxSerializationConverter::class.java)
    }

    @Test
    public fun binaryFormatRegistersKotlinxContentConverterForRequestedContentType(): Unit {
        val contentType = ContentType.parse("application/vnd.example.binary-format")
        val format = RecordingBinaryFormat()
        val configuration = RecordingConfiguration()

        configuration.serialization(contentType, format)

        val registration = configuration.singleRegistration()
        assertThat(registration.contentType).isEqualTo(contentType)
        assertThat(registration.converter).isInstanceOf(KotlinxSerializationConverter::class.java)
    }

    @Test
    public fun stringConverterSerializesUsingResolvedGenericSerializerAndCharset(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val format = RecordingStringFormat()
            val converter = KotlinxSerializationConverter(format)
            val contentType = ContentType.parse("application/vnd.example.text")
            val value = listOf("alpha", "beta")

            val outgoingContent = converter.serializeToByteArrayContent<List<String>>(
                value = value,
                contentType = contentType,
                charset = StandardCharsets.UTF_16
            )

            assertThat(outgoingContent.contentType?.withoutParameters()).isEqualTo(contentType)
            assertThat(outgoingContent.bytes().toString(StandardCharsets.UTF_8)).contains("alpha")
            val encodeCall = format.encodeCalls.single()
            assertThat(encodeCall.descriptorName).contains("ArrayList")
            assertThat(encodeCall.value).isEqualTo(value)
        }
    }

    @Test
    public fun stringConverterDeserializesTextWithRequestedCharset(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val expected = listOf("decoded", "items")
            val format = RecordingStringFormat(decodedValue = expected)
            val converter = KotlinxSerializationConverter(format)
            val encoded = "payload=decoded-items"

            val decoded = converter.deserializeFromText<List<String>>(encoded, StandardCharsets.UTF_16)

            assertThat(decoded).isEqualTo(expected)
            val decodeCall = format.decodeCalls.single()
            assertThat(decodeCall.descriptorName).contains("ArrayList")
            assertThat(decodeCall.payload).isEqualTo(encoded)
        }
    }

    @Test
    public fun binaryConverterRoundTripsByteArrayPayloads(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val expected = mapOf("one" to 1, "two" to 2)
            val format = RecordingBinaryFormat(decodedValue = expected)
            val converter = KotlinxSerializationConverter(format)
            val contentType = ContentType.parse("application/vnd.example.binary")

            val outgoingContent = converter.serializeToByteArrayContent<Map<String, Int>>(
                value = expected,
                contentType = contentType,
            )
            val decoded = converter.deserializeFromBytes<Map<String, Int>>(outgoingContent.bytes())

            assertThat(outgoingContent.contentType).isEqualTo(contentType)
            assertThat(outgoingContent.bytes().toString(StandardCharsets.UTF_8)).contains("one")
            assertThat(decoded).isEqualTo(expected)
            val encodeCall = format.encodeCalls.single()
            assertThat(encodeCall.descriptorName).contains("LinkedHashMap")
            assertThat(encodeCall.value).isEqualTo(expected)
            val decodeCall = format.decodeCalls.single()
            assertThat(decodeCall.payload).isEqualTo(outgoingContent.bytes().toList())
        }
    }

    @Test
    public fun converterFallsBackToValueBasedSerializerWhenDeclaredTypeIsNotSerializable(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val format = RecordingStringFormat()
            val converter = KotlinxSerializationConverter(format)
            val value = listOf("fallback", "serializer")

            val outgoingContent = converter.serializeToByteArrayContent(
                value = value,
                typeInfo = typeInfo<UnserializableMarker>(),
                contentType = ContentType.Text.Plain,
                charset = StandardCharsets.UTF_8
            )

            assertThat(outgoingContent.bytes().toString(StandardCharsets.UTF_8)).contains("fallback")
            val encodeCall = format.encodeCalls.single()
            assertThat(encodeCall.descriptorName).contains("ArrayList")
            assertThat(encodeCall.value).isEqualTo(value)
        }
    }

    @Test
    public fun valueBasedSerializerMarksCollectionElementsNullableWhenNullsArePresent(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val value = listOf("alpha", null, "omega")
            val format = DescriptorInspectingStringFormat { descriptor ->
                assertThat(descriptor.serialName).contains("ArrayList")
                assertThat(descriptor.elementsCount).isGreaterThan(0)
                assertThat(descriptor.getElementDescriptor(0).isNullable).isTrue()
            }
            val converter = KotlinxSerializationConverter(format)

            val outgoingContent = converter.serializeToByteArrayContent(
                value = value,
                typeInfo = typeInfo<UnserializableMarker>(),
                contentType = ContentType.Text.Plain,
                charset = StandardCharsets.UTF_8
            )

            assertThat(outgoingContent.bytes().toString(StandardCharsets.UTF_8)).contains("alpha", "null", "omega")
            assertThat(format.encodedValue).isEqualTo(value)
        }
    }

    @Test
    public fun contextualSerializersModuleIsUsedForSerializerLookup(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val module = SerializersModule {
                contextual(TrackingCode::class, TrackingCodeSerializer)
            }
            val format = RecordingStringFormat(serializersModule = module)
            val converter = KotlinxSerializationConverter(format)
            val value = TrackingCode("shipment-17")

            val outgoingContent = converter.serializeToByteArrayContent<TrackingCode>(
                value = value,
                contentType = ContentType.Text.Plain,
                charset = StandardCharsets.UTF_8
            )

            assertThat(outgoingContent.bytes().toString(StandardCharsets.UTF_8)).contains("shipment-17")
            val encodeCall = format.encodeCalls.single()
            assertThat(encodeCall.descriptorName).isEqualTo("TrackingCode")
            assertThat(encodeCall.value).isEqualTo(value)
        }
    }

    @Test
    public fun converterSupportsNullableDeclaredTypes(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val format = RecordingStringFormat(decodedValue = null)
            val converter = KotlinxSerializationConverter(format)
            val contentType = ContentType.Text.Plain
            val typeInfo = typeInfo<String?>()

            val outgoingContent = converter.serializeToByteArrayContent(
                value = null,
                typeInfo = typeInfo,
                contentType = contentType,
                charset = StandardCharsets.UTF_8
            )
            val decoded = converter.deserialize(
                charset = StandardCharsets.UTF_8,
                typeInfo = typeInfo,
                content = ByteReadChannel("null".toByteArray(StandardCharsets.UTF_8))
            )

            assertThat(outgoingContent.contentType?.withoutParameters()).isEqualTo(contentType)
            assertThat(outgoingContent.bytes().toString(StandardCharsets.UTF_8)).contains("null")
            assertThat(decoded).isNull()
            val encodeCall = format.encodeCalls.single()
            assertThat(encodeCall.descriptorName).contains("String")
            assertThat(encodeCall.value).isNull()
            val decodeCall = format.decodeCalls.single()
            assertThat(decodeCall.descriptorName).contains("String")
            assertThat(decodeCall.payload).isEqualTo("null")
        }
    }

    @Test
    public fun malformedStringPayloadIsWrappedInKtorConversionException(): Unit {
        val format = RecordingStringFormat(decodeFailure = SerializationException("broken text"))
        val converter = KotlinxSerializationConverter(format)

        assertThatThrownBy {
            runBlocking {
                withTimeout(TEST_TIMEOUT_MILLIS) {
                    converter.deserializeFromText<String>("not-decodable")
                }
            }
        }.isInstanceOf(JsonConvertException::class.java)
            .hasMessageContaining("Illegal input: broken text")
    }

    @Test
    public fun websocketStringConverterSerializesAndDeserializesTextFrames(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val expected = listOf("ws", "text")
            val format = RecordingStringFormat(decodedValue = expected)
            val converter = KotlinxWebsocketSerializationConverter(format)

            val frame = converter.serializeFrame<List<String>>(expected)
            val decoded = converter.deserializeFrame<List<String>>(frame)

            assertThat(frame).isInstanceOf(Frame.Text::class.java)
            assertThat(format.encodeCalls.single().value).isEqualTo(expected)
            assertThat(decoded).isEqualTo(expected)
            assertThat(converter.isApplicable(frame)).isTrue()
        }
    }

    @Test
    public fun websocketBinaryConverterSerializesAndDeserializesBinaryFrames(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val expected = mapOf("ws" to 3)
            val format = RecordingBinaryFormat(decodedValue = expected)
            val converter = KotlinxWebsocketSerializationConverter(format)

            val frame = converter.serializeFrame<Map<String, Int>>(expected)
            val decoded = converter.deserializeFrame<Map<String, Int>>(frame)

            assertThat(frame).isInstanceOf(Frame.Binary::class.java)
            assertThat(decoded).isEqualTo(expected)
            assertThat(converter.isApplicable(frame)).isTrue()
        }
    }

    @Test
    public fun websocketConverterReportsUnsupportedFramesAndFormatMismatches(): Unit {
        val converter = KotlinxWebsocketSerializationConverter(RecordingStringFormat())
        val closeFrame = Frame.Close()
        val binaryFrame = Frame.Binary(fin = true, data = byteArrayOf(1, 2, 3))

        assertThat(converter.isApplicable(closeFrame)).isFalse()
        assertThat(converter.isApplicable(binaryFrame)).isTrue()

        assertThatThrownBy {
            runBlocking {
                withTimeout(TEST_TIMEOUT_MILLIS) {
                    converter.deserializeFrame<String>(closeFrame)
                }
            }
        }.isInstanceOf(WebsocketConverterNotFoundException::class.java)
            .hasMessageContaining("Unsupported frame")

        assertThatThrownBy {
            runBlocking {
                withTimeout(TEST_TIMEOUT_MILLIS) {
                    converter.deserializeFrame<String>(binaryFrame)
                }
            }
        }.isInstanceOf(WebsocketDeserializeException::class.java)
            .hasMessageContaining("Unsupported format")
    }

    private suspend inline fun <reified T> ContentConverter.serializeToByteArrayContent(
        value: T,
        contentType: ContentType,
        charset: Charset = StandardCharsets.UTF_8,
    ): OutgoingContent.ByteArrayContent {
        return serializeToByteArrayContent(value, typeInfo<T>(), contentType, charset)
    }

    private suspend fun ContentConverter.serializeToByteArrayContent(
        value: Any?,
        typeInfo: TypeInfo,
        contentType: ContentType,
        charset: Charset,
    ): OutgoingContent.ByteArrayContent {
        val outgoingContent = serialize(
            contentType = contentType,
            charset = charset,
            typeInfo = typeInfo,
            value = value
        )

        assertThat(outgoingContent).isInstanceOf(OutgoingContent.ByteArrayContent::class.java)
        return outgoingContent as OutgoingContent.ByteArrayContent
    }

    private suspend inline fun <reified T> ContentConverter.deserializeFromText(
        text: String,
        charset: Charset = StandardCharsets.UTF_8,
    ): T {
        return deserializeFromBytes(text.toByteArray(charset), charset)
    }

    private suspend inline fun <reified T> ContentConverter.deserializeFromBytes(
        bytes: ByteArray,
        charset: Charset = StandardCharsets.UTF_8,
    ): T {
        val decoded = deserialize(
            charset = charset,
            typeInfo = typeInfo<T>(),
            content = ByteReadChannel(bytes)
        )

        assertThat(decoded).isInstanceOf(T::class.java)
        return decoded as T
    }

    private suspend inline fun <reified T> KotlinxWebsocketSerializationConverter.serializeFrame(value: T): Frame {
        return serialize(StandardCharsets.UTF_8, typeInfo<T>(), value)
    }

    private suspend inline fun <reified T> KotlinxWebsocketSerializationConverter.deserializeFrame(frame: Frame): T {
        val decoded = deserialize(StandardCharsets.UTF_8, typeInfo<T>(), frame)

        assertThat(decoded).isInstanceOf(T::class.java)
        return decoded as T
    }

    private companion object {
        private const val TEST_TIMEOUT_MILLIS: Long = 5_000
    }
}

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

private data class ConverterRegistration(
    val contentType: ContentType,
    val converter: ContentConverter,
)

private class RecordingStringFormat(
    override val serializersModule: SerializersModule = EmptySerializersModule(),
    private val decodedValue: Any? = null,
    private val decodeFailure: SerializationException? = null,
) : StringFormat {
    val encodeCalls: MutableList<EncodeCall> = mutableListOf()
    val decodeCalls: MutableList<TextDecodeCall> = mutableListOf()

    override fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T): String {
        encodeCalls += EncodeCall(serializer.descriptor.serialName, value)
        return "text:${serializer.descriptor.serialName}:$value"
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, string: String): T {
        decodeCalls += TextDecodeCall(deserializer.descriptor.serialName, string)
        decodeFailure?.let { throw it }
        return decodedValue as T
    }
}

private class DescriptorInspectingStringFormat(
    private val inspect: (SerialDescriptor) -> Unit,
) : StringFormat {
    override val serializersModule: SerializersModule = EmptySerializersModule()
    var encodedValue: Any? = null
        private set

    override fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T): String {
        inspect(serializer.descriptor)
        encodedValue = value
        return "inspected:$value"
    }

    override fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, string: String): T {
        throw UnsupportedOperationException("DescriptorInspectingStringFormat only supports encoding")
    }
}

private class RecordingBinaryFormat(
    override val serializersModule: SerializersModule = EmptySerializersModule(),
    private val decodedValue: Any? = null,
    private val decodeFailure: SerializationException? = null,
) : BinaryFormat {
    val encodeCalls: MutableList<EncodeCall> = mutableListOf()
    val decodeCalls: MutableList<BinaryDecodeCall> = mutableListOf()

    override fun <T> encodeToByteArray(serializer: SerializationStrategy<T>, value: T): ByteArray {
        encodeCalls += EncodeCall(serializer.descriptor.serialName, value)
        return "binary:${serializer.descriptor.serialName}:$value".toByteArray(StandardCharsets.UTF_8)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> decodeFromByteArray(deserializer: DeserializationStrategy<T>, bytes: ByteArray): T {
        decodeCalls += BinaryDecodeCall(deserializer.descriptor.serialName, bytes.toList())
        decodeFailure?.let { throw it }
        return decodedValue as T
    }
}

private data class EncodeCall(
    val descriptorName: String,
    val value: Any?,
)

private data class TextDecodeCall(
    val descriptorName: String,
    val payload: String,
)

private data class BinaryDecodeCall(
    val descriptorName: String,
    val payload: List<Byte>,
)

private class UnserializableMarker

private data class TrackingCode(
    val value: String,
)

private object TrackingCodeSerializer : KSerializer<TrackingCode> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("TrackingCode", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: TrackingCode) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): TrackingCode {
        return TrackingCode(decoder.decodeString())
    }
}
