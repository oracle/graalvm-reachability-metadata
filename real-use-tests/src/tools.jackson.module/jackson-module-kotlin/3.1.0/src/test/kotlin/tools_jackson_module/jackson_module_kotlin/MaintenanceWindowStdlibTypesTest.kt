/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_module.jackson_module_kotlin

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinFeature
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.kotlinModule
import tools.jackson.module.kotlin.readValue

/**
 * Kotlin standard-library types (ranges, Regex, Sequence, Pair, Triple and kotlin.time.Duration)
 * bound as fields of the maintenance-window plans an operations scheduler stores and loads.
 */
public class MaintenanceWindowStdlibTypesTest {

    data class MaintenanceWindow(
        val name: String,
        val hostPattern: Regex,
        val ports: IntRange,
        val shards: CharRange,
        val offsetsMillis: LongRange,
        val owner: Pair<String, String>,
        val version: Triple<Int, Int, Int>,
    ) {
        fun appliesTo(host: String, port: Int, shard: Char): Boolean =
            hostPattern.matches(host) && port in ports && shard in shards
    }

    data class DrainPlan(val window: String, val steps: Sequence<String>, val timeout: Duration)

    private val mapper = jacksonObjectMapper()

    private val durationMapper: JsonMapper = JsonMapper.builder()
        .addModule(kotlinModule { enable(KotlinFeature.UseJavaDurationConversion) })
        .build()

    private val nightly = MaintenanceWindow(
        name = "nightly-db",
        hostPattern = Regex("db-[0-9]+\\.eu-west"),
        ports = 5432..5439,
        shards = 'a'..'f',
        offsetsMillis = 0L..3_600_000L,
        owner = "team" to "storage",
        version = Triple(2, 4, 1),
    )

    @Test
    fun writesRangesPairsAndTriplesAsStructuredJson(): Unit {
        val tree: JsonNode = mapper.readTree(mapper.writeValueAsString(nightly))

        assertThat(tree.get("ports").get("start").intValue()).isEqualTo(5432)
        assertThat(tree.get("ports").get("end").intValue()).isEqualTo(5439)
        assertThat(tree.get("shards").get("start").asString()).isEqualTo("a")
        assertThat(tree.get("shards").get("end").asString()).isEqualTo("f")
        assertThat(tree.get("offsetsMillis").get("end").longValue()).isEqualTo(3_600_000L)
        assertThat(tree.get("owner").get("first").asString()).isEqualTo("team")
        assertThat(tree.get("owner").get("second").asString()).isEqualTo("storage")
        assertThat(tree.get("version").get("third").intValue()).isEqualTo(1)
    }

    @Test
    fun loadsAStoredWindowAndAppliesItToHosts(): Unit {
        val stored = """
            {
              "name": "weekly-cache",
              "hostPattern": "cache-[a-z]+",
              "ports": {"start": 6379, "end": 6381},
              "shards": {"start": "m", "end": "p"},
              "offsetsMillis": {"start": 1000, "end": 2000},
              "owner": {"first": "team", "second": "edge"},
              "version": {"first": 1, "second": 0, "third": 7}
            }
        """.trimIndent()

        val window: MaintenanceWindow = mapper.readValue(stored)

        assertThat(window.ports).isEqualTo(6379..6381)
        assertThat(window.shards).isEqualTo('m'..'p')
        assertThat(window.offsetsMillis).isEqualTo(1000L..2000L)
        assertThat(window.owner).isEqualTo("team" to "edge")
        assertThat(window.version).isEqualTo(Triple(1, 0, 7))
        assertThat(window.appliesTo("cache-blue", 6380, 'n')).isTrue()
        assertThat(window.appliesTo("cache-blue", 6382, 'n')).isFalse()
        assertThat(window.appliesTo("db-1", 6380, 'n')).isFalse()
    }

    @Test
    fun roundTripsAWindowIncludingItsHostPattern(): Unit {
        val restored: MaintenanceWindow = mapper.readValue(mapper.writeValueAsString(nightly))

        assertThat(restored.hostPattern.pattern).isEqualTo(nightly.hostPattern.pattern)
        assertThat(restored.copy(hostPattern = nightly.hostPattern)).isEqualTo(nightly)
        assertThat(restored.appliesTo("db-12.eu-west", 5435, 'c')).isTrue()
        assertThat(restored.appliesTo("db-12.us-east", 5435, 'c')).isFalse()
    }

    @Test
    fun readsARegexWithOptionsFromItsObjectForm(): Unit {
        val stored = """{"pattern": "^EDGE-[0-9]+$", "options": ["IGNORE_CASE"]}"""

        val pattern: Regex = mapper.readValue(stored)

        assertThat(pattern.options).containsExactly(RegexOption.IGNORE_CASE)
        assertThat(pattern.matches("edge-42")).isTrue()
        assertThat(pattern.matches("core-42")).isFalse()
    }

    @Test
    fun writesAndReadsADrainPlanWithASequenceAndAKotlinDuration(): Unit {
        val plan = DrainPlan(
            window = "nightly-db",
            steps = generateSequence(1) { it + 1 }.take(3).map { "drain-replica-$it" },
            timeout = 1.hours + 30.minutes + 15.seconds,
        )

        val json: String = durationMapper.writeValueAsString(plan)
        val tree: JsonNode = durationMapper.readTree(json)
        val stepNodes: Iterable<JsonNode> = tree.get("steps")
        assertThat(stepNodes.map { it.asString() }).containsExactly(
            "drain-replica-1", "drain-replica-2", "drain-replica-3",
        )
        assertThat(tree.get("timeout").asString()).isEqualTo("PT1H30M15S")

        val restored: DrainPlan = durationMapper.readValue(json)
        assertThat(restored.window).isEqualTo("nightly-db")
        assertThat(restored.steps.toList())
            .containsExactly("drain-replica-1", "drain-replica-2", "drain-replica-3")
        assertThat(restored.timeout).isEqualTo(5_415.seconds)
    }
}
