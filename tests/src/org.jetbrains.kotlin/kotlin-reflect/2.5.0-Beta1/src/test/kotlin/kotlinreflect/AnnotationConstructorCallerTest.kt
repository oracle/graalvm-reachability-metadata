package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class AnnotationConstructorCallerTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val constructor = ReflectTag::class.constructors.single()
        val tag = constructor.call("runtime")
        assertThat(tag.value).isEqualTo("runtime")
        assertThat(tag.annotationClass).isEqualTo(ReflectTag::class)
    }
}
