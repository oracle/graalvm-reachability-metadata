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
    fun autoHintLoadsClassFromClassCastExceptionAndHintsNextRound(): Unit {
        val recorder: AutoHintRecorder = AutoHintRecorder()
        val hinter: JvmAutoHinter = JvmAutoHinter()
        var attempts: Int = 0
        val hintedClassName: String = AutoHintedReturn::class.java.name

        hinter.autoHint(recorder, i = 1, n = 2) {
            attempts += 1
            if (attempts == 1) {
                throw ClassCastException("class java.lang.Object cannot be cast to class $hintedClassName")
            }
        }

        assertThat(attempts).isEqualTo(2)
        assertThat(recorder.rounds).containsExactly(1 to 2, 1 to 2)
        assertThat(recorder.discardedRounds).isEqualTo(1)
        assertThat(recorder.hints).containsExactly(AutoHint(AutoHintedReturn::class, 1))
    }
}

private data class AutoHint(
    val type: KClass<*>,
    val callNumber: Int,
)

private class AutoHintedReturn

private class AutoHintRecorder : MockKGateway.CallRecorder {
    val rounds: MutableList<Pair<Int, Int>> = mutableListOf()
    val hints: MutableList<AutoHint> = mutableListOf()
    var discardedRounds: Int = 0

    override val calls: MutableList<RecordedCall> = mutableListOf()

    override fun round(n: Int, total: Int): Unit {
        rounds += n to total
    }

    override fun nCalls(): Int = 1

    override fun hintNextReturnType(cls: KClass<*>, n: Int): Unit {
        hints += AutoHint(cls, n)
    }

    override fun discardLastCallRound(): Unit {
        discardedRounds += 1
    }

    override fun startStubbing(): Unit = unsupported()

    override fun startVerification(params: MockKGateway.VerificationParameters): Unit = unsupported()

    override fun startExclusion(params: MockKGateway.ExclusionParameters): Unit = unsupported()

    override fun <T : Any> matcher(matcher: Matcher<*>, cls: KClass<T>): T = unsupported()

    override fun call(invocation: Invocation): Any? = unsupported()

    override fun answerOpportunity(): MockKGateway.AnswerOpportunity<*> = unsupported()

    override fun done(): Unit = unsupported()

    override fun reset(): Unit = unsupported()

    override fun estimateCallRounds(): Int = unsupported()

    override fun wasNotCalled(list: List<Any>): Unit = unsupported()

    override fun isLastCallReturnsNothing(): Boolean = unsupported()

    private fun unsupported(): Nothing = error("Unexpected CallRecorder interaction")
}
