package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class UtilKtTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val annotation = RichReflectionFixture::class.annotations.filterIsInstance<ReflectDetails>().single()
        assertThat(annotation.values).containsExactly("one", "two")

        val javaTags = ReflectJavaFixtures.JavaBean::class.annotations
            .filterIsInstance<ReflectJavaFixtures.JavaTag>()
        assertThat(javaTags.map { it.value }).containsExactly("alpha", "beta")

        val companionProperty = CompanionFixture.Companion::companionValue
        val mappedProperty = companionProperty.javaField!!.kotlinProperty!!
        assertThat(mappedProperty.call(CompanionFixture.Companion)).isEqualTo("companion")
        assertThat(Array<String>::class.starProjectedType.toString()).contains("Array")
    }
}
