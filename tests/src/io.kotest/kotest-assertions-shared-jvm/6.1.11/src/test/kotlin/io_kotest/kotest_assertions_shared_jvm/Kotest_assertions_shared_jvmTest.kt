/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kotest.kotest_assertions_shared_jvm

import io.kotest.assertions.Actual
import io.kotest.assertions.AssertionErrorBuilder
import io.kotest.assertions.BasicErrorCollector
import io.kotest.assertions.ErrorCollectionMode
import io.kotest.assertions.Expected
import io.kotest.assertions.KotestAssertionFailedError
import io.kotest.assertions.MultiAssertionError
import io.kotest.assertions.MultiAssertionErrorBuilder
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.assertionCounter
import io.kotest.assertions.collectErrors
import io.kotest.assertions.collectOrThrow
import io.kotest.assertions.createLazyAssertionError
import io.kotest.assertions.errorCollector
import io.kotest.assertions.print.Printed
import io.kotest.assertions.pushErrors
import io.kotest.assertions.throwCollectedErrors
import io.kotest.matchers.DiffableMatcherResult
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.MatcherResultBuilder
import io.kotest.matchers.ThrowableMatcherResult
import io.kotest.matchers.and
import io.kotest.matchers.or
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

public class Kotest_assertions_shared_jvmTest {

    @BeforeEach
    fun resetSharedState() {
        assertionCounter.reset()
        errorCollector.clear()
        errorCollector.depth = 0
        errorCollector.subject = null
        errorCollector.setCollectionMode(ErrorCollectionMode.Hard)
    }

    @AfterEach
    fun clearSharedState() {
        errorCollector.clear()
        errorCollector.depth = 0
        errorCollector.subject = null
        errorCollector.setCollectionMode(ErrorCollectionMode.Hard)
        assertionCounter.reset()
    }

    @Test
    fun assertionCounterTracksIncrementsAndResetOperations() {
        assertEquals(0, assertionCounter.get())

        assertionCounter.inc()
        assertionCounter.inc(3)

        assertEquals(4, assertionCounter.get())
        assertEquals(4, assertionCounter.getAndReset())
        assertEquals(0, assertionCounter.get())
    }

    @Test
    fun basicErrorCollectorMaintainsModeErrorsSubjectDepthAndClues() {
        val collector = BasicErrorCollector()
        val firstError = IllegalArgumentException("first")
        val secondError = IllegalStateException("second")
        val subject = Printed("subject-value")

        collector.subject = subject
        collector.depth = 2
        collector.setCollectionMode(ErrorCollectionMode.Soft)
        collector.pushError(firstError)
        collector.pushError(secondError)
        collector.pushClue { "outer clue" }
        collector.pushClue { "inner clue" }

        assertSame(subject, collector.subject)
        assertEquals(2, collector.depth)
        assertEquals(ErrorCollectionMode.Soft, collector.getCollectionMode())
        assertEquals(listOf(firstError, secondError), collector.errors())
        assertEquals(listOf("outer clue", "inner clue"), collector.clueContext().map { it() })

        collector.popClue()
        assertEquals(listOf("outer clue"), collector.clueContext().map { it() })
        collector.clear()
        assertTrue(collector.errors().isEmpty())
    }

    @Test
    fun collectOrThrowHonorsHardSoftAndInspectorHardModes() {
        val collector = BasicErrorCollector()
        val hardError = AssertionError("hard failure")

        val thrownHard = assertThrows(AssertionError::class.java) {
            collector.collectOrThrow(hardError)
        }
        assertEquals("hard failure", thrownHard.message)

        collector.setCollectionMode(ErrorCollectionMode.Soft)
        val softError = AssertionError("soft failure")
        collector.collectOrThrow(softError)
        assertEquals(listOf("soft failure"), collector.errors().map { it.message })

        collector.setCollectionMode(ErrorCollectionMode.InspectorHard)
        val inspectorError = createLazyAssertionError { "inspector failure" }
        val thrownInspector = assertThrows(AssertionError::class.java) {
            collector.collectOrThrow(inspectorError)
        }
        assertSame(inspectorError, thrownInspector)
    }

    @Test
    fun collectedErrorsAreConvertedToSingleOrMultiAssertionErrorsAndCleared() {
        val singleCollector = BasicErrorCollector()
        val singleError = AssertionError("single failure")
        singleCollector.pushError(singleError)

        assertSame(singleError, singleCollector.collectErrors())
        assertTrue(singleCollector.errors().isEmpty())

        val multiCollector = BasicErrorCollector()
        multiCollector.subject = Printed("numbers")
        multiCollector.pushErrors(listOf(AssertionError("first"), AssertionError("second")))

        val aggregated = multiCollector.collectErrors()

        assertTrue(aggregated is MultiAssertionError)
        assertTrue(aggregated!!.message!!.contains("The following 2 assertions for numbers failed:"))
        assertTrue(aggregated.message!!.contains("1) first"))
        assertTrue(aggregated.message!!.contains("2) second"))
        assertTrue(multiCollector.errors().isEmpty())
    }

