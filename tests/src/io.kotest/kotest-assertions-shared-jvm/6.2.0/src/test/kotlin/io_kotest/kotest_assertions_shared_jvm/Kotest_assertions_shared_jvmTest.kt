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
import io.kotest.assertions.assertionCounterContextElement
import io.kotest.assertions.clueContextAsString
import io.kotest.assertions.collectErrors
import io.kotest.assertions.collectOrThrow
import io.kotest.assertions.createLazyAssertionError
import io.kotest.assertions.errorCollector
import io.kotest.assertions.errorCollectorContextElement
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentest4j.AssertionFailedError

public class Kotest_assertions_shared_jvmTest {

    @BeforeEach
    fun resetBeforeEach(): Unit {
        resetGlobalAssertionState()
    }

    @AfterEach
    fun resetAfterEach(): Unit {
        resetGlobalAssertionState()
    }

    @Test
    fun matcherResultInvokesLazyMessageFunctions(): Unit {
        var failureMessagesEvaluated: Int = 0
        var negatedMessagesEvaluated: Int = 0

        val result: MatcherResult = MatcherResult(
            passed = false,
            failureMessageFn = {
                failureMessagesEvaluated++
                "value should be accepted"
            },
            negatedFailureMessageFn = {
                negatedMessagesEvaluated++
                "value should be rejected"
            },
        )

        assertFalse(result.passed())
        assertEquals(0, failureMessagesEvaluated)
        assertEquals("value should be accepted", result.failureMessage())
        assertEquals("value should be rejected", result.negatedFailureMessage())
        assertEquals(1, failureMessagesEvaluated)
        assertEquals(1, negatedMessagesEvaluated)
    }

    @Test
    fun matcherCompositionSupportsContramapInversionAndConditionalInversion(): Unit {
        val longerThanThree: Matcher<String> = Matcher { value: String ->
            MatcherResult(
                value.length > 3,
                { "length of '$value' should be greater than 3" },
                { "length of '$value' should not be greater than 3" },
            )
        }

        val collectionSizeMatcher: Matcher<Collection<Int>> = longerThanThree.contramap { values: Collection<Int> ->
            "x".repeat(values.size)
        }

        val passingResult: MatcherResult = collectionSizeMatcher.test(listOf(1, 2, 3, 4))
        val failingResult: MatcherResult = collectionSizeMatcher.test(listOf(1, 2))
        val invertedResult: MatcherResult = collectionSizeMatcher.invert().test(listOf(1, 2))

        assertTrue(passingResult.passed())
        assertFalse(failingResult.passed())
        assertTrue(invertedResult.passed())
        assertEquals(failingResult.negatedFailureMessage(), invertedResult.failureMessage())
        assertSame(collectionSizeMatcher, collectionSizeMatcher.invertIf(false))
        assertFalse(collectionSizeMatcher.invertIf(true).test(listOf(1, 2, 3, 4)).passed())
    }

    @Test
    fun matcherAndOrOperatorsShortCircuitAndReturnFirstDecisiveResult(): Unit {
        var secondMatcherCalls: Int = 0
        val pass: Matcher<Int> = Matcher { value: Int ->
            MatcherResult(true, { "pass failed for $value" }, { "pass negated" })
        }
        val fail: Matcher<Int> = Matcher { value: Int ->
            MatcherResult(false, { "fail failed for $value" }, { "fail negated" })
        }
        val countingPass: Matcher<Int> = Matcher { value: Int ->
            secondMatcherCalls++
            MatcherResult(true, { "counting failed for $value" }, { "counting negated" })
        }

        val failedAndResult: MatcherResult = (fail and countingPass).test(7)
        assertFalse(failedAndResult.passed())
        assertEquals("fail failed for 7", failedAndResult.failureMessage())
        assertEquals(0, secondMatcherCalls)

        val passedAndResult: MatcherResult = (pass and countingPass).test(8)
        assertTrue(passedAndResult.passed())
        assertEquals(1, secondMatcherCalls)

        val passedOrResult: MatcherResult = (pass or countingPass).test(9)
        assertTrue(passedOrResult.passed())
        assertEquals(1, secondMatcherCalls)

        val recoveredOrResult: MatcherResult = (fail or countingPass).test(10)
        assertTrue(recoveredOrResult.passed())
        assertEquals(2, secondMatcherCalls)
    }

    @Test
    fun matcherFactoryCanCreateAlwaysFailingMatcher(): Unit {
        val matcher: Matcher<String> = Matcher.failure("always rejected")
        val result: MatcherResult = matcher.test("sample")

        assertFalse(result.passed())
        assertEquals("always rejected", result.failureMessage())
        assertEquals("", result.negatedFailureMessage())
    }

