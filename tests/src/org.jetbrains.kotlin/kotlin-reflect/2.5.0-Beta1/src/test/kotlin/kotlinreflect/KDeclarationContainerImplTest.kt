package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty1
import kotlinreflect.ReflectJavaFixtures.JavaBean
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class KDeclarationContainerImplTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val constructor = RichReflectionFixture::class.primaryConstructor!!
        val fixture = constructor.callBy(emptyMap())
        val constructorReference = ::RichReflectionFixture
        val javaConstructor = constructorReference.javaConstructor!!
        val mappedFixture = javaConstructor.kotlinFunction!!.call("mapped")
        assertThat(mappedFixture.prefix).isEqualTo("mapped")

        val javaReference: (String) -> JavaBean = ::JavaBean
        @Suppress("UNCHECKED_CAST")
        val reflectiveJavaReference = javaReference as KFunction<JavaBean>
        assertThat(reflectiveJavaReference.call("reference").name).isEqualTo("reference")

        val greet = RichReflectionFixture::class.declaredMemberFunctions.single { it.name == "greet" }
        assertThat(greet.callBy(mapOf(greet.parameters[0] to fixture))).isEqualTo("hello, world")

        val covariantValue = ReflectJavaFixtures.StringValue::class.declaredMemberFunctions
            .single { it.name == "value" }
        assertThat(covariantValue.call(ReflectJavaFixtures.StringValue())).isEqualTo("java-value")
    }
}
