package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class JavaEnumEntriesKPropertyTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val entries = ReflectJavaFixtures.JavaEnum::class.staticProperties.single { it.name == "entries" }
        assertThat(entries.call()).isEqualTo(ReflectJavaFixtures.JavaEnum.entries)
    }
}