    @Test
    fun matcherResultBuilderCreatesSimpleDiffableAndThrowableResults(): Unit {
        val simple: MatcherResult = MatcherResultBuilder.create(false)
            .withFailureMessage { "custom failure" }
            .withNegatedFailureMessage { "custom negated failure" }
            .build()

        assertFalse(simple.passed())
        assertEquals("custom failure", simple.failureMessage())
        assertEquals("custom negated failure", simple.negatedFailureMessage())

        val diffable: MatcherResult = MatcherResultBuilder.create(false)
            .withFailureMessage { "numbers differ" }
            .withNegatedFailureMessage { "numbers should differ" }
            .withValues(
                expected = Printed("1", Int::class),
                actual = Printed("2", Int::class),
            )
            .build()

        val diffableResult: DiffableMatcherResult = assertInstanceOf(DiffableMatcherResult::class.java, diffable)
        assertFalse(diffableResult.passed())
        assertEquals("numbers differ", diffableResult.failureMessage())
        assertEquals(Printed("1", Int::class), diffableResult.expected())
        assertEquals(Printed("2", Int::class), diffableResult.actual())

        val attachedError: IllegalStateException = IllegalStateException("prebuilt error")
        val throwable: MatcherResult = MatcherResultBuilder.create(false)
            .withError(attachedError)
            .build()

        val throwableResult: ThrowableMatcherResult = assertInstanceOf(ThrowableMatcherResult::class.java, throwable)
        assertFalse(throwableResult.passed())
        assertSame(attachedError, throwableResult.error)
        assertEquals("", throwableResult.failureMessage())
    }

    @Test
    fun assertionErrorBuilderCreatesOpenTest4jErrorsWithMessagesValuesAndCauses(): Unit {
        val cause: IllegalArgumentException = IllegalArgumentException("bad input")
        val error: AssertionError = AssertionErrorBuilder.create()
            .withMessage("comparison failed: ")
            .withCause(cause)
            .withValues(
                expected = Expected(Printed("alpha", String::class)),
                actual = Actual(Printed("beta", String::class)),
            )
            .build()

        val failedError: AssertionFailedError = assertInstanceOf(AssertionFailedError::class.java, error)
        assertSame(cause, failedError.cause)
        assertTrue(failedError.message!!.contains("comparison failed: expected:<alpha> but was:<beta>"))
        assertTrue(failedError.isExpectedDefined)
        assertTrue(failedError.isActualDefined)
        assertEquals("alpha", failedError.expected.stringRepresentation)
        assertEquals("beta", failedError.actual.stringRepresentation)
    }

    @Test
    fun assertionErrorBuilderIncludesTypeNamesWhenComparedPrintedTypesDiffer(): Unit {
        val error: AssertionError = AssertionErrorBuilder.create()
            .withValues(
                expected = Expected(Printed("123", Int::class)),
                actual = Actual(Printed("123", String::class)),
            )
            .build()

        assertTrue(error.message!!.contains("expected:kotlin.Int<123> but was:kotlin.String<123>"))
    }

    @Test
    fun assertionErrorBuilderPrependsActiveClueContextAndFailThrowsBuiltError(): Unit {
        errorCollector.pushClue { "outer clue" }
        errorCollector.pushClue { "inner clue" }
        try {
            val error: AssertionError = assertThrows(AssertionError::class.java) {
                AssertionErrorBuilder.fail("the assertion failed")
            }

            assertTrue(error.message!!.startsWith("outer clue\ninner clue\nthe assertion failed"))
        } finally {
            errorCollector.popClue()
            errorCollector.popClue()
        }
    }

    @Test
    fun lazyAssertionErrorCachesSuppliedMessage(): Unit {
        var invocations: Int = 0
        val error: AssertionError = createLazyAssertionError {
            invocations++
            "lazy message"
        }

        assertEquals(1, invocations)
        assertEquals("lazy message", error.message)
        assertEquals("lazy message", error.message)
        assertEquals(1, invocations)
    }

    @Test
    fun basicErrorCollectorTracksModeSubjectCluesErrorsAndClear(): Unit {
        val collector: BasicErrorCollector = BasicErrorCollector()
        val first: AssertionError = AssertionError("first")
        val second: AssertionError = AssertionError("second")

        collector.setCollectionMode(ErrorCollectionMode.Soft)
        collector.subject = Printed("subject value")
        collector.depth = 2
        collector.pushClue { "first clue" }
        collector.pushClue { "second clue" }
        collector.pushError(first)
        collector.pushError(second)

        assertEquals(ErrorCollectionMode.Soft, collector.getCollectionMode())
        assertEquals(Printed("subject value"), collector.subject)
        assertEquals(2, collector.depth)
        assertEquals(listOf("first clue", "second clue"), collector.clueContext().map { clue -> clue() })
        assertEquals(listOf(first, second), collector.errors())

        collector.popClue()
        assertEquals(listOf("first clue"), collector.clueContext().map { clue -> clue() })
        collector.clear()
        assertTrue(collector.errors().isEmpty())
    }

