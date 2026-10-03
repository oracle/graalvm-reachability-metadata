package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class Java16RecordComponentsLoaderTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val propertyNames = ReflectJavaFixtures.JavaRecord::class.memberProperties.map { it.name }
        assertThat(propertyNames).contains("name", "count")
        assertThat(ReflectJavaFixtures.JavaRecord("record", 3).name()).isEqualTo("record")
    }
}
