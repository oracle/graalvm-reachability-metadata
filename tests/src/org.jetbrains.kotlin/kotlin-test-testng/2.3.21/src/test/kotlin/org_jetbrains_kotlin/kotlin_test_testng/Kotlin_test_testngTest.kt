/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_test_testng

import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test as KotlinTest
import kotlin.test.assertEquals as kotlinAssertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse as kotlinAssertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals as kotlinAssertNotEquals
import kotlin.test.assertNotNull as kotlinAssertNotNull
import kotlin.test.assertNull as kotlinAssertNull
import kotlin.test.assertSame as kotlinAssertSame
import kotlin.test.assertTrue as kotlinAssertTrue
import kotlin.test.fail as kotlinFail
import kotlin.test.testng.TestNGAsserter
import kotlin.test.testng.TestNGContributor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.testng.ITestContext
import org.testng.ITestListener
import org.testng.ITestResult
import org.testng.TestNG
import org.testng.annotations.DataProvider

public class Kotlin_test_testngTest {
    @Test
    public fun contributorProvidesTestNgAsserterWhenTestNgIsAvailable(): Unit {
        val contributedAsserter = TestNGContributor().contribute()

        assertThat(contributedAsserter).isSameAs(TestNGAsserter)
    }

    @Test
    public fun testNgAsserterDelegatesSuccessfulCoreAssertionsToTestNg(): Unit {
        val sharedReference = Any()
        val distinctReference = Any()

        TestNGAsserter.assertEquals("lists with equal content should match", listOf("alpha", "beta"), listOf("alpha", "beta"))
        TestNGAsserter.assertNotEquals("different numbers should not match", 41, 42)
        TestNGAsserter.assertSame("same reference should be accepted", sharedReference, sharedReference)
        TestNGAsserter.assertNotSame("different references should be accepted", sharedReference, distinctReference)
        TestNGAsserter.assertNotNull("non-null value should be accepted", "value")
        TestNGAsserter.assertNull("null value should be accepted", null)
    }

    @Test
    public fun testNgAsserterReportsFailuresWithMessagesAndCauses(): Unit {
        val equalityError = assertThrows(AssertionError::class.java) {
            TestNGAsserter.assertEquals("explicit equality message", "expected", "actual")
        }
        assertThat(equalityError).hasMessageContaining("explicit equality message")

        val nullError = assertThrows(AssertionError::class.java) {
            TestNGAsserter.assertNotNull(null, null)
        }
        assertThat(nullError).hasMessageContaining("actual value is null")

        val cause = IllegalStateException("root cause")
        val failed = assertThrows(AssertionError::class.java) {
            TestNGAsserter.fail("explicit failure", cause)
        }
        assertThat(failed).hasMessageContaining("explicit failure")
        assertThat(failed.cause).isSameAs(cause)
    }

    @Test
    public fun testNgAsserterUsesLazyMessagesForBooleanAssertions(): Unit {
        val evaluations = AtomicInteger(0)

        TestNGAsserter.assertTrue(
            lazyMessage = {
                evaluations.incrementAndGet()
                "this message is only needed on failure"
            },
            actual = true,
        )

        assertThat(evaluations).hasValue(0)

        val failure = assertThrows(AssertionError::class.java) {
            TestNGAsserter.assertTrue(
                lazyMessage = {
                    evaluations.incrementAndGet()
                    "computed boolean failure"
                },
                actual = false,
            )
        }

        assertThat(evaluations).hasValue(1)
        assertThat(failure).hasMessageContaining("computed boolean failure")
    }

