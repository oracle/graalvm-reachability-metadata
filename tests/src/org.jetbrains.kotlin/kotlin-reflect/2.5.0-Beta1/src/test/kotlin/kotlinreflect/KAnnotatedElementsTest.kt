package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class KAnnotatedElementsTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        assertThat(RichReflectionFixture::class.findAnnotations<ReflectTag>().map { it.value })
            .containsExactly("first", "second")
    }
}
