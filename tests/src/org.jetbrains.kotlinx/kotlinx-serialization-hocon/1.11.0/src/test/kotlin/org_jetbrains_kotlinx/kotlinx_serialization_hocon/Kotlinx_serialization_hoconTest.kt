/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package org_jetbrains_kotlinx.kotlinx_serialization_hocon

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigMemorySize
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigValue
import java.time.Duration
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.hocon.Hocon
import kotlinx.serialization.hocon.serializers.ConfigMemorySizeSerializer
import kotlinx.serialization.hocon.serializers.JavaDurationSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class Kotlinx_serialization_hoconTest {
    @Test
    fun decodesNestedHoconConfigWithConventionalNames(): Unit {
        val config: Config = ConfigFactory.parseString(
            """
            service-name = checkout
            enabled = true
            database {
              host = db.internal
              port = 5432
            }
            labels = [native, hocon, serialization]
            thresholds {
              warning = 70
              critical = 95
            }
            request-timeout = 2 minutes
            memory-limit = 128 MiB
            """.trimIndent()
        ).resolve()
        val hocon: Hocon = Hocon { useConfigNamingConvention = true }

        val actual: AppSettings = hocon.decodeFromConfig(AppSettingsSerializer, config)

        assertThat(actual).isEqualTo(
            AppSettings(
                serviceName = "checkout",
                enabled = true,
                database = DatabaseConfig("db.internal", 5432),
                labels = listOf("native", "hocon", "serialization"),
                thresholds = mapOf("warning" to 70, "critical" to 95),
                requestTimeout = Duration.ofMinutes(2),
                memoryLimit = ConfigMemorySize.ofBytes(128L * 1024L * 1024L)
            )
        )
    }

    @Test
    fun encodesClassModelToTypesafeConfigValues(): Unit {
        val hocon: Hocon = Hocon {
            encodeDefaults = true
            useConfigNamingConvention = true
        }
        val settings: AppSettings = AppSettings(
            serviceName = "billing",
            enabled = false,
            database = DatabaseConfig("postgres.local", 15432),
            labels = listOf("blue", "green"),
            thresholds = mapOf("soft" to 5, "hard" to 10),
            requestTimeout = Duration.ofSeconds(45),
            memoryLimit = ConfigMemorySize.ofBytes(32L * 1024L * 1024L)
        )

        val actual: Config = hocon.encodeToConfig(AppSettingsSerializer, settings)

        assertThat(actual.getString("service-name")).isEqualTo("billing")
        assertThat(actual.getBoolean("enabled")).isFalse()
        assertThat(actual.getString("database.host")).isEqualTo("postgres.local")
        assertThat(actual.getInt("database.port")).isEqualTo(15432)
        assertThat(actual.getStringList("labels")).containsExactly("blue", "green")
        assertThat(actual.getInt("thresholds.soft")).isEqualTo(5)
        assertThat(actual.getInt("thresholds.hard")).isEqualTo(10)
        assertThat(actual.getDuration("request-timeout")).isEqualTo(Duration.ofSeconds(45))
        assertThat(actual.getMemorySize("memory-limit")).isEqualTo(ConfigMemorySize.ofBytes(32L * 1024L * 1024L))
    }

    @Test
    fun omitsOptionalDefaultsWhenEncodeDefaultsIsDisabled(): Unit {
        val hocon: Hocon = Hocon { useConfigNamingConvention = true }
        val settings: AppSettings = AppSettings(
            serviceName = "minimal",
            database = DatabaseConfig("localhost", 8080)
        )

        val actual: Config = hocon.encodeToConfig(AppSettingsSerializer, settings)

        assertThat(actual.getString("service-name")).isEqualTo("minimal")
        assertThat(actual.hasPath("database.host")).isTrue()
        assertThat(actual.hasPath("enabled")).isFalse()
        assertThat(actual.hasPath("labels")).isFalse()
        assertThat(actual.hasPath("thresholds")).isFalse()
        assertThat(actual.hasPath("request-timeout")).isFalse()
        assertThat(actual.hasPath("memory-limit")).isFalse()
    }

    @Test
    fun roundTripsCollectionsNestedObjectsAndSpecialHoconScalars(): Unit {
        val hocon: Hocon = Hocon {
            encodeDefaults = true
            useConfigNamingConvention = true
        }
        val expected: AppSettings = AppSettings(
            serviceName = "analytics",
            enabled = true,
            database = DatabaseConfig("analytics-db", 1900),
            labels = listOf("one", "two", "three"),
            thresholds = mapOf("low" to 1, "medium" to 50, "high" to 100),
            requestTimeout = Duration.ofMillis(2500),
            memoryLimit = ConfigMemorySize.ofBytes(512L * 1024L)
        )

        val encoded: Config = hocon.encodeToConfig(AppSettingsSerializer, expected)
        val actual: AppSettings = hocon.decodeFromConfig(AppSettingsSerializer, encoded)

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun supportsObjectPolymorphismWithCustomClassDiscriminator(): Unit {
        val hocon: Hocon = Hocon {
            serializersModule = notificationModule
            classDiscriminator = "kind"
            useConfigNamingConvention = true
        }
        val config: Config = ConfigFactory.parseString(
            """
            id = alert-7
            notification {
              kind = sms
              phone-number = "+15550100"
              urgent = true
            }
            """.trimIndent()
        )

        val actual: NotificationEnvelope = hocon.decodeFromConfig(NotificationEnvelopeSerializer, config)

        assertThat(actual).isEqualTo(
            NotificationEnvelope(
                id = "alert-7",
                notification = SmsNotification(phoneNumber = "+15550100", urgent = true)
            )
        )
    }

    @Test
    fun encodesPolymorphicObjectsUsingConfiguredDiscriminator(): Unit {
        val hocon: Hocon = Hocon {
            serializersModule = notificationModule
            classDiscriminator = "kind"
            useConfigNamingConvention = true
        }
        val envelope: NotificationEnvelope = NotificationEnvelope(
            id = "mail-3",
            notification = EmailNotification(address = "ops@example.test", subject = "Deploy complete")
        )

        val actual: Config = hocon.encodeToConfig(NotificationEnvelopeSerializer, envelope)

        assertThat(actual.getString("id")).isEqualTo("mail-3")
        assertThat(actual.getString("notification.kind")).isEqualTo("email")
        assertThat(actual.getString("notification.address")).isEqualTo("ops@example.test")
        assertThat(actual.getString("notification.subject")).isEqualTo("Deploy complete")
    }

    @Test
    fun supportsArrayPolymorphismWhenConfigured(): Unit {
        val hocon: Hocon = Hocon {
            serializersModule = notificationModule
            useArrayPolymorphism = true
            useConfigNamingConvention = true
        }
        val config: Config = ConfigFactory.parseString(
            """
            id = digest-9
            notification = [
              email,
              {
                address = "team@example.test"
                subject = "Daily digest"
              }
            ]
            """.trimIndent()
        )

        val decoded: NotificationEnvelope = hocon.decodeFromConfig(NotificationEnvelopeSerializer, config)

        assertThat(decoded).isEqualTo(
            NotificationEnvelope(
                id = "digest-9",
                notification = EmailNotification(address = "team@example.test", subject = "Daily digest")
            )
        )

        val expected: NotificationEnvelope = NotificationEnvelope(
            id = "sms-5",
            notification = SmsNotification(phoneNumber = "+15550123", urgent = false)
        )
        val encoded: Config = hocon.encodeToConfig(NotificationEnvelopeSerializer, expected)
        val notificationValues: List<ConfigValue> = encoded.getList("notification")
        val payload: Config = (notificationValues[1] as ConfigObject).toConfig()

        assertThat(notificationValues).hasSize(2)
        assertThat(notificationValues[0].unwrapped()).isEqualTo("sms")
        assertThat(payload.getString("phone-number")).isEqualTo("+15550123")
        assertThat(payload.getBoolean("urgent")).isFalse()
        assertThat(hocon.decodeFromConfig(NotificationEnvelopeSerializer, encoded)).isEqualTo(expected)
    }

    @Test
    fun preservesExplicitNullConfigValues(): Unit {
        val hocon: Hocon = Hocon {
            encodeDefaults = true
            useConfigNamingConvention = true
        }
        val config: Config = ConfigFactory.parseString(
            """
            primary-owner = null
            fallback-owner = release-team
            """.trimIndent()
        )

        val decoded: Ownership = hocon.decodeFromConfig(OwnershipSerializer, config)
        val encoded: Config = hocon.encodeToConfig(OwnershipSerializer, decoded)

        assertThat(decoded).isEqualTo(Ownership(primaryOwner = null, fallbackOwner = "release-team"))
        assertThat(encoded.hasPathOrNull("primary-owner")).isTrue()
        assertThat(encoded.getIsNull("primary-owner")).isTrue()
        assertThat(encoded.getString("fallback-owner")).isEqualTo("release-team")
        assertThat(hocon.decodeFromConfig(OwnershipSerializer, encoded)).isEqualTo(decoded)
    }
}

private data class DatabaseConfig(
    val host: String,
    val port: Int
)

private data class AppSettings(
    val serviceName: String,
    val enabled: Boolean = DEFAULT_ENABLED,
    val database: DatabaseConfig,
    val labels: List<String> = DEFAULT_LABELS,
    val thresholds: Map<String, Int> = DEFAULT_THRESHOLDS,
    val requestTimeout: Duration = DEFAULT_REQUEST_TIMEOUT,
    val memoryLimit: ConfigMemorySize = DEFAULT_MEMORY_LIMIT
) {
    companion object {
        const val DEFAULT_ENABLED: Boolean = false
        val DEFAULT_LABELS: List<String> = emptyList()
        val DEFAULT_THRESHOLDS: Map<String, Int> = emptyMap()
        val DEFAULT_REQUEST_TIMEOUT: Duration = Duration.ofSeconds(30)
        val DEFAULT_MEMORY_LIMIT: ConfigMemorySize = ConfigMemorySize.ofBytes(64L * 1024L * 1024L)
    }
}

private sealed interface Notification

private data class EmailNotification(
    val address: String,
    val subject: String
) : Notification

private data class SmsNotification(
    val phoneNumber: String,
    val urgent: Boolean
) : Notification

private data class NotificationEnvelope(
    val id: String,
    val notification: Notification
)

private data class Ownership(
    val primaryOwner: String?,
    val fallbackOwner: String
)

private object DatabaseConfigSerializer : KSerializer<DatabaseConfig> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(
        "org_jetbrains_kotlinx.kotlinx_serialization_hocon.DatabaseConfig"
    ) {
        element<String>("host")
        element<Int>("port")
    }

    override fun serialize(encoder: Encoder, value: DatabaseConfig): Unit = encoder.encodeStructure(descriptor) {
        encodeStringElement(descriptor, 0, value.host)
        encodeIntElement(descriptor, 1, value.port)
    }

    override fun deserialize(decoder: Decoder): DatabaseConfig = decoder.decodeStructure(descriptor) {
        var host: String? = null
        var port: Int? = null
        while (true) {
            when (val index: Int = decodeElementIndex(descriptor)) {
                0 -> host = decodeStringElement(descriptor, index)
                1 -> port = decodeIntElement(descriptor, index)
                CompositeDecoder.DECODE_DONE -> break
                else -> throw SerializationException("Unexpected database element index: $index")
            }
        }
        DatabaseConfig(
            host = host ?: missingField("host"),
            port = port ?: missingField("port")
        )
    }
}