    @Test
    fun collectorCollectOrThrowHonorsSoftHardAndInspectorHardModes(): Unit {
        val softCollector: BasicErrorCollector = BasicErrorCollector()
        val softError: AssertionError = AssertionError("soft failure")
        softCollector.setCollectionMode(ErrorCollectionMode.Soft)
        softCollector.collectOrThrow(softError)
        assertEquals(listOf(softError), softCollector.errors())

        val hardCollector: BasicErrorCollector = BasicErrorCollector()
        hardCollector.setCollectionMode(ErrorCollectionMode.Hard)
        val hardError: AssertionError = assertThrows(AssertionError::class.java) {
            hardCollector.collectOrThrow(AssertionError("hard failure"))
        }
        assertEquals("hard failure", hardError.message)
        assertTrue(hardCollector.errors().isEmpty())

        val inspectorCollector: BasicErrorCollector = BasicErrorCollector()
        inspectorCollector.setCollectionMode(ErrorCollectionMode.InspectorHard)
        val inspectorError: AssertionError = AssertionError("inspector failure")
        val thrownInspectorError: AssertionError = assertThrows(AssertionError::class.java) {
            inspectorCollector.collectOrThrow(inspectorError)
        }
        assertSame(inspectorError, thrownInspectorError)
    }

    @Test
    fun collectorCombinesMultipleErrorsAndClearsAfterCollection(): Unit {
        val collector: BasicErrorCollector = BasicErrorCollector()
        val first: AssertionError = AssertionError("first failure")
        val second: AssertionError = AssertionError("second failure")

        collector.pushErrors(listOf(first, second))
        val collected: AssertionError = collector.collectErrors()!!

        val multi: MultiAssertionError = assertInstanceOf(MultiAssertionError::class.java, collected)
        assertEquals(listOf(first, second), multi.errors)
        assertTrue(multi.message!!.contains("The following 2 assertions failed:"))
        assertTrue(multi.message!!.contains("1) first failure"))
        assertTrue(multi.message!!.contains("2) second failure"))
        assertTrue(collector.errors().isEmpty())
    }

    @Test
    fun collectorBulkCollectOrThrowAccumulatesInSoftModeAndThrowsInHardMode(): Unit {
        val first: AssertionError = AssertionError("first bulk failure")
        val second: AssertionError = AssertionError("second bulk failure")
        val errors: List<AssertionError> = listOf(first, second)

        val softCollector: BasicErrorCollector = BasicErrorCollector()
        softCollector.setCollectionMode(ErrorCollectionMode.Soft)
        softCollector.collectOrThrow(errors)
        assertEquals(errors, softCollector.errors())

        val hardCollector: BasicErrorCollector = BasicErrorCollector()
        hardCollector.setCollectionMode(ErrorCollectionMode.Hard)
        val thrown: MultiAssertionError = assertThrows(MultiAssertionError::class.java) {
            hardCollector.collectOrThrow(errors)
        }

        assertEquals(errors, thrown.errors)
        assertTrue(thrown.message!!.contains("first bulk failure"))
        assertTrue(thrown.message!!.contains("second bulk failure"))
        assertTrue(hardCollector.errors().isEmpty())
    }

    @Test
    fun collectorPrefixesSubjectInformationForSingleAssertionError(): Unit {
        val collector: BasicErrorCollector = BasicErrorCollector()
        collector.subject = Printed("customer record")
        collector.pushError(AssertionError("name should not be blank"))

        val collected: AssertionError = collector.collectErrors()!!

        assertTrue(collected.message!!.contains("The following assertion for customer record failed:"))
        assertTrue(collected.message!!.contains("name should not be blank"))
        assertTrue(collector.errors().isEmpty())
    }

    @Test
    fun throwCollectedErrorsThrowsAndClearsPendingErrors(): Unit {
        val collector: BasicErrorCollector = BasicErrorCollector()
        collector.pushError(AssertionError("first"))
        collector.pushError(AssertionError("second"))

        val error: MultiAssertionError = assertThrows(MultiAssertionError::class.java) {
            collector.throwCollectedErrors()
        }

        assertEquals(2, error.errors.size)
        assertTrue(collector.errors().isEmpty())
    }