    @Test
    fun throwCollectedErrorsDoesNothingWhenEmptyAndThrowsAggregateWhenErrorsExist() {
        val collector = BasicErrorCollector()

        collector.throwCollectedErrors()
        collector.pushErrors(listOf(AssertionError("alpha"), AssertionError("beta")))

        val thrown = assertThrows(MultiAssertionError::class.java) {
            collector.throwCollectedErrors()
        }
        assertEquals(2, thrown.errors.size)
        assertTrue(thrown.message!!.contains("alpha"))
        assertTrue(thrown.message!!.contains("beta"))
        assertTrue(collector.errors().isEmpty())
    }

    @Test
    fun assertSoftlyAggregatesFailuresAndRestoresHardMode() {
        val thrown = assertThrows(MultiAssertionError::class.java) {
            assertSoftly {
                AssertionErrorBuilder.failSoftly("first soft failure")
                AssertionErrorBuilder.failSoftly("second soft failure")
                "completed"
            }
        }

        assertEquals(ErrorCollectionMode.Hard, errorCollector.getCollectionMode())
        assertTrue(errorCollector.errors().isEmpty())
        assertEquals(2, thrown.errors.size)
        assertTrue(thrown.message!!.contains("first soft failure"))
        assertTrue(thrown.message!!.contains("second soft failure"))
    }

    @Test
    fun nestedAssertSoftlyPreservesOuterFailuresAndReturnValues() {
        val thrown = assertThrows(MultiAssertionError::class.java) {
            assertSoftly {
                AssertionErrorBuilder.failSoftly("outer failure")
                val nestedResult = assertSoftly {
                    AssertionErrorBuilder.failSoftly("inner failure")
                    42
                }
                assertEquals(42, nestedResult)
            }
        }

        assertTrue(thrown.message!!.contains("outer failure"))
        assertTrue(thrown.message!!.contains("inner failure"))
        assertEquals(ErrorCollectionMode.Hard, errorCollector.getCollectionMode())
    }

    @Test
    fun assertionErrorBuilderIncludesMessageCauseValuesAndClues() {
        val cause = IllegalStateException("root cause")
        errorCollector.pushClue { "because setup matters" }
        try {
            val error = AssertionErrorBuilder.create()
                .withMessage("values differed")
                .withCause(cause)
                .withValues(Expected(Printed("expected")), Actual(Printed("actual")))
                .build()

            assertSame(cause, error.cause)
            assertTrue(error.message!!.startsWith("because setup matters\nvalues differed"))
            assertTrue(error.message!!.contains("expected:<expected> but was:<actual>"))
        } finally {
            errorCollector.popClue()
        }
    }

    @Test
    fun assertionErrorBuilderFailAndFailSoftlyUseHardAndSoftCollectionModes() {
        val hard = assertThrows(AssertionError::class.java) {
            AssertionErrorBuilder.fail("hard builder failure")
        }
        assertTrue(hard.message!!.contains("hard builder failure"))

        errorCollector.setCollectionMode(ErrorCollectionMode.Soft)
        AssertionErrorBuilder.failSoftly("soft builder failure")

        assertEquals(1, errorCollector.errors().size)
        assertTrue(errorCollector.errors().single().message!!.contains("soft builder failure"))
    }

    @Test
    fun lazyAssertionErrorCachesComputedMessage() {
        val calls = AtomicInteger(0)
        val error = createLazyAssertionError {
            calls.incrementAndGet()
            "computed message"
        }

        assertEquals("computed message", error.message)
        assertEquals("computed message", error.message)
        assertEquals(1, calls.get())
    }

    @Test
    fun multiAssertionErrorBuilderRequiresMultipleErrorsAndFormatsMessages() {
        val first = AssertionError("first")
        val second = AssertionError("second")

        val error = MultiAssertionErrorBuilder.create(listOf(first, second)).build()

        assertEquals(listOf(first, second), error.errors)
        assertTrue(error.message!!.contains("The following 2 assertions failed:"))
        assertTrue(error.message!!.contains("1) first"))
        assertTrue(error.message!!.contains("2) second"))
        assertThrows(IllegalArgumentException::class.java) {
            MultiAssertionError(listOf(first), "invalid")
        }
    }

