/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_mockk.mockk_jvm

import io.mockk.impl.instantiation.JvmAnyValueGenerator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

public class JvmAnyValueGeneratorInnerAnyValueAnonymous2Test {
    @Test
    fun createsEmptyArrayValueForObjectArrayClass(): Unit {
        val generator: JvmAnyValueGenerator = JvmAnyValueGenerator(Unit)
        var fallbackCalled: Boolean = false

        val arrayClass: KClass<*> = emptyArray<String>()::class
        val generatedValue: Any? = generator.anyValue(arrayClass, isNullable = false) {
            fallbackCalled = true
            "fallback"
        }

        val generatedArray: Array<*> = generatedValue as Array<*>
        assertThat(generatedArray).isEmpty()
        assertThat(generatedArray.javaClass.componentType).isEqualTo(String::class.java)
        assertThat(fallbackCalled).isFalse()
    }
}
