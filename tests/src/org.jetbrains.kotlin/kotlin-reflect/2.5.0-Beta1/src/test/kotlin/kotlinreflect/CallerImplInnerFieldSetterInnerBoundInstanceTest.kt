package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class CallerImplInnerFieldSetterInnerBoundInstanceTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val fixture = RichReflectionFixture()
        val property = fixture::fieldValue
        property.set("bound-change")
        assertThat(property.get()).isEqualTo("bound-change")
    }
}
