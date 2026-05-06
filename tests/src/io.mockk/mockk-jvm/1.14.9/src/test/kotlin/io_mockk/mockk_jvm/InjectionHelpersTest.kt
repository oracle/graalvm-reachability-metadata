/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_mockk.mockk_jvm

import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.InjectMockKs
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class InjectionHelpersTest {
    @Test
    fun initInjectsMatchingDependencyIntoImmutableProperty(): Unit {
        val fixture = ImmutableInjectionFixture()

        MockKAnnotations.init(fixture)

        assertThat(fixture.subject.description()).isEqualTo("injected-service")
    }
}

private class ImmutableInjectionFixture {
    val dependency: ImmutableDependency = ImmutableDependency("injected-service")

    @InjectMockKs(injectImmutable = true)
    lateinit var subject: ImmutableInjectionTarget
}

public class ImmutableInjectionTarget {
    private val dependency: ImmutableDependency? = null

    fun description(): String = checkNotNull(dependency).name
}

private class ImmutableDependency(val name: String)
