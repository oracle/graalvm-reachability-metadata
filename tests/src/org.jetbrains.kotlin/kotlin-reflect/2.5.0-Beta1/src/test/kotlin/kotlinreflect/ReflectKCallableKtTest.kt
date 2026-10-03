package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class ReflectKCallableKtTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val function = RichReflectionFixture::arraySize
        assertThat(function.call(RichReflectionFixture(), arrayOf("a", "b"))).isEqualTo(2)
    }
}
