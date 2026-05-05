/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:OptIn(ExperimentalSerializationApi::class)

package io_ktor.ktor_serialization_kotlinx_protobuf_jvm

import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.Configuration
import io.ktor.serialization.ContentConverter
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.kotlinx.protobuf.protobuf
import io.ktor.util.reflect.typeInfo
import io.ktor.utils.io.ByteReadChannel
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
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

public class KtorSerializationKotlinxProtobufJvmTest {
    @Test
    public fun protobufRegistersDefaultApplicationProtobufConverter(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val configuration = RecordingConfiguration()

            configuration.protobuf()

            val registration = configuration.singleRegistration()
            assertThat(registration.contentType).isEqualTo(ContentType.Application.ProtoBuf)
            assertThat(registration.converter).isInstanceOf(ContentConverter::class.java)
        }
    }

    @Test
    public fun registeredConverterRoundTripsNestedSerializableMessage(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val converter = registeredConverter()
            val original = ProtoProfile(
                id = 42,
                name = "Ada",
                active = false,
                tags = listOf("ktor", "protobuf", "native"),
                attributes = mapOf("score" to 99, "rank" to 7),
                location = ProtoLocation(city = "Prague", country = "CZ")
            )

            val bytes = converter.serializeToByteArray(original)
            val decoded = converter.deserializeFromByteArray<ProtoProfile>(bytes)

            assertThat(bytes).isNotEmpty()
            assertThat(decoded).isEqualTo(original)
        }
    }

    @Test
    public fun registeredConverterPreservesGenericCollections(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val converter = registeredConverter()
            val original = ProtoBatch(
                profiles = listOf(
                    ProtoProfile(id = 1, name = "one", tags = listOf("first"), location = ProtoLocation("Oslo", "NO")),
                    ProtoProfile(id = 2, name = "two", attributes = mapOf("even" to 1), location = ProtoLocation("Paris", "FR"))
                ),
                aliases = mapOf("primary" to ProtoLocation("London", "GB"))
            )

            val decoded = converter.deserializeFromByteArray<ProtoBatch>(converter.serializeToByteArray(original))

            assertThat(decoded).isEqualTo(original)
            assertThat(decoded.profiles.map { it.id }).containsExactly(1, 2)
            assertThat(decoded.aliases).containsEntry("primary", ProtoLocation("London", "GB"))
        }
    }

    @Test
    public fun converterIgnoresUnknownFieldsForSchemaEvolution(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val converter = registeredConverter()
            val evolvedMessage = EvolvedProtoMessage(
                id = 7,
                label = "kept-fields",
                notes = listOf("new", "optional", "data"),
                location = ProtoLocation("Berlin", "DE"),
                enabled = true
            )

            val decoded = converter.deserializeFromByteArray<StableProtoMessage>(
                converter.serializeToByteArray(evolvedMessage)
            )

            assertThat(decoded).isEqualTo(StableProtoMessage(id = 7, label = "kept-fields"))
        }
    }

    @Test
    public fun customProtoBufConfigurationAndContentTypeAreUsed(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val customContentType = ContentType.parse("application/vnd.ktor.test-profile+protobuf")
            val customFormat = ProtoBuf { encodeDefaults = false }
            val configuration = RecordingConfiguration()

            configuration.protobuf(protobuf = customFormat, contentType = customContentType)

            val registration = configuration.singleRegistration()
            val bytes = registration.converter.serializeToByteArray(
                value = DefaultedProtoMessage(),
                contentType = customContentType
            )
            val decoded = registration.converter.deserializeFromByteArray<DefaultedProtoMessage>(bytes)

            assertThat(registration.contentType).isEqualTo(customContentType)
            assertThat(bytes).isEmpty()
            assertThat(decoded).isEqualTo(DefaultedProtoMessage())
        }
    }

    @Test
    public fun defaultProtoBufConfigurationSerializesDefaultValues(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val converter = registeredConverter()
            val valueWithOnlyDefaults = DefaultedProtoMessage()

            val bytes = converter.serializeToByteArray(valueWithOnlyDefaults)
            val decoded = converter.deserializeFromByteArray<DefaultedProtoMessage>(bytes)

            assertThat(bytes).isNotEmpty()
            assertThat(decoded).isEqualTo(valueWithOnlyDefaults)
        }
    }

    @Test
    public fun customSerializersModuleSupportsContextualProtoFields(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val module = SerializersModule {
                contextual(TrackingCode::class, TrackingCodeSerializer)
            }
            val configuration = RecordingConfiguration()
            configuration.protobuf(protobuf = ProtoBuf { serializersModule = module })
            val converter = configuration.singleRegistration().converter
            val original = ContextualProtoEnvelope(code = TrackingCode("shipment-17"))

            val bytes = converter.serializeToByteArray(original)
            val decoded = converter.deserializeFromByteArray<ContextualProtoEnvelope>(bytes)

            assertThat(bytes).isNotEmpty()
            assertThat(decoded).isEqualTo(original)
        }
    }

    @Test
    public fun malformedPayloadFailsWithKtorConversionException(): Unit {
        val converter = registeredConverter()
        val malformedVarint = byteArrayOf(0x08, 0x80.toByte())

        assertThatThrownBy {
            runBlocking {
                withTimeout(TEST_TIMEOUT_MILLIS) {
                    converter.deserializeFromByteArray<DefaultedProtoMessage>(malformedVarint)
                }
            }
        }.isInstanceOf(JsonConvertException::class.java)
            .hasMessageContaining("Illegal input")
    }

    private fun registeredConverter(): ContentConverter {
        val configuration = RecordingConfiguration()
        configuration.protobuf()
        return configuration.singleRegistration().converter
    }

    private suspend inline fun <reified T> ContentConverter.serializeToByteArray(
        value: T,
        contentType: ContentType = ContentType.Application.ProtoBuf
    ): ByteArray {
        val outgoingContent = serialize(
            contentType = contentType,
            charset = Charsets.UTF_8,
            typeInfo = typeInfo<T>(),
            value = value
        )

        assertThat(outgoingContent).isInstanceOf(OutgoingContent.ByteArrayContent::class.java)
        val byteArrayContent = outgoingContent as OutgoingContent.ByteArrayContent
        assertThat(byteArrayContent.contentType).isEqualTo(contentType)
        return byteArrayContent.bytes()
    }

    private suspend inline fun <reified T> ContentConverter.deserializeFromByteArray(bytes: ByteArray): T {
        val decoded = deserialize(
            charset = Charsets.UTF_8,
            typeInfo = typeInfo<T>(),
            content = ByteReadChannel(bytes)
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
private data class ProtoProfile(
    @ProtoNumber(1) val id: Int,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val active: Boolean = true,
    @ProtoNumber(4) val tags: List<String> = emptyList(),
    @ProtoNumber(5) val attributes: Map<String, Int> = emptyMap(),
    @ProtoNumber(6) val location: ProtoLocation? = null
)

@Serializable
private data class ProtoLocation(
    @ProtoNumber(1) val city: String,
    @ProtoNumber(2) val country: String
)

@Serializable
private data class ProtoBatch(
    @ProtoNumber(1) val profiles: List<ProtoProfile>,
    @ProtoNumber(2) val aliases: Map<String, ProtoLocation>
)

@Serializable
private data class EvolvedProtoMessage(
    @ProtoNumber(1) val id: Int,
    @ProtoNumber(2) val label: String,
    @ProtoNumber(3) val notes: List<String>,
    @ProtoNumber(4) val location: ProtoLocation,
    @ProtoNumber(5) val enabled: Boolean
)

@Serializable
private data class StableProtoMessage(
    @ProtoNumber(1) val id: Int,
    @ProtoNumber(2) val label: String
)

@Serializable
private data class DefaultedProtoMessage(
    @ProtoNumber(1) val title: String = "",
    @ProtoNumber(2) val count: Int = 0,
    @ProtoNumber(3) val enabled: Boolean = false
)

@Serializable
private data class ContextualProtoEnvelope(
    @ProtoNumber(1) @Contextual val code: TrackingCode
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
