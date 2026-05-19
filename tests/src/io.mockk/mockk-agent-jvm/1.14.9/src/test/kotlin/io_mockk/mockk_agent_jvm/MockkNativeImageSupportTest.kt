/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_mockk.mockk_agent_jvm

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class MockkNativeImageSupportTest {
    @Test
    fun treatsByteBuddyAttachmentProviderFailureAsExpectedInNativeImageRuntime(): Unit {
        withNativeImageRuntimeProperty {
            val throwable: Throwable = IllegalStateException("No compatible attachment provider is available")

            assertThat(MockkNativeImageSupport.isExpectedNativeImageFailure(throwable)).isTrue()
        }
    }

    private fun withNativeImageRuntimeProperty(block: () -> Unit): Unit {
        val key: String = "org.graalvm.nativeimage.imagecode"
        val previousValue: String? = System.getProperty(key)
        System.setProperty(key, "runtime")

        try {
            block()
        } finally {
            if (previousValue == null) {
                System.clearProperty(key)
            } else {
                System.setProperty(key, previousValue)
            }
        }
    }
}
