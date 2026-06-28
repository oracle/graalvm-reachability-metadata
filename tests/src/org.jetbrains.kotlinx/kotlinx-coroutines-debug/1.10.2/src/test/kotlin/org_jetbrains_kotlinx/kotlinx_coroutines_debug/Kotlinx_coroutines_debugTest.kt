/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_debug

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.debug.State
import kotlinx.coroutines.debug.junit4.CoroutinesTimeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.coroutines.EmptyCoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
public class Kotlinx_coroutines_debugTest {
    @Test
    fun debugProbeConfigurationPropertiesAreMutableAndRestored(): Unit {
        val originalSanitizeStackTraces: Boolean = DebugProbes.sanitizeStackTraces
        val originalCreationStackTraces: Boolean = DebugProbes.enableCreationStackTraces
        val originalIgnoreEmptyContext: Boolean = DebugProbes.ignoreCoroutinesWithEmptyContext

        try {
            DebugProbes.sanitizeStackTraces = !originalSanitizeStackTraces
            DebugProbes.enableCreationStackTraces = !originalCreationStackTraces
            DebugProbes.ignoreCoroutinesWithEmptyContext = !originalIgnoreEmptyContext

            assertThat(DebugProbes.sanitizeStackTraces).isEqualTo(!originalSanitizeStackTraces)
            assertThat(DebugProbes.enableCreationStackTraces).isEqualTo(!originalCreationStackTraces)
            assertThat(DebugProbes.ignoreCoroutinesWithEmptyContext).isEqualTo(!originalIgnoreEmptyContext)
        } finally {
            DebugProbes.sanitizeStackTraces = originalSanitizeStackTraces
            DebugProbes.enableCreationStackTraces = originalCreationStackTraces
            DebugProbes.ignoreCoroutinesWithEmptyContext = originalIgnoreEmptyContext
        }
    }

    @Test
    fun dumpAndHierarchyApisRequireInstalledDebugProbes(): Unit {
        val job: Job = Job()
        val output: ByteArrayOutputStream = ByteArrayOutputStream()
        val printStream: PrintStream = PrintStream(output)

        try {
            assertThat(DebugProbes.isInstalled).isFalse()
            assertThatThrownBy { DebugProbes.dumpCoroutinesInfo() }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("Debug probes are not installed")
            assertThatThrownBy { DebugProbes.dumpCoroutines(printStream) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("Debug probes are not installed")
            assertThatThrownBy { DebugProbes.jobToString(job) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("Debug probes are not installed")
            assertThatThrownBy { DebugProbes.printJob(job, printStream) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("Debug probes are not installed")
            assertThat(output.toString()).isEmpty()
        } finally {
            printStream.close()
            job.cancel()
        }
    }

    @Test
    fun scopeHierarchyApisValidateThatScopeHasAJob(): Unit {
        val scopeWithoutJob: CoroutineScope = object : CoroutineScope {
            override val coroutineContext = EmptyCoroutineContext
        }
        val printStream: PrintStream = PrintStream(ByteArrayOutputStream())

        try {
            assertThatThrownBy { DebugProbes.scopeToString(scopeWithoutJob) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("Job is not present in the scope")
            assertThatThrownBy { DebugProbes.printScope(scopeWithoutJob, printStream) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("Job is not present in the scope")
        } finally {
            printStream.close()
        }
    }

    @Test
    fun coroutineStateEnumExposesDocumentedLifecycleStatesInOrder(): Unit {
        assertThat(State.entries.map { state -> state.name })
            .containsExactly("CREATED", "RUNNING", "SUSPENDED")
        assertThat(State.valueOf("CREATED")).isSameAs(State.CREATED)
        assertThat(State.valueOf("RUNNING")).isSameAs(State.RUNNING)
        assertThat(State.valueOf("SUSPENDED")).isSameAs(State.SUSPENDED)
    }

    @Test
    fun junit4CoroutinesTimeoutRejectsNonPositiveTimeouts(): Unit {
        assertThatThrownBy { CoroutinesTimeout(0) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Expected positive test timeout, but had 0")
        assertThatThrownBy { CoroutinesTimeout(-1) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Expected positive test timeout, but had -1")
    }

    @Test
    fun scopeHierarchyApisRequireInstalledDebugProbesWhenScopeHasAJob(): Unit {
        val job: Job = Job()
        val scope: CoroutineScope = CoroutineScope(job)
        val printStream: PrintStream = PrintStream(ByteArrayOutputStream())

        try {
            assertThatThrownBy { DebugProbes.scopeToString(scope) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("Debug probes are not installed")
            assertThatThrownBy { DebugProbes.printScope(scope, printStream) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("Debug probes are not installed")
        } finally {
            printStream.close()
            job.cancel()
        }
    }
}
