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

        val topLevelReference = ::packageDelegatedReference
        topLevelReference.isAccessible = true
        assertThat(topLevelReference.getDelegate()).isEqualTo(::packageDelegateSource)
        assertThat(topLevelReference.get()).isEqualTo("package-source")

        val memberReference = RichReflectionFixture::delegatedReference
        memberReference.isAccessible = true
        assertThat(memberReference.getDelegate(fixture)).isEqualTo(fixture::delegateSource)
        assertThat(memberReference.get(fixture)).isEqualTo("reflect-source")

        val topLevelExtension = String::packageDelegatedExtension
        topLevelExtension.isAccessible = true
        assertThat(topLevelExtension.getDelegate("receiver")).isEqualTo(::packageDelegateSource)
        assertThat(topLevelExtension.get("receiver")).isEqualTo("package-source")

        val memberExtension = ExtensionDelegateFixture::class.declaredMemberExtensionProperties
            .single { it.name == "memberDelegatedExtension" }
        memberExtension.isAccessible = true
        assertThat(memberExtension.getDelegate(ExtensionDelegateFixture(), "receiver"))
            .isEqualTo(::packageDelegateSource)
        assertThat(memberExtension.get(ExtensionDelegateFixture(), "receiver"))
            .isEqualTo("package-source")
    }
}