private object AppSettingsSerializer : KSerializer<AppSettings> {
    private val labelsSerializer: KSerializer<List<String>> = ListSerializer(String.serializer())
    private val thresholdsSerializer: KSerializer<Map<String, Int>> = MapSerializer(String.serializer(), Int.serializer())

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(
        "org_jetbrains_kotlinx.kotlinx_serialization_hocon.AppSettings"
    ) {
        element<String>("serviceName")
        element<Boolean>("enabled", isOptional = true)
        element("database", DatabaseConfigSerializer.descriptor)
        element("labels", labelsSerializer.descriptor, isOptional = true)
        element("thresholds", thresholdsSerializer.descriptor, isOptional = true)
        element("requestTimeout", JavaDurationSerializer.descriptor, isOptional = true)
        element("memoryLimit", ConfigMemorySizeSerializer.descriptor, isOptional = true)
    }

    override fun serialize(encoder: Encoder, value: AppSettings): Unit = encoder.encodeStructure(descriptor) {
        encodeStringElement(descriptor, 0, value.serviceName)
        if (value.enabled != AppSettings.DEFAULT_ENABLED || shouldEncodeElementDefault(descriptor, 1)) {
            encodeBooleanElement(descriptor, 1, value.enabled)
        }
        encodeSerializableElement(descriptor, 2, DatabaseConfigSerializer, value.database)
        if (value.labels != AppSettings.DEFAULT_LABELS || shouldEncodeElementDefault(descriptor, 3)) {
            encodeSerializableElement(descriptor, 3, labelsSerializer, value.labels)
        }
        if (value.thresholds != AppSettings.DEFAULT_THRESHOLDS || shouldEncodeElementDefault(descriptor, 4)) {
            encodeSerializableElement(descriptor, 4, thresholdsSerializer, value.thresholds)
        }
        if (value.requestTimeout != AppSettings.DEFAULT_REQUEST_TIMEOUT || shouldEncodeElementDefault(descriptor, 5)) {
            encodeSerializableElement(descriptor, 5, JavaDurationSerializer, value.requestTimeout)
        }
        if (value.memoryLimit != AppSettings.DEFAULT_MEMORY_LIMIT || shouldEncodeElementDefault(descriptor, 6)) {
            encodeSerializableElement(descriptor, 6, ConfigMemorySizeSerializer, value.memoryLimit)
        }
    }

