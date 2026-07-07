/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_mockk.mockk_jvm

import io.mockk.impl.annotations.InjectionLookupType
import io.mockk.impl.annotations.MockInjector
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class InjectionHelpersTest {
    @Test
    fun injectsImmutablePropertyByUsingMockInjector(): Unit {
        val collaborator: Collaborator = Collaborator("configured dependency")
        val holder: DependencyHolder = DependencyHolder(collaborator)
        val subject: SubjectWithImmutableDependency = SubjectWithImmutableDependency()
        val injector: MockInjector = MockInjector(
            holder,
            InjectionLookupType.BOTH,
            injectImmutable = true,
            overrideValues = false,
        )

        assertThat(subject.collaborator).isNull()

        injector.propertiesInjection(subject)

        assertThat(subject.collaborator).isSameAs(collaborator)
        assertThat(subject.collaborator?.name).isEqualTo("configured dependency")
    }

    private class DependencyHolder(
        val collaborator: Collaborator,
    )

    private class SubjectWithImmutableDependency {
        val collaborator: Collaborator? = null
    }

    private data class Collaborator(
        val name: String,
    )
}
