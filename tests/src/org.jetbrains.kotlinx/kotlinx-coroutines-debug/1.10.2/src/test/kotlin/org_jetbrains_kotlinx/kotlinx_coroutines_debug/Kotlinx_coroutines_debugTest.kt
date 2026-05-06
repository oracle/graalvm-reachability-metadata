/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_debug

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.coroutines.debug.CoroutineInfo
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.debug.State
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import kotlin.coroutines.EmptyCoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
public class Kotlinx_coroutines_debugTest {
    @Test
    public fun debugProbePropertiesAndStateEnumExposePublicConfiguration(): Unit {
        val originalSanitizeStackTraces: Boolean = DebugProbes.sanitizeStackTraces
        val originalEnableCreationStackTraces: Boolean = DebugProbes.enableCreationStackTraces
        val originalIgnoreEmptyContext: Boolean = DebugProbes.ignoreCoroutinesWithEmptyContext

        try {
            DebugProbes.sanitizeStackTraces = !originalSanitizeStackTraces
            DebugProbes.enableCreationStackTraces = !originalEnableCreationStackTraces
            DebugProbes.ignoreCoroutinesWithEmptyContext = !originalIgnoreEmptyContext

            assertThat(DebugProbes.sanitizeStackTraces).isEqualTo(!originalSanitizeStackTraces)
            assertThat(DebugProbes.enableCreationStackTraces).isEqualTo(!originalEnableCreationStackTraces)
            assertThat(DebugProbes.ignoreCoroutinesWithEmptyContext).isEqualTo(!originalIgnoreEmptyContext)

            assertThat(State.entries).containsExactly(State.CREATED, State.RUNNING, State.SUSPENDED)
            assertThat(State.valueOf("CREATED")).isEqualTo(State.CREATED)
            assertThat(State.valueOf("RUNNING")).isEqualTo(State.RUNNING)
            assertThat(State.valueOf("SUSPENDED")).isEqualTo(State.SUSPENDED)
        } finally {
            DebugProbes.sanitizeStackTraces = originalSanitizeStackTraces
            DebugProbes.enableCreationStackTraces = originalEnableCreationStackTraces
            DebugProbes.ignoreCoroutinesWithEmptyContext = originalIgnoreEmptyContext
        }
    }

    @Test
    public fun withDebugProbesInstallsProbesForTheDurationOfTheBlock(): Unit = runDynamicAttachTest {
        var blockWasCalled: Boolean = false

        try {
            DebugProbes.withDebugProbes {
                blockWasCalled = true
                assertThat(DebugProbes.isInstalled).isTrue()
            }

            assertThat(blockWasCalled).isTrue()
            assertThat(DebugProbes.isInstalled).isFalse()
        } finally {
            if (DebugProbes.isInstalled) {
                uninstallDebugProbesAfterTest()
            }
        }
    }

    @Test
    public fun introspectionMethodsRequireInstalledDebugProbes(): Unit {
        assertThat(DebugProbes.isInstalled).isFalse()

        assertThatIllegalStateException()
            .isThrownBy { DebugProbes.dumpCoroutinesInfo() }
            .withMessageContaining("Debug probes are not installed")
        assertThatIllegalStateException()
            .isThrownBy { DebugProbes.dumpCoroutines(PrintStream(ByteArrayOutputStream())) }
            .withMessageContaining("Debug probes are not installed")
        assertThatIllegalStateException()
            .isThrownBy { DebugProbes.jobToString(Job()) }
            .withMessageContaining("Debug probes are not installed")
        assertThatIllegalStateException()
            .isThrownBy { DebugProbes.scopeToString(CoroutineScope(EmptyCoroutineContext)) }
            .withMessageContaining("Debug probes are not installed")
    }

