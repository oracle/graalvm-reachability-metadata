/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_test_junit

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test as KotlinTest
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.expect
import kotlin.test.fail
import kotlin.test.junit.JUnitAsserter
import kotlin.test.junit.JUnitContributor
import org.junit.ComparisonFailure
import org.junit.runner.JUnitCore
import org.junit.jupiter.api.Test as JupiterTest

public class Kotlin_test_junitTest {
    @JupiterTest
    fun commonAssertionsAreRoutedThroughJUnit4Asserter(): Unit {
        val shared = StringBuilder("kotlin-test")
        val equalButDistinct = StringBuilder("kotlin-test")

        assertTrue(shared.isNotEmpty(), "builder should contain text")
        assertTrue("lazy true assertion should pass") { shared.length == 11 }
        assertFalse(shared.isEmpty(), "builder should not be empty")
        assertFalse("lazy false assertion should pass") { shared.length == 0 }
        assertEquals("kotlin-test", shared.toString(), "string content should match")
        assertNotEquals("junit5", shared.toString(), "different content should not match")
        assertSame(shared, shared, "same reference should be accepted")
        assertNotSame(shared, equalButDistinct, "distinct builders should not be identical")
        assertNull(null, "null values should be accepted")
        val confirmed = assertNotNull(shared.toString(), "non-null values should be returned")
        assertEquals(11, confirmed.length)
        assertNotNull(shared.toString(), "block overload should receive a non-null value") { value ->
            assertTrue(value.startsWith("kotlin"))
        }
        expect("kotlin-test", "expect should compare the block result") { shared.toString() }
    }

    @JupiterTest
    fun junitContributorSelectsJUnitAsserterInJUnitStack(): Unit {
        val contributed = JUnitContributor().contribute()

        assertSame(JUnitAsserter, contributed)
    }

    @JupiterTest
    fun directJUnitAsserterPassesCoreAssertionOperations(): Unit {
        val shared = Any()
        val different = Any()

        JUnitAsserter.assertTrue("boolean condition", true)
        JUnitAsserter.assertEquals("equal lists", listOf("a", "b"), listOf("a", "b"))
        JUnitAsserter.assertNotEquals("different lists", listOf("a"), listOf("b"))
        JUnitAsserter.assertSame("same instance", shared, shared)
        JUnitAsserter.assertNotSame("different instances", shared, different)
        JUnitAsserter.assertNotNull("non-null instance", shared)
        JUnitAsserter.assertNull("null reference", null)
    }

    @JupiterTest
    fun directJUnitAsserterPreservesJUnitFailureTypesAndMessages(): Unit {
        val comparisonFailure = assertFailsWith<ComparisonFailure> {
            JUnitAsserter.assertEquals("strings should match", "expected", "actual")
        }
        assertEquals("expected", comparisonFailure.expected)
        assertEquals("actual", comparisonFailure.actual)
        assertTrue(comparisonFailure.message?.contains("strings should match") ?: false)

        val nullFailure = assertFailsWith<AssertionError> {
            JUnitAsserter.assertNotNull(null, null)
        }
        assertEquals("actual value is null", nullFailure.message)

        val notNullFailure = assertFailsWith<AssertionError> {
            JUnitAsserter.assertNull(null, "present")
        }
        assertTrue(notNullFailure.message?.startsWith("actual value is not null") ?: false)
    }

    @JupiterTest
    fun directJUnitAsserterEvaluatesLazyMessagesOnlyForFailures(): Unit {
        var evaluated = false
        JUnitAsserter.assertTrue(
            {
                evaluated = true
                "successful assertion should not need this message"
            },
            true,
        )
        assertFalse(evaluated, "lazy messages should remain lazy for successful assertions")

        val failure = assertFailsWith<AssertionError> {
            JUnitAsserter.assertTrue({ "computed failure message" }, false)
        }
        assertEquals("computed failure message", failure.message)
    }

