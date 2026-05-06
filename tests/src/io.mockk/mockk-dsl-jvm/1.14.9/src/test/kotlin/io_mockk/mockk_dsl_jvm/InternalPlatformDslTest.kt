/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_mockk.mockk_dsl_jvm

import io.mockk.InternalPlatformDsl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class InternalPlatformDslTest {
    @Test
    public fun classForNameReturnsKotlinClassForNamedJavaClass(): Unit {
        val loadedClass: Any = InternalPlatformDsl.classForName("java.lang.String")

        assertThat(loadedClass).isEqualTo(String::class)
    }

    @Test
    public fun dynamicSetFieldUpdatesPrivateBackingField(): Unit {
        val target: InternalPlatformDslFieldHolder = InternalPlatformDslFieldHolder("before")

        InternalPlatformDsl.dynamicSetField(target, "secret", "after")

        assertThat(target.currentSecret()).isEqualTo("after")
    }
}

public class InternalPlatformDslFieldHolder(initialSecret: String) {
    private var secret: String = initialSecret

    public fun currentSecret(): String = secret
}
