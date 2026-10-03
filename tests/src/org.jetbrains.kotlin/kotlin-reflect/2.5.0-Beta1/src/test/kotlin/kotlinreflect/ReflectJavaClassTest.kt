package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class ReflectJavaClassTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val type = ReflectJavaFixtures.JavaBean::class
        assertThat(type.constructors).hasSizeGreaterThanOrEqualTo(2)
        assertThat(type.nestedClasses.map { it.simpleName }).contains("Nested")
        assertThat(type.members.map { it.name }).contains("publicField", "greet")
    }
}
