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

        val first = ::firstPackagePart.javaMethod!!.kotlinFunction!!
        val second = ::secondPackagePart.javaMethod!!.kotlinFunction!!
        assertThat(first.call("one")).isEqualTo("first:one")
        assertThat(second.call("two")).isEqualTo("second:two")
        assertThat(::packageNumber.get()).isEqualTo(42)
    }
}
