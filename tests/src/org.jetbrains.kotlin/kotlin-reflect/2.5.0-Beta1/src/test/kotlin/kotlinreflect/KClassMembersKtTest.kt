package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class KClassMembersKtTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val names = RichReflectionFixture::class.declaredMemberFunctions.map { it.name }
        assertThat(names).contains("greet", "arraySize")
        assertThat(RichReflectionFixture::class.declaredMemberProperties.map { it.name }).contains("fieldValue")

        val annotationProperties = ReflectJavaFixtures.JavaDetails::class.declaredMemberProperties
        assertThat(annotationProperties.map { it.name }).containsExactlyInAnyOrder("name", "count")
        assertThat(annotationProperties.single { it.name == "count" }.getter.call(
            ReflectJavaFixtures.JavaBean::class.annotations
                .filterIsInstance<ReflectJavaFixtures.JavaDetails>()
                .single(),
        )).isEqualTo(2)
    }
}
