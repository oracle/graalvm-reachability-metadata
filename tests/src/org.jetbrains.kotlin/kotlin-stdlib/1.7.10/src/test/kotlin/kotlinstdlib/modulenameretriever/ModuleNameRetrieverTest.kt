/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package kotlinstdlib.modulenameretriever

import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.createCoroutine
import kotlin.coroutines.jvm.internal.CoroutineStackFrame
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val MODULE_NAME: String = "kotlinstdlib.modulenameretrievertest"

public class ModuleNameRetrieverTest {
    @Test
    public fun generatedCoroutineInNamedModuleProvidesADebugStackTraceElement(): Unit {
        val block: suspend () -> Unit = {
            suspendForModuleNameInspection()
        }
        val continuation: Continuation<Unit> = block.createCoroutine(ModuleNameCompletion())
        val outerFrame: CoroutineStackFrame = continuation as CoroutineStackFrame
        val generatedFrame: CoroutineStackFrame = outerFrame.callerFrame ?: outerFrame

        continuation.resumeWith(Result.success(Unit))
        val stackTraceElement: StackTraceElement = assertNotNull(generatedFrame.getStackTraceElement())

        assertEquals(MODULE_NAME, stackTraceElement.className.substringBefore('/'))
        assertTrue(
            stackTraceElement.className.substringAfter('/')
                .startsWith("kotlinstdlib.modulenameretriever.ModuleNameRetrieverTest")
        )
        assertEquals("ModuleNameRetrieverTest.kt", stackTraceElement.fileName)
        assertTrue(stackTraceElement.lineNumber > 0)
        assertTrue(generatedFrame.toString().contains("ModuleNameRetrieverTest.kt"))
    }
}

private suspend fun suspendForModuleNameInspection(): Unit =
    suspendCoroutine { _: Continuation<Unit> ->
        // Keep the generated continuation suspended for stack-frame inspection.
    }

private class ModuleNameCompletion : Continuation<Unit> {
    override val context: CoroutineContext = EmptyCoroutineContext

    override fun resumeWith(result: Result<Unit>): Unit {
        result.getOrThrow()
    }
}