    @JupiterTest
    fun kotlinTestFailuresExposeJUnit4AssertionErrors(): Unit {
        val comparisonFailure = assertFailsWith<ComparisonFailure> {
            assertEquals("expected", "actual", "common assertion should use JUnit Assert")
        }
        assertEquals("expected", comparisonFailure.expected)
        assertEquals("actual", comparisonFailure.actual)
        assertTrue(comparisonFailure.message?.contains("common assertion should use JUnit Assert") ?: false)

        val explicitFailure = assertFailsWith<AssertionError> {
            fail("explicit failure from kotlin.test")
        }
        assertEquals("explicit failure from kotlin.test", explicitFailure.message)
    }

    @JupiterTest
    fun failureAssertionsReturnTheThrownException(): Unit {
        val genericFailure = assertFails("assertFails should capture any exception") {
            error("boom")
        }
        assertTrue(genericFailure is IllegalStateException)
        assertEquals("boom", genericFailure.message)

        val typedFailure = assertFailsWith<IllegalArgumentException>("typed assertion should capture matching exception") {
            throw IllegalArgumentException("invalid input")
        }
        assertEquals("invalid input", typedFailure.message)

        val wrongTypeFailure = assertFailsWith<AssertionError> {
            assertFailsWith<IllegalArgumentException> {
                throw IllegalStateException("wrong type")
            }
        }
        assertTrue(wrongTypeFailure.message?.contains("Expected an exception") ?: false)
        assertTrue(wrongTypeFailure.message?.contains("IllegalArgumentException") ?: false)
    }

    @JupiterTest
    fun kotlinTestAnnotationsAreExecutableByJUnit4Runner(): Unit {
        JUnitAnnotatedFixture.events.clear()

        val result = JUnitCore.runClasses(JUnitAnnotatedFixture::class.java)

        assertTrue(result.wasSuccessful(), result.failures.joinToString { it.toString() })
        assertEquals(1, result.runCount)
        assertEquals(0, result.ignoreCount)
        assertEquals(listOf("before", "test", "after"), JUnitAnnotatedFixture.events)
    }

    @JupiterTest
    fun kotlinTestAnnotationSupportsJUnitExpectedExceptionAttribute(): Unit {
        ExpectedExceptionJUnitFixture.events.clear()

        val result = JUnitCore.runClasses(ExpectedExceptionJUnitFixture::class.java)

        assertTrue(result.wasSuccessful(), result.failures.joinToString { it.toString() })
        assertEquals(1, result.runCount)
        assertEquals(listOf("expected exception test"), ExpectedExceptionJUnitFixture.events)
    }

    @JupiterTest
    fun ignoreAnnotationIsRecognizedByJUnit4Runner(): Unit {
        IgnoredJUnitFixture.events.clear()

        val result = JUnitCore.runClasses(IgnoredJUnitFixture::class.java)

        assertTrue(result.wasSuccessful(), result.failures.joinToString { it.toString() })
        assertEquals(1, result.ignoreCount)
        assertTrue(IgnoredJUnitFixture.events.isEmpty(), "ignored tests should not execute")
    }

    public class JUnitAnnotatedFixture {
        @BeforeTest
        fun setUp(): Unit {
            events += "before"
        }

        @KotlinTest
        fun runsWithKotlinTestAlias(): Unit {
            events += "test"
            assertEquals(listOf("before", "test"), events)
        }

        @AfterTest
        fun tearDown(): Unit {
            events += "after"
        }

        public companion object {
            val events: MutableList<String> = mutableListOf()
        }
    }

    public class ExpectedExceptionJUnitFixture {
        @KotlinTest(expected = IllegalStateException::class)
        fun acceptsExpectedException(): Unit {
            events += "expected exception test"
            throw IllegalStateException("handled by JUnit")
        }

        public companion object {
            val events: MutableList<String> = mutableListOf()
        }
    }

    public class IgnoredJUnitFixture {
        @Ignore
        @KotlinTest
        fun skippedTest(): Unit {
            events += "executed"
        }

        public companion object {
            val events: MutableList<String> = mutableListOf()
        }
    }
}
