/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_properties_jvm

import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.properties.Properties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

public class Kotlinx_serialization_properties_jvmTest {
    @Test
    public fun encodeToMapPreservesPrimitiveValueTypesAndEnumNames() {
        val endpoint: ServerEndpoint = ServerEndpoint(
            host = "db.internal",
            port = 15_432,
            tls = true,
            grade = 'A',
            loadFactor = 0.875,
            mode = EndpointMode.PRIMARY,
        )

        val encoded: Map<String, Any> = Properties.Default.encodeToMap(ServerEndpoint.serializer(), endpoint)

        assertThat(encoded)
            .containsEntry("host", "db.internal")
            .containsEntry("port", 15_432)
            .containsEntry("tls", true)
            .containsEntry("grade", 'A')
            .containsEntry("loadFactor", 0.875)
            .containsEntry("mode", "PRIMARY")
    }

    @Test
    public fun decodeFromMapAcceptsTypedValuesAndEnumOrdinals() {
        val decoded: ServerEndpoint = Properties.Default.decodeFromMap(
            ServerEndpoint.serializer(),
            mapOf(
                "host" to "cache.internal",
                "port" to 11_211,
                "tls" to false,
                "grade" to 'B',
                "loadFactor" to 1.25,
                "mode" to 1,
            ),
        )

        assertThat(decoded).isEqualTo(
            ServerEndpoint(
                host = "cache.internal",
                port = 11_211,
                tls = false,
                grade = 'B',
                loadFactor = 1.25,
                mode = EndpointMode.SECONDARY,
            ),
        )
    }

    @Test
    public fun encodeToStringMapConvertsAllScalarValuesToStrings() {
        val values: ScalarValues = ScalarValues(
            byteValue = 7,
            shortValue = 320,
            intValue = 65_536,
            longValue = 9_876_543_210,
            floatValue = 1.5f,
            doubleValue = 2.75,
            booleanValue = true,
            charValue = 'K',
            enumValue = EndpointMode.SECONDARY,
        )

        val encoded: Map<String, String> = Properties.Default.encodeToStringMap(ScalarValues.serializer(), values)

        assertThat(encoded).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                "byteValue" to "7",
                "shortValue" to "320",
                "intValue" to "65536",
                "longValue" to "9876543210",
                "floatValue" to "1.5",
                "doubleValue" to "2.75",
                "booleanValue" to "true",
                "charValue" to "K",
                "enumValue" to "SECONDARY",
            ),
        )
    }

    @Test
    public fun decodeFromStringMapParsesScalarValues() {
        val decoded: ScalarValues = Properties.Default.decodeFromStringMap(
            ScalarValues.serializer(),
            mapOf(
                "byteValue" to "-8",
                "shortValue" to "1024",
                "intValue" to "42",
                "longValue" to "123456789",
                "floatValue" to "3.25",
                "doubleValue" to "6.5",
                "booleanValue" to "true",
                "charValue" to "Z",
                "enumValue" to "PRIMARY",
            ),
        )

        assertThat(decoded).isEqualTo(
            ScalarValues(
                byteValue = -8,
                shortValue = 1024,
                intValue = 42,
                longValue = 123_456_789,
                floatValue = 3.25f,
                doubleValue = 6.5,
                booleanValue = true,
                charValue = 'Z',
                enumValue = EndpointMode.PRIMARY,
            ),
        )
    }

    @Test
    public fun nestedObjectsListsMapsAndDefaultNullsRoundTripThroughProperties() {
        val application: ApplicationConfig = ApplicationConfig(
            name = "payments",
            owner = Owner(name = "platform", email = "platform@example.test"),
            services = listOf(
                Service(name = "api", ports = listOf(8080, 8443), metadata = mapOf("region" to "eu")),
                Service(name = "worker", ports = listOf(9090), metadata = mapOf("queue" to "settlement")),
            ),
            labels = mapOf("tier" to "backend", "critical" to "true"),
        )

        val encoded: Map<String, Any> = Properties.Default.encodeToMap(ApplicationConfig.serializer(), application)
        val decoded: ApplicationConfig = Properties.Default.decodeFromMap(ApplicationConfig.serializer(), encoded)

        assertThat(encoded)
            .containsEntry("name", "payments")
            .containsEntry("owner.name", "platform")
            .containsEntry("owner.email", "platform@example.test")
            .containsEntry("services.0.name", "api")
            .containsEntry("services.1.name", "worker")
        assertThat(encoded.keys).noneMatch { key: String -> key == "description" || key.startsWith("description.") }
        assertThat(decoded).isEqualTo(application)
    }

    @Test
    public fun absentOptionalFieldsAreDecodedWithDefaultValues() {
        val decoded: FeatureToggle = Properties.Default.decodeFromStringMap(
            FeatureToggle.serializer(),
            mapOf("name" to "native-image-cache"),
        )

        assertThat(decoded).isEqualTo(
            FeatureToggle(
                name = "native-image-cache",
                retryCount = 3,
                note = null,
            ),
        )
    }

    @Test
    public fun serialNameAnnotationsDefineFlattenedPropertyPaths() {
        val config: PublicClientConfig = PublicClientConfig(
            displayName = "checkout-api",
            endpoint = PublicEndpoint(host = "api.example.test", port = 443),
        )

        val encoded: Map<String, String> = Properties.Default.encodeToStringMap(PublicClientConfig.serializer(), config)
        val decoded: PublicClientConfig = Properties.Default.decodeFromStringMap(PublicClientConfig.serializer(), encoded)

        assertThat(encoded).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                "display-name" to "checkout-api",
                "server.host-name" to "api.example.test",
                "server.port-number" to "443",
            ),
        )
        assertThat(encoded.keys).doesNotContain("displayName", "endpoint.host", "endpoint.port")
        assertThat(decoded).isEqualTo(config)
    }

    @Test
    public fun customSerializersModuleSupportsPolymorphicProperties() {
        val module: SerializersModule = SerializersModule {
            polymorphic(AuditEvent::class) {
                subclass(LoginEvent::class)
                subclass(PermissionChangeEvent::class)
            }
        }
        val properties: Properties = kotlinx.serialization.properties.Properties(module)
        val event: AuditEvent = LoginEvent(user = "ada", successful = true)

        val encoded: Map<String, String> = properties.encodeToStringMap(PolymorphicSerializer(AuditEvent::class), event)
        val decoded: AuditEvent = properties.decodeFromStringMap(PolymorphicSerializer(AuditEvent::class), encoded)

        assertThat(encoded).containsEntry("type", "login")
        assertThat(encoded).containsEntry("user", "ada")
        assertThat(encoded).containsEntry("successful", "true")
        assertThat(decoded).isEqualTo(event)
    }

    @Test
    public fun valueClassPropertiesAreEncodedAtOwningPropertyPath() {
        val route: GatewayRoute = GatewayRoute(
            name = RouteName("checkout"),
            retryLimit = RetryLimit(5),
            enabled = EnabledFlag(true),
        )

        val encoded: Map<String, String> = Properties.Default.encodeToStringMap(GatewayRoute.serializer(), route)
        val decoded: GatewayRoute = Properties.Default.decodeFromStringMap(GatewayRoute.serializer(), encoded)

        assertThat(encoded).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                "name" to "checkout",
                "retryLimit" to "5",
                "enabled" to "true",
            ),
        )
        assertThat(encoded.keys).doesNotContain("name.value", "retryLimit.value", "enabled.value")
        assertThat(decoded).isEqualTo(route)
    }

    @Test
    public fun invalidEnumNameReportsSerializationException() {
        assertThatThrownBy {
            Properties.Default.decodeFromStringMap(
                ServerEndpoint.serializer(),
                mapOf(
                    "host" to "db.internal",
                    "port" to "15432",
                    "tls" to "true",
                    "grade" to "A",
                    "loadFactor" to "0.875",
                    "mode" to "UNKNOWN",
                ),
            )
        }.isInstanceOf(SerializationException::class.java)
            .hasMessageContaining("EndpointMode")
            .hasMessageContaining("UNKNOWN")
    }
}

