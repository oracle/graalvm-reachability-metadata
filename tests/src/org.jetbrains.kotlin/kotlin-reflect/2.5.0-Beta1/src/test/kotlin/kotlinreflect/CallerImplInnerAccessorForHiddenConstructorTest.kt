package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class CallerImplInnerAccessorForHiddenConstructorTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val constructor = ValueClassHolder::class.constructors.single()
        assertThat(constructor.call(ReflectionValue("hidden")).value.text).isEqualTo("hidden")
    }
}
