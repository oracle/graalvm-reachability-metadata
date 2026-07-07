/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_mockk.mockk_jvm

import io.mockk.Invocation
import io.mockk.Matcher
import io.mockk.MockKGateway
import io.mockk.RecordedCall
import io.mockk.impl.recording.JvmAutoHinter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

public class JvmAutoHinterTest {
    @Test
    fun retriesRecordingAfterInferringHintFromClassCastException(): Unit {
        val recorder: CapturingCallRecorder = CapturingCallRecorder()
        var attempts: Int = 0

        JvmAutoHinter().autoHint(recorder, ROUND_INDEX, ROUND_COUNT) {
            attempts += 1
            if (attempts == 1) {
                recorder.recordedCallsInCurrentRound = 1
                throw ClassCastException(STRING_CAST_EXCEPTION_MESSAGE)
            }
        }

        assertThat(attempts).isEqualTo(2)
        assertThat(recorder.discardedRounds).isEqualTo(1)
        assertThat(recorder.rounds).containsExactly(
            RecordingRound(ROUND_INDEX, ROUND_COUNT),
            RecordingRound(ROUND_INDEX, ROUND_COUNT),
        )
        assertThat(recorder.returnTypeHints).containsExactly(ReturnTypeHint(String::class, 1))
    }

    private data class RecordingRound(
        val index: Int,
        val count: Int,
    )

    private data class ReturnTypeHint(
        val type: KClass<*>,
        val callIndex: Int,
    )

    private class CapturingCallRecorder : MockKGateway.CallRecorder {
        override val calls: List<RecordedCall> = emptyList()
        val rounds: MutableList<RecordingRound> = mutableListOf()
        val returnTypeHints: MutableList<ReturnTypeHint> = mutableListOf()
        var recordedCallsInCurrentRound: Int = 0
        var discardedRounds: Int = 0

        override fun round(
            n: Int,
            total: Int,
        ): Unit {
            rounds += RecordingRound(n, total)
        }

        override fun nCalls(): Int = recordedCallsInCurrentRound

        override fun hintNextReturnType(
            cls: KClass<*>,
            n: Int,
        ): Unit {
            returnTypeHints += ReturnTypeHint(cls, n)
        }

        override fun discardLastCallRound(): Unit {
            discardedRounds += 1
        }

        override fun startStubbing(): Unit = unused()

        override fun startVerification(params: MockKGateway.VerificationParameters): Unit = unused()

        override fun startExclusion(params: MockKGateway.ExclusionParameters): Unit = unused()

        override fun <T : Any> matcher(
            matcher: Matcher<*>,
            cls: KClass<T>,
        ): T = unused()

        override fun call(invocation: Invocation): Any? = unused()

        override fun answerOpportunity(): MockKGateway.AnswerOpportunity<*> = unused()

        override fun done(): Unit = unused()

        override fun reset(): Unit = unused()

        override fun estimateCallRounds(): Int = unused()

        override fun wasNotCalled(list: List<Any>): Unit = unused()

        override fun isLastCallReturnsNothing(): Boolean = false

        private fun unused(): Nothing = error("This call recorder method is not used by the auto-hinting scenario")
    }

    private companion object {
        private const val ROUND_INDEX: Int = 0
        private const val ROUND_COUNT: Int = 64
        private const val STRING_CAST_EXCEPTION_MESSAGE: String =
            "class java.lang.Object cannot be cast to class java.lang.String " +
                "(java.lang.Object is in module java.base of loader 'bootstrap'; " +
                "java.lang.String is in module java.base of loader 'bootstrap')"
    }
}
