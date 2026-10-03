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
        assertThat(Array<String>::class.starProjectedType.toString()).contains("Array")
    }
}
