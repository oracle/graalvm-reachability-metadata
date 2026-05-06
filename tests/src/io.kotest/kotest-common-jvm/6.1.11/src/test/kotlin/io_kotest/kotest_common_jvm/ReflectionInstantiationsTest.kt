/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kotest.kotest_common_jvm

import io.kotest.common.reflection.ReflectionInstantiations
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class ReflectionInstantiationsTest {
    @Test
    fun `instantiates class with public no-argument constructor`(): Unit {
        val instance: PublicNoArgConstructorClass =
            ReflectionInstantiations.newInstanceNoArgConstructorOrObjectInstance(PublicNoArgConstructorClass::class)

        assertThat(instance.value).isEqualTo("created")
    }

    public class PublicNoArgConstructorClass {
        val value: String = "created"
    }
}
