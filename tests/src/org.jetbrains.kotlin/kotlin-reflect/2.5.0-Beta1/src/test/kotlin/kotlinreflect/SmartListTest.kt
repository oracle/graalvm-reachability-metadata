package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.full.memberFunctions

class SmartListTest {
    @Test
    fun resolvesPublicMembersAndCovariantOverrides() {
        val members = RichReflectionFixture::class.members.associateBy { it.name }
        assertThat(members.keys).contains("greet", "arraySize", "fieldValue")

        val valueFunction = ReflectJavaFixtures.StringValue::class.memberFunctions
            .single { it.name == "value" }
        assertThat(valueFunction.returnType.classifier).isEqualTo(String::class)
        assertThat(valueFunction.call(ReflectJavaFixtures.StringValue())).isEqualTo("java-value")
    }
}
