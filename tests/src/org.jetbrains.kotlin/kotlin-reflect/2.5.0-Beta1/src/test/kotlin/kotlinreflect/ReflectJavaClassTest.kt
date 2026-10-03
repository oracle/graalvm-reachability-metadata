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
        val constructors = type.constructors
        assertThat(constructors).hasSizeGreaterThanOrEqualTo(2)
        val noArg = constructors.single { it.parameters.isEmpty() }.call()
        val named = constructors.single { it.parameters.size == 1 }.call("constructed")
        assertThat(noArg.name).isEqualTo("default")
        assertThat(named.name).isEqualTo("constructed")
        assertThat(type.nestedClasses.map { it.simpleName }).contains("Nested")
        assertThat(type.members.map { it.name }).contains("publicField", "greet")
    }
}
