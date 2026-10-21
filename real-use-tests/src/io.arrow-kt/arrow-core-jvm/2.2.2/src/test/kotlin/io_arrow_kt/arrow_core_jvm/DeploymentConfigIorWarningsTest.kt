/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_core_jvm

import arrow.core.Either
import arrow.core.Ior
import arrow.core.IorNel
import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.raise.IorRaise
import arrow.core.raise.iorNel
import arrow.core.right
import arrow.core.toNonEmptyListOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The `Ior` DSL (`iorNel {}` over `IorRaise`) that keeps non-fatal warnings next to a result.
 *
 * A deployment tool loads a service's `key=value` configuration. Deprecated keys, unknown keys
 * and unparsable optional values are not fatal: the loader still produces a configuration and
 * reports the problems as warnings, so the result is `Ior.Both`. A missing required key aborts
 * the load, and the operator then sees every warning gathered so far together with the fatal
 * problem in `Ior.Left`. A clean file yields `Ior.Right`. Per-key readers return `Ior` values
 * that the loader binds, and optional values fall back to defaults with `getOrAccumulate`.
 */
class DeploymentConfigIorWarningsTest {

    sealed interface ConfigIssue {
        val key: String

        data class Deprecated(override val key: String, val replacement: String) : ConfigIssue

        data class Unknown(override val key: String) : ConfigIssue

        data class Unparsable(override val key: String, val value: String) : ConfigIssue

        data class Missing(override val key: String) : ConfigIssue
    }

    data class DeploymentConfig(
        val serviceName: String,
        val replicas: Int,
        val port: Int,
        val tags: List<String>,
    )

    private val knownKeys: Set<String> =
        setOf("service.name", "service.replicas", "http.port", "service.tags")

    private val renamedKeys: Map<String, String> =
        mapOf("app.name" to "service.name", "server.port" to "http.port")

    private fun parse(text: String): Map<String, String> =
        text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .associate { line ->
                val (key: String, value: String) = line.split("=", limit = 2)
                key.trim() to value.trim()
            }

    /** Renames deprecated keys, warning about each, and flags keys nobody reads. */
    private fun normalize(raw: Map<String, String>): IorNel<ConfigIssue, Map<String, String>> {
        val issues: MutableList<ConfigIssue> = mutableListOf()
        val normalized: Map<String, String> = raw.mapKeys { (key: String, _) ->
            val replacement: String? = renamedKeys[key]
            if (replacement != null) {
                issues += ConfigIssue.Deprecated(key, replacement)
                replacement
            } else {
                if (key !in knownKeys) issues += ConfigIssue.Unknown(key)
                key
            }
        }.filterKeys { it in knownKeys }
        val warnings: NonEmptyList<ConfigIssue>? = issues.toNonEmptyListOrNull()
        return if (warnings == null) Ior.Right(normalized) else Ior.Both(warnings, normalized)
    }

    private fun intValue(settings: Map<String, String>, key: String): Either<ConfigIssue, Int>? {
        val value: String = settings[key] ?: return null
        return value.toIntOrNull()?.right() ?: ConfigIssue.Unparsable(key, value).left()
    }

    private fun IorRaise<NonEmptyList<ConfigIssue>>.optionalInt(
        settings: Map<String, String>,
        key: String,
        default: Int,
    ): Int {
        val parsed: Either<ConfigIssue, Int> = intValue(settings, key) ?: return default
        return parsed.mapLeft { nonEmptyListOf(it) }.getOrAccumulate { default }
    }

    private fun load(text: String): IorNel<ConfigIssue, DeploymentConfig> = iorNel {
        val settings: Map<String, String> = normalize(parse(text)).bind()
        val serviceName: String = settings["service.name"]
            ?: raise(nonEmptyListOf(ConfigIssue.Missing("service.name")))
        val replicas: Int = optionalInt(settings, "service.replicas", default = 1)
        val port: Int = optionalInt(settings, "http.port", default = 8080)
        val tags: List<String> = settings["service.tags"]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        DeploymentConfig(serviceName, replicas, port, tags)
    }

    @Test
    fun cleanConfigurationLoadsWithoutWarnings() {
        val result: IorNel<ConfigIssue, DeploymentConfig> = load(
            """
            # billing service
            service.name = billing
            service.replicas = 3
            http.port = 9090
            service.tags = payments, eu-west
            """.trimIndent(),
        )

        assertTrue(result.isRight())
        assertEquals(
            DeploymentConfig("billing", 3, 9090, listOf("payments", "eu-west")),
            result.getOrNull(),
        )
        assertEquals(null, result.leftOrNull())
    }

    @Test
    fun legacyConfigurationLoadsAndReportsEveryWarning() {
        val result: IorNel<ConfigIssue, DeploymentConfig> = load(
            """
            app.name = billing
            server.port = 7070
            service.replicas = three
            cache.ttl = 30
            """.trimIndent(),
        )

        assertTrue(result.isBoth())
        assertEquals(DeploymentConfig("billing", 1, 7070, emptyList()), result.getOrNull())
        assertEquals(
            listOf(
                ConfigIssue.Deprecated("app.name", "service.name"),
                ConfigIssue.Deprecated("server.port", "http.port"),
                ConfigIssue.Unknown("cache.ttl"),
                ConfigIssue.Unparsable("service.replicas", "three"),
            ),
            result.leftOrNull()?.toList(),
        )
    }

    @Test
    fun missingServiceNameFailsWithTheWarningsGatheredSoFar() {
        val result: IorNel<ConfigIssue, DeploymentConfig> = load(
            """
            server.port = 7070
            debug = true
            """.trimIndent(),
        )

        assertTrue(result.isLeft())
        assertEquals(null, result.getOrNull())
        val report: List<String> = result.fold(
            { issues -> issues.map { "${it::class.simpleName}:${it.key}" } },
            { listOf("loaded") },
            { issues, _ -> issues.map { "${it::class.simpleName}:${it.key}" } + "loaded" },
        )
        assertEquals(
            listOf("Deprecated:server.port", "Unknown:debug", "Missing:service.name"),
            report,
        )
    }

    @Test
    fun deploymentSummaryKeepsTheConfigurationAndCountsWarnings() {
        val legacy: IorNel<ConfigIssue, DeploymentConfig> = load(
            """
            app.name = search
            http.port = 80
            service.tags = internal
            """.trimIndent(),
        )

        val summary: Ior<Int, String> = legacy
            .mapLeft { issues -> issues.size }
            .map { config -> "${config.serviceName}@${config.port}${config.tags}" }

        assertEquals(Ior.Both(1, "search@80[internal]"), summary)
    }
}
