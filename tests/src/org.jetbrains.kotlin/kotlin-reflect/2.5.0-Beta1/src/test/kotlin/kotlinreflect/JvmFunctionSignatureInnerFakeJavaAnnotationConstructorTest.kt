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
        assertThat(constructor.toString()).contains("JavaDetails")
    }
}
