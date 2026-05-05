/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_serialization_kotlinx_cbor_jvm

import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.Configuration
import io.ktor.serialization.ContentConverter
import io.ktor.serialization.kotlinx.cbor.DefaultCbor
import io.ktor.serialization.kotlinx.cbor.cbor
import io.ktor.util.reflect.typeInfo
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@OptIn(ExperimentalSerializationApi::class)
public class Ktor_serialization_kotlinx_cbor_jvmTest {
    @Test
    fun cborRegistersDefaultConverterForApplicationCbor(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val configuration = CapturingConfiguration()

            configuration.cbor()

            val registration = configuration.singleRegistration()
            val bytes = registration.converter.serializeToBytes("ktor-cbor")
            val decoded: String = registration.converter.deserializeFromBytes(bytes)

            assertThat(registration.contentType).isEqualTo(ContentType.Application.Cbor)
            assertThat(decoded).isEqualTo("ktor-cbor")
        }
    }

    @Test
    fun registeredCborConverterRoundTripsNestedCollectionPayload(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val converter = registeredDefaultConverter()
            val payload: Map<String, List<Int>> = linkedMapOf(
                "odds" to listOf(1, 3, 5),
                "evens" to listOf(2, 4, 6),
            )

            val bytes = converter.serializeToBytes(payload)
            val decoded: Map<String, List<Int>> = converter.deserializeFromBytes(bytes)

            assertThat(bytes).isNotEmpty()
            assertThat(decoded).isEqualTo(payload)
        }
    }

    @Test
    fun cborRegistersExplicitFormatForCustomContentType(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val contentType = ContentType.parse("application/vnd.ktor.test+cbor")
            val configuration = CapturingConfiguration()

            configuration.cbor(DefaultCbor, contentType)

            val registration = configuration.singleRegistration()
            assertThat(registration.contentType).isEqualTo(contentType)

            val payload: List<String> = listOf("alpha", "beta", "gamma")
            val bytes = registration.converter.serializeToBytes(payload, contentType)
            val decoded: List<String> = registration.converter.deserializeFromBytes(bytes)
            assertThat(decoded).containsExactlyElementsOf(payload)
        }
    }

    @Test
    fun defaultCborConfigurationSerializesDefaultValuedProperties(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val cbor: Cbor = Cbor(from = DefaultCbor) {
                serializersModule = SerializersModule {
                    contextual(DefaultedPayload::class, DefaultedPayloadSerializer)
                }
            }
            val configuration = CapturingConfiguration()

            configuration.cbor(cbor)

            val registration = configuration.singleRegistration()
            val payload = DefaultedPayload()
            val bytes = registration.converter.serializeToBytes(payload)
            val decoded: DefaultedPayload = registration.converter.deserializeFromBytes(bytes)

            assertThat(registration.contentType).isEqualTo(ContentType.Application.Cbor)
            assertThat(decoded).isEqualTo(payload)
        }
    }

    @Test
    fun customCborConfigurationIgnoresUnknownObjectFields(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val cbor: Cbor = Cbor(from = DefaultCbor) {
                ignoreUnknownKeys = true
                serializersModule = SerializersModule {
                    contextual(ExpandedPayload::class, ExpandedPayloadSerializer)
                    contextual(CompactPayload::class, CompactPayloadSerializer)
                }
            }
            val configuration = CapturingConfiguration()

            configuration.cbor(cbor)

            val registration = configuration.singleRegistration()
            val bytes = registration.converter.serializeToBytes(
                ExpandedPayload(id = "request-7", trace = "diagnostic"),
            )
            val decoded: CompactPayload = registration.converter.deserializeFromBytes(bytes)

            assertThat(registration.contentType).isEqualTo(ContentType.Application.Cbor)
            assertThat(decoded).isEqualTo(CompactPayload(id = "request-7"))
        }
    }

    private fun registeredDefaultConverter(): ContentConverter {
        val configuration = CapturingConfiguration()
        configuration.cbor()
        return configuration.singleRegistration().converter
    }

    private suspend inline fun <reified T> ContentConverter.serializeToBytes(
        value: T,
        contentType: ContentType = ContentType.Application.Cbor,
    ): ByteArray {
        val outgoingContent = serialize(contentType, Charsets.UTF_8, typeInfo<T>(), value)
        check(outgoingContent is OutgoingContent.ByteArrayContent) {
            "Expected CBOR serialization to produce byte array content."
        }

        val byteArrayContent: OutgoingContent.ByteArrayContent = outgoingContent
        assertThat(byteArrayContent.contentType).isEqualTo(contentType)
        assertThat(byteArrayContent.contentLength).isEqualTo(byteArrayContent.bytes().size.toLong())
        return byteArrayContent.bytes()
    }

    private suspend inline fun <reified T> ContentConverter.deserializeFromBytes(bytes: ByteArray): T {
        val decoded = deserialize(Charsets.UTF_8, typeInfo<T>(), ByteReadChannel(bytes))
        return decoded as T
    }

    private data class ConverterRegistration(
        val contentType: ContentType,
        val converter: ContentConverter,
    )

    private data class DefaultedPayload(
        val name: String = "primary",
        val retryCount: Int = 3,
    )

    private data class ExpandedPayload(
        val id: String,
        val trace: String,
    )

    private data class CompactPayload(
        val id: String,
    )

    private object DefaultedPayloadSerializer : KSerializer<DefaultedPayload> {
        private const val NAME_INDEX: Int = 0
        private const val RETRY_COUNT_INDEX: Int = 1
        private const val MISSING_NAME: String = "missing"
        private const val MISSING_RETRY_COUNT: Int = -1

        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("tests.DefaultedPayload") {
            element<String>("name")
            element<Int>("retryCount")
        }

        override fun serialize(encoder: Encoder, value: DefaultedPayload): Unit {
            encoder.encodeStructure(descriptor) {
                if (value.name != DefaultedPayload().name || shouldEncodeElementDefault(descriptor, NAME_INDEX)) {
                    encodeStringElement(descriptor, NAME_INDEX, value.name)
                }
                if (
                    value.retryCount != DefaultedPayload().retryCount ||
                    shouldEncodeElementDefault(descriptor, RETRY_COUNT_INDEX)
                ) {
                    encodeIntElement(descriptor, RETRY_COUNT_INDEX, value.retryCount)
                }
            }
        }

        override fun deserialize(decoder: Decoder): DefaultedPayload {
            return decoder.decodeStructure(descriptor) {
                var name = MISSING_NAME
                var retryCount = MISSING_RETRY_COUNT

                decodeLoop@ while (true) {
                    when (val index = decodeElementIndex(descriptor)) {
                        NAME_INDEX -> name = decodeStringElement(descriptor, NAME_INDEX)
                        RETRY_COUNT_INDEX -> retryCount = decodeIntElement(descriptor, RETRY_COUNT_INDEX)
                        CompositeDecoder.DECODE_DONE -> break@decodeLoop
                        else -> error("Unexpected element index: $index")
                    }
                }

                DefaultedPayload(name, retryCount)
            }
        }
    }

    private object ExpandedPayloadSerializer : KSerializer<ExpandedPayload> {
        private const val ID_INDEX: Int = 0
        private const val TRACE_INDEX: Int = 1
        private const val MISSING_ID: String = "missing"
        private const val MISSING_TRACE: String = "missing"

        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("tests.ExpandedPayload") {
            element<String>("id")
            element<String>("trace")
        }

        override fun serialize(encoder: Encoder, value: ExpandedPayload): Unit {
            encoder.encodeStructure(descriptor) {
                encodeStringElement(descriptor, ID_INDEX, value.id)
                encodeStringElement(descriptor, TRACE_INDEX, value.trace)
            }
        }

        override fun deserialize(decoder: Decoder): ExpandedPayload {
            return decoder.decodeStructure(descriptor) {
                var id = MISSING_ID
                var trace = MISSING_TRACE

                decodeLoop@ while (true) {
                    when (val index = decodeElementIndex(descriptor)) {
                        ID_INDEX -> id = decodeStringElement(descriptor, ID_INDEX)
                        TRACE_INDEX -> trace = decodeStringElement(descriptor, TRACE_INDEX)
                        CompositeDecoder.DECODE_DONE -> break@decodeLoop
                        else -> error("Unexpected element index: $index")
                    }
                }

                ExpandedPayload(id, trace)
            }
        }
    }

    private object CompactPayloadSerializer : KSerializer<CompactPayload> {
        private const val ID_INDEX: Int = 0
        private const val MISSING_ID: String = "missing"

        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("tests.CompactPayload") {
            element<String>("id")
        }

        override fun serialize(encoder: Encoder, value: CompactPayload): Unit {
            encoder.encodeStructure(descriptor) {
                encodeStringElement(descriptor, ID_INDEX, value.id)
            }
        }

        override fun deserialize(decoder: Decoder): CompactPayload {
            return decoder.decodeStructure(descriptor) {
                var id = MISSING_ID

                decodeLoop@ while (true) {
                    when (val index = decodeElementIndex(descriptor)) {
                        ID_INDEX -> id = decodeStringElement(descriptor, ID_INDEX)
                        CompositeDecoder.DECODE_DONE -> break@decodeLoop
                        else -> error("Unexpected element index: $index")
                    }
                }

                CompactPayload(id)
            }
        }
    }

    private class CapturingConfiguration : Configuration {
        private val registrations: MutableList<ConverterRegistration> = mutableListOf()

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

    private companion object {
        private const val TEST_TIMEOUT_MILLIS: Long = 5_000L
    }
}
