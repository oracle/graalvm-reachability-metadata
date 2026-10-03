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
        val nameAccessor = recordType.memberFunctions.single { it.name == "name" }
        val countAccessor = recordType.memberFunctions.single { it.name == "count" }
        assertThat(properties.keys).contains("name", "count")
        assertThat(nameProperty.returnType.classifier).isEqualTo(String::class)
        assertThat(nameAccessor.call(record)).isEqualTo("record")
        assertThat(countAccessor.call(record)).isEqualTo(3)
        assertThat(nameAccessor.annotations.filterIsInstance<ReflectJavaFixtures.RecordDetail>().single().value)
            .isEqualTo("name-component")
        assertThat(record.name()).isEqualTo("record")
        assertThat(record.count()).isEqualTo(3)
    }
}
