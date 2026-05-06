/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_test_dispatcher_jvm

import io.ktor.test.dispatcher.runTestWithRealTime
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import kotlin.coroutines.ContinuationInterceptor
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
public class Ktor_test_dispatcher_jvmTest {
    @Test
    fun runTestWithRealTimeExecutesSuspendBodyOnNonTestDispatcher() {
        runTestWithRealTime(timeout = 5.seconds) {
            val dispatcher: ContinuationInterceptor? = currentCoroutineContext()[ContinuationInterceptor]
            val result: Int = coroutineScope {
                val first = async { 21 }
                val second = async { first.await() * 2 }
                second.await()
            }

            assertThat(dispatcher).isNotNull
            assertThat(dispatcher).isNotInstanceOf(TestDispatcher::class.java)
            assertThat(result).isEqualTo(42)
        }
    }

    @Test
    fun runTestWithRealTimeKeepsAdditionalContextFromProvidedTestDispatcherContext() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val coroutineName = "ktor-real-time-context"

        runTestWithRealTime(dispatcher + CoroutineName(coroutineName), timeout = 5.seconds) {
            val context = currentCoroutineContext()

            assertThat(context[CoroutineName]?.name).isEqualTo(coroutineName)
            assertThat(context[ContinuationInterceptor]).isNotInstanceOf(TestDispatcher::class.java)
        }
    }

    @Test
    fun delaysInBodyAreNotAdvancedByProvidedTestScheduler() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)

        runTestWithRealTime(dispatcher, timeout = 5.seconds) {
            var delayedBlockCompleted = false
            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                delay(2.seconds)
                delayedBlockCompleted = true
            }

            scheduler.advanceUntilIdle()

            assertThat(delayedBlockCompleted).isFalse()
            job.cancelAndJoin()
        }
    }

    @Test
    fun runTestWithRealTimePropagatesBodyFailure() {
        assertThatThrownBy {
            runTestWithRealTime(timeout = 5.seconds) {
                throw IllegalStateException("boom")
            }
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessage("boom")
    }

    @Test
    fun runTestWithRealTimeAppliesBoundedTimeoutToNonCompletingBody() {
        assertThatThrownBy {
            runTestWithRealTime(timeout = 250.milliseconds) {
                awaitCancellation()
            }
        }.isInstanceOf(AssertionError::class.java)
    }
}
