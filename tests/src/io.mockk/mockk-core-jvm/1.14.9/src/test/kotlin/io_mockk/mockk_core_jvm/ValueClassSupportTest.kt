/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_mockk.mockk_core_jvm

import java.lang.reflect.Method
import io.mockk.core.ValueClassSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class ValueClassSupportTest {
    @Test
    public fun maybeUnboxValueForMethodReturnUsesUnboxMethodForGenericReturnTypes(): Unit {
        val genericReturnMethod: Method = ValueClassSupportGenericReturnApi::class.java.getDeclaredMethod("genericValue")
        val value: ValueClassSupportNullableValue = ValueClassSupportNullableValue(null)

        val result: Any? = with(ValueClassSupport) {
            value.maybeUnboxValueForMethodReturn(genericReturnMethod)
        }

        assertThat(result).isNull()
    }
}

public class ValueClassSupportGenericReturnApi {
    @Suppress("unused")
    public fun <T> genericValue(): T {
        throw UnsupportedOperationException("The method is inspected by ValueClassSupport but never invoked")
    }
}

@JvmInline
public value class ValueClassSupportNullableValue(public val value: String?)
