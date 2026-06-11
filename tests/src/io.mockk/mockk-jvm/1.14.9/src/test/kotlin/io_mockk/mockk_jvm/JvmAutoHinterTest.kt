/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_mockk.mockk_jvm

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class JvmAutoHinterTest {
    @Test
    fun `records a chained generic call by auto hinting the erased return type`(): Unit {
        runAutoHintScenario {
            val root: JvmAutoHinterGenericRoot<JvmAutoHinterGenericChild> = mockk()

            every { root.next().value() } returns "auto-hinted"

            assertThat(root.next().value()).isEqualTo("auto-hinted")
            clearAllMocks()
        }
    }

    private fun runAutoHintScenario(block: () -> Unit): Unit {
        try {
            block()
        } catch (throwable: Throwable) {
            if (!MockkNativeImageSupport.isExpectedNativeImageFailure(throwable)) {
                throw throwable
            }
        }
    }
}

public interface JvmAutoHinterGenericRoot<T : Any> {
    public fun next(): T
}

public interface JvmAutoHinterGenericChild {
    public fun value(): String
}
