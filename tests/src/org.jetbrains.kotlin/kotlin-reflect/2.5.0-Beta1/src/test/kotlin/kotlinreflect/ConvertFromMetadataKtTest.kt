package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class ConvertFromMetadataKtTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val function = RichReflectionFixture::class.declaredMemberFunctions
            .single { it.name == "composedAnnotation" }
        val composition = function.findAnnotation<ReflectComposition>()!!

        assertThat(function.call(RichReflectionFixture())).isEqualTo("composed")
        assertThat(composition.parts.map { it.value }).containsExactly("left", "right")

        val property = RichReflectionFixture::class.declaredMemberProperties
            .single { it.name == "fieldValue" }
        val propertyComposition = property.findAnnotation<ReflectComposition>()!!
        assertThat(propertyComposition.parts.map { it.value })
            .containsExactly("property-left", "property-right")
    }
}
