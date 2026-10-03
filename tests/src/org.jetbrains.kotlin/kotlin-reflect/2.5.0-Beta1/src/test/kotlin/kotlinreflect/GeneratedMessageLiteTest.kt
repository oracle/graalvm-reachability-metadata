package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class GeneratedMessageLiteTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val constructor = RichReflectionFixture::class.primaryConstructor!!
        assertThat(constructor.parameters.single().isOptional).isTrue()
        assertThat(constructor.returnType.toString()).contains("RichReflectionFixture")
    }
}
