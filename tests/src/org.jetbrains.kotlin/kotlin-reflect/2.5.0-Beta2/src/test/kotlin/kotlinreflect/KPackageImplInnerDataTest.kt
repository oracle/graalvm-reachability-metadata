package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class KPackageImplInnerDataTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val function = ::packageGreeting
        assertThat(function.call("api")).isEqualTo("package:api")

        val firstReference = ::firstPackagePart
        val secondReference = ::secondPackagePart
        assertThat(firstReference.parameters.single().type.classifier).isEqualTo(String::class)
        assertThat(secondReference.returnType.classifier).isEqualTo(String::class)
        assertThat(firstReference.call("direct")).isEqualTo("first:direct")
        assertThat(secondReference.call("direct")).isEqualTo("second:direct")

        val first = firstReference.javaMethod!!.kotlinFunction!!
        val second = ::secondPackagePart.javaMethod!!.kotlinFunction!!
        assertThat(first.call("one")).isEqualTo("first:one")
        assertThat(second.call("two")).isEqualTo("second:two")
        assertThat(::packageNumber.get()).isEqualTo(42)
    }
}
