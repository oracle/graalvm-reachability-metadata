/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_swing

import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class Kotlinx_coroutines_swingTest {
    @Test
    fun swingDispatcherRunsCoroutineBodyOnEventDispatchThread(): Unit = runBlocking {
        withTimeout(5_000) {
            val callerThread: Thread = Thread.currentThread()

            val result: SwingThreadResult = withContext(Dispatchers.Swing) {
                SwingThreadResult(
                    isEventDispatchThread = SwingUtilities.isEventDispatchThread(),
                    thread = Thread.currentThread(),
                    dispatcher = coroutineContext[ContinuationInterceptor],
                )
            }

            assertThat(result.isEventDispatchThread).isTrue()
            assertThat(result.thread).isNotSameAs(callerThread)
            assertThat(result.dispatcher).isSameAs(Dispatchers.Swing)
        }
    }

    @Test
    fun mainDispatcherIsProvidedBySwingFactoryAndDispatchesToEventDispatchThread(): Unit = runBlocking {
        withTimeout(5_000) {
            val result: SwingThreadResult = withContext(Dispatchers.Main) {
                SwingThreadResult(
                    isEventDispatchThread = SwingUtilities.isEventDispatchThread(),
                    thread = Thread.currentThread(),
                    dispatcher = coroutineContext[ContinuationInterceptor],
                )
            }

            assertThat(result.isEventDispatchThread).isTrue()
            assertThat(result.dispatcher).isSameAs(Dispatchers.Main)
            assertThat(Dispatchers.Main.immediate).isSameAs(Dispatchers.Swing.immediate)
        }
    }

    @Test
    fun immediateDispatcherRunsInlineOnlyWhenAlreadyOnEventDispatchThread(): Unit = runBlocking {
        withTimeout(5_000) {
            assertThat(Dispatchers.Swing.immediate.isDispatchNeeded(coroutineContext)).isTrue()

            val events: MutableList<String> = mutableListOf()
            withContext(Dispatchers.Swing) {
                val eventDispatchThread: Thread = Thread.currentThread()

                assertThat(Dispatchers.Swing.isDispatchNeeded(coroutineContext)).isTrue()
                assertThat(Dispatchers.Swing.immediate.isDispatchNeeded(coroutineContext)).isFalse()
                assertThat(Dispatchers.Swing.immediate.immediate).isSameAs(Dispatchers.Swing.immediate)

                withContext(Dispatchers.Swing.immediate) {
                    events += "immediate"
                    assertThat(Thread.currentThread()).isSameAs(eventDispatchThread)
                    assertThat(SwingUtilities.isEventDispatchThread()).isTrue()
                }
                events += "after-immediate"
            }

            assertThat(events).containsExactly("immediate", "after-immediate")
        }
    }

    @Test
    fun asyncWorkComposesOnSwingDispatcher(): Unit = runBlocking {
        withTimeout(5_000) {
            val combined: String = withContext(Dispatchers.Swing) {
                val greeting = async(Dispatchers.Swing.immediate) {
                    assertThat(SwingUtilities.isEventDispatchThread()).isTrue()
                    "hello"
                }
                val target = async(Dispatchers.Swing.immediate) {
                    assertThat(SwingUtilities.isEventDispatchThread()).isTrue()
                    "swing"
                }

                "${greeting.await()} ${target.await()}"
            }

            assertThat(combined).isEqualTo("hello swing")
        }
    }

    @Test
    fun swingDispatcherPostsNestedWorkInsteadOfRunningInlineOnEventDispatchThread(): Unit = runBlocking {
        withTimeout(5_000) {
            val events: MutableList<String> = mutableListOf()

            withContext(Dispatchers.Swing) {
                events += "outer-start"
                val job = launch(Dispatchers.Swing) {
                    assertThat(SwingUtilities.isEventDispatchThread()).isTrue()
                    events += "nested"
                }

                assertThat(job.isCompleted).isFalse()
                events += "outer-after-launch"
            }

            assertThat(events).containsExactly("outer-start", "outer-after-launch", "nested")
        }
    }

    @Test
    fun delayResumesOnEventDispatchThreadAndCancellationStopsPendingContinuation(): Unit = runBlocking {
        withTimeout(5_000) {
            val events: MutableList<String> = mutableListOf()

            withContext(Dispatchers.Swing) {
                events += "before-delay"
                delay(10)
                assertThat(SwingUtilities.isEventDispatchThread()).isTrue()
                events += "after-delay"
            }

            val completedAfterCancellation = AtomicBoolean(false)
            val job = launch(Dispatchers.Swing) {
                delay(10_000)
                completedAfterCancellation.set(true)
            }

            job.cancelAndJoin()

            assertThat(events).containsExactly("before-delay", "after-delay")
            assertThat(completedAfterCancellation).isFalse()
        }
    }

    @Test
    fun timeoutScheduledOnSwingDispatcherCancelsSuspendedCoroutineOnEventDispatchThread(): Unit = runBlocking {
        withTimeout(5_000) {
            val events: MutableList<String> = mutableListOf()
            val finalizerRanOnEventDispatchThread = AtomicBoolean(false)

            val thrown: Throwable? = runCatching {
                withContext(Dispatchers.Swing) {
                    withTimeout(100) {
                        try {
                            events += "started"
                            delay(10_000)
                            events += "after-delay"
                        } finally {
                            finalizerRanOnEventDispatchThread.set(SwingUtilities.isEventDispatchThread())
                            events += "finally"
                        }
                    }
                }
            }.exceptionOrNull()

            assertThat(thrown).isInstanceOf(TimeoutCancellationException::class.java)
            assertThat(events).containsExactly("started", "finally")
            assertThat(finalizerRanOnEventDispatchThread).isTrue()
        }
    }
}

private data class SwingThreadResult(
    val isEventDispatchThread: Boolean,
    val thread: Thread,
    val dispatcher: ContinuationInterceptor?,
)
