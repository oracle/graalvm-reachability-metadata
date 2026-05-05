/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_core_jvm

import arrow.core.Either
import arrow.core.Ior
import arrow.core.None
import arrow.core.NonEmptyList
import arrow.core.NonEmptySet
import arrow.core.Option
import arrow.core.Some
import arrow.core.align
import arrow.core.bothIor
import arrow.core.combine
import arrow.core.elementAtOrNone
import arrow.core.filterIsInstance
import arrow.core.filterOption
import arrow.core.firstOrNone
import arrow.core.flatMap
import arrow.core.flatMapValues
import arrow.core.flatten
import arrow.core.flattenOrAccumulate
import arrow.core.getOrElse
import arrow.core.getOrNone
import arrow.core.handleErrorWith
import arrow.core.mapOrAccumulate
import arrow.core.mapValuesNotNull
import arrow.core.nonEmptyListOf
import arrow.core.nonEmptySetOf
import arrow.core.none
import arrow.core.padZip
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import arrow.core.raise.option
import arrow.core.recover
import arrow.core.rightIor
import arrow.core.toEitherNel
import arrow.core.toNonEmptyListOrNone
import arrow.core.toNonEmptySetOrNone
import arrow.core.toOption
import arrow.core.unalign
import arrow.core.unzip
import arrow.core.zip
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class ArrowCoreJvmTest {
    @Test
    fun optionPipelinesPreservePresenceAndAbsence() {
        val present: Option<String> = Option.fromNullable("arrow")
            .filter { value: String -> value.length > 3 }
            .flatMap { value: String -> Some(value.uppercase()) }
            .map { value: String -> "$value-core" }

        val missing: Option<String> = Option.fromNullable<String>(null)
            .flatMap { value: String -> Some(value.uppercase()) }

        assertThat(present).isEqualTo(Some("ARROW-core"))
        assertThat(present.getOrElse { "fallback" }).isEqualTo("ARROW-core")
        assertThat(missing).isEqualTo(None)
        assertThat(missing.getOrElse { "fallback" }).isEqualTo("fallback")
        val nullText: String? = null

        assertThat("value".toOption()).isEqualTo(Some("value"))
        assertThat(nullText.toOption()).isEqualTo(None)
        assertThat(Some(Option.fromNullable(7)).flatten()).isEqualTo(Some(7))
        assertThat(Some("text").filterIsInstance<String>()).isEqualTo(Some("text"))
        assertThat(Some(12).toEither { "empty" }).isEqualTo(Either.Right(12))
        assertThat(none<Int>().toEither { "empty" }).isEqualTo(Either.Left("empty"))
    }

    @Test
    fun optionBuilderBindsNestedOptionsAndShortCircuitsOnNone() {
        val result: Option<Int> = option {
            val values: List<Int> = listOf(Some(1), Some(2), Some(3)).bindAll()
            ensure(values.size == 3)
            values.sum()
        }

        val shortCircuited: Option<Int> = option {
            val values: List<Int> = listOf(Some(1), none(), Some(3)).bindAll()
            values.sum()
        }

        val recovered: Option<Int> = shortCircuited.recover { Some(42).bind() }

        assertThat(result).isEqualTo(Some(6))
        assertThat(shortCircuited).isEqualTo(None)
        assertThat(recovered).isEqualTo(Some(42))
    }

    @Test
    fun eitherComposesExceptionCaptureMappingAndRecovery() {
        val parsed: Either<String, Int> = Either.catch { "21".toInt() }
            .mapLeft { throwable: Throwable -> "not a number: ${throwable.message}" }
            .flatMap { value: Int -> Either.Right(value * 2) }

        val failed: Either<String, Int> = Either.catch { "NaN".toInt() }
            .mapLeft { throwable: Throwable ->
                if (throwable is NumberFormatException) "number-format" else "unknown"
            }

        val recoveredFailure: Either<Nothing, Int> = failed.recover { name: String -> name.length }

        assertThat(parsed).isEqualTo(Either.Right(42))
        assertThat(parsed.getOrElse { -1 }).isEqualTo(42)
        assertThat(parsed.swap()).isEqualTo(Either.Left(42))
        assertThat(failed.isLeft { name: String -> name == "number-format" }).isTrue()
        assertThat(recoveredFailure).isEqualTo(Either.Right("number-format".length))
        assertThat(Either.Left("left").toIor()).isEqualTo(Ior.Left("left"))
        assertThat(Either.Right(5).toEitherNel()).isEqualTo(Either.Right(5))
    }

    @Test
    fun eitherZipAndFlattenAccumulateAllValidationErrors() {
        val invalidName: Either<String, String> = Either.Left("name is blank")
        val invalidAge: Either<String, Int> = Either.Left("age is negative")
        val validName: Either<String, String> = Either.Right("Ada")
        val validAge: Either<String, Int> = Either.Right(37)

        val combinedErrors: Either<String, String> = Either.zipOrAccumulate(
            { first: String, second: String -> "$first; $second" },
            invalidName,
            invalidAge,
        ) { name: String, age: Int -> "$name:$age" }

        val profile: Either<String, String> = Either.zipOrAccumulate(
            { first: String, second: String -> "$first; $second" },
            validName,
            validAge,
        ) { name: String, age: Int -> "$name:$age" }

        val flattened: Either<NonEmptyList<String>, List<Int>> = listOf(
            Either.Right(1),
            Either.Left("first failure"),
            Either.Right(3),
            Either.Left("second failure"),
        ).flattenOrAccumulate()

        assertThat(combinedErrors).isEqualTo(Either.Left("name is blank; age is negative"))
        assertThat(profile).isEqualTo(Either.Right("Ada:37"))
        assertThat(flattened).isEqualTo(Either.Left(nonEmptyListOf("first failure", "second failure")))
    }

    @Test
    fun raiseDslTurnsDomainChecksIntoTypedEitherFailures() {
        assertThat(parsePort("8080")).isEqualTo(Either.Right(8080))
        assertThat(parsePort(null)).isEqualTo(Either.Left("port is missing"))
        assertThat(parsePort("not-a-number")).isEqualTo(Either.Left("port is not numeric"))
        assertThat(parsePort("70000")).isEqualTo(Either.Left("port is out of range"))
    }

    @Test
    fun nonEmptyListKeepsListOperationsNonEmpty() {
        val words = nonEmptyListOf("arrow", "core", "jvm")
        val lengths = words.map { word: String -> word.length }
        val suffixHeads = words.coflatMap { suffix: NonEmptyList<String> -> suffix.head }
        val zipped = words.zip(nonEmptyListOf("A", "C", "J")) { word: String, abbreviation: String ->
            "$abbreviation:${word.length}"
        }
        val padded = words.padZip(nonEmptyListOf(1))
        val fromIterable = listOf("one", "two").toNonEmptyListOrNone()
        val emptyFromIterable = emptyList<String>().toNonEmptyListOrNone()

        assertThat(words.head).isEqualTo("arrow")
        assertThat(words.tail).containsExactly("core", "jvm")
        assertThat(lengths.toList()).containsExactly(5, 4, 3)
        assertThat((words + "native").toList()).containsExactly("arrow", "core", "jvm", "native")
        assertThat(suffixHeads.toList()).containsExactly("arrow", "core", "jvm")
        assertThat(zipped.toList()).containsExactly("A:5", "C:4", "J:3")
        assertThat(padded.toList()).containsExactly("arrow" to 1, "core" to null, "jvm" to null)
        assertThat(fromIterable).isEqualTo(Some(nonEmptyListOf("one", "two")))
        assertThat(emptyFromIterable).isEqualTo(None)
    }

    @Test
    fun nonEmptySetDeduplicatesValuesAndAccumulatesValidationFailures() {
        val rawTags: NonEmptySet<String> = nonEmptySetOf("Arrow", "arrow", "Core")
        val normalizedTags: Either<NonEmptyList<String>, NonEmptySet<String>> =
            rawTags.mapOrAccumulate { tag: String ->
                ensure(tag.isNotBlank()) { "blank tag" }
                tag.lowercase()
            }
        val invalidTags: Either<NonEmptyList<String>, NonEmptySet<String>> = nonEmptySetOf(
            "Arrow",
            "",
            " ",
            "Native",
        ).mapOrAccumulate { tag: String ->
            val trimmedTag: String = tag.trim()
            ensure(trimmedTag.isNotEmpty()) { "blank tag" }
            trimmedTag.lowercase()
        }
        val fromIterable: Option<NonEmptySet<String>> =
            listOf("left", "right", "left").toNonEmptySetOrNone()

        assertThat(rawTags.toSet()).containsExactlyInAnyOrder("Arrow", "arrow", "Core")
        assertThat((rawTags + "Native").toSet())
            .containsExactlyInAnyOrder("Arrow", "arrow", "Core", "Native")
        assertThat(normalizedTags.map { tags: NonEmptySet<String> -> tags.toSet() })
            .isEqualTo(Either.Right(setOf("arrow", "core")))
        assertThat(invalidTags).isEqualTo(Either.Left(nonEmptyListOf("blank tag", "blank tag")))
        assertThat(fromIterable.map { tags: NonEmptySet<String> -> tags.toSet() })
            .isEqualTo(Some(setOf("left", "right")))
    }

    @Test
    fun iorRepresentsLeftRightAndBothCasesDuringAlignment() {
        val aligned: List<Ior<String, Int>> = listOf("a", "b").align(listOf(1, 2, 3))
        val combinedBoth: Ior<String, Int> = Ior.Both("first warning", 10)
            .flatMap({ first: String, second: String -> "$first; $second" }) { value: Int ->
                Ior.Both("second warning", value + 5)
            }
        val initialLeft: Ior<String, Int> = Ior.Left("missing")
        val handledLeft: Ior<Int, Int> = initialLeft.handleErrorWith(
            { first: Int, second: Int -> first + second },
        ) { problem: String ->
            Ior.Both(problem.length, problem.length * 2)
        }
        val unaligned: Pair<List<String?>, List<Int?>> = aligned.unalign()

        assertThat(aligned).containsExactly(Ior.Both("a", 1), Ior.Both("b", 2), Ior.Right(3))
        assertThat(("warning" to 99).bothIor()).isEqualTo(Ior.Both("warning", 99))
        assertThat(42.rightIor()).isEqualTo(Ior.Right(42))
        assertThat(combinedBoth).isEqualTo(Ior.Both("first warning; second warning", 15))
        assertThat(handledLeft).isEqualTo(Ior.Both(7, 14))
        assertThat(unaligned.first).containsExactly("a", "b", null)
        assertThat(unaligned.second).containsExactly(1, 2, 3)
    }

    @Test
    fun mapExtensionsFilterTransformAndCombineEntriesByKey() {
        val optionalScores: Map<String, Option<Int>> = mapOf(
            "alice" to Some(10),
            "bob" to none(),
            "carol" to Some(30),
        )
        val presentScores: Map<String, Int> = optionalScores.filterOption()
        val typedValues: Map<String, Int> = mapOf<String, Any>(
            "count" to 3,
            "label" to "core",
            "retries" to 2,
        ).filterIsInstance<String, Int>()
        val labels: Map<String, String> = mapOf(
            "library" to "arrow",
            "missing" to null,
            "module" to "core",
        ).mapValuesNotNull { entry: Map.Entry<String, String?> ->
            entry.value?.uppercase()
        }
        val firstSquares: Map<String, Int> = mapOf(
            "odd" to listOf(1, 3),
            "even" to listOf(2),
            "empty" to emptyList(),
        ).flatMapValues { entry: Map.Entry<String, List<Int>> ->
            entry.value.firstOrNull()?.let { value: Int ->
                mapOf(entry.key to value * value)
            } ?: emptyMap()
        }
        val combined: Map<String, Int> = mapOf("hits" to 1, "misses" to 2)
            .combine(mapOf("hits" to 4, "timeouts" to 3)) { first: Int, second: Int ->
                first + second
            }

        assertThat(presentScores).isEqualTo(mapOf("alice" to 10, "carol" to 30))
        assertThat(presentScores.getOrNone("alice")).isEqualTo(Some(10))
        assertThat(presentScores.getOrNone("bob")).isEqualTo(None)
        assertThat(typedValues).isEqualTo(mapOf("count" to 3, "retries" to 2))
        assertThat(labels).isEqualTo(mapOf("library" to "ARROW", "module" to "CORE"))
        assertThat(firstSquares).isEqualTo(mapOf("odd" to 1, "even" to 4))
        assertThat(combined).isEqualTo(mapOf("hits" to 5, "misses" to 2, "timeouts" to 3))
    }

    @Test
    fun iterableAndSequenceExtensionsProvideTotalAlternatives() {
        val firstEven = listOf(1, 3, 4, 6).firstOrNone { value: Int -> value % 2 == 0 }
        val missingElement = listOf("a", "b").elementAtOrNone(5)
        val padded = listOf("left-1", "left-2").padZip(listOf(10)) { left: String?, right: Int? ->
            "${left ?: "none"}:${right ?: -1}"
        }
        val zipped = listOf(1, 2).zip(listOf(10, 20), listOf(100, 200)) { a: Int, b: Int, c: Int ->
            a + b + c
        }
        val sequenceAligned = sequenceOf("x").align(sequenceOf(1, 2)).toList()
        val unzipped = sequenceOf("a" to 1, "b" to 2).unzip()

        assertThat(firstEven).isEqualTo(Some(4))
        assertThat(missingElement).isEqualTo(None)
        assertThat(padded).containsExactly("left-1:10", "left-2:-1")
        assertThat(zipped).containsExactly(111, 222)
        assertThat(sequenceAligned).containsExactly(Ior.Both("x", 1), Ior.Right(2))
        assertThat(unzipped.first.toList()).containsExactly("a", "b")
        assertThat(unzipped.second.toList()).containsExactly(1, 2)
    }

    private fun parsePort(rawPort: String?): Either<String, Int> = either {
        val text: String = ensureNotNull(rawPort) { "port is missing" }
        val port: Int = Either.catch { text.toInt() }
            .mapLeft { "port is not numeric" }
            .bind()

        ensure(port in 1..65535) { "port is out of range" }
        port
    }
}