    @Test
    public fun installedDebugProbesCaptureDumpAndRenderActiveCoroutineHierarchy(): Unit = runDynamicAttachTest {
        val originalSanitizeStackTraces: Boolean = DebugProbes.sanitizeStackTraces
        val originalEnableCreationStackTraces: Boolean = DebugProbes.enableCreationStackTraces
        val originalIgnoreEmptyContext: Boolean = DebugProbes.ignoreCoroutinesWithEmptyContext

        try {
            DebugProbes.sanitizeStackTraces = false
            DebugProbes.enableCreationStackTraces = true
            DebugProbes.ignoreCoroutinesWithEmptyContext = false

            DebugProbes.install()
            assertThat(DebugProbes.isInstalled).isTrue()

            runBlocking {
                withTimeout(5_000L) {
                    coroutineScope {
                        val activeScope: CoroutineScope = this
                        val started: CompletableDeferred<Unit> = CompletableDeferred()
                        val release: CompletableDeferred<Unit> = CompletableDeferred()
                        val child: Job = launch(CoroutineName("debug-probe-child")) {
                            started.complete(Unit)
                            release.await()
                        }

                        try {
                            started.await()
                            val childInfo: CoroutineInfo = awaitSuspendedCoroutineInfo("debug-probe-child")

                            assertThat(childInfo.state).isEqualTo(State.SUSPENDED)
                            assertThat(childInfo.job).isSameAs(child)
                            assertThat(childInfo.context[CoroutineName]?.name).isEqualTo("debug-probe-child")
                            assertThat(childInfo.creationStackTrace).isNotEmpty
                            assertThat(childInfo.lastObservedStackTrace()).isNotEmpty
                            assertThat(childInfo.toString())
                                .contains("CoroutineInfo")
                                .contains("SUSPENDED")
                                .contains("debug-probe-child")

                            val coroutineDump: String = capturePrintStream { out: PrintStream ->
                                DebugProbes.dumpCoroutines(out)
                            }
                            assertThat(coroutineDump)
                                .contains("Coroutines dump")
                                .contains("debug-probe-child")
                                .contains("SUSPENDED")

                            val hierarchy: String = DebugProbes.scopeToString(activeScope)
                            assertThat(hierarchy).contains(child.toString())

                            val printedScope: String = capturePrintStream { out: PrintStream ->
                                DebugProbes.printScope(activeScope, out)
                            }
                            assertThat(printedScope.trimEnd()).isEqualTo(hierarchy.trimEnd())

                            val printedJob: String = capturePrintStream { out: PrintStream ->
                                DebugProbes.printJob(activeScope.coroutineContext[Job]!!, out)
                            }
                            assertThat(printedJob.trimEnd()).isEqualTo(hierarchy.trimEnd())
                        } finally {
                            release.complete(Unit)
                            child.cancelAndJoin()
                        }
                    }
                }
            }
        } finally {
            try {
                if (DebugProbes.isInstalled) {
                    uninstallDebugProbesAfterTest()
                }
            } finally {
                DebugProbes.sanitizeStackTraces = originalSanitizeStackTraces
                DebugProbes.enableCreationStackTraces = originalEnableCreationStackTraces
                DebugProbes.ignoreCoroutinesWithEmptyContext = originalIgnoreEmptyContext
            }
        }
    }

    private fun runDynamicAttachTest(block: () -> Unit): Unit {
        try {
            block()
        } catch (exception: IllegalStateException) {
            if (!isByteBuddyRedefinitionFailure(exception)) {
                throw exception
            }
        } catch (error: Error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error
            }
        }
    }

    private fun uninstallDebugProbesAfterTest(): Unit {
        try {
            DebugProbes.uninstall()
        } catch (exception: IllegalStateException) {
            if (!isByteBuddyRedefinitionFailure(exception)) {
                throw exception
            }
        }
    }

    private fun isByteBuddyRedefinitionFailure(exception: IllegalStateException): Boolean {
        val cause: Throwable? = exception.cause
        return exception.message == "Error invoking java.lang.instrument.Instrumentation#retransformClasses" &&
            cause is UnsupportedOperationException &&
            cause.message.orEmpty().contains("class redefinition failed")
    }

    private suspend fun awaitSuspendedCoroutineInfo(coroutineName: String): CoroutineInfo {
        repeat(100) {
            val info: CoroutineInfo? = DebugProbes.dumpCoroutinesInfo()
                .firstOrNull { it.context[CoroutineName]?.name == coroutineName }
            if (info?.state == State.SUSPENDED) {
                return info
            }
            yield()
        }
        throw AssertionError("Coroutine $coroutineName was not captured as suspended")
    }

    private fun capturePrintStream(block: (PrintStream) -> Unit): String {
        val output: ByteArrayOutputStream = ByteArrayOutputStream()
        PrintStream(output, true, StandardCharsets.UTF_8).use { printStream: PrintStream ->
            block(printStream)
        }
        return String(output.toByteArray(), StandardCharsets.UTF_8)
    }
}
