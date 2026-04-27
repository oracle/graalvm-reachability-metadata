/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_stdlib_common

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlin.properties.Delegates
import kotlin.random.Random

public class KotlinStdlibCommonTest {
    @Test
    fun collectionTransformationsPreserveOrderAndShapeData() {
        val transactions = listOf(
            Transaction("books", 12),
            Transaction("games", 20),
            Transaction("books", 8),
            Transaction("music", 15),
            Transaction("games", 7),
        )

        val totalsByCategory = transactions
            .groupBy { it.category }
            .mapValues { (_, entries) -> entries.fold(0) { total, transaction -> total + transaction.amount } }

        assertThat(totalsByCategory).containsEntry("books", 20)
        assertThat(totalsByCategory).containsEntry("games", 27)
        assertThat(totalsByCategory).containsEntry("music", 15)

        val expensiveCategories = transactions
            .filter { it.amount >= 10 }
            .map { it.category }
            .distinct()

        assertThat(expensiveCategories).containsExactly("books", "games", "music")

        val (small, large) = transactions.partition { it.amount < 10 }
        assertThat(small.map { it.category }).containsExactly("books", "games")
        assertThat(large.map { it.amount }).containsExactly(12, 20, 15)
    }

    @Test
    fun mapsPairsAndTriplesModelStructuredValues() {
        val abbreviations = mapOf("kotlin" to "kt", "metadata" to "md").withDefault { key -> key.take(2) }
        assertThat(abbreviations.getValue("kotlin")).isEqualTo("kt")
        assertThat(abbreviations.getValue("native")).isEqualTo("na")

        val pairs = listOf("red" to 3, "green" to 5, "blue" to 4)
        val indexedByName = pairs.associate { (name, length) -> name to "${name.first()}:$length" }
        assertThat(indexedByName).containsEntry("green", "g:5")

        val triple = Triple("stdlib", 1, true)
        val (name, majorVersion, stable) = triple
        assertThat(name).isEqualTo("stdlib")
        assertThat(majorVersion).isEqualTo(1)
        assertThat(stable).isTrue()
    }

    @Test
    fun rangesProgressionsAndComparatorsHandleBoundaries() {
        assertThat((3..7).toList()).containsExactly(3, 4, 5, 6, 7)
        assertThat((3 until 7).toList()).containsExactly(3, 4, 5, 6)
        assertThat((12 downTo 2 step 5).toList()).containsExactly(12, 7, 2)
        assertThat(('d' downTo 'a').joinToString(separator = "")).isEqualTo("dcba")
        assertThat(5.coerceIn(1, 4)).isEqualTo(4)
        assertThat((-2).coerceAtLeast(0)).isZero()

        val sorted = listOf(
            Score("Ada", 98),
            Score("Grace", 98),
            Score("Linus", 91),
            Score("Ken", 99),
        ).sortedWith(compareByDescending<Score> { it.points }.thenBy { it.name })

        assertThat(sorted.map { it.name }).containsExactly("Ken", "Ada", "Grace", "Linus")
    }

    @Test
    fun sequencesEvaluateLazilyAndComposeMultipleOperations() {
        val visited = mutableListOf<Int>()
        val values = generateSequence(1) { it + 1 }
            .map {
                visited += it
                it * it
            }
            .dropWhile { it < 10 }
            .take(4)
            .toList()

        assertThat(values).containsExactly(16, 25, 36, 49)
        assertThat(visited).containsExactly(1, 2, 3, 4, 5, 6, 7)

        val emitted = sequence {
            yield("header")
            yieldAll(listOf("body", "footer"))
        }.mapIndexed { index, value -> "$index:$value" }

        assertThat(emitted.toList()).containsExactly("0:header", "1:body", "2:footer")
    }

    @Test
    fun textUtilitiesAndRegexParseContentWithoutPlatformApis() {
        val block = """
            name=kotlin
            version=1.3.72
            target=common
        """.trimIndent()

        val entries = Regex("""([a-z]+)=([^\n]+)""").findAll(block)
            .map { match -> match.groupValues[1] to match.groupValues[2] }
            .toMap()

        assertThat(entries).containsEntry("name", "kotlin")
        assertThat(entries).containsEntry("version", "1.3.72")
        assertThat(entries).containsEntry("target", "common")

        val normalized = listOf(" Kotlin ", "", " Standard", "Library ")
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(separator = "-")

        assertThat(normalized).isEqualTo("Kotlin-Standard-Library")
        assertThat("72".toIntOrNull()).isEqualTo(72)
        assertThat("not-a-number".toIntOrNull()).isNull()
    }

    @Test
    fun arraysAndPrimitiveCollectionsUseCommonOperations() {
        val numbers = intArrayOf(5, 1, 3, 3, 2)
        assertThat(numbers.sorted()).containsExactly(1, 2, 3, 3, 5)
        assertThat(numbers.distinct()).containsExactly(5, 1, 3, 2)
        assertThat(numbers.fold(0) { total, value -> total + value }).isEqualTo(14)

        val words = arrayOf("alpha", "beta", "gamma")
        assertThat(words.map { it.length }).containsExactly(5, 4, 5)
        assertThat(words.any { it.startsWith("g") }).isTrue()
        assertThat(words.all { it.isNotBlank() }).isTrue()
    }

