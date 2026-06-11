/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_mockk.mockk_jvm

import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Test

public class JvmAnyValueGeneratorInnerAnyValueAnonymous2Test {
    @Test
    fun `relaxed mock returns an empty reference array default value`(): Unit {
        runMockKScenario {
            val service: JvmAnyValueGeneratorArrayService = mockk(relaxed = true)

            val generatedValues: Array<String> = service.values()

            assertThat(generatedValues).isEmpty()
            verify(exactly = 1) { service.values() }
            clearAllMocks()
        }
    }

    private fun runMockKScenario(block: () -> Unit): Unit {
        try {
            block()
        } catch (throwable: Throwable) {
            if (!isExpectedNativeImageAgentFailure(throwable)) {
                throw throwable
            }
        }
    }

    private fun isExpectedNativeImageAgentFailure(throwable: Throwable): Boolean =
        throwable.causeSequence().any { current: Throwable ->
            current is Error && NativeImageSupport.isUnsupportedFeatureError(current)
        } || throwable.causeSequence().any { current: Throwable ->
            current is IllegalStateException &&
                (current.message?.startsWith("Error during attachment using:") == true ||
                    current.message == "No compatible attachment provider is available")
        }

    private fun Throwable.causeSequence(): Sequence<Throwable> =
        generateSequence(this) { current: Throwable -> current.cause }
}

public interface JvmAnyValueGeneratorArrayService {
    public fun values(): Array<String>
}
