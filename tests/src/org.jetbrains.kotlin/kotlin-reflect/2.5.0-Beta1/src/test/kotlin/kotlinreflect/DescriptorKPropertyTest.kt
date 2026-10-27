package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class DescriptorKPropertyTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val property = RichReflectionFixture::class.declaredMemberProperties.single { it.name == "fieldValue" }
        assertThat(property.javaField?.name).isEqualTo("fieldValue")
    }
}