    @Test
    fun lazyAndScopeFunctionsControlInitializationAndMutation() {
        var initializationCount = 0
        val expensiveValue = lazy(LazyThreadSafetyMode.NONE) {
            initializationCount += 1
            "computed-${initializationCount}"
        }

        assertThat(expensiveValue.isInitialized()).isFalse()
        assertThat(expensiveValue.value).isEqualTo("computed-1")
        assertThat(expensiveValue.value).isEqualTo("computed-1")
        assertThat(initializationCount).isEqualTo(1)

        val configured = mutableMapOf<String, String>().apply {
            this["language"] = "kotlin"
            this["module"] = "stdlib-common"
        }.also { map ->
            map["entries"] = map.size.toString()
        }.let { map ->
            "${map.getValue("language")}:${map.getValue("module")}:${map.getValue("entries")}"
        }

        assertThat(configured).isEqualTo("kotlin:stdlib-common:2")
    }

    @Test
    fun randomInstancesAreDeterministicWhenSeeded() {
        val first = Random(137)
        val second = Random(137)

        val firstValues = List(6) { first.nextInt(from = 10, until = 100) }
        val secondValues = List(6) { second.nextInt(from = 10, until = 100) }

        assertThat(firstValues).isEqualTo(secondValues)
        assertThat(firstValues).allSatisfy { value -> assertThat(value).isBetween(10, 99) }
    }

    @Test
    fun delegatedPropertiesTrackInitializationObservationAndVetoes() {
        val settings = DelegatedSettings()

        assertThatThrownBy { settings.requiredLimit }
            .isInstanceOf(IllegalStateException::class.java)

        settings.requiredLimit = 5
        assertThat(settings.requiredLimit).isEqualTo(5)

        settings.mode = "active"
        settings.mode = "paused"
        assertThat(settings.mode).isEqualTo("paused")
        assertThat(settings.modeChanges).containsExactly("idle->active", "active->paused")

        settings.priority = 3
        settings.priority = -1
        settings.priority = 8
        assertThat(settings.priority).isEqualTo(8)
        assertThat(settings.rejectedPriorities).containsExactly(-1)
    }

    @Test
    fun preconditionsThrowTheDocumentedExceptionTypes() {
        assertThatThrownBy { require(false) { "invalid argument" } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("invalid argument")

        assertThatThrownBy { check(false) { "invalid state" } }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("invalid state")

        assertThat("metadata".takeIf { it.startsWith("meta") }).isEqualTo("metadata")
        assertThat("metadata".takeUnless { it.length > 20 }).isEqualTo("metadata")
    }

    @Test
    fun resultsComposeSuccessFailureAndRecoveryPaths() {
        val quotas = mapOf("images" to 4, "documents" to 8)

        val scaled = runCatching { readQuota(quotas, "documents") }
            .map { quota -> quota * 3 }
            .fold(
                onSuccess = { quota -> "allowed:$quota" },
                onFailure = { error -> "missing:${error.message}" },
            )
        assertThat(scaled).isEqualTo("allowed:24")

        val fallback = runCatching { readQuota(quotas, "audio") }
            .recover { error ->
                assertThat(error).isInstanceOf(NoSuchElementException::class.java)
                1
            }
        assertThat(fallback.isSuccess).isTrue()
        assertThat(fallback.getOrThrow()).isEqualTo(1)

        val failedComputation = runCatching { readQuota(quotas, "images") }
            .mapCatching { quota -> quota / (quota - 4) }
        assertThat(failedComputation.isFailure).isTrue()
        assertThat(failedComputation.exceptionOrNull()).isInstanceOf(ArithmeticException::class.java)
    }

    @Test
    fun coroutinePrimitivesResumeContinuationsAndCombineContexts() {
        var completed: String? = null
        var failed: Throwable? = null
        val coroutine = suspend { suspendEcho("metadata") }

        coroutine.startCoroutine(object : Continuation<String> {
            override val context: CoroutineContext = EmptyCoroutineContext

            override fun resumeWith(result: Result<String>) {
                result.onSuccess { completed = it }
                result.onFailure { failed = it }
            }
        })

        assertThat(failed).isNull()
        assertThat(completed).isEqualTo("echo:metadata")

        val context = NamedElement("left") + TaggedElement(42)
        assertThat(context[NamedElement]).isEqualTo(NamedElement("left"))
        assertThat(context[TaggedElement]?.tag).isEqualTo(42)
        assertThat(context.minusKey(NamedElement)[NamedElement]).isNull()
        assertThat(context.minusKey(NamedElement)[TaggedElement]?.tag).isEqualTo(42)
    }

    private suspend fun suspendEcho(value: String): String = suspendCoroutine { continuation ->
        continuation.resume("echo:$value")
    }

    private fun readQuota(quotas: Map<String, Int>, name: String): Int =
        quotas[name] ?: throw NoSuchElementException("No quota named $name")

    private data class Transaction(val category: String, val amount: Int)

    private data class Score(val name: String, val points: Int)

    private class DelegatedSettings {
        val modeChanges = mutableListOf<String>()
        val rejectedPriorities = mutableListOf<Int>()

        var requiredLimit: Int by Delegates.notNull()
        var mode: String by Delegates.observable("idle") { _, oldValue, newValue ->
            modeChanges += "$oldValue->$newValue"
        }
        var priority: Int by Delegates.vetoable(0) { _, _, newValue ->
            (newValue >= 0).also { accepted ->
                if (!accepted) {
                    rejectedPriorities += newValue
                }
            }
        }
    }

    private data class NamedElement(val label: String) : AbstractCoroutineContextElement(NamedElement) {
        companion object Key : CoroutineContext.Key<NamedElement>
    }

    private data class TaggedElement(val tag: Int) : AbstractCoroutineContextElement(TaggedElement) {
        companion object Key : CoroutineContext.Key<TaggedElement>
    }
}
