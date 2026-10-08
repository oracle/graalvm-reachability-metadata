package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class InternalUnderlyingValOfInlineClassTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val value = ReflectionValue("underlying")
        val property = ReflectionValue::class.memberProperties.single { it.name == "text" }
        assertThat(property.get(value)).isEqualTo("underlying")
        assertThat(ReflectionValue::text.get(value)).isEqualTo("underlying")
        assertThat(value::text.get()).isEqualTo("underlying")

        val internalValue = InternalReflectionValue("value")
        val internalProperty = InternalReflectionValue::class.memberProperties
            .single { it.name == "content" }
        assertThat(internalProperty.get(internalValue)).isEqualTo("value")
        assertThat(internalValue::content.get()).isEqualTo("value")
        assertThat(internalValue.render()).isEqualTo("internal:value")
    }
}
