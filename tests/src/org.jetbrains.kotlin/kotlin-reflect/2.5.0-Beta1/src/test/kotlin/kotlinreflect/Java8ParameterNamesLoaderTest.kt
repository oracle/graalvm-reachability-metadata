package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class Java8ParameterNamesLoaderTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val constructor = ReflectJavaFixtures.JavaBean::class.constructors.maxBy { it.parameters.size }
        assertThat(constructor.parameters).hasSize(1)
        assertThat(constructor.call("named").name).isEqualTo("named")
    }
}