    override fun deserialize(decoder: Decoder): AppSettings = decoder.decodeStructure(descriptor) {
        var serviceName: String? = null
        var enabled: Boolean = AppSettings.DEFAULT_ENABLED
        var database: DatabaseConfig? = null
        var labels: List<String> = AppSettings.DEFAULT_LABELS
        var thresholds: Map<String, Int> = AppSettings.DEFAULT_THRESHOLDS
        var requestTimeout: Duration = AppSettings.DEFAULT_REQUEST_TIMEOUT
        var memoryLimit: ConfigMemorySize = AppSettings.DEFAULT_MEMORY_LIMIT
        while (true) {
            when (val index: Int = decodeElementIndex(descriptor)) {
                0 -> serviceName = decodeStringElement(descriptor, index)
                1 -> enabled = decodeBooleanElement(descriptor, index)
                2 -> database = decodeSerializableElement(descriptor, index, DatabaseConfigSerializer)
                3 -> labels = decodeSerializableElement(descriptor, index, labelsSerializer)
                4 -> thresholds = decodeSerializableElement(descriptor, index, thresholdsSerializer)
                5 -> requestTimeout = decodeSerializableElement(descriptor, index, JavaDurationSerializer)
                6 -> memoryLimit = decodeSerializableElement(descriptor, index, ConfigMemorySizeSerializer)
                CompositeDecoder.DECODE_DONE -> break
                else -> throw SerializationException("Unexpected app settings element index: $index")
            }
        }
        AppSettings(
            serviceName = serviceName ?: missingField("serviceName"),
            enabled = enabled,
            database = database ?: missingField("database"),
            labels = labels,
            thresholds = thresholds,
            requestTimeout = requestTimeout,
            memoryLimit = memoryLimit
        )
    }
}

