package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.jvm.isAccessible

object BoundJvmStaticFieldFixture {
    @JvmStatic
    private var value: String = "initial"

    fun mutableProperty(): KMutableProperty0<String> = this::value

    fun currentValue(): String = value
}

class CallerImplInnerFieldSetterInnerBoundJvmStaticInObjectTest {
    @Test
    fun setsPrivateJvmStaticFieldThroughBoundProperty() {
        val property: KMutableProperty0<String> = BoundJvmStaticFieldFixture.mutableProperty()
        property.isAccessible = true

        property.setter.call("updated")

        assertThat(BoundJvmStaticFieldFixture.currentValue()).isEqualTo("updated")
    }
}
