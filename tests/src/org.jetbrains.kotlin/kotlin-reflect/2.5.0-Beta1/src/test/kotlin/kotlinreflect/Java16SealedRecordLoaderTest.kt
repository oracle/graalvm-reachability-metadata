package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class Java16SealedRecordLoaderTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        assertThat(ReflectJavaFixtures.JavaRecord::class.isData).isFalse()
        assertThat(ReflectJavaFixtures.JavaSealed::class.isSealed).isTrue()
        assertThat(ReflectJavaFixtures.JavaSealed::class.sealedSubclasses).hasSize(2)
    }
}