private val notificationModule: SerializersModule = SerializersModule {
    polymorphic(Notification::class) {
        subclass(EmailNotification::class, EmailNotificationSerializer)
        subclass(SmsNotification::class, SmsNotificationSerializer)
    }
}

private object EmailNotificationSerializer : KSerializer<EmailNotification> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("email") {
        element<String>("address")
        element<String>("subject")
    }

    override fun serialize(encoder: Encoder, value: EmailNotification): Unit = encoder.encodeStructure(descriptor) {
        encodeStringElement(descriptor, 0, value.address)
        encodeStringElement(descriptor, 1, value.subject)
    }

    override fun deserialize(decoder: Decoder): EmailNotification = decoder.decodeStructure(descriptor) {
        var address: String? = null
        var subject: String? = null
        while (true) {
            when (val index: Int = decodeElementIndex(descriptor)) {
                0 -> address = decodeStringElement(descriptor, index)
                1 -> subject = decodeStringElement(descriptor, index)
                CompositeDecoder.DECODE_DONE -> break
                else -> throw SerializationException("Unexpected email notification element index: $index")
            }
        }
        EmailNotification(
            address = address ?: missingField("address"),
            subject = subject ?: missingField("subject")
        )
    }
}

private object SmsNotificationSerializer : KSerializer<SmsNotification> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("sms") {
        element<String>("phoneNumber")
        element<Boolean>("urgent")
    }

    override fun serialize(encoder: Encoder, value: SmsNotification): Unit = encoder.encodeStructure(descriptor) {
        encodeStringElement(descriptor, 0, value.phoneNumber)
        encodeBooleanElement(descriptor, 1, value.urgent)
    }

    override fun deserialize(decoder: Decoder): SmsNotification = decoder.decodeStructure(descriptor) {
        var phoneNumber: String? = null
        var urgent: Boolean? = null
        while (true) {
            when (val index: Int = decodeElementIndex(descriptor)) {
                0 -> phoneNumber = decodeStringElement(descriptor, index)
                1 -> urgent = decodeBooleanElement(descriptor, index)
                CompositeDecoder.DECODE_DONE -> break
                else -> throw SerializationException("Unexpected sms notification element index: $index")
            }
        }
        SmsNotification(
            phoneNumber = phoneNumber ?: missingField("phoneNumber"),
            urgent = urgent ?: missingField("urgent")
        )
    }
}

