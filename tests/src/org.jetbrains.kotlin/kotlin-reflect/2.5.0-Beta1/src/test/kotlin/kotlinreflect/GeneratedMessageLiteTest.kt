package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor

class GeneratedMessageLiteTest {
    @Test
    fun readsKotlinMetadataThroughPublicReflectionApi() {
        val type = RichReflectionFixture::class
        val constructor = type.primaryConstructor!!
        val composed = type.members.single { it.name == "composedAnnotation" }
        val composition = composed.findAnnotation<ReflectComposition>()!!

        assertThat(constructor.parameters.single().isOptional).isTrue()
        assertThat(constructor.callBy(emptyMap()).prefix).isEqualTo("hello")
        assertThat(constructor.returnType.toString()).contains("RichReflectionFixture")
        assertThat(composition.parts.map { it.value }).containsExactly("left", "right")
    }
}
