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

        val packagesMask = DescriptorKindFilter.PACKAGES.kindMask
        val functionsMask = DescriptorKindFilter.FUNCTIONS.kindMask
        val variablesMask = DescriptorKindFilter.VARIABLES.kindMask
        val packageFunctions = DescriptorKindFilter(packagesMask or functionsMask, emptyList())
        assertThat(packageFunctions.acceptsKinds(packagesMask)).isTrue()
        assertThat(packageFunctions.acceptsKinds(functionsMask)).isTrue()
        assertThat(packageFunctions.acceptsKinds(variablesMask)).isFalse()
        assertThat(packageFunctions.toString()).startsWith("DescriptorKindFilter(")
    }
}
