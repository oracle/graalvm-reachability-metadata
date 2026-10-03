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
        val members = type.members.associateBy { it.name }
        val constructor = type.constructors.single()
        assertThat(constructor.annotations.filterIsInstance<ReflectTag>().map { it.value })
            .contains("constructor")
        assertThat(constructor.parameters.single().annotations.filterIsInstance<ReflectTag>().map { it.value })
            .contains("parameter")
        assertThat(members.keys).contains("greet", "fieldValue")
        val greet = members.getValue("greet")
        assertThat(greet.annotations.filterIsInstance<ReflectTag>().map { it.value }).contains("method")
        val fieldValue = members.getValue("fieldValue")
        assertThat(fieldValue.annotations.filterIsInstance<ReflectTag>().map { it.value })
            .contains("property")
    }
}
