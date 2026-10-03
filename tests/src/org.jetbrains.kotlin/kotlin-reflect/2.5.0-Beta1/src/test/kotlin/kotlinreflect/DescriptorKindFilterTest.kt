package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class DescriptorKindFilterTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val rendered = RichReflectionFixture::class.memberFunctions.single { it.name == "greet" }.toString()
        assertThat(rendered).contains("greet").contains("kotlin.String")
    }
}
