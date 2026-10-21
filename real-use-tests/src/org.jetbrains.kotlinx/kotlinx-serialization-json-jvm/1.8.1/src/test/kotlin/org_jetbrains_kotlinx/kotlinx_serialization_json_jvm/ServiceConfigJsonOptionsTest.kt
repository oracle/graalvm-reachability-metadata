/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_json_jvm

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonNamingStrategy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Exercises the `Json` builder configuration: `JsonNamingStrategy.SnakeCase`, `@JsonNames`
 * aliases, lenient parsing with comments and trailing commas, `coerceInputValues`,
 * case-insensitive enums, `explicitNulls`, `@EncodeDefault`, and deriving one `Json` from another.
 *
 * A service loads its hand-edited configuration file, tolerating legacy key names and sloppy
 * values, and writes back a normalized file that holds only the settings that differ from defaults.
 */
@OptIn(ExperimentalSerializationApi::class)
public class ServiceConfigJsonOptionsTest {

    @Serializable
    public enum class LogLevel { TRACE, DEBUG, INFO, WARN, ERROR }

    @Serializable
    public enum class Region {
        @SerialName("eu-west")
        EU_WEST,

        @SerialName("us-east")
        US_EAST,
    }

    @Serializable
    public data class PoolSettings(val maxConnections: Int = 10, val idleTimeoutSeconds: Long = 60)

    @Serializable
    public data class ServiceConfig(
        val serviceName: String,
        @JsonNames("listen_port", "port") val httpPort: Int = 8080,
        val logLevel: LogLevel = LogLevel.INFO,
        val region: Region = Region.EU_WEST,
        val databaseUrl: String?,
        val connectionPool: PoolSettings = PoolSettings(),
        val featureFlags: Map<String, Boolean> = emptyMap(),
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) val schemaVersion: Int = 2,
        val adminEmail: String? = null,
    )

    /** Reads hand-edited configuration files the way operators actually write them. */
    private val configReader: Json = Json {
        namingStrategy = JsonNamingStrategy.SnakeCase
        isLenient = true
        allowComments = true
        allowTrailingComma = true
        ignoreUnknownKeys = true
        coerceInputValues = true
        decodeEnumsCaseInsensitive = true
        explicitNulls = false
    }

    /** Writes the normalized file with the reader's naming, printed for humans. */
    private val configWriter: Json = Json(configReader) {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    @Test
    fun loadsHandEditedConfigWithLegacyKeysAndSloppyValues() {
        val fileContent: String = """
            {
              // edited by ops during the March migration
              service_name: "billing-api",
              listen_port: 9090,
              log_level: debug,
              region: "ap-south",
              connection_pool: { max_connections: 32, idle_timeout_seconds: null, },
              feature_flags: { "darkMode": true, "betaCheckout": false, },
              legacy_timeout: 5,
              /* paged when the nightly settlement job fails */
              admin_email: "oncall@example.com",
            }
        """.trimIndent()

        val config: ServiceConfig = configReader.decodeFromString<ServiceConfig>(fileContent)

        assertEquals("billing-api", config.serviceName)
        assertEquals(9090, config.httpPort)
        assertEquals(LogLevel.DEBUG, config.logLevel)
        assertEquals(Region.EU_WEST, config.region)
        assertNull(config.databaseUrl)
        assertEquals(PoolSettings(maxConnections = 32, idleTimeoutSeconds = 60), config.connectionPool)
        assertEquals(mapOf("darkMode" to true, "betaCheckout" to false), config.featureFlags)
        assertEquals(2, config.schemaVersion)
        assertEquals("oncall@example.com", config.adminEmail)
    }

    @Test
    fun acceptsEveryAliasOfThePortKey() {
        val ports: List<Int> = listOf("http_port", "listen_port", "port").mapIndexed { index, key ->
            val fileContent: String = """{"service_name": "search", "$key": ${7000 + index}}"""
            configReader.decodeFromString<ServiceConfig>(fileContent).httpPort
        }

        assertEquals(listOf(7000, 7001, 7002), ports)
    }

    @Test
    fun writesOnlyNonDefaultSettingsInSnakeCase(@TempDir workDir: Path) {
        val config: ServiceConfig = ServiceConfig(
            serviceName = "billing-api",
            httpPort = 9090,
            logLevel = LogLevel.WARN,
            region = Region.US_EAST,
            databaseUrl = null,
            connectionPool = PoolSettings(maxConnections = 32),
            featureFlags = mapOf("darkMode" to true),
            adminEmail = null,
        )

        val configFile: Path = workDir.resolve("service.json")
        Files.writeString(configFile, configWriter.encodeToString<ServiceConfig>(config))
        val written: String = Files.readString(configFile)

        assertEquals(
            """
            {
              "service_name": "billing-api",
              "http_port": 9090,
              "log_level": "WARN",
              "region": "us-east",
              "connection_pool": {
                "max_connections": 32
              },
              "feature_flags": {
                "darkMode": true
              },
              "schema_version": 2
            }
            """.trimIndent(),
            written,
        )
        assertEquals(config, configReader.decodeFromString<ServiceConfig>(written))
    }

    @Test
    fun strictDefaultJsonRejectsTheHandEditedFile() {
        val fileContent: String = """
            {
              // comment
              "serviceName": "billing-api",
              "databaseUrl": null,
            }
        """.trimIndent()

        assertThrows(SerializationException::class.java) {
            Json.decodeFromString<ServiceConfig>(fileContent)
        }
        assertEquals(
            "billing-api",
            configReader.decodeFromString<ServiceConfig>(fileContent.replace("serviceName", "service_name"))
                .serviceName,
        )
    }
}
