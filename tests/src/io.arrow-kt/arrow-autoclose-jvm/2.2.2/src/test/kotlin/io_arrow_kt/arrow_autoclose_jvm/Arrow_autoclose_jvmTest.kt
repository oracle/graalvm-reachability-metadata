/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_autoclose_jvm

import arrow.autoCloseScope
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import java.util.concurrent.CancellationException
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine

public class Arrow_autoclose_jvmTest {
    @Test
    fun autoCloseScopeReturnsBlockResultAndReleasesResourcesInReverseOrder(): Unit {
        val events: MutableList<String> = mutableListOf()

        val result: String = autoCloseScope {
            autoClose(
                acquire = {
                    events += "acquire:first"
                    "first"
                },
                release = { resource, failure -> events += "release:$resource:${failure == null}" },
            )
            autoClose(
                acquire = {
                    events += "acquire:second"
                    "second"
                },
                release = { resource, failure -> events += "release:$resource:${failure == null}" },
            )
            events += "body"
            "completed"
        }

        assertThat(result).isEqualTo("completed")
        assertThat(events).containsExactly(
            "acquire:first",
            "acquire:second",
            "body",
            "release:second:true",
            "release:first:true",
        )
    }

    @Test
    fun onCloseRegistersManualFinalizer(): Unit {
        val events: MutableList<String> = mutableListOf()

        autoCloseScope {
            onClose { failure -> events += "manual:${failure == null}" }
            events += "body"
        }

        assertThat(events).containsExactly("body", "manual:true")
    }

    @Test
    fun installReturnsAutoCloseableAndClosesInstalledResourcesInReverseOrder(): Unit {
        val events: MutableList<String> = mutableListOf()
        val first: RecordingCloseable = RecordingCloseable("first", events)
        val second: RecordingCloseable = RecordingCloseable("second", events)

        autoCloseScope {
            assertThat(install(first)).isSameAs(first)
            assertThat(install(second)).isSameAs(second)
            assertThat(first.closed).isFalse()
            assertThat(second.closed).isFalse()
        }

        assertThat(first.closed).isTrue()
        assertThat(second.closed).isTrue()
        assertThat(events).containsExactly("close:second", "close:first")
    }

    @Test
    fun autoCloseAcquiresValueAndPassesItToReleaseWithCompletionCause(): Unit {
        val events: MutableList<String> = mutableListOf()
        lateinit var acquired: ManagedResource

        val result: String = autoCloseScope {
            val resource: ManagedResource = autoClose(
                acquire = {
                    ManagedResource("resource").also {
                        acquired = it
                        events += "acquire:${it.name}"
                    }
                },
                release = { resource, failure ->
                    events += "release:${resource.name}:${failure == null}"
                },
            )

            assertThat(resource).isSameAs(acquired)
            events += "use:${resource.name}"
            "used"
        }

        assertThat(result).isEqualTo("used")
        assertThat(events).containsExactly("acquire:resource", "use:resource", "release:resource:true")
    }

    @Test
    fun acquireFailureReleasesPreviouslyAcquiredResourcesWithTheOriginalFailure(): Unit {
        val acquireFailure: IllegalStateException = IllegalStateException("acquire failed")
        val events: MutableList<String> = mutableListOf()
        var observedFailure: Throwable? = null

        assertThatThrownBy {
            autoCloseScope<Unit> {
                autoClose(
                    acquire = {
                        events += "acquire:first"
                        "first"
                    },
                    release = { resource, cause ->
                        observedFailure = cause
                        events += "release:$resource"
                    },
                )
                autoClose(
                    acquire = {
                        events += "acquire:second"
                        throw acquireFailure
                    },
                    release = { resource, _ -> events += "release:$resource" },
                )
            }
        }.isSameAs(acquireFailure)

        assertThat(observedFailure).isSameAs(acquireFailure)
        assertThat(events).containsExactly("acquire:first", "acquire:second", "release:first")
    }

    @Test
    fun blockFailureIsRethrownAfterFinalizersReceiveTheOriginalFailure(): Unit {
        val failure: IllegalArgumentException = IllegalArgumentException("body failed")
        val events: MutableList<String> = mutableListOf()
        var observedFailure: Throwable? = null

        assertThatThrownBy {
            autoCloseScope<Unit> {
                autoClose(
                    acquire = { "resource" },
                    release = { _, cause ->
                        observedFailure = cause
                        events += "released"
                    },
                )
                throw failure
            }
        }.isSameAs(failure)

        assertThat(observedFailure).isSameAs(failure)
        assertThat(events).containsExactly("released")
    }

