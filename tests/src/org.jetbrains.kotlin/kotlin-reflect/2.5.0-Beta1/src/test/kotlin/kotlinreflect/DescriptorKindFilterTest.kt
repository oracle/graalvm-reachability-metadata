package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.memberFunctions

class DescriptorKindFilterTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val type = RichReflectionFixture::class
        val functions = type.memberFunctions.associateBy { it.name }
        val properties = type.declaredMemberProperties.associateBy { it.name }

        assertThat(functions.keys).contains("greet", "arraySize", "join")
        assertThat(properties.keys).contains("fieldValue", "delegatedValue", "observedValue")

        val rendered = functions.getValue("greet").toString()
        assertThat(rendered).contains("greet").contains("kotlin.String")
    }
}
