package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class GeneratedMessageLiteTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val type = RichReflectionFixture::class
        val members = type.members.associateBy { it.name }
        val constructor = type.primaryConstructor!!
        val composed = members.getValue("composedAnnotation")
        assertThat(constructor.parameters.single().isOptional).isTrue()
        assertThat(constructor.returnType.toString()).contains("RichReflectionFixture")
        assertThat(composed.annotations.filterIsInstance<ReflectComposition>().single().parts.map { it.value })
            .containsExactly("left", "right")
    }
}
