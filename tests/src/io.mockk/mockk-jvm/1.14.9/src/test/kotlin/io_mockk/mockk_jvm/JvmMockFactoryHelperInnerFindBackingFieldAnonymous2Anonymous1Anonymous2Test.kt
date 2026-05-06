/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_mockk.mockk_jvm

import io.mockk.every
import io.mockk.spyk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class JvmMockFactoryHelperInnerFindBackingFieldAnonymous2Anonymous1Anonymous2Test {
    @Test
    fun answerCanWriteThePropertyBackingFieldFromASpy(): Unit {
        try {
            val counter: MutableBackingFieldCounter = spyk(MutableBackingFieldCounter(0))

            every { counter.value = any() } propertyType Int::class answers {
                fieldValue = 42
            }

            counter.value = 1

            assertThat(counter.value).isEqualTo(42)
        } catch (throwable: Throwable) {
            if (!isUnsupportedMockkNativeImageFailure(throwable)) {
                throw throwable
            }
        }
    }
}

private class MutableBackingFieldCounter(
    var value: Int,
)