    @Test
    public fun kotlinTestTopLevelAssertionsUseTheTestNgAdapter(): Unit {
        val sharedReference = Any()

        kotlinAssertEquals("kotlin", "kot" + "lin", "top-level equality should delegate successfully")
        kotlinAssertNotEquals("left", "right", "top-level inequality should delegate successfully")
        kotlinAssertSame(sharedReference, sharedReference, "top-level identity should delegate successfully")
        kotlinAssertNotNull("present", "top-level non-null assertion should delegate successfully")
        kotlinAssertNull(null, "top-level null assertion should delegate successfully")
        kotlinAssertTrue("top-level true assertion should delegate successfully") { 2 + 2 == 4 }
        kotlinAssertFalse("top-level false assertion should delegate successfully") { 2 + 2 == 5 }

        val failure = assertThrows(AssertionError::class.java) {
            kotlinAssertEquals("expected", "actual", "top-level assertion failure")
        }
        assertThat(failure).hasMessageContaining("top-level assertion failure")
    }

    @Test
    public fun kotlinTestExceptionAndTypeAssertionsWorkWithTestNgFailures(): Unit {
        val thrown = assertFailsWith<IllegalArgumentException>("expected exception should be returned") {
            throw IllegalArgumentException("bad argument")
        }
        kotlinAssertEquals("bad argument", thrown.message)

        val typed: CharSequence = assertIs<String>("typed value", "assertIs should return the narrowed value")
        kotlinAssertEquals(11, typed.length)

        val mismatch = assertThrows(AssertionError::class.java) {
            assertFailsWith<IllegalArgumentException>("wrong exception type should fail") {
                throw IllegalStateException("wrong exception")
            }
        }
        assertThat(mismatch).hasMessageContaining("wrong exception type should fail")
    }

