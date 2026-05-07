/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_utils_jvm

import io.ktor.util.AttributeKey
import io.ktor.util.Attributes
import io.ktor.util.DeflateEncoder
import io.ktor.util.Encoder
import io.ktor.util.GZipEncoder
import io.ktor.util.StringValuesBuilderImpl
import io.ktor.util.appendIfNameAndValueAbsent
import io.ktor.util.converters.ConversionService
import io.ktor.util.converters.DataConversion
import io.ktor.util.date.GMTDate
import io.ktor.util.date.Month
import io.ktor.util.date.plus
import io.ktor.util.date.toJvmDate
import io.ktor.util.date.truncateToSeconds
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.decodeBase64String
import io.ktor.util.decodeString
import io.ktor.util.encodeBase64
import io.ktor.util.filter
import io.ktor.util.flattenEntries
import io.ktor.util.generateNonce
import io.ktor.util.hex
import io.ktor.util.moveTo
import io.ktor.util.putAll
import io.ktor.util.reflect.TypeInfo
import io.ktor.util.sha1
import io.ktor.util.toLowerCasePreservingASCIIRules
import io.ktor.util.toMap
import io.ktor.util.toUpperCasePreservingASCIIRules
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.toByteArray
import java.nio.ByteBuffer
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.coroutines.coroutineContext

public class Ktor_utils_jvmTest {
    @Test
    fun attributesStoreTypedValuesAndCanBeCopied(): Unit {
        val source = Attributes(true)
        val nameKey = AttributeKey<String>("name")
        val countKey = AttributeKey<Int>("count")

        assertThat(source.getOrNull(nameKey)).isNull()
        assertThat(source.computeIfAbsent(nameKey) { "first" }).isEqualTo("first")
        assertThat(source.computeIfAbsent(nameKey) { "second" }).isEqualTo("first")
        source.put(countKey, 7)

        val target = Attributes()
        target.putAll(source)

        assertThat(target.contains(nameKey)).isTrue()
        assertThat(target.get(nameKey)).isEqualTo("first")
        assertThat(target.get(countKey)).isEqualTo(7)
        assertThat(target.allKeys.map { it.name }).containsExactlyInAnyOrder("name", "count")
        assertThat(target.take(countKey)).isEqualTo(7)
        assertThat(target.takeOrNull(countKey)).isNull()
    }

    @Test
    fun stringValuesBuilderHonorsCaseInsensitiveNamesAndFiltering(): Unit {
        val builder = StringValuesBuilderImpl(true)
        builder.append("Accept", "text/plain")
        builder.append("ACCEPT", "application/json")
        builder.append("Cache-Control", "no-cache")
        builder.appendIfNameAndValueAbsent("accept", "text/plain")
        builder.appendIfNameAndValueAbsent("accept", "application/xml")

        val values = builder.build()
        val filtered = values.filter { name, value ->
            name.equals("accept", ignoreCase = true) && value.contains("application")
        }

        val mappedAcceptValues = values.toMap().entries.single { it.key.equals("accept", ignoreCase = true) }.value
        val flattened = filtered.flattenEntries()

        assertThat(values.getAll("accept")).containsExactly("text/plain", "application/json", "application/xml")
        assertThat(values.contains("ACCEPT", "text/plain")).isTrue()
        assertThat(mappedAcceptValues).containsExactly("text/plain", "application/json", "application/xml")
        assertThat(flattened.map { it.first.lowercase() }).containsExactly("accept", "accept")
        assertThat(flattened.map { it.second }).containsExactly("application/json", "application/xml")
    }

    @Test
    fun textAndBase64HelpersUseStableAsciiRules(): Unit {
        val text = "Ktor utils: Привет"
        val encoded = text.encodeBase64()

        assertThat(encoded.decodeBase64String()).isEqualTo(text)
        assertThat(encoded.decodeBase64Bytes().toString(Charsets.UTF_8)).isEqualTo(text)
        assertThat("abcXYZ-09".toUpperCasePreservingASCIIRules()).isEqualTo("ABCXYZ-09")
        assertThat("ABCxyz-09".toLowerCasePreservingASCIIRules()).isEqualTo("abcxyz-09")
    }

