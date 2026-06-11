/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_mockk.mockk_jvm

import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Test

public class JvmMockKGatewayTest {
    @Test
    fun `mocking an interface initializes the JVM gateway and records calls`(): Unit {
        runGatewayScenario {
            val service: JvmMockKGatewayGreetingService = mockk()

            every { service.greeting("MockK") } returns "hello MockK"

            assertThat(service.greeting("MockK")).isEqualTo("hello MockK")
            verify(exactly = 1) { service.greeting("MockK") }
            confirmVerified(service)
            clearAllMocks()
        }
    }

    private fun runGatewayScenario(block: () -> Unit): Unit {
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

public interface JvmMockKGatewayGreetingService {
    public fun greeting(name: String): String
}
