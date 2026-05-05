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