@Serializable
public data class ServerEndpoint(
    val host: String,
    val port: Int,
    val tls: Boolean,
    val grade: Char,
    val loadFactor: Double,
    val mode: EndpointMode,
)

@Serializable
public enum class EndpointMode {
    PRIMARY,
    SECONDARY,
}

@Serializable
public data class ScalarValues(
    val byteValue: Byte,
    val shortValue: Short,
    val intValue: Int,
    val longValue: Long,
    val floatValue: Float,
    val doubleValue: Double,
    val booleanValue: Boolean,
    val charValue: Char,
    val enumValue: EndpointMode,
)

@Serializable
public data class ApplicationConfig(
    val name: String,
    val owner: Owner,
    val services: List<Service>,
    val labels: Map<String, String>,
    val description: String? = null,
)

@Serializable
public data class Owner(
    val name: String,
    val email: String,
)

@Serializable
public data class Service(
    val name: String,
    val ports: List<Int>,
    val metadata: Map<String, String>,
)

@Serializable
public data class FeatureToggle(
    val name: String,
    val retryCount: Int = 3,
    val note: String? = null,
)

@Serializable
public data class PublicClientConfig(
    @SerialName("display-name") val displayName: String,
    @SerialName("server") val endpoint: PublicEndpoint,
)

@Serializable
public data class PublicEndpoint(
    @SerialName("host-name") val host: String,
    @SerialName("port-number") val port: Int,
)

@Serializable
public data class GatewayRoute(
    val name: RouteName,
    val retryLimit: RetryLimit,
    val enabled: EnabledFlag,
)

@Serializable
@JvmInline
public value class RouteName(public val value: String)

@Serializable
@JvmInline
public value class RetryLimit(public val value: Int)

@Serializable
@JvmInline
public value class EnabledFlag(public val value: Boolean)

@Serializable
public abstract class AuditEvent

@Serializable
@SerialName("login")
public data class LoginEvent(
    val user: String,
    val successful: Boolean,
) : AuditEvent()

@Serializable
@SerialName("permission-change")
public data class PermissionChangeEvent(
    val user: String,
    val permission: String,
) : AuditEvent()
