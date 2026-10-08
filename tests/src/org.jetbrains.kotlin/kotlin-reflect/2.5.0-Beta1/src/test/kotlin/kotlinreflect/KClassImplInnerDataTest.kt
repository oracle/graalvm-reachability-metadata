package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class KClassImplInnerDataTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        assertThat(RichReflectionFixture::class.constructors).isNotEmpty()
        assertThat(String::class.constructors).isNotEmpty()
        assertThat(RichReflectionFixture::class.nestedClasses.map { it.simpleName }).contains("Nested")
        assertThat(ReflectionSingleton::class.objectInstance?.message).isEqualTo("singleton")
        assertThat(CompanionFixture.Companion::class.objectInstance)
            .isSameAs(CompanionFixture.Companion)
    }
}
