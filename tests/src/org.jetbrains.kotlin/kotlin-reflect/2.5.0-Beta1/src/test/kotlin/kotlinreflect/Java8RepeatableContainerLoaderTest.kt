package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class Java8RepeatableContainerLoaderTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val tags = ReflectJavaFixtures.JavaBean::class.findAnnotations<ReflectJavaFixtures.JavaTag>()
        assertThat(tags.map { it.value }).containsExactly("alpha", "beta")
    }
}