    @Test
    fun multiAssertionErrorBuilderRequiresAndPreservesMultipleErrors(): Unit {
        val first: AssertionError = AssertionError("one")
        val second: AssertionError = AssertionError("two")

        val built: MultiAssertionError = MultiAssertionErrorBuilder.create(listOf(first, second)).build()

        assertEquals(listOf(first, second), built.errors)
        assertTrue(built.message!!.contains("The following 2 assertions failed:"))
        assertThrows(IllegalArgumentException::class.java) {
            MultiAssertionError(emptyList(), "invalid")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MultiAssertionError(listOf(first), "invalid")
        }
    }

    @Test
    fun assertSoftlyCollectsFailuresAndRestoresHardMode(): Unit {
        val error: MultiAssertionError = assertThrows(MultiAssertionError::class.java) {
            assertSoftly {
                AssertionErrorBuilder.failSoftly("first soft failure")
                AssertionErrorBuilder.failSoftly("second soft failure")
                "unreachable result is ignored"
            }
        }

        assertEquals(2, error.errors.size)
        assertTrue(error.message!!.contains("first soft failure"))
        assertTrue(error.message!!.contains("second soft failure"))
        assertEquals(ErrorCollectionMode.Hard, errorCollector.getCollectionMode())
        assertTrue(errorCollector.errors().isEmpty())
    }

    @Test
    fun nestedAssertSoftlyAddsInnerAggregateToOuterCollection(): Unit {
        val error: MultiAssertionError = assertThrows(MultiAssertionError::class.java) {
            assertSoftly {
                AssertionErrorBuilder.failSoftly("outer failure")
                assertSoftly {
                    AssertionErrorBuilder.failSoftly("inner first failure")
                    AssertionErrorBuilder.failSoftly("inner second failure")
                    "inner result"
                }
            }
        }

        assertEquals(2, error.errors.size)
        assertTrue(error.message!!.contains("outer failure"))
        assertTrue(error.message!!.contains("inner first failure"))
        assertTrue(error.message!!.contains("inner second failure"))
    }

    @Test
    fun assertSoftlyReturnsBlockResultWhenAllAssertionsPass(): Unit {
        val result: String = assertSoftly {
            "all assertions passed"
        }

        assertEquals("all assertions passed", result)
        assertEquals(ErrorCollectionMode.Hard, errorCollector.getCollectionMode())
        assertTrue(errorCollector.errors().isEmpty())
    }

    @Test
    fun clueContextAsStringFormatsActiveCluesInOrder(): Unit {
        assertEquals("", clueContextAsString())

        errorCollector.pushClue { "first clue" }
        errorCollector.pushClue { "second clue" }
        try {
            assertEquals("first clue\nsecond clue\n", clueContextAsString())
        } finally {
            errorCollector.popClue()
            errorCollector.popClue()
        }
    }

    @Test
    fun assertionCounterTracksIncrementsAndReset(): Unit {
        assertionCounter.reset()
        assertionCounter.inc()
        assertionCounter.inc(3)

        assertEquals(4, assertionCounter.get())
        assertEquals(4, assertionCounter.getAndReset())
        assertEquals(0, assertionCounter.get())
    }

    @Test
    fun coroutineContextElementAccessorsReturnUsableElements(): Unit {
        assertNotNull(errorCollectorContextElement.key)
        assertNotNull(assertionCounterContextElement.key)
    }

    @Test
    fun printedExpectedActualAndKotestAssertionFailedErrorExposeValueSemantics(): Unit {
        val printed: Printed = Printed("display", String::class)
        val samePrinted: Printed = printed.copy(value = "display", type = String::class)
        val expected: Expected = Expected(printed)
        val actual: Actual = Actual(Printed("actual"))
        val cause: IllegalStateException = IllegalStateException("cause")
        val kotestError: KotestAssertionFailedError = KotestAssertionFailedError("message", cause, "expected", "actual")

        assertEquals(printed, samePrinted)
        assertEquals("display", printed.value)
        assertEquals(String::class, printed.type)
        assertEquals(printed, expected.value)
        assertEquals("actual", actual.value.value)
        assertEquals("message", kotestError.message)
        assertSame(cause, kotestError.cause)
        assertEquals("expected", kotestError.expected)
        assertEquals("actual", kotestError.actual)
        assertEquals(
            kotestError,
            kotestError.copy(message = "message", cause = cause, expected = "expected", actual = "actual"),
        )
    }

    private fun resetGlobalAssertionState(): Unit {
        errorCollector.clear()
        errorCollector.subject = null
        errorCollector.depth = 0
        errorCollector.setCollectionMode(ErrorCollectionMode.Hard)
        assertionCounter.reset()
    }
}
