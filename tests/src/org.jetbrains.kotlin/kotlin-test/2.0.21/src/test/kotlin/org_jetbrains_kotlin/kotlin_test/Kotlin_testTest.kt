/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_test

import org.junit.jupiter.api.Test
import kotlin.test.Asserter
import kotlin.test.DefaultAsserter
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertIsNot
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.expect
import kotlin.test.fail

public class Kotlin_testTest {
    @Test
    fun equalityIdentityAndBooleanAssertionsSupportEagerAndLazyInputs() {
        val shared = mutableListOf("alpha", "beta")
        val sameReference = shared
        val equalButDistinct = mutableListOf("alpha", "beta")

        assertTrue(shared.isNotEmpty(), "non-empty collections should be accepted")
        assertTrue("lazy predicate should be evaluated") { shared.size == 2 }
        assertFalse(shared.contains("gamma"), "missing element should be false")
        assertFalse("lazy false predicate should be evaluated") { shared.isEmpty() }

        assertEquals(shared, equalButDistinct, "structurally equal lists should compare equal")
        assertNotEquals(shared, listOf("alpha"), "different lists should not compare equal")
        assertSame(shared, sameReference, "same object reference should be recognized")
        assertNotSame(shared, equalButDistinct, "distinct instances should not be identical")
    }

    @Test
    fun numericAssertionsSupportExactAndDeltaComparisons() {
        assertEquals(3.14159, 3.1416, 0.00002, "double values should compare within delta")
        assertEquals(2.5f, 2.45f, 0.051f, "float values should compare within delta")
        assertNotEquals(10.0, 10.5, 0.1, "double values outside delta should differ")
        assertNotEquals(4.0f, 4.5f, 0.2f, "float values outside delta should differ")
    }

    @Test
    fun nullabilityAndTypeAssertionsRefineValues() {
        val nullableName: String? = "kotlin"
        val confirmedName = assertNotNull(nullableName, "value should be present")
        assertEquals(6, confirmedName.length)

        assertNotNull(nullableName, "lambda overload should receive a non-null value") { name ->
            assertTrue(name.startsWith("kot"))
        }
        assertNull(null, "null assertion should accept a null reference")

        val numberAsAny: Any = 42
        val typedNumber = assertIs<Int>(numberAsAny, "assertIs should return the narrowed value")
        assertEquals(43, typedNumber + 1)
        assertIsNot<String>(numberAsAny, "assertIsNot should accept a different runtime type")
    }

    @Test
    fun containsAssertionsCoverCollectionsSequencesArraysRangesMapsAndText() {
        assertContains(listOf("red", "green", "blue"), "green")
        assertContains(sequenceOf(1, 1, 2, 3, 5), 5)
        assertContains(arrayOf("north", "south"), "south")
        assertContains(intArrayOf(8, 13, 21), 13)
        assertContains(charArrayOf('a', 'b', 'c'), 'b')
        assertContains(booleanArrayOf(false, true), true)
        assertContains(10..20, 15)
        assertContains(100L..200L, 150L)
        assertContains('a'..'z', 'q')
        assertContains(0 until 10, 9)
        assertContains(mapOf("compiler" to "kotlinc", "runner" to "junit"), "runner")
        assertContains("Kotlin testing library", "TESTING", ignoreCase = true)
        assertContains("native-image ready", Regex("native[- ]image"))
    }

    @Test
    fun contentAssertionsComparePrimitiveArraysObjectArraysIterablesSetsAndSequences() {
        assertContentEquals(listOf("first", "second"), listOf("first", "second"))
        assertEquals(setOf("metadata", "tests"), linkedSetOf("tests", "metadata"))
        assertContentEquals(sequenceOf(2, 4, 6), sequenceOf(2, 4, 6))
        assertContentEquals(arrayOf("a", "b", "c"), arrayOf("a", "b", "c"))
        assertContentEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3))
        assertContentEquals(shortArrayOf(4, 5, 6), shortArrayOf(4, 5, 6))
        assertContentEquals(intArrayOf(7, 8, 9), intArrayOf(7, 8, 9))
        assertContentEquals(longArrayOf(10L, 11L), longArrayOf(10L, 11L))
        assertContentEquals(floatArrayOf(1.25f, 2.5f), floatArrayOf(1.25f, 2.5f))
        assertContentEquals(doubleArrayOf(3.5, 4.75), doubleArrayOf(3.5, 4.75))
        assertContentEquals(booleanArrayOf(true, false), booleanArrayOf(true, false))
        assertContentEquals(charArrayOf('x', 'y'), charArrayOf('x', 'y'))
    }

    @Test
    fun expectedFailureAssertionsExposeThrownExceptions() {
        val genericFailure = assertFails("assertFails should return the thrown exception") {
            error("generic failure")
        }
        assertIs<IllegalStateException>(genericFailure)
        assertEquals("generic failure", genericFailure.message)

        val typedFailure = assertFailsWith<IllegalArgumentException>("typed failures should be matched") {
            require(false) { "invalid argument" }
        }
        assertEquals("invalid argument", typedFailure.message)

        val assertionFailure = assertFailsWith<AssertionError> {
            assertContains(listOf("present"), "absent", "missing value should fail")
        }
        assertNotNull(assertionFailure.message)
        assertContains(assertionFailure.message ?: "", "missing value should fail")
    }

    @Test
    fun failWithCausePreservesOriginalException() {
        val cause = IllegalStateException("root cause")

        val failure = assertFailsWith<AssertionError> {
            fail("failure with cause", cause)
        }

        assertContains(failure.message ?: "", "failure with cause")
        assertSame(cause, failure.cause)
    }

    @Test
    fun expectAndFailComposeWithAssertionFailures() {
        expect("GRAALVM") { "graalvm".uppercase() }
        expect(6, "computed expectation should match") { (1..3).sum() }

        val failure = assertFailsWith<AssertionError> {
            fail("forced failure from kotlin.test.fail")
        }
        assertContains(failure.message ?: "", "forced failure")
    }

    @Test
    fun defaultAsserterDefersLazyFailureMessagesUntilAssertionFails() {
        val defaultAsserter: Asserter = DefaultAsserter
        var evaluatedMessage = false

        defaultAsserter.assertTrue(
            lazyMessage = {
                evaluatedMessage = true
                "success details should stay lazy"
            },
            actual = true,
        )
        assertFalse(evaluatedMessage, "successful assertions should not evaluate lazy messages")

        val failure = assertFailsWith<AssertionError> {
            defaultAsserter.assertTrue(
                lazyMessage = {
                    evaluatedMessage = true
                    "computed failure details"
                },
                actual = false,
            )
        }

        assertTrue(evaluatedMessage, "failed assertions should evaluate lazy messages")
        assertContains(failure.message ?: "", "computed failure details")
    }
}
