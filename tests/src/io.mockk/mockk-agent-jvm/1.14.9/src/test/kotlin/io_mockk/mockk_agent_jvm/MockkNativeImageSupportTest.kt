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
    fun acceptsLegacyByteBuddyAttachmentFailureMessage(): Unit {
        assertRecognizedNativeImageFailure(
            IllegalStateException("Error during attachment using: attachment provider"),
        )
    }

    @Test
    fun acceptsCurrentByteBuddyAttachmentProviderFailureMessage(): Unit {
        assertRecognizedNativeImageFailure(
            IllegalStateException("No compatible attachment provider is available"),
        )
    }

    private fun assertRecognizedNativeImageFailure(throwable: Throwable): Unit {
        val propertyName: String = "org.graalvm.nativeimage.imagecode"
        val previousValue: String? = System.getProperty(propertyName)
        System.setProperty(propertyName, "runtime")

        try {
            assertThat(MockkNativeImageSupport.isExpectedNativeImageFailure(throwable)).isTrue()
        } finally {
            if (previousValue == null) {
                System.clearProperty(propertyName)
            } else {
                System.setProperty(propertyName, previousValue)
            }
        }
    }
}
