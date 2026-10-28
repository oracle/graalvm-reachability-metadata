package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class CallerImplInnerAccessorForHiddenBoundConstructorTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val constructor = OuterFixture("outer")::InnerValue
        val instance = constructor.call(ReflectionValue("bound"))
        assertThat(instance.render()).isEqualTo("outer:bound")
    }
}
