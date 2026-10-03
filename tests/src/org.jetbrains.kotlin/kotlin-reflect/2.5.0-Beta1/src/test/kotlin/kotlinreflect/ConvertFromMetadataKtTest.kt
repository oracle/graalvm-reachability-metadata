package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class ConvertFromMetadataKtTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val details = RichReflectionFixture::class.findAnnotations<ReflectDetails>().single()
        assertThat(details.values.toList()).containsExactly("one", "two")
    }
}
