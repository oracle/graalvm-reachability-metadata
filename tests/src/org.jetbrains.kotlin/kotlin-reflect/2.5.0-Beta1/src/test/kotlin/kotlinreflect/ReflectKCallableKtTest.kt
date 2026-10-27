package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class ReflectKCallableKtTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val fixture = RichReflectionFixture()
        val arraySize = RichReflectionFixture::arraySize
        assertThat(arraySize.call(fixture, arrayOf("a", "b"))).isEqualTo(2)

        val join = RichReflectionFixture::class.declaredMemberFunctions.single { it.name == "join" }
        val instance = join.parameters.single { it.kind == kotlin.reflect.KParameter.Kind.INSTANCE }
        assertThat(join.callBy(mapOf(instance to fixture))).isEqualTo("")

        val boundValueFunction = ReflectionValue("inline")::decorate
        assertThat(boundValueFunction.call("-receiver")).isEqualTo("inline-receiver")
    }
}
