package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class JavaAnnotationConstructorTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val constructor = ReflectJavaFixtures.JavaDetails::class.constructors.single()
        val annotation = constructor.callBy(constructor.parameters.associateWith {
            if (it.name == "name") "dynamic" else 4
        })
        assertThat(annotation.name).isEqualTo("dynamic")
        assertThat(annotation.count).isEqualTo(4)
    }
}
