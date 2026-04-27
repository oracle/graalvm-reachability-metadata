/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_stdlib_common

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.absoluteValue
import kotlin.properties.Delegates
import kotlin.random.Random

public class Kotlin_stdlib_commonTest {
    @Test
    fun collectionOperationsTransformGroupAndOrderData() {
        data class Dependency(val group: String, val artifact: String, val version: String, val runtimeOnly: Boolean)

        val dependencies = listOf(
            Dependency("org.jetbrains.kotlin", "kotlin-stdlib-common", "1.5.31", false),
            Dependency("org.jetbrains.kotlin", "kotlin-test-common", "1.5.31", true),
            Dependency("org.junit.jupiter", "junit-jupiter-api", "5.8.2", false),
            Dependency("org.assertj", "assertj-core", "3.22.0", false)
        )

        val artifactsByGroup = dependencies
            .groupBy { it.group }
            .mapValues { (_, values) -> values.map { it.artifact }.sorted() }

        assertEquals(
            listOf("kotlin-stdlib-common", "kotlin-test-common"),
            artifactsByGroup.getValue("org.jetbrains.kotlin")
        )
        assertEquals("3.22.0", dependencies.associateBy { it.artifact }.getValue("assertj-core").version)

        val (runtimeOnly, compileTime) = dependencies.partition { it.runtimeOnly }
        assertEquals(listOf("kotlin-test-common"), runtimeOnly.map { it.artifact })
        assertEquals(
            listOf("assertj-core", "junit-jupiter-api", "kotlin-stdlib-common"),
            compileTime.sortedWith(compareBy<Dependency> { it.artifact }.thenBy { it.version }).map { it.artifact }
        )
    }

    @Test
    fun sequencePipelinesAreLazyAndProcessOnlyNeededElements() {
        val consumed = mutableListOf<Int>()
        val squaresOfMultiplesOfThree = generateSequence(1) { it + 1 }
            .onEach { consumed += it }
            .filter { it % 3 == 0 }
            .map { it * it }
            .take(4)

        assertTrue(consumed.isEmpty())

        assertEquals(listOf(9, 36, 81, 144), squaresOfMultiplesOfThree.toList())
        assertEquals((1..12).toList(), consumed)
    }

    @Test
    fun rangesProgressionsAndCoercionHandleBoundaries() {
        assertEquals(listOf(10, 8, 6, 4, 2), (10 downTo 2 step 2).toList())
        assertEquals(listOf('a', 'c', 'e'), ('a'..'f' step 2).toList())
        assertTrue('k' in 'a'..'z')
        assertFalse('K' in 'a'..'z')

        val scores = listOf(-15, 0, 37, 101)
        assertEquals(listOf(0, 0, 37, 100), scores.map { it.coerceIn(0, 100) })
        assertEquals(7, (-7).absoluteValue)
    }

    @Test
    fun stringAndRegexUtilitiesParseNormalizeAndReplaceText() {
        val descriptor = "kotlin-1.5.31"
        val match = Regex("""([a-z]+)-(\d+)\.(\d+)\.(\d+)""").matchEntire(descriptor)
        assertNotNull(match)

        val (name, major, minor, patch) = match!!.destructured
        assertEquals("kotlin", name)
        assertEquals(listOf(1, 5, 31), listOf(major, minor, patch).map { it.toInt() })

        val normalized = """
            |  common metadata
            |  reflection-free tests
            |  native-image compatible
        """.trimMargin()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(separator = ";") { it.replace("-", " ").uppercase() }

        assertEquals("COMMON METADATA;REFLECTION FREE TESTS;NATIVE IMAGE COMPATIBLE", normalized)
    }

    @Test
    fun resultAndPreconditionUtilitiesModelSuccessAndFailurePaths() {
        val doubled = runCatching { "21".toInt() }
            .map { it * 2 }
            .getOrThrow()
        assertEquals(42, doubled)

        val recovered = runCatching { "not-a-number".toInt() }
            .recover { exception -> exception.message.orEmpty().length }
            .getOrThrow()
        assertTrue(recovered > 0)

        val thrown = assertThrows(IllegalArgumentException::class.java) {
            require("metadata".startsWith("stdlib")) { "unexpected artifact name" }
        }
        assertEquals("unexpected artifact name", thrown.message)
    }

    @Test
    fun lazyAndObservableDelegatesCacheAndReportStateChanges() {
        var computations = 0
        val expensiveSummary by lazy(LazyThreadSafetyMode.NONE) {
            computations += 1
            listOf("stdlib", "common", "api").joinToString(separator = ":")
        }

        assertEquals(0, computations)
        assertEquals("stdlib:common:api", expensiveSummary)
        assertEquals("stdlib:common:api", expensiveSummary)
        assertEquals(1, computations)

        val changes = mutableListOf<Pair<String, String>>()
        var lifecycleState by Delegates.observable("created") { _, oldValue, newValue ->
            changes += oldValue to newValue
        }
        lifecycleState = "configured"
        lifecycleState = "verified"

        assertEquals("verified", lifecycleState)
        assertEquals(listOf("created" to "configured", "configured" to "verified"), changes)
    }

