package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class CallerImplInnerFieldSetterTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val fixture = RichReflectionFixture()
        val property = RichReflectionFixture::class.memberProperties.single { it.name == "fieldValue" }
            as KMutableProperty1<RichReflectionFixture, String>
        property.set(fixture, "changed")
        assertThat(fixture.fieldValue).isEqualTo("changed")
    }
}
