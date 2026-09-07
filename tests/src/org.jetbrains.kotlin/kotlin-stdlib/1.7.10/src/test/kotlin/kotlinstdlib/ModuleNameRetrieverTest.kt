/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package kotlinstdlib

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

public class ModuleNameRetrieverTest {
    @Test
    public fun generatedCoroutineProvidesADebugStackTraceElement(): Unit {
        val block: suspend () -> Unit = {
            suspendForModuleNameInspection()
        }
        val continuation: Continuation<Unit> = block.createCoroutine(ModuleNameCompletion())
        val frame: CoroutineStackFrame = continuation as CoroutineStackFrame

        continuation.resumeWith(Result.success(Unit))
        val stackTraceElement: StackTraceElement = assertNotNull(frame.getStackTraceElement())

        assertEquals("ModuleNameRetrieverTest.kt", stackTraceElement.fileName)
        assertTrue(stackTraceElement.lineNumber > 0)
        assertTrue(frame.toString().contains("ModuleNameRetrieverTest.kt"))
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
