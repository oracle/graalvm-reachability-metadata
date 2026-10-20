/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_module.jackson_module_kotlin

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.exc.MismatchedInputException
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.module.kotlin.KotlinFeature
import tools.jackson.module.kotlin.kotlinModule
import tools.jackson.module.kotlin.readValue

/**
 * Null coercion configured through `KotlinFeature` (empty collections and maps for nulls,
 * nulls replaced by constructor defaults, strict null checks on collection elements), as a
 * service loads YAML configuration whose blank keys arrive as nulls.
 */
public class ServiceConfigNullCoercionTest {

    data class Endpoint(val path: String, val methods: List<String> = listOf("GET"))

    data class ServiceConfig(
        val name: String,
        val port: Int = 8080,
        val timeoutSeconds: Long = 30,
        val logLevel: String = "INFO",
        val allowedOrigins: List<String>,
        val endpoints: List<Endpoint>,
        val labels: Map<String, String>,
        val adminEmails: Set<String> = setOf("ops@example.com"),
    )

    private val lenientMapper: YAMLMapper = YAMLMapper.builder()
        .addModule(
            kotlinModule {
                enable(KotlinFeature.NullToEmptyCollection)
                enable(KotlinFeature.NullToEmptyMap)
                enable(KotlinFeature.NullIsSameAsDefault)
            },
        )
        .build()

    private val strictMapper: YAMLMapper = YAMLMapper.builder()
        .addModule(kotlinModule { enable(KotlinFeature.StrictNullChecks) })
        .build()

    @Test
    fun blankYamlKeysBecomeEmptyCollectionsAndMaps(): Unit {
        val yaml = """
            name: billing
            allowedOrigins:
            endpoints:
            labels:
        """.trimIndent()

        val config: ServiceConfig = lenientMapper.readValue(yaml)

        assertThat(config.name).isEqualTo("billing")
        assertThat(config.allowedOrigins).isEmpty()
        assertThat(config.endpoints).isEmpty()
        assertThat(config.labels).isEmpty()
        assertThat(config.port).isEqualTo(8080)
    }

    @Test
    fun explicitNullsFallBackToConstructorDefaults(): Unit {
        val yaml = """
            name: search
            port: 9200
            logLevel: null
            adminEmails:
            allowedOrigins: ["https://shop.example.com"]
            endpoints:
              - path: /query
                methods:
              - path: /index
                methods: [POST, PUT]
            labels:
              team: discovery
        """.trimIndent()

        val config: ServiceConfig = lenientMapper.readValue(yaml)

        assertThat(config.port).isEqualTo(9200)
        assertThat(config.timeoutSeconds).isEqualTo(30L)
        assertThat(config.logLevel).isEqualTo("INFO")
        assertThat(config.adminEmails).containsExactly("ops@example.com")
        assertThat(config.allowedOrigins).containsExactly("https://shop.example.com")
        assertThat(config.endpoints).containsExactly(
            Endpoint(path = "/query", methods = listOf("GET")),
            Endpoint(path = "/index", methods = listOf("POST", "PUT")),
        )
        assertThat(config.labels).containsEntry("team", "discovery")
    }

    @Test
    fun withoutTheFeaturesABlankRequiredListIsRejected(): Unit {
        val plainMapper: YAMLMapper = YAMLMapper.builder().addModule(kotlinModule()).build()
        val yaml = """
            name: billing
            allowedOrigins:
            endpoints: []
            labels: {}
        """.trimIndent()

        assertThatThrownBy { plainMapper.readValue<ServiceConfig>(yaml) }
            .isInstanceOf(MismatchedInputException::class.java)
            .hasMessageContaining("allowedOrigins")
    }

    @Test
    fun strictNullChecksRejectANullElementInANonNullList(): Unit {
        val yaml = """
            name: gateway
            allowedOrigins:
              - https://a.example.com
              - null
            endpoints: []
            labels: {}
        """.trimIndent()

        assertThatThrownBy { strictMapper.readValue<ServiceConfig>(yaml) }
            .isInstanceOf(MismatchedInputException::class.java)
            .hasMessageContaining("allowedOrigins")
    }

    @Test
    fun strictNullChecksAcceptAWellFormedConfigAndRoundTripIt(): Unit {
        val config = ServiceConfig(
            name = "gateway",
            port = 9443,
            allowedOrigins = listOf("https://a.example.com", "https://b.example.com"),
            endpoints = listOf(Endpoint(path = "/health"), Endpoint(path = "/orders", methods = listOf("POST"))),
            labels = mapOf("tier" to "edge"),
        )

        val yaml = strictMapper.writeValueAsString(config)
        val reloaded: ServiceConfig = strictMapper.readValue(yaml)

        assertThat(yaml).contains("port: 9443").contains("tier: \"edge\"")
        assertThat(reloaded).isEqualTo(config)
    }
}
