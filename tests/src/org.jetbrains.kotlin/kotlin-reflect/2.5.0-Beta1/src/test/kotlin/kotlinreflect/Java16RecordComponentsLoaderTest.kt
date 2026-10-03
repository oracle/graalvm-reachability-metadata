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

        val nameProperty = properties.getValue("name")
        val countProperty = properties.getValue("count")
        assertThat(properties.keys).contains("name", "count")
        assertThat(nameProperty.returnType.classifier).isEqualTo(String::class)
        assertThat(nameProperty.getter.call(record)).isEqualTo("record")
        assertThat(countProperty.getter.call(record)).isEqualTo(3)
        assertThat(nameProperty.annotations.filterIsInstance<ReflectJavaFixtures.RecordDetail>().single().value)
            .isEqualTo("name-component")
        assertThat(record.name()).isEqualTo("record")
        assertThat(record.count()).isEqualTo(3)
    }
}
