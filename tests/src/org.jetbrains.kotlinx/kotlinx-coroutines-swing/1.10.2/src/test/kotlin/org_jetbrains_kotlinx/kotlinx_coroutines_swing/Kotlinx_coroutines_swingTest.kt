/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_swing

import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.SwingUtilities
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class Kotlinx_coroutines_swingTest {
    @Test
    public fun mainDispatcherIsProvidedBySwingModule(): Unit = runBlockingWithTimeout {
        assertThat(Dispatchers.Main).isSameAs(Dispatchers.Swing)
        assertThat(Dispatchers.Main.immediate).isSameAs(Dispatchers.Swing.immediate)

        val ranOnEventDispatchThread: Boolean = withContext(Dispatchers.Main) {
            SwingUtilities.isEventDispatchThread()
        }

        assertThat(ranOnEventDispatchThread).isTrue()
    }

    @Test
    public fun swingDispatcherRunsCoroutinesOnEventDispatchThreadAndUpdatesComponents(): Unit = runBlockingWithTimeout {
        val label: JLabel = JLabel("initial")
        val observations: List<Boolean> = withContext(Dispatchers.Swing) {
            val beforeDelayOnEdt: Boolean = SwingUtilities.isEventDispatchThread()
            label.text = "started"

            delay(10)

            val afterDelayOnEdt: Boolean = SwingUtilities.isEventDispatchThread()
            label.text = "finished"
            listOf(beforeDelayOnEdt, afterDelayOnEdt)
        }

        assertThat(observations).containsExactly(true, true)
        assertThat(label.text).isEqualTo("finished")
    }

    @Test
    public fun immediateDispatcherRunsInlineWhenAlreadyOnEventDispatchThread(): Unit {
        val events: CopyOnWriteArrayList<String> = CopyOnWriteArrayList()
        val queuedCoroutineRan: CountDownLatch = CountDownLatch(1)
        val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)

        try {
            invokeOnEventDispatchThread {
                events.add("entered")
                scope.launch {
                    events.add("queued")
                    queuedCoroutineRan.countDown()
                }

                runBlocking {
                    withContext(Dispatchers.Swing.immediate) {
                        events.add("immediate")
                    }
                }
                events.add("leaving")
            }

            assertThat(queuedCoroutineRan.await(5, TimeUnit.SECONDS)).isTrue()
            assertThat(events).containsExactly("entered", "immediate", "leaving", "queued")
        } finally {
            scope.cancel()
        }
    }

    @Test
    public fun immediateDispatcherDispatchesFromBackgroundThreadsToEventDispatchThread(): Unit = runBlockingWithTimeout {
        val initialThreadWasEdt: Boolean = SwingUtilities.isEventDispatchThread()

        val ranOnEventDispatchThread: Boolean = withContext(Dispatchers.Swing.immediate) {
            SwingUtilities.isEventDispatchThread()
        }

        assertThat(initialThreadWasEdt).isFalse()
        assertThat(ranOnEventDispatchThread).isTrue()
    }

    @Test
    public fun timeoutInsideSwingContextCancelsSuspendedCoroutineOnEventDispatchThread(): Unit = runBlockingWithTimeout {
        val finallyRanOnEventDispatchThread: AtomicBoolean = AtomicBoolean(false)

        val failure: Throwable = assertFails {
            withContext(Dispatchers.Swing) {
                withTimeout(50) {
                    try {
                        suspendCancellableCoroutine<Unit> { }
                    } finally {
                        finallyRanOnEventDispatchThread.set(SwingUtilities.isEventDispatchThread())
                    }
                }
            }
        }

        assertThat(failure).isInstanceOf(TimeoutCancellationException::class.java)
        assertThat(finallyRanOnEventDispatchThread.get()).isTrue()
    }

    @Test
    public fun cancellingDelayedSwingCoroutineRunsFinallyOnEventDispatchThread(): Unit = runBlockingWithTimeout {
        val started: CountDownLatch = CountDownLatch(1)
        val completedFinally: CountDownLatch = CountDownLatch(1)
        val finallyRanOnEventDispatchThread: AtomicBoolean = AtomicBoolean(false)
        val job: Job = launch(Dispatchers.Swing) {
            try {
                started.countDown()
                delay(TimeUnit.MINUTES.toMillis(1))
            } finally {
                finallyRanOnEventDispatchThread.set(SwingUtilities.isEventDispatchThread())
                completedFinally.countDown()
            }
        }

        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()
        job.cancelAndJoin()

        assertThat(completedFinally.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(finallyRanOnEventDispatchThread.get()).isTrue()
    }

    @Test
    public fun swingEventHandlersCanLaunchCoroutinesThatResumeOnEventDispatchThread(): Unit {
        val button: JButton = JButton("ready")
        val completed: CountDownLatch = CountDownLatch(1)
        val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)
        val resumedOnEventDispatchThread: AtomicBoolean = AtomicBoolean(false)

        try {
            button.addActionListener {
                scope.launch {
                    button.text = "running"
                    delay(10)
                    resumedOnEventDispatchThread.set(SwingUtilities.isEventDispatchThread())
                    button.text = "done"
                    completed.countDown()
                }
            }

            invokeOnEventDispatchThread { button.doClick() }

            assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue()
            assertThat(resumedOnEventDispatchThread.get()).isTrue()
            assertThat(invokeOnEventDispatchThread { button.text }).isEqualTo("done")
        } finally {
            scope.cancel()
        }
    }

    @Test
    public fun swingDispatcherCanAwaitCallbacksScheduledOnEventDispatchQueue(): Unit = runBlockingWithTimeout {
        val message: String = withContext(Dispatchers.Swing) {
            suspendCancellableCoroutine { continuation ->
                SwingUtilities.invokeLater {
                    continuation.resume("callback on EDT: ${SwingUtilities.isEventDispatchThread()}")
                }
            }
        }

        assertThat(message).isEqualTo("callback on EDT: true")
    }

    private fun <T> runBlockingWithTimeout(block: suspend CoroutineScope.() -> T): T = runBlocking {
        withTimeout(5_000) {
            block()
        }
    }

    private suspend fun assertFails(block: suspend () -> Unit): Throwable {
        try {
            block()
        } catch (throwable: Throwable) {
            return throwable
        }
        throw AssertionError("Expected the suspending operation to fail")
    }

    private fun <T> invokeOnEventDispatchThread(action: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) {
            return action()
        }

        val result: EdtResult<T> = EdtResult()
        SwingUtilities.invokeAndWait {
            try {
                result.value = action()
            } catch (throwable: Throwable) {
                result.failure = throwable
            }
        }
        result.failure?.let { throw it }

        @Suppress("UNCHECKED_CAST")
        return result.value as T
    }
}

private class EdtResult<T> {
    var value: T? = null
    var failure: Throwable? = null
}