    @Test
    public fun kotlinTestAnnotationsAreUsableByATestNgRunner(): Unit {
        KotlinTestTestNgAnnotatedSuite.resetCounters()
        val listener = CountingTestNgListener()
        val outputDirectory = Files.createTempDirectory("kotlin-test-testng-output")

        try {
            val testNg = TestNG(false)
            testNg.setUseDefaultListeners(false)
            testNg.setVerbose(0)
            testNg.setOutputDirectory(outputDirectory.toString())
            testNg.setTestClasses(arrayOf(KotlinTestTestNgAnnotatedSuite::class.java))
            testNg.addListener(listener)

            testNg.run()

            assertThat(testNg.hasFailure()).isFalse()
            assertThat(listener.failures).isEqualTo(0)
            assertThat(listener.successes).isEqualTo(1)
            assertThat(KotlinTestTestNgAnnotatedSuite.beforeCalls).hasValue(1)
            assertThat(KotlinTestTestNgAnnotatedSuite.afterCalls).hasValue(1)
            assertThat(KotlinTestTestNgAnnotatedSuite.executedTests).hasValue(1)
            assertThat(KotlinTestTestNgAnnotatedSuite.ignoredTests).hasValue(0)
        } finally {
            outputDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    public fun kotlinTestAnnotationSupportsTestNgExpectedExceptions(): Unit {
        val listener = CountingTestNgListener()
        val outputDirectory = Files.createTempDirectory("kotlin-test-testng-expected-exception-output")

        try {
            val testNg = TestNG(false)
            testNg.setUseDefaultListeners(false)
            testNg.setVerbose(0)
            testNg.setOutputDirectory(outputDirectory.toString())
            testNg.setTestClasses(arrayOf(KotlinTestTestNgExpectedExceptionSuite::class.java))
            testNg.addListener(listener)

            testNg.run()

            assertThat(testNg.hasFailure()).isFalse()
            assertThat(listener.failures).isEqualTo(0)
            assertThat(listener.successes).isEqualTo(1)
        } finally {
            outputDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    public fun kotlinTestAnnotationSupportsTestNgDataProviders(): Unit {
        KotlinTestTestNgDataProviderSuite.resetCounters()
        val listener = CountingTestNgListener()
        val outputDirectory = Files.createTempDirectory("kotlin-test-testng-data-provider-output")

        try {
            val testNg = TestNG(false)
            testNg.setUseDefaultListeners(false)
            testNg.setVerbose(0)
            testNg.setOutputDirectory(outputDirectory.toString())
            testNg.setTestClasses(arrayOf(KotlinTestTestNgDataProviderSuite::class.java))
            testNg.addListener(listener)

            testNg.run()

            assertThat(testNg.hasFailure()).isFalse()
            assertThat(listener.failures).isEqualTo(0)
            assertThat(listener.successes).isEqualTo(KotlinTestTestNgDataProviderSuite.providedRows)
            assertThat(KotlinTestTestNgDataProviderSuite.invocations).hasValue(
                KotlinTestTestNgDataProviderSuite.providedRows,
            )
            assertThat(KotlinTestTestNgDataProviderSuite.totalObservedLength).hasValue(11)
        } finally {
            outputDirectory.toFile().deleteRecursively()
        }
    }
}

public class KotlinTestTestNgExpectedExceptionSuite {
    @KotlinTest(
        expectedExceptions = [IllegalArgumentException::class],
        expectedExceptionsMessageRegExp = ".*expected marker.*",
    )
    public fun expectedExceptionIsTreatedAsTestSuccess(): Unit {
        throw IllegalArgumentException("the expected marker is present")
    }
}

public class KotlinTestTestNgDataProviderSuite {
    @DataProvider(name = "words")
    public fun words(): Array<Array<Any>> = arrayOf(
        arrayOf("alpha", 5),
        arrayOf("kotlin", 6),
    )

    @KotlinTest(dataProvider = "words")
    public fun dataProviderArgumentsAreDeliveredToKotlinTest(word: String, expectedLength: Int): Unit {
        kotlinAssertEquals(expectedLength, word.length)
        invocations.incrementAndGet()
        totalObservedLength.addAndGet(word.length)
    }

    public companion object {
        public val providedRows: Int = 2
        public val invocations: AtomicInteger = AtomicInteger(0)
        public val totalObservedLength: AtomicInteger = AtomicInteger(0)

        public fun resetCounters(): Unit {
            invocations.set(0)
            totalObservedLength.set(0)
        }
    }
}

public class KotlinTestTestNgAnnotatedSuite {
    private var prepared = false

    @BeforeTest
    public fun setUp(): Unit {
        prepared = true
        beforeCalls.incrementAndGet()
    }

    @AfterTest
    public fun tearDown(): Unit {
        afterCalls.incrementAndGet()
        prepared = false
    }

    @KotlinTest
    public fun testAnnotatedWithKotlinTestRunsUnderTestNg(): Unit {
        kotlinAssertTrue(prepared, "BeforeTest should run before each TestNG method")
        executedTests.incrementAndGet()
    }

    @Ignore
    @KotlinTest
    public fun ignoredKotlinTestAnnotationIsHonoredByTestNg(): Unit {
        ignoredTests.incrementAndGet()
        kotlinFail("ignored Kotlin TestNG test should not be executed")
    }

    public companion object {
        public val beforeCalls: AtomicInteger = AtomicInteger(0)
        public val afterCalls: AtomicInteger = AtomicInteger(0)
        public val executedTests: AtomicInteger = AtomicInteger(0)
        public val ignoredTests: AtomicInteger = AtomicInteger(0)

        public fun resetCounters(): Unit {
            beforeCalls.set(0)
            afterCalls.set(0)
            executedTests.set(0)
            ignoredTests.set(0)
        }
    }
}

private class CountingTestNgListener : ITestListener {
    var successes: Int = 0
        private set
    var failures: Int = 0
        private set

    override fun onTestSuccess(result: ITestResult): Unit {
        successes++
    }

    override fun onTestFailure(result: ITestResult): Unit {
        failures++
    }

    override fun onTestFailedWithTimeout(result: ITestResult): Unit {
        failures++
    }

    override fun onFinish(context: ITestContext): Unit {
        failures += context.failedTests.allResults.size
        failures += context.failedConfigurations.allResults.size
    }
}
