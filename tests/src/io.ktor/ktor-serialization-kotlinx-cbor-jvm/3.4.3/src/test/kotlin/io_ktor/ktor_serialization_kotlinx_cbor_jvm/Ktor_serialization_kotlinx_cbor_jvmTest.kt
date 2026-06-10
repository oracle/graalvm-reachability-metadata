/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:OptIn(ExperimentalSerializationApi::class)

package io_ktor.ktor_serialization_kotlinx_cbor_jvm

import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.Configuration
import io.ktor.serialization.ContentConverter
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.kotlinx.cbor.cbor
import io.ktor.util.reflect.typeInfo
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.cbor.CborLabel
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

public class KtorSerializationKotlinxCborJvmTest {
    @Test
    public fun cborRegistersDefaultApplicationCborConverter(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val configuration = RecordingConfiguration()

            configuration.cbor()

            val registration = configuration.singleRegistration()
            assertThat(registration.contentType).isEqualTo(ContentType.Application.Cbor)
            assertThat(registration.converter).isInstanceOf(ContentConverter::class.java)
        }
    }

    @Test
    public fun registeredConverterRoundTripsNestedSerializableDocument(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val converter = registeredConverter()
            val original = CborProfile(
                id = 42,
                name = "Ada",
                active = false,
                tags = listOf("ktor", "cbor", "native"),
                attributes = mapOf("score" to 99, "rank" to 7),
                location = CborLocation(city = "Prague", country = "CZ")
            )

            val bytes = converter.serializeToByteArray(original)
            val decoded = converter.deserializeFromByteArray<CborProfile>(bytes)

            assertThat(bytes).isNotEmpty()
            assertThat(decoded).isEqualTo(original)
        }
    }

    @Test
    public fun registeredConverterPreservesGenericCollectionsAndMaps(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val converter = registeredConverter()
            val original = CborBatch(
                profiles = listOf(
                    CborProfile(id = 1, name = "one", tags = listOf("first"), location = CborLocation("Oslo", "NO")),
                    CborProfile(id = 2, name = "two", attributes = mapOf("even" to 1), location = CborLocation("Paris", "FR"))
                ),
                aliases = mapOf("primary" to CborLocation("London", "GB"))
            )

            val decoded = converter.deserializeFromByteArray<CborBatch>(converter.serializeToByteArray(original))

            assertThat(decoded).isEqualTo(original)
            assertThat(decoded.profiles.map { profile: CborProfile -> profile.id }).containsExactly(1, 2)
            assertThat(decoded.aliases).containsEntry("primary", CborLocation("London", "GB"))
        }
    }

    @Test
    public fun byteStringFieldsRoundTripBinaryPayloads(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val converter = registeredConverter()
            val original = CborBinaryPacket(
                payload = byteArrayOf(0, 1, 2, 3, 0x7f, 0x80.toByte(), 0xff.toByte()),
                samples = listOf(5, 8, 13)
            )

            val decoded = converter.deserializeFromByteArray<CborBinaryPacket>(converter.serializeToByteArray(original))

            assertArrayEquals(original.payload, decoded.payload)
            assertThat(decoded.samples).containsExactly(5, 8, 13)
        }
    }

    @Test
    public fun customCborConfigurationAndContentTypeAreUsed(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val customContentType = ContentType.parse("application/vnd.ktor.test-profile+cbor")
            val customFormat = Cbor {
                encodeDefaults = false
                useDefiniteLengthEncoding = true
            }
            val configuration = RecordingConfiguration()

            configuration.cbor(cbor = customFormat, contentType = customContentType)

            val registration = configuration.singleRegistration()
            val bytes = registration.converter.serializeToByteArray(
                value = DefaultedCborMessage(),
                contentType = customContentType
            )
            val decoded = registration.converter.deserializeFromByteArray<DefaultedCborMessage>(bytes)

            assertThat(registration.contentType).isEqualTo(customContentType)
            assertThat(bytes).isNotEmpty()
            assertThat(decoded).isEqualTo(DefaultedCborMessage())
        }
    }

    @Test
    public fun customSerializersModuleSupportsContextualCborFields(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val module = SerializersModule {
                contextual(TrackingCode::class, TrackingCodeSerializer)
            }
            val configuration = RecordingConfiguration()
            configuration.cbor(cbor = Cbor { serializersModule = module })
            val converter = configuration.singleRegistration().converter
            val original = ContextualCborEnvelope(code = TrackingCode("shipment-17"))

            val bytes = converter.serializeToByteArray(original)
            val decoded = converter.deserializeFromByteArray<ContextualCborEnvelope>(bytes)

            assertThat(bytes).isNotEmpty()
            assertThat(decoded).isEqualTo(original)
        }
    }

    @Test
    public fun cborLabelsCanBePreferredOverPropertyNames(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val configuration = RecordingConfiguration()
            configuration.cbor(
                cbor = Cbor {
                    preferCborLabelsOverNames = true
                    useDefiniteLengthEncoding = true
                }
            )
            val converter = configuration.singleRegistration().converter
            val original = LabeledCborMessage(id = 7, label = "labeled")

            val decoded = converter.deserializeFromByteArray<LabeledCborMessage>(
                converter.serializeToByteArray(original)
            )

            assertThat(decoded).isEqualTo(original)
        }
    }

    @Test
    public fun malformedPayloadFailsWithKtorConversionException(): Unit {
        val converter = registeredConverter()
        val malformedCbor = byteArrayOf(0xbf.toByte(), 0x62, 0x69, 0x64)

        assertThatThrownBy {
            runBlocking {
                withTimeout(TEST_TIMEOUT_MILLIS) {
                    converter.deserializeFromByteArray<CborProfile>(malformedCbor)
                }
            }
        }.isInstanceOf(JsonConvertException::class.java)
            .hasMessageContaining("Illegal input")
    }

    private fun registeredConverter(): ContentConverter {
        val configuration = RecordingConfiguration()
        configuration.cbor()
        return configuration.singleRegistration().converter
    }

    private suspend inline fun <reified T> ContentConverter.serializeToByteArray(
        value: T,
        contentType: ContentType = ContentType.Application.Cbor
    ): ByteArray {
        val outgoingContent = serialize(
            contentType = contentType,
            charset = Charsets.UTF_8,
            typeInfo = typeInfo<T>(),
            value = value
        )

        assertThat(outgoingContent).isInstanceOf(OutgoingContent.ByteArrayContent::class.java)
        val byteArrayContent = outgoingContent as OutgoingContent.ByteArrayContent
        assertThat(byteArrayContent.contentType?.withoutParameters()).isEqualTo(contentType.withoutParameters())
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
private data class CborProfile(
    val id: Int,
    val name: String,
    val active: Boolean = true,
    val tags: List<String> = emptyList(),
    val attributes: Map<String, Int> = emptyMap(),
    val location: CborLocation? = null
)

@Serializable
private data class CborLocation(
    val city: String,
    val country: String
)

@Serializable
private data class CborBatch(
    val profiles: List<CborProfile>,
    val aliases: Map<String, CborLocation>
)

@Serializable
private data class CborBinaryPacket(
    @ByteString val payload: ByteArray,
    val samples: List<Int>
)

@Serializable
private data class DefaultedCborMessage(
    val title: String = "",
    val count: Int = 0,
    val enabled: Boolean = false
)

@Serializable
private data class ContextualCborEnvelope(
    @Contextual val code: TrackingCode
)

@Serializable
private data class LabeledCborMessage(
    @CborLabel(1) val id: Int,
    @CborLabel(2) val label: String
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
