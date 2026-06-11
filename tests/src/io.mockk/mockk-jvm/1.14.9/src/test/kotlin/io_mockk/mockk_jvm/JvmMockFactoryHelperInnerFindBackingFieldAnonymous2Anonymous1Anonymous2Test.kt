/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_mockk.mockk_jvm

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Test

public class JvmMockFactoryHelperInnerFindBackingFieldAnonymous2Anonymous1Anonymous2Test {
    @Test
    fun `answer can update a spied property backing field`(): Unit {
        runMockKBackingFieldScenario {
            val subject: JvmMockFactoryHelperSetBackingFieldSubject =
                spyk(JvmMockFactoryHelperSetBackingFieldSubject("initial"))

            try {
                every { subject.label = any() } propertyType String::class answers { fieldValue = value }

                subject.label = "changed"

                assertThat(subject.label).isEqualTo("changed")
                verify(exactly = 1) { subject.label = "changed" }
            } finally {
                clearAllMocks()
            }
        }
    }

    private fun runMockKBackingFieldScenario(block: () -> Unit): Unit {
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

public class JvmMockFactoryHelperSetBackingFieldSubject(initialLabel: String) {
    public var label: String = initialLabel
}
