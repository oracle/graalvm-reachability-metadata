package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class CallerImplInnerFieldSetterInnerBoundJvmStaticInObjectTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val property = ReflectionStatics::globalValue
        property.setter.call("static-change")
        assertThat(property.getter.call()).isEqualTo("static-change")
    }
}