    @Test
    fun pairTripleAndComparatorHelpersSupportStructuredDataProcessing() {
        val coordinates = Triple("org.jetbrains.kotlin", "kotlin-stdlib-common", "1.5.31")
        val (group, artifact, version) = coordinates
        assertEquals("org.jetbrains.kotlin:kotlin-stdlib-common:1.5.31", listOf(group, artifact, version).joinToString(":"))

        val aliases = mapOf(
            artifact to setOf("common", "metadata"),
            "kotlin-stdlib" to setOf("jvm", "collections")
        )
        val flattened = aliases.flatMap { (name, tags) -> tags.map { tag -> name to tag } }.toSet()

        assertTrue(("kotlin-stdlib-common" to "metadata") in flattened)
        assertTrue(("kotlin-stdlib" to "collections") in flattened)

        val preferred = listOf("1.5.10", "1.5.31", "1.4.32").maxWithOrNull(
            compareBy<String> { it.substringBefore('.').toInt() }
                .thenBy { it.substringAfter('.').substringBefore('.').toInt() }
                .thenBy { it.substringAfterLast('.').toInt() }
        )
        assertEquals("1.5.31", preferred)
    }

    @Test
    fun arraysAndListAlgorithmsCoverPrimitiveAndGenericContainers() {
        val numbers = intArrayOf(8, 3, 5, 3, 13, 21)
        assertEquals(listOf(3, 3, 5, 8, 13, 21), numbers.sorted())
        assertEquals(53, numbers.sum())
        assertEquals(listOf(8, 13, 21), numbers.filter { it > 5 && it % 2 == 1 || it == 8 })

        val sortedArtifacts = listOf("assertj-core", "junit-jupiter-api", "kotlin-stdlib-common")
        assertEquals(2, sortedArtifacts.binarySearch("kotlin-stdlib-common"))
        assertTrue(sortedArtifacts.windowed(size = 2).all { (left, right) -> left < right })
    }

    @Test
    fun randomUtilitiesProduceReproducibleBoundedValuesAndBytes() {
        val first = Random(8675309)
        val second = Random(8675309)

        val firstValues = List(20) { first.nextInt(from = 10, until = 25) }
        val secondValues = List(20) { second.nextInt(from = 10, until = 25) }

        assertEquals(firstValues, secondValues)
        assertTrue(firstValues.all { it in 10 until 25 })

        val bytes = ByteArray(8) { -1 }
        val controlBytes = ByteArray(8) { -1 }
        Random(123456).nextBytes(bytes, fromIndex = 2, toIndex = 6)
        Random(123456).nextBytes(controlBytes, fromIndex = 2, toIndex = 6)

        assertArrayEquals(controlBytes, bytes)
        assertEquals(-1, bytes[0].toInt())
        assertEquals(-1, bytes[1].toInt())
        assertEquals(-1, bytes[6].toInt())
        assertEquals(-1, bytes[7].toInt())
    }

    @Test
    fun objectSingletonsAndEnumUtilitiesExposeStableCommonSemantics() {
        val first = KotlinCommonFixture
        val second = KotlinCommonFixture
        assertSame(first, second)
        assertEquals("common:3", first.describe(listOf("collections", "text", "sequences")))

        val severities = TestSeverity.values().map { it.name.lowercase() to it.ordinal }.toMap()
        assertEquals(mapOf("low" to 0, "medium" to 1, "high" to 2), severities)
        assertEquals(TestSeverity.HIGH, enumValueOf<TestSeverity>("HIGH"))
    }

    @Test
    fun arrayDequeSupportsDoubleEndedMutationAndIterationOrder() {
        val processingQueue = ArrayDeque<String>()

        processingQueue.addLast("compile")
        processingQueue.addLast("test")
        processingQueue.addFirst("resolve")

        assertEquals(listOf("resolve", "compile", "test"), processingQueue.toList())
        assertEquals("resolve", processingQueue.removeFirst())

        processingQueue.addLast("package")
        processingQueue.addFirst("validate")

        assertEquals("package", processingQueue.removeLast())
        assertEquals(listOf("validate", "compile", "test"), processingQueue.toList())
        assertEquals("validate", processingQueue.first())
        assertEquals("test", processingQueue.last())
    }

    private object KotlinCommonFixture {
        fun describe(features: List<String>): String = "common:${features.distinct().size}"
    }

    private enum class TestSeverity {
        LOW,
        MEDIUM,
        HIGH
    }
}
