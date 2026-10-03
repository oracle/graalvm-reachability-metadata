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
        property.set("static-change")
        assertThat(property.get()).isEqualTo("static-change")
    }
}