    @Test
    fun finalizerFailuresAreSuppressedOntoBlockFailureAndDoNotStopOtherFinalizers(): Unit {
        val bodyFailure: IllegalStateException = IllegalStateException("body failed")
        val firstReleaseFailure: IllegalStateException = IllegalStateException("first release failed")
        val secondReleaseFailure: IllegalArgumentException = IllegalArgumentException("second release failed")
        val events: MutableList<String> = mutableListOf()

        val thrown: Throwable = catchThrowable {
            autoCloseScope<Unit> {
                autoClose(
                    acquire = { "first" },
                    release = { _, _ ->
                        events += "first"
                        throw firstReleaseFailure
                    },
                )
                autoClose(
                    acquire = { "second" },
                    release = { _, _ ->
                        events += "second"
                        throw secondReleaseFailure
                    },
                )
                throw bodyFailure
            }
        }

        assertThat(thrown).isSameAs(bodyFailure)
        assertThat(thrown.suppressed).containsExactly(secondReleaseFailure, firstReleaseFailure)
        assertThat(events).containsExactly("second", "first")
    }

    @Test
    fun finalizerFailureOnSuccessfulBlockIsRethrownWithEarlierFailuresSuppressed(): Unit {
        val firstReleaseFailure: IllegalStateException = IllegalStateException("first release failed")
        val secondReleaseFailure: IllegalArgumentException = IllegalArgumentException("second release failed")
        val events: MutableList<String> = mutableListOf()

        val thrown: Throwable = catchThrowable {
            autoCloseScope<Unit> {
                autoClose(
                    acquire = { "first" },
                    release = { _, _ ->
                        events += "first"
                        throw firstReleaseFailure
                    },
                )
                autoClose(
                    acquire = { "second" },
                    release = { _, _ ->
                        events += "second"
                        throw secondReleaseFailure
                    },
                )
            }
        }

        assertThat(thrown).isSameAs(secondReleaseFailure)
        assertThat(thrown.suppressed).containsExactly(firstReleaseFailure)
        assertThat(events).containsExactly("second", "first")
    }

    @Test
    fun cancellationFailureIsRethrownAndStillPassedToFinalizers(): Unit {
        val cancellation: CancellationException = CancellationException("cancelled")
        var observedFailure: Throwable? = null

        assertThatThrownBy {
            autoCloseScope<Unit> {
                autoClose(
                    acquire = { "resource" },
                    release = { _, cause -> observedFailure = cause },
                )
                throw cancellation
            }
        }.isSameAs(cancellation)

        assertThat(observedFailure).isSameAs(cancellation)
    }

    @Test
    fun autoCloseScopeCanBeUsedAcrossSuspendCalls(): Unit {
        val events: MutableList<String> = mutableListOf()
        var completed: String? = null
        var failed: Throwable? = null

        suspend { useAutoCloseScopeAcrossSuspendCall(events) }
            .startCoroutine(
                object : Continuation<String> {
                    override val context: CoroutineContext = EmptyCoroutineContext

                    override fun resumeWith(result: Result<String>): Unit {
                        result.fold(
                            onSuccess = { completed = it },
                            onFailure = { failed = it },
                        )
                    }
                },
            )

        assertThat(failed).isNull()
        assertThat(completed).isEqualTo("completed")
        assertThat(events).containsExactly("before-suspend", "suspended", "after-suspend", "close:true")
    }

    private suspend fun useAutoCloseScopeAcrossSuspendCall(events: MutableList<String>): String = autoCloseScope {
        onClose { failure -> events += "close:${failure == null}" }
        events += "before-suspend"
        suspendOnce(events)
        events += "after-suspend"
        "completed"
    }

    private suspend fun suspendOnce(events: MutableList<String>): Unit = suspendCoroutine { continuation ->
        events += "suspended"
        continuation.resume(Unit)
    }

    private data class ManagedResource(val name: String)

    private class RecordingCloseable(
        private val name: String,
        private val events: MutableList<String>,
    ) : AutoCloseable {
        var closed: Boolean = false
            private set

        override fun close(): Unit {
            closed = true
            events += "close:$name"
        }
    }
}
