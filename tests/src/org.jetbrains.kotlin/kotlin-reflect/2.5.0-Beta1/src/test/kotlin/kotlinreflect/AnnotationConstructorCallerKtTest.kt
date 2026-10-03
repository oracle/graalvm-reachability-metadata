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
        val arguments = constructor.parameters.associateWith { parameter ->
            when (parameter.name) { "name" -> "created"; "values" -> arrayOf("x", "y"); else -> error(parameter.name ?: "parameter") }
        }
        val annotation = constructor.callBy(arguments)
        assertThat(annotation.name).isEqualTo("created")
        assertThat(annotation.values).containsExactly("x", "y")
    }
}
