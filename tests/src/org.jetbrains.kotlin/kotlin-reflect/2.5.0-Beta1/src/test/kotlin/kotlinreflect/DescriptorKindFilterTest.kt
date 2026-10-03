package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.internal.impl.resolve.scopes.DescriptorKindFilter

class DescriptorKindFilterTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val rendered = RichReflectionFixture::class.memberFunctions.single { it.name == "greet" }.toString()
        assertThat(rendered).contains("greet").contains("kotlin.String")

        val callables = DescriptorKindFilter.CALLABLES.toString()
        val classifiers = DescriptorKindFilter.CLASSIFIERS.toString()
        assertThat(callables).contains("CALLABLES")
        assertThat(classifiers).contains("CLASSIFIERS")
    }
}
