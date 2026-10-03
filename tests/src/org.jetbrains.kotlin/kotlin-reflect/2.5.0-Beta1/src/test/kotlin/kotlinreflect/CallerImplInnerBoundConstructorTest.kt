package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class CallerImplInnerBoundConstructorTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val constructor = OuterFixture("prefix")::PlainInner
        val inner = constructor.call("value")
        assertThat(inner.render()).isEqualTo("prefix:value")
    }
}
