package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class InternalUnderlyingValOfInlineClassTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val property = ReflectionValue::class.memberProperties.single { it.name == "text" }
        assertThat(property.get(ReflectionValue("underlying"))).isEqualTo("underlying")
    }
}
