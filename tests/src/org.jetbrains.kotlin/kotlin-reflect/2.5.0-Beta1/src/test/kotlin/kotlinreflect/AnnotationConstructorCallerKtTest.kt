package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class AnnotationConstructorCallerKtTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val constructor = ReflectDetails::class.constructors.single()
        val nameParameter = constructor.parameters.single { it.name == "name" }
        val annotation = constructor.callBy(mapOf(nameParameter to "created"))

        assertThat(annotation.name).isEqualTo("created")
        assertThat(annotation.values).isEmpty()
    }
}
