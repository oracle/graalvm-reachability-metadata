/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_mockk.mockk_dsl_jvm

import io.mockk.JvmCoroutineCall
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class JvmCoroutineCallTest {
    @Test
    public fun callWithContinuationInvokesCoroutineMethod(): Unit {
        val call: JvmCoroutineCall<String> = JvmCoroutineCall { "completed" }
        val continuation: Continuation<String> = Continuation(EmptyCoroutineContext) { result: Result<String> ->
            result.getOrThrow()
        }

        val result: String = call.callWithContinuation(continuation)

        assertThat(result).isEqualTo("completed")
    }
}
