package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class SmartListTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val overloads = RichReflectionFixture::class.members.toTypedArray()
        assertThat(overloads.map { it.name }).contains("greet", "arraySize", "fieldValue")
    }
}
