/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:OptIn(ExperimentalSerializationApi::class)

package io_ktor.ktor_serialization_kotlinx_json_jvm

import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.Configuration
import io.ktor.serialization.ContentConverter
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.json.jsonIo
import io.ktor.util.reflect.typeInfo
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

public class KtorSerializationKotlinxJsonJvmTest {
    @Test
    public fun jsonRegistersDefaultApplicationJsonConverter(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val configuration = RecordingConfiguration()

            configuration.json()

            val registration = configuration.singleRegistration()
            assertThat(registration.contentType).isEqualTo(ContentType.Application.Json)
            assertThat(registration.converter).isInstanceOf(ContentConverter::class.java)
        }
    }

    @Test
    public fun registeredConverterRoundTripsNestedSerializableDocument(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val converter = registeredConverter()
            val original = JsonProfile(
                id = 42,
                name = "Ada",
                active = false,
                tags = listOf("ktor", "json", "native"),
                attributes = mapOf("score" to 99, "rank" to 7),
                location = JsonLocation(city = "Prague", country = "CZ")
            )

            val text = converter.serializeToText(original)
            val decoded = converter.deserializeFromText<JsonProfile>(text)

            assertThat(text).contains("\"id\":42")
            assertThat(decoded).isEqualTo(original)
        }
    }

    @Test
    public fun registeredConverterPreservesGenericCollectionsAndMaps(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val converter = registeredConverter()
            val original = JsonBatch(
                profiles = listOf(
                    JsonProfile(id = 1, name = "one", tags = listOf("first"), location = JsonLocation("Oslo", "NO")),
                    JsonProfile(id = 2, name = "two", attributes = mapOf("even" to 1), location = JsonLocation("Paris", "FR"))
                ),
                aliases = mapOf("primary" to JsonLocation("London", "GB"))
            )

            val decoded = converter.deserializeFromText<JsonBatch>(converter.serializeToText(original))

            assertThat(decoded).isEqualTo(original)
            assertThat(decoded.profiles.map { it.id }).containsExactly(1, 2)
            assertThat(decoded.aliases).containsEntry("primary", JsonLocation("London", "GB"))
        }
    }

    @Test
    public fun defaultJsonConfigurationSerializesDefaultValuesAndNulls(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val converter = registeredConverter()

            val text = converter.serializeToText(DefaultedJsonMessage())
            val decoded = converter.deserializeFromText<DefaultedJsonMessage>(text)

            assertThat(text).contains("\"title\":\"\"")
            assertThat(text).contains("\"count\":0")
            assertThat(text).contains("\"enabled\":false")
            assertThat(text).contains("\"note\":null")
            assertThat(decoded).isEqualTo(DefaultedJsonMessage())
        }
    }

    @Test
    public fun customJsonConfigurationAndContentTypeAreUsed(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val customContentType = ContentType.parse("application/vnd.ktor.test-profile+json")
            val customFormat = Json { encodeDefaults = false }
            val configuration = RecordingConfiguration()

            configuration.json(json = customFormat, contentType = customContentType)

            val registration = configuration.singleRegistration()
            val text = registration.converter.serializeToText(
                value = DefaultedJsonMessage(),
                contentType = customContentType
            )
            val decoded = registration.converter.deserializeFromText<DefaultedJsonMessage>(text)

            assertThat(registration.contentType).isEqualTo(customContentType)
            assertThat(text).isEqualTo("{}")
            assertThat(decoded).isEqualTo(DefaultedJsonMessage())
        }
    }

    @Test
    public fun customJsonConfigurationIgnoresUnknownKeys(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val configuration = RecordingConfiguration()
            configuration.json(json = Json { ignoreUnknownKeys = true })
            val converter = configuration.singleRegistration().converter

            val decoded = converter.deserializeFromText<StableJsonMessage>(
                """
                {
                  "id": 7,
                  "label": "kept-fields",
                  "ignored": { "nested": true },
                  "items": [1, 2, 3]
                }
                """.trimIndent()
            )

            assertThat(decoded).isEqualTo(StableJsonMessage(id = 7, label = "kept-fields"))
        }
    }

    @Test
    public fun customSerializersModuleSupportsContextualFields(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val module = SerializersModule {
                contextual(TrackingCode::class, TrackingCodeSerializer)
            }
            val configuration = RecordingConfiguration()
            configuration.json(json = Json { serializersModule = module })
            val converter = configuration.singleRegistration().converter
            val original = ContextualJsonEnvelope(code = TrackingCode("shipment-17"))

            val text = converter.serializeToText(original)
            val decoded = converter.deserializeFromText<ContextualJsonEnvelope>(text)

            assertThat(text).isEqualTo("{\"code\":\"shipment-17\"}")
            assertThat(decoded).isEqualTo(original)
        }
    }

    @Test
    public fun jsonIoConverterUsesChannelBasedJsonSerialization(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val configuration = RecordingConfiguration()
            configuration.jsonIo()
            val registration = configuration.singleRegistration()
            val original = StableJsonMessage(id = 23, label = "io-channel")

            val text = registration.converter.serializeChannelContentToText(original)
            val decoded = registration.converter.deserializeFromText<StableJsonMessage>(text)

            assertThat(registration.contentType).isEqualTo(ContentType.Application.Json)
            assertThat(text).isEqualTo("{\"id\":23,\"label\":\"io-channel\"}")
            assertThat(decoded).isEqualTo(original)
        }
    }

    @Test
    public fun malformedPayloadFailsWithKtorConversionException(): Unit {
        val converter = registeredConverter()

        assertThatThrownBy {
            runBlocking {
                withTimeout(TEST_TIMEOUT_MILLIS) {
                    converter.deserializeFromText<JsonProfile>("{\"id\": 1, \"name\":")
                }
            }
        }.isInstanceOf(JsonConvertException::class.java)
            .hasMessageContaining("Illegal input")
    }

    private fun registeredConverter(): ContentConverter {
        val configuration = RecordingConfiguration()
        configuration.json()
        return configuration.singleRegistration().converter
    }

    private suspend inline fun <reified T> ContentConverter.serializeToText(
        value: T,
        contentType: ContentType = ContentType.Application.Json,
        charset: java.nio.charset.Charset = Charsets.UTF_8
    ): String {
        val outgoingContent = serialize(
            contentType = contentType,
            charset = charset,
            typeInfo = typeInfo<T>(),
            value = value
        )

        assertThat(outgoingContent).isInstanceOf(OutgoingContent.ByteArrayContent::class.java)
        val byteArrayContent = outgoingContent as OutgoingContent.ByteArrayContent
        assertThat(byteArrayContent.contentType?.withoutParameters()).isEqualTo(contentType.withoutParameters())
        return byteArrayContent.bytes().toString(charset)
    }

    private suspend inline fun <reified T> ContentConverter.serializeChannelContentToText(
        value: T,
        contentType: ContentType = ContentType.Application.Json,
        charset: java.nio.charset.Charset = Charsets.UTF_8
    ): String {
        val outgoingContent = serialize(
            contentType = contentType,
            charset = charset,
            typeInfo = typeInfo<T>(),
            value = value
        )

        assertThat(outgoingContent).isInstanceOf(OutgoingContent.WriteChannelContent::class.java)
        val writeChannelContent = outgoingContent as OutgoingContent.WriteChannelContent
        assertThat(writeChannelContent.contentType?.withoutParameters()).isEqualTo(contentType.withoutParameters())

        return coroutineScope {
            val channel = ByteChannel(autoFlush = true)
            val writer = launch {
                try {
                    writeChannelContent.writeTo(channel)
                } finally {
                    channel.flushAndClose()
                }
            }
            val bytes = channel.toByteArray()
            writer.join()
            bytes.toString(charset)
        }
    }

    private suspend inline fun <reified T> ContentConverter.deserializeFromText(
        text: String,
        charset: java.nio.charset.Charset = Charsets.UTF_8
    ): T {
        val decoded = deserialize(
            charset = charset,
            typeInfo = typeInfo<T>(),
            content = ByteReadChannel(text.toByteArray(charset))
        )

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
        configuration: T.() -> Unit
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
    val converter: ContentConverter
)

@Serializable
private data class JsonProfile(
    val id: Int,
    val name: String,
    val active: Boolean = true,
    val tags: List<String> = emptyList(),
    val attributes: Map<String, Int> = emptyMap(),
    val location: JsonLocation? = null
)

@Serializable
private data class JsonLocation(
    val city: String,
    val country: String
)

@Serializable
private data class JsonBatch(
    val profiles: List<JsonProfile>,
    val aliases: Map<String, JsonLocation>
)

@Serializable
private data class StableJsonMessage(
    val id: Int,
    val label: String
)

@Serializable
private data class DefaultedJsonMessage(
    val title: String = "",
    val count: Int = 0,
    val enabled: Boolean = false,
    val note: String? = null
)

@Serializable
private data class ContextualJsonEnvelope(
    @Contextual val code: TrackingCode
)

private data class TrackingCode(
    val value: String
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
