package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class Java16RecordComponentsLoaderTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val recordType = ReflectJavaFixtures.JavaRecord::class
        val properties = recordType.memberProperties.associateBy { it.name }
        val constructor = recordType.constructors.single()
        val arguments = constructor.parameters.associateWith { parameter ->
            when (parameter.name) {
                "name" -> "record"
                "count" -> 3
                else -> error(parameter.name ?: "record component")
            }
        }
        val record = constructor.callBy(arguments)

        assertThat(properties.keys).contains("name", "count")
        assertThat(properties.getValue("name").returnType.classifier).isEqualTo(String::class)
        assertThat(properties.getValue("name").getter.call(record)).isEqualTo("record")
        assertThat(record.name()).isEqualTo("record")
        assertThat(record.count()).isEqualTo(3)
    }
}
