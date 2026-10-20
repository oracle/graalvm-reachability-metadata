/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:OptIn(ExperimentalSerializationApi::class)

package org_jetbrains_kotlinx.kotlinx_serialization_core_jvm

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.Transient
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises default-value handling in generated serializers: `@EncodeDefault` modes, the
 * format's `shouldEncodeElementDefault` decision, defaults filled in for missing fields,
 * `@Transient` initializers, serialized body properties and `init` validation on decode.
 *
 * A notification service stores per-user preferences compactly, omitting whatever still holds
 * its default, and must read back documents written by older clients that lacked newer fields.
 */
public class NotificationPreferencesDefaultValuesTest {

    /** Delivery channel; entries carry the lower-case names the public API publishes. */
    @Serializable
    public enum class Channel {
        @SerialName("email")
        EMAIL,

        @SerialName("sms")
        SMS,

        @SerialName("push")
        PUSH,
    }

    /** A daily window, in hours, during which nothing is delivered. */
    @Serializable
    public data class QuietHours(val fromHour: Int, val toHour: Int = 7)

    /**
     * Hands out revision numbers above every explicit revision the scenario stores. The generated
     * serializer also evaluates the default to compare against it, so a stored revision is never
     * mistaken for a freshly issued one.
     */
    public object RevisionSource {
        private val counter: AtomicInteger = AtomicInteger(1_000)

        public fun next(): Int = counter.incrementAndGet()

        public fun issued(): Int = counter.get()
    }

    /** Per-user preferences; most properties are optional with application defaults. */
    @Serializable
    public data class NotificationPreferences(
        val userId: String,
        val channel: Channel = Channel.EMAIL,
        @EncodeDefault val locale: String = "en-US",
        @EncodeDefault(EncodeDefault.Mode.NEVER) val digestHour: Int? = null,
        val mutedTopics: List<String> = emptyList(),
        @SerialName("quiet_hours") val quietHours: QuietHours? = null,
        val revision: Int = RevisionSource.next(),
    ) {
        /** Derived lookup key; never stored and recomputed for every decoded instance. */
        @Transient
        val routingKey: String = "$userId/${channel.name.lowercase()}"

        /** Body property with an initializer: serialized, but optional on decode. */
        var updatedBy: String = "self-service"

        init {
            require(userId.isNotBlank()) { "userId must not be blank" }
            require(digestHour == null || digestHour in 0..23) { "digestHour out of range: $digestHour" }
        }
    }

    /**
     * Records which class properties a serializer writes, answering the format's
     * `shouldEncodeElementDefault` question with a fixed [encodeDefaults] policy.
     */
    public class PropertyRecordingEncoder(private val encodeDefaults: Boolean) : AbstractEncoder() {
        public val written: MutableList<String> = mutableListOf()

        override val serializersModule: SerializersModule = EmptySerializersModule()

        override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
            if (descriptor.kind == StructureKind.CLASS) {
                written += "${descriptor.serialName.substringAfterLast('.')}.${descriptor.getElementName(index)}"
            }
            return true
        }

        override fun shouldEncodeElementDefault(descriptor: SerialDescriptor, index: Int): Boolean = encodeDefaults

        override fun encodeValue(value: Any) {}

        override fun encodeNull() {}

