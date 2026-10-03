package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.internal.impl.utils.SmartList

class SmartListTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val overloads = RichReflectionFixture::class.members.toTypedArray()
        assertThat(overloads.map { it.name }).contains("greet", "arraySize", "fieldValue")

        val values = SmartList("smart")
        assertThat(values.toArray(emptyArray<String>())).containsExactly("smart")
    }
}
