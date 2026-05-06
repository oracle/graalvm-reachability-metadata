/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_test_junit5

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.junit5.JUnit5Asserter
import kotlin.test.junit5.JUnit5Contributor
import org.opentest4j.AssertionFailedError

public class Kotlin_test_junit5Test {
    private lateinit var fixture: MutableList<String>

    @BeforeTest
    fun setUp(): Unit {
        fixture = mutableListOf("created")
    }

    @AfterTest
    fun tearDown(): Unit {
        assertEquals("created", fixture.first())
        fixture.clear()
    }

    @Test
    fun commonTestAnnotationsAreBackedByJUnitJupiter(): Unit {
        fixture += "executed"

        assertContentEquals(listOf("created", "executed"), fixture)
    }

    @Test
    fun asserterContributorProvidesJUnit5Asserter(): Unit {
        val contributedAsserter = JUnit5Contributor().contribute()

        assertSame(JUnit5Asserter, contributedAsserter)
    }

    @Test
    fun junit5AsserterPassesCoreAssertions(): Unit {
        val sharedValue = Any()
        val distinctValue = Any()

        JUnit5Asserter.assertTrue("boolean condition", true)
        JUnit5Asserter.assertEquals("equal values", listOf("a", "b"), listOf("a", "b"))
        JUnit5Asserter.assertNotEquals("different values", listOf("a"), listOf("b"))
        JUnit5Asserter.assertSame("same instance", sharedValue, sharedValue)
        JUnit5Asserter.assertNotSame("different instances", sharedValue, distinctValue)
        JUnit5Asserter.assertNotNull("non-null value", sharedValue)
        JUnit5Asserter.assertNull("null value", null)
    }

    @Test
    fun junit5AsserterProducesStructuredAssertionFailures(): Unit {
        val failure = assertFailsWith<AssertionFailedError> {
            JUnit5Asserter.assertEquals("values should match", "expected", "actual")
        }

        assertTrue(failure.isExpectedDefined)
        assertTrue(failure.isActualDefined)
        assertEquals("expected", failure.expected.value)
        assertEquals("actual", failure.actual.value)
        assertContains(assertNotNull(failure.message), "values should match")
    }

    @Test
    fun junit5AsserterUsesLazyMessagesOnlyForFailures(): Unit {
        var messageEvaluated = false
        JUnit5Asserter.assertTrue(
            {
                messageEvaluated = true
                "should not be needed"
            },
            true,
        )
        assertFalse(messageEvaluated, "successful lazy assertion should not evaluate its message")

        val failure = assertFailsWith<AssertionFailedError> {
            JUnit5Asserter.assertTrue({ "lazy failure message" }, false)
        }
        assertContains(assertNotNull(failure.message), "lazy failure message")
    }

    @Test
    fun junit5AsserterPreservesFailureCause(): Unit {
        val cause = IllegalStateException("root cause")

        val failure = assertFailsWith<AssertionFailedError> {
            JUnit5Asserter.fail("wrapped failure", cause)
        }

        assertSame(cause, failure.cause)
        assertContains(assertNotNull(failure.message), "wrapped failure")
    }

    @Test
    fun kotlinTestAssertionsAreRoutedThroughJUnit5(): Unit {
        val sameReference = StringBuilder("kotlin-test")
        val differentReference = StringBuilder("kotlin-test")

        assertTrue(sameReference.isNotEmpty())
        assertFalse(sameReference.isEmpty())
        assertEquals("kotlin-test", sameReference.toString())
        assertNotEquals("other", sameReference.toString())
        assertSame(sameReference, sameReference)
        assertNotSame(sameReference, differentReference)
        assertNotNull(sameReference)
        assertNull(null)
        assertContains("JUnit Jupiter", "jupiter", ignoreCase = true)
        assertContentEquals(intArrayOf(1, 2, 3), intArrayOf(1, 2, 3))
    }

    @Test
    fun kotlinTestFailureAssertionsCaptureJUnit5AssertionErrors(): Unit {
        val equalityFailure = assertFailsWith<AssertionFailedError> {
            assertEquals(10, 20, "numbers differ")
        }
        assertEquals(10, equalityFailure.expected.value)
        assertEquals(20, equalityFailure.actual.value)
        assertContains(assertNotNull(equalityFailure.message), "numbers differ")

        val nullFailure = assertFailsWith<AssertionFailedError> {
            assertNotNull(null, "value is required")
        }
        assertContains(assertNotNull(nullFailure.message), "value is required")
    }

    @Test
    fun assertFailsWithReturnsTypedExceptionFromTestedBlock(): Unit {
        val exception = assertFailsWith<IllegalArgumentException> {
            throw IllegalArgumentException("invalid argument")
        }

        assertEquals("invalid argument", exception.message)
    }
}
