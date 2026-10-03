package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class ReflectClassStructureTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val type = RichReflectionFixture::class
        assertThat(type.constructors.flatMap { it.annotations }).isEmpty()
        assertThat(type.declaredMemberFunctions.map { it.name }).contains("greet")
        assertThat(type.declaredMemberProperties.map { it.name }).contains("fieldValue")
    }
}