    @Test
    fun kotestAssertionFailedErrorIsADataAssertionError() {
        val cause = IllegalArgumentException("bad input")
        val error = KotestAssertionFailedError("message", cause, "expected", "actual")
        val copied = error.copy(message = "copy", expected = "new expected")

        assertEquals("message", error.message)
        assertSame(cause, error.cause)
        assertEquals("expected", error.expected)
        assertEquals("actual", error.actual)
        assertEquals("copy", copied.message)
        assertEquals("new expected", copied.expected)
        assertEquals("actual", copied.actual)
        assertNotSame(error, copied)
    }

    @Test
    fun matcherResultFactoryAndBuilderCreateSimpleDiffableAndThrowableResults() {
        val simple = MatcherResult(false, { "failed" }, { "negated" })

        assertFalse(simple.passed())
        assertEquals("failed", simple.failureMessage())
        assertEquals("negated", simple.negatedFailureMessage())

        val diffable = MatcherResultBuilder.create(false)
            .withFailureMessage { "expected words to match" }
            .withNegatedFailureMessage { "expected words not to match" }
            .withValues(Printed("expected"), Printed("actual"))
            .build()

        assertTrue(diffable is DiffableMatcherResult)
        assertFalse(diffable.passed())
        assertEquals("expected words to match", diffable.failureMessage())
        assertEquals("expected words not to match", diffable.negatedFailureMessage())
        val diffableResult = diffable as DiffableMatcherResult
        assertEquals(Printed("expected"), diffableResult.expected())
        assertEquals(Printed("actual"), diffableResult.actual())

        val throwable = IllegalStateException("prebuilt matcher error")
        val throwableResult = MatcherResultBuilder.create(false).withError(throwable).build()

        assertTrue(throwableResult is ThrowableMatcherResult)
        assertSame(throwable, (throwableResult as ThrowableMatcherResult).error)
        assertEquals("", throwableResult.failureMessage())
        assertEquals("", throwableResult.negatedFailureMessage())
    }

    @Test
    fun matcherCombinatorsShortCircuitAndPreserveMessages() {
        val calls = mutableListOf<String>()
        val even = Matcher<Int> { value ->
            calls.add("even:$value")
            MatcherResult(value % 2 == 0, { "$value was not even" }, { "$value was even" })
        }
        val positive = Matcher<Int> { value ->
            calls.add("positive:$value")
            MatcherResult(value > 0, { "$value was not positive" }, { "$value was positive" })
        }

        val andFailure = (even and positive).test(3)
        assertFalse(andFailure.passed())
        assertEquals("3 was not even", andFailure.failureMessage())
        assertEquals(listOf("even:3"), calls)

        calls.clear()
        val andSuccess = (even and positive).test(4)
        assertTrue(andSuccess.passed())
        assertEquals(listOf("even:4", "positive:4"), calls)

        calls.clear()
        val orSuccess = (even or positive).test(2)
        assertTrue(orSuccess.passed())
        assertEquals(listOf("even:2"), calls)

        calls.clear()
        val orFailure = (even or positive).test(-3)
        assertFalse(orFailure.passed())
        assertEquals("-3 was not positive", orFailure.failureMessage())
        assertEquals(listOf("even:-3", "positive:-3"), calls)
    }

    @Test
    fun matcherInvertInvertIfContramapAndFailureFactoryTransformResults() {
        val longerThanThree = Matcher<String> { value ->
            MatcherResult(value.length > 3, { "$value was too short" }, { "$value was long enough" })
        }

        val inverted = longerThanThree.invert().test("kotlin")
        assertFalse(inverted.passed())
        assertEquals("kotlin was long enough", inverted.failureMessage())
        assertEquals("kotlin was too short", inverted.negatedFailureMessage())

        assertSame(longerThanThree, longerThanThree.invertIf(false))
        assertFalse(longerThanThree.invertIf(true).test("kotest").passed())

        val listSizeMatcher = longerThanThree.contramap<List<Int>> { values -> values.joinToString("") }
        assertTrue(listSizeMatcher.test(listOf(1, 2, 3, 4)).passed())
        assertFalse(listSizeMatcher.test(listOf(1, 2)).passed())

        val failure = Matcher.failure<Int>("always fails").test(10)
        assertFalse(failure.passed())
        assertEquals("always fails", failure.failureMessage())
        assertEquals("", failure.negatedFailureMessage())
    }

    @Test
    fun printedExpectedAndActualAreValueTypes() {
        val printed = Printed("value")
        val expected = Expected(printed)
        val actual = Actual(printed.copy(value = "other"))

        assertEquals("value", printed.value)
        assertNull(printed.type)
        assertEquals(printed, expected.value)
        assertEquals("other", actual.value.value)
        assertEquals(Expected(Printed("value")), expected)
        assertEquals(Actual(Printed("other")), actual)
    }
}
