package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class KotlinKPropertyTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val property = RichReflectionFixture::fieldValue
        assertThat(property.annotations.filterIsInstance<ReflectTag>().map { it.value })
            .containsExactly("property")
        assertThat(property.javaField?.name).isEqualTo("fieldValue")

        val interfaceProperty = AnnotatedReflectionInterface::class.declaredMemberProperties
            .single { it.name == "interfaceValue" }
        assertThat(interfaceProperty.annotations.filterIsInstance<ReflectTag>().map { it.value })
            .containsExactly("interface-property")
    }
}
