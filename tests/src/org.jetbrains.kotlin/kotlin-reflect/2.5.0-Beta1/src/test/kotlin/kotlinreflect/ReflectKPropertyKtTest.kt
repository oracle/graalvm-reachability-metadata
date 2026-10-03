package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class ReflectKPropertyKtTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val fixture = RichReflectionFixture("reflect")
        val property = RichReflectionFixture::class.memberProperties.single { it.name == "delegatedValue" }
        property.isAccessible = true
        assertThat(property.getDelegate(fixture)).isNotNull
        assertThat(property.get(fixture)).isEqualTo("reflect-delegate")
    }
}
