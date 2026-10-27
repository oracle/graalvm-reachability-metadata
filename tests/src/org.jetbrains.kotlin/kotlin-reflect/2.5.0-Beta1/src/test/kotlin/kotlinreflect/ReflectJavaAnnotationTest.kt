package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class ReflectJavaAnnotationTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val details = ReflectJavaFixtures.JavaBean::class.annotations
            .filterIsInstance<ReflectJavaFixtures.JavaDetails>().single()
        assertThat(details.name).isEqualTo("bean")
        assertThat(details.count).isEqualTo(2)
    }
}
