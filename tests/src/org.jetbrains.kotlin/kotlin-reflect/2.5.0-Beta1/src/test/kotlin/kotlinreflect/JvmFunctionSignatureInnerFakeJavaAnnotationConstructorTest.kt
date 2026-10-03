package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class JvmFunctionSignatureInnerFakeJavaAnnotationConstructorTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val constructor = ReflectJavaFixtures.JavaDetails::class.constructors.single()
        assertThat(constructor.parameters.map { it.name }).containsExactlyInAnyOrder("name", "count")
        val arguments = constructor.parameters.associateWith { parameter ->
            if (parameter.name == "name") "signature" else 7
        }
        val annotation = constructor.callBy(arguments)
        assertThat(annotation.name).isEqualTo("signature")
        assertThat(annotation.count).isEqualTo(7)
        assertThat(constructor.toString()).contains("JavaDetails")
    }
}
