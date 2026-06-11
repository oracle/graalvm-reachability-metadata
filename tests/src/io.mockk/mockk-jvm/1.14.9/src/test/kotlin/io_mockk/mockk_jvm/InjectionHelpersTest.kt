/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_mockk.mockk_jvm

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Test

public class InjectionHelpersTest {
    @MockK
    public lateinit var collaborator: InjectionHelpersCollaborator

    @InjectMockKs(injectImmutable = true)
    public lateinit var subject: InjectionHelpersImmutableSubject

    @Test
    fun `annotation injection assigns mocks to immutable properties`(): Unit {
        runMockKAnnotationScenario {
            MockKAnnotations.init(this)

            every { collaborator.describe("metadata") } returns "covered metadata"

            assertThat(subject.describe("metadata")).isEqualTo("covered metadata")
            verify(exactly = 1) { collaborator.describe("metadata") }
        }
    }

    private fun runMockKAnnotationScenario(block: () -> Unit): Unit {
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

public interface InjectionHelpersCollaborator {
    public fun describe(value: String): String
}

public class InjectionHelpersImmutableSubject {
    private val collaborator: InjectionHelpersCollaborator? = null

    public fun describe(value: String): String = requireNotNull(collaborator).describe(value)
}