        public companion object {
            public fun <T> record(serializer: SerializationStrategy<T>, value: T, encodeDefaults: Boolean): List<String> {
                val encoder = PropertyRecordingEncoder(encodeDefaults)
                encoder.encodeSerializableValue(serializer, value)
                return encoder.written
            }
        }
    }

    private val compactJson: Json = Json { encodeDefaults = false }

    private val fullJson: Json = Json { encodeDefaults = true }

    @Test
    fun compactDocumentsOmitDefaultsExceptThoseMarkedAlways() {
        val fresh = NotificationPreferences(userId = "u-100", revision = 1)

        val freshJson: String = compactJson.encodeToString(NotificationPreferences.serializer(), fresh)
        assertEquals("""{"userId":"u-100","locale":"en-US","revision":1}""", freshJson)

        val customized = NotificationPreferences(
            userId = "u-200",
            channel = Channel.SMS,
            locale = "de-DE",
            digestHour = 8,
            mutedTopics = listOf("marketing"),
            quietHours = QuietHours(fromHour = 22),
            revision = 4,
        ).apply { updatedBy = "admin" }

        val expected: String = """
            {"userId":"u-200","channel":"sms","locale":"de-DE","digestHour":8,
            "mutedTopics":["marketing"],"quiet_hours":{"fromHour":22},"revision":4,"updatedBy":"admin"}
        """.trimIndent().replace("\n", "")
        assertEquals(expected, compactJson.encodeToString(NotificationPreferences.serializer(), customized))
    }

    @Test
    fun neverModeOverridesAFormatThatEncodesDefaults() {
        val fresh = NotificationPreferences(userId = "u-100", revision = 1)

        val expected: String = """
            {"userId":"u-100","channel":"email","locale":"en-US","mutedTopics":[],
            "quiet_hours":null,"revision":1,"updatedBy":"self-service"}
        """.trimIndent().replace("\n", "")
        assertEquals(expected, fullJson.encodeToString(NotificationPreferences.serializer(), fresh))

        val withDigest: String = fullJson.encodeToString(NotificationPreferences.serializer(), fresh.copy(digestHour = 6))
        assertTrue(withDigest.contains(""""digestHour":6"""), withDigest)
    }

    @Test
    fun generatedSerializerAsksTheFormatBeforeSkippingDefaults() {
        val serializer: SerializationStrategy<NotificationPreferences> = NotificationPreferences.serializer()
        val fresh = NotificationPreferences(userId = "u-100", quietHours = QuietHours(fromHour = 23), revision = 1)

        val compact: List<String> = PropertyRecordingEncoder.record(serializer, fresh, encodeDefaults = false)
        assertEquals(
            listOf(
                "NotificationPreferences.userId",
                "NotificationPreferences.locale",
                "NotificationPreferences.quiet_hours",
                "QuietHours.fromHour",
                "NotificationPreferences.revision",
            ),
            compact,
        )

        val full: List<String> = PropertyRecordingEncoder.record(serializer, fresh, encodeDefaults = true)
        assertEquals(
            listOf(
                "NotificationPreferences.userId",
                "NotificationPreferences.channel",
                "NotificationPreferences.locale",
                "NotificationPreferences.mutedTopics",
                "NotificationPreferences.quiet_hours",
                "QuietHours.fromHour",
                "QuietHours.toHour",
                "NotificationPreferences.revision",
                "NotificationPreferences.updatedBy",
            ),
            full,
        )
        assertFalse(full.contains("NotificationPreferences.digestHour"), full.toString())
    }

    @Test
    fun documentsFromOlderClientsAreCompletedWithDefaults() {
        val issuedBefore: Int = RevisionSource.issued()

        val legacy: NotificationPreferences =
            compactJson.decodeFromString(NotificationPreferences.serializer(), """{"userId":"u-300","channel":"push"}""")

        assertEquals(Channel.PUSH, legacy.channel)
        assertEquals("en-US", legacy.locale)
        assertEquals(null, legacy.digestHour)
        assertEquals(emptyList<String>(), legacy.mutedTopics)
        assertEquals(null, legacy.quietHours)
        assertEquals("self-service", legacy.updatedBy)
        assertEquals("u-300/push", legacy.routingKey)
        assertEquals(issuedBefore + 1, legacy.revision)
        assertEquals(issuedBefore + 1, RevisionSource.issued())

        val stored: NotificationPreferences = compactJson.decodeFromString(
            NotificationPreferences.serializer(),
            """{"userId":"u-301","revision":42,"updatedBy":"import","quiet_hours":{"fromHour":21}}""",
        )
        assertEquals(42, stored.revision)
        assertEquals(issuedBefore + 1, RevisionSource.issued())
        assertEquals("import", stored.updatedBy)
        assertEquals(QuietHours(fromHour = 21, toHour = 7), stored.quietHours)
        assertEquals("u-301/email", stored.routingKey)
    }

    @Test
    fun transientPropertiesAreNeitherWrittenNorAccepted() {
        val prefs = NotificationPreferences(userId = "u-400", channel = Channel.SMS, revision = 2)

        val encoded: String = fullJson.encodeToString(NotificationPreferences.serializer(), prefs)
        assertFalse(encoded.contains("routingKey"), encoded)

        val decoded: NotificationPreferences = compactJson.decodeFromString(NotificationPreferences.serializer(), encoded)
        assertEquals(prefs, decoded)
        assertEquals("u-400/sms", decoded.routingKey)

        val withTransient = """{"userId":"u-400","routingKey":"forged"}"""
        assertThrows(IllegalArgumentException::class.java) {
            compactJson.decodeFromString(NotificationPreferences.serializer(), withTransient)
        }
    }

    @Test
    fun initValidationRunsOnDecodedDocuments() {
        val blank: IllegalArgumentException = assertThrows(IllegalArgumentException::class.java) {
            compactJson.decodeFromString(NotificationPreferences.serializer(), """{"userId":" ","revision":1}""")
        }
        assertTrue(blank.message!!.contains("userId must not be blank"), blank.message)

        val outOfRange: IllegalArgumentException = assertThrows(IllegalArgumentException::class.java) {
            compactJson.decodeFromString(NotificationPreferences.serializer(), """{"userId":"u-500","digestHour":25,"revision":1}""")
        }
        assertTrue(outOfRange.message!!.contains("digestHour out of range: 25"), outOfRange.message)
    }

    @Test
    fun cborHonoursTheSameDefaultPolicy() {
        val compactCbor: Cbor = Cbor { encodeDefaults = false }
        val fullCbor: Cbor = Cbor { encodeDefaults = true }
        val prefs: List<NotificationPreferences> = listOf(
            NotificationPreferences(userId = "u-600", revision = 1),
            NotificationPreferences(userId = "u-601", digestHour = 9, quietHours = QuietHours(20, 6), revision = 3)
                .apply { updatedBy = "mobile" },
        )

        val compactBytes: ByteArray = compactCbor.encodeToByteArray(prefs)
        val fullBytes: ByteArray = fullCbor.encodeToByteArray(prefs)
        assertTrue(compactBytes.size < fullBytes.size, "${compactBytes.size} >= ${fullBytes.size}")

        val fromCompact: List<NotificationPreferences> = compactCbor.decodeFromByteArray(compactBytes)
        val fromFull: List<NotificationPreferences> = compactCbor.decodeFromByteArray(fullBytes)
        assertEquals(prefs, fromCompact)
        assertEquals(prefs, fromFull)
        assertEquals("mobile", fromCompact[1].updatedBy)
        assertEquals("self-service", fromCompact[0].updatedBy)
        assertEquals("u-601/email", fromFull[1].routingKey)
    }
}
