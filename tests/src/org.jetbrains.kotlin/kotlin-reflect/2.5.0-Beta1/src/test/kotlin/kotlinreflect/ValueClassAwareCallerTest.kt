package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class ValueClassAwareCallerTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val service = ValueClassService()
        val describe = ValueClassService::class.memberFunctions.single { it.name == "describe" }
        val create = ValueClassService::class.memberFunctions.single { it.name == "create" }
        assertThat(describe.call(service, ReflectionValue("boxed"))).isEqualTo("value:boxed")
        assertThat(create.call(service, "made")).isEqualTo(ReflectionValue("made"))
    }
}