    @Test
    fun cryptographicHelpersProduceExpectedDigestsAndNonceBytes(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val sha1Digest = sha1("abc".toByteArray(Charsets.UTF_8))
            val nonce = generateNonce(16)

            assertThat(hex(sha1Digest)).isEqualTo("a9993e364706816aba3e25717850c26c9cd0d89d")
            assertThat(hex("0001020aff"))
                .containsExactly(0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x0a.toByte(), 0xff.toByte())
            assertThat(nonce).hasSize(16)
            assertThat(generateNonce()).isNotBlank()
        }
    }

    @Test
    fun gzipAndDeflateEncodersRoundTripByteReadChannels(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val text = "compressible ktor payload ".repeat(16)

            val gzipResult = roundTrip(GZipEncoder, text)
            val deflateResult = roundTrip(DeflateEncoder, text)

            assertThat(gzipResult).isEqualTo(text)
            assertThat(deflateResult).isEqualTo(text)
        }
    }

    @Test
    fun gmtDatesExposeCalendarFieldsAndArithmetic(): Unit {
        val epoch = GMTDate(0L)
        val precise = GMTDate(1_234L)
        val constructed = GMTDate(seconds = 5, minutes = 4, hours = 3, dayOfMonth = 2, month = Month.JANUARY, year = 2020)
        val later = epoch + 2_000L

        assertThat(epoch.year).isEqualTo(1970)
        assertThat(epoch.month).isEqualTo(Month.JANUARY)
        assertThat(epoch.dayOfMonth).isEqualTo(1)
        assertThat(later.timestamp).isEqualTo(2_000L)
        assertThat(precise.truncateToSeconds().timestamp).isEqualTo(1_000L)
        assertThat(constructed.toJvmDate().toInstant()).isEqualTo(Instant.parse("2020-01-02T03:04:05Z"))
    }

    @Test
    fun pipelinePhasesTransformSubjectInOrder(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val setup = io.ktor.util.pipeline.PipelinePhase("setup")
            val transform = io.ktor.util.pipeline.PipelinePhase("transform")
            val pipeline = io.ktor.util.pipeline.Pipeline<String, MutableList<String>>(setup)
            val events = mutableListOf<String>()

            pipeline.insertPhaseAfter(setup, transform)
            pipeline.intercept(setup) { subject ->
                context += "setup:$subject"
                proceedWith("$subject|setup")
            }
            pipeline.intercept(transform) { subject ->
                context += "transform:$subject"
                this.subject = "$subject|transform"
            }

            val result = pipeline.execute(events, "start")

            assertThat(result).isEqualTo("start|setup|transform")
            assertThat(events).containsExactly("setup:start", "transform:start|setup")
            assertThat(pipeline.items.map { it.name }).containsExactly("setup", "transform")
        }
    }

    @Test
    fun dataConversionUsesCustomConversionService(): Unit {
        val configuration = DataConversion.Configuration()
        configuration.convert(
            ColorCode::class,
            object : ConversionService {
                override fun fromValues(values: List<String>, type: TypeInfo): Any =
                    ColorCode(values[0], values[1].toInt())

                override fun toValues(value: Any?): List<String> {
                    val color = value as ColorCode
                    return listOf(color.name, color.number.toString())
                }
            },
        )
        val service = DataConversion(configuration)

        assertThat(service.fromValues(listOf("green", "7"), TypeInfo(ColorCode::class)))
            .isEqualTo(ColorCode("green", 7))
        assertThat(service.toValues(ColorCode("blue", 9))).containsExactly("blue", "9")
    }

    @Test
    fun byteBufferUtilitiesMoveAndDecodeWithoutChangingUnrelatedBytes(): Unit {
        val source = ByteBuffer.wrap("abcdef".toByteArray(Charsets.UTF_8))
        val destination = ByteBuffer.allocate(4)
        source.position(1)
        source.limit(5)

        val moved = source.moveTo(destination, limit = 3)
        destination.flip()
        val copied = ByteArray(destination.remaining())
        destination.get(copied)

        assertThat(moved).isEqualTo(3)
        assertThat(source.position()).isEqualTo(4)
        assertThat(copied.toString(Charsets.UTF_8)).isEqualTo("bcd")
        assertThat(ByteBuffer.wrap("hello".toByteArray(Charsets.UTF_8)).decodeString()).isEqualTo("hello")
    }

    private suspend fun roundTrip(encoder: Encoder, text: String): String {
        val original = text.toByteArray(Charsets.UTF_8)
        val encoded = encoder.encode(ByteReadChannel(original), coroutineContext).toByteArray()
        val decoded = encoder.decode(ByteReadChannel(encoded), coroutineContext).toByteArray()

        assertThat(encoded).isNotEmpty()
        return decoded.toString(Charsets.UTF_8)
    }

    private data class ColorCode(val name: String, val number: Int)

    private companion object {
        private const val TEST_TIMEOUT_MILLIS: Long = 10_000L
    }
}
