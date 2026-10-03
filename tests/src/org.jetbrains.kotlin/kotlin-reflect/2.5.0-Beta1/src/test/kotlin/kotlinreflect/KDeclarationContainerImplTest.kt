package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class KDeclarationContainerImplTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val constructor = RichReflectionFixture::class.primaryConstructor!!
        val fixture = constructor.callBy(emptyMap())
        val greet = RichReflectionFixture::class.declaredMemberFunctions.single { it.name == "greet" }
        assertThat(greet.callBy(mapOf(greet.parameters[0] to fixture))).isEqualTo("hello, world")
    }
}
