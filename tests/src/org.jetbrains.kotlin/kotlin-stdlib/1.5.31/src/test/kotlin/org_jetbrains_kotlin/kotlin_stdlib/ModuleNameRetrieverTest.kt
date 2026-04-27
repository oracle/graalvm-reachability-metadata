/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_stdlib

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.startCoroutine

public class ModuleNameRetrieverTest {
    @Test
    public fun suspendedCoroutineResumesWithCapturedContinuation() {
        val captured = CapturedSuspension()

        suspend fun suspendAndCaptureFrame(): String {
            val value: String = suspendCoroutineUninterceptedOrReturn { continuation ->
                captured.continuation = continuation
                COROUTINE_SUSPENDED
            }
            return "resumed $value"
        }

        ::suspendAndCaptureFrame.startCoroutine(captured.completion)

        captured.continuation.resumeWith(Result.success("coroutine"))

        assertThat(captured.failure).isNull()
        assertThat(captured.result).isEqualTo("resumed coroutine")
    }

    private class CapturedSuspension {
        lateinit var continuation: Continuation<String>
        var result: String? = null
        var failure: Throwable? = null

        val completion: Continuation<String> = object : Continuation<String> {
            override val context: CoroutineContext = EmptyCoroutineContext

            override fun resumeWith(result: Result<String>) {
                result.fold(
                    onSuccess = { value -> this@CapturedSuspension.result = value },
                    onFailure = { exception -> failure = exception }
                )
            }
        }
    }
}
