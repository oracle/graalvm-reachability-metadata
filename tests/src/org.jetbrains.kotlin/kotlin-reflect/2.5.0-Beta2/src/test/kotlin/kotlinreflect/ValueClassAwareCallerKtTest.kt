package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class ValueClassAwareCallerKtTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val constructor = ValueClassHolder::class.primaryConstructor!!
        val holder = constructor.call(ReflectionValue("inside"))
        assertThat(holder.value.text).isEqualTo("inside")
    }
}
