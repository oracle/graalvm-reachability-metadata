/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_test_jvm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
public class Kotlinx_coroutines_test_jvmTest {
    @Test
    fun runTestExecutesDelayedCoroutinesUsingVirtualTime(): Unit = runTest {
        val events: MutableList<String> = mutableListOf()

        launch {
            delay(100)
            events += "slow at $currentTime"
        }
        launch {
            delay(10)
            events += "fast at $currentTime"
        }

        assertThat(currentTime).isZero()
        runCurrent()
        assertThat(events).isEmpty()

        advanceTimeBy(10)
        runCurrent()
        assertThat(events).containsExactly("fast at 10")
        assertThat(currentTime).isEqualTo(10)

        advanceUntilIdle()
        assertThat(events).containsExactly("fast at 10", "slow at 100")
        assertThat(currentTime).isEqualTo(100)
    }

    @Test
    fun sharedSchedulerCoordinatesMultipleStandardDispatchers(): Unit {
        val scheduler: TestCoroutineScheduler = TestCoroutineScheduler()
        val firstDispatcher: TestDispatcher = StandardTestDispatcher(scheduler, name = "first")
        val secondDispatcher: TestDispatcher = StandardTestDispatcher(scheduler, name = "second")
        val scope: TestScope = TestScope(firstDispatcher)
        val events: MutableList<String> = mutableListOf()

        try {
            scope.launch {
                delay(100)
                events += "first at ${scheduler.currentTime}"
            }
            scope.launch(secondDispatcher) {
                delay(50)
                events += "second at ${scheduler.currentTime}"
            }

            scheduler.runCurrent()
            assertThat(events).isEmpty()

            scheduler.advanceTimeBy(50)
            scheduler.runCurrent()
            assertThat(events).containsExactly("second at 50")

            scheduler.advanceUntilIdle()
            assertThat(events).containsExactly("second at 50", "first at 100")
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun unconfinedTestDispatcherStartsCoroutineEagerlyAndResumesOnScheduler(): Unit = runTest {
        val dispatcher: TestDispatcher = UnconfinedTestDispatcher(testScheduler, name = "eager")
        val events: MutableList<String> = mutableListOf()

        launch(dispatcher) {
            events += "entered at $currentTime"
            delay(25)
            events += "resumed at $currentTime"
        }

        assertThat(events).containsExactly("entered at 0")
        advanceUntilIdle()
        assertThat(events).containsExactly("entered at 0", "resumed at 25")
        assertThat(currentTime).isEqualTo(25)
    }

    @Test
    fun backgroundScopeUsesVirtualTimeAndIsCancelledByRunTest(): Unit = runTest {
        var ticks: Int = 0

        backgroundScope.launch {
            while (isActive) {
                delay(100)
                ticks += 1
            }
        }

        runCurrent()
        advanceTimeBy(300)
        runCurrent()

        assertThat(ticks).isEqualTo(3)
        assertThat(currentTime).isEqualTo(300)
    }

    @Test
    fun setMainMakesDispatchersMainUseTheTestScheduler(): Unit {
        val mainDispatcher: TestDispatcher = StandardTestDispatcher(name = "main")
        Dispatchers.setMain(mainDispatcher)

        try {
            runTest {
                val events: MutableList<String> = mutableListOf()

                launch(Dispatchers.Main) {
                    delay(25)
                    events += "main launch at $currentTime"
                }
                launch {
                    delay(50)
                    events += "test launch at $currentTime"
                }

                advanceUntilIdle()

                assertThat(events).containsExactly("main launch at 25", "test launch at 50")
                assertThat(currentTime).isEqualTo(50)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun withContextOnMainDispatcherIsDrivenByVirtualTime(): Unit {
        val mainDispatcher: TestDispatcher = StandardTestDispatcher(name = "main-context")
        Dispatchers.setMain(mainDispatcher)

        try {
            runTest {
                var result: String = "pending"

                launch {
                    result = withContext(Dispatchers.Main) {
                        delay(75)
                        "completed at $currentTime"
                    }
                }

                runCurrent()
                assertThat(result).isEqualTo("pending")

                advanceUntilIdle()
                assertThat(result).isEqualTo("completed at 75")
                assertThat(currentTime).isEqualTo(75)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun virtualTimeoutsThrowWithoutWaitingForWallClockTime(): Unit {
        assertThatThrownBy {
            runTest {
                withTimeout(100) {
                    delay(1_000)
                }
            }
        }.isInstanceOf(TimeoutCancellationException::class.java)
    }

    @Test
    fun runTestRethrowsUncaughtChildFailures(): Unit {
        assertThatThrownBy {
            runTest {
                launch {
                    throw IllegalStateException("child failed")
                }
            }
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessage("child failed")
    }

    @Test
    fun cancellingAJobRemovesItsPendingScheduledWork(): Unit = runTest {
        val events: MutableList<String> = mutableListOf()
        val job = launch {
            delay(100)
            events += "should not run"
        }

        runCurrent()
        job.cancel()
        advanceUntilIdle()

        assertThat(events).isEmpty()
        assertThat(currentTime).isZero()
    }

    @Test
    fun testTimeSourceMeasuresElapsedVirtualTime(): Unit = runTest {
        val mark = testScheduler.timeSource.markNow()

        delay(250)
        assertThat(mark.elapsedNow().inWholeMilliseconds).isEqualTo(250)

        val laterMark = testScheduler.timeSource.markNow()
        advanceTimeBy(75)
        runCurrent()

        assertThat(laterMark.elapsedNow().inWholeMilliseconds).isEqualTo(75)
        assertThat(currentTime).isEqualTo(325)
    }
}
