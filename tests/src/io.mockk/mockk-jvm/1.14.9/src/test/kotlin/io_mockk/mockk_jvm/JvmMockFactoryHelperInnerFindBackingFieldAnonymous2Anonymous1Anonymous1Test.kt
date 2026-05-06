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
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Test

public class JvmMockFactoryHelperInnerFindBackingFieldAnonymous2Anonymous1Anonymous1Test {
    @Test
    fun answerCanReadThePropertyBackingFieldFromASpy(): Unit {
        try {
            val counter: BackingFieldCounter = spyk(BackingFieldCounter(41))

            every { counter.value } answers { fieldValue + 1 }

            assertThat(counter.value).isEqualTo(42)
        } catch (error: Error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error
            }
        }
    }
}

private class BackingFieldCounter(
    var value: Int,
)