private object NotificationEnvelopeSerializer : KSerializer<NotificationEnvelope> {
    private val notificationSerializer: KSerializer<Notification> = PolymorphicSerializer(Notification::class)

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(
        "org_jetbrains_kotlinx.kotlinx_serialization_hocon.NotificationEnvelope"
    ) {
        element<String>("id")
        element("notification", notificationSerializer.descriptor)
    }

    override fun serialize(encoder: Encoder, value: NotificationEnvelope): Unit = encoder.encodeStructure(descriptor) {
        encodeStringElement(descriptor, 0, value.id)
        encodeSerializableElement(descriptor, 1, notificationSerializer, value.notification)
    }

    override fun deserialize(decoder: Decoder): NotificationEnvelope = decoder.decodeStructure(descriptor) {
        var id: String? = null
        var notification: Notification? = null
        while (true) {
            when (val index: Int = decodeElementIndex(descriptor)) {
                0 -> id = decodeStringElement(descriptor, index)
                1 -> notification = decodeSerializableElement(descriptor, index, notificationSerializer)
                CompositeDecoder.DECODE_DONE -> break
                else -> throw SerializationException("Unexpected envelope element index: $index")
            }
        }
        NotificationEnvelope(
            id = id ?: missingField("id"),
            notification = notification ?: missingField("notification")
        )
    }
}

private object OwnershipSerializer : KSerializer<Ownership> {
    private val nullableStringSerializer: KSerializer<String?> = String.serializer().nullable

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(
        "org_jetbrains_kotlinx.kotlinx_serialization_hocon.Ownership"
    ) {
        element("primaryOwner", nullableStringSerializer.descriptor)
        element<String>("fallbackOwner")
    }

    override fun serialize(encoder: Encoder, value: Ownership): Unit = encoder.encodeStructure(descriptor) {
        encodeNullableSerializableElement(descriptor, 0, nullableStringSerializer, value.primaryOwner)
        encodeStringElement(descriptor, 1, value.fallbackOwner)
    }

    override fun deserialize(decoder: Decoder): Ownership = decoder.decodeStructure(descriptor) {
        var primaryOwner: String? = null
        var fallbackOwner: String? = null
        while (true) {
            when (val index: Int = decodeElementIndex(descriptor)) {
                0 -> primaryOwner = decodeNullableSerializableElement(descriptor, index, nullableStringSerializer)
                1 -> fallbackOwner = decodeStringElement(descriptor, index)
                CompositeDecoder.DECODE_DONE -> break
                else -> throw SerializationException("Unexpected ownership element index: $index")
            }
        }
        Ownership(
            primaryOwner = primaryOwner,
            fallbackOwner = fallbackOwner ?: missingField("fallbackOwner")
        )
    }
}

private fun missingField(name: String): Nothing = throw SerializationException("Missing required field: $name")
