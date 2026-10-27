package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class JvmBuiltInsSignaturesTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val names = String::class.memberFunctions.map { it.name }
        assertThat(names).contains("compareTo", "toString")
        assertThat(String::class.supertypes).isNotEmpty()
    }
}
