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
        assertThat(type.constructors.flatMap { it.annotations }.filterIsInstance<ReflectTag>().map { it.value })
            .contains("constructor")
        val greet = type.declaredMemberFunctions.single { it.name == "greet" }
        assertThat(greet.annotations.filterIsInstance<ReflectTag>().map { it.value }).contains("method")
        val fieldValue = type.declaredMemberProperties.single { it.name == "fieldValue" }
        assertThat(fieldValue.annotations.filterIsInstance<ReflectTag>().map { it.value })
            .contains("property")
    }
}
