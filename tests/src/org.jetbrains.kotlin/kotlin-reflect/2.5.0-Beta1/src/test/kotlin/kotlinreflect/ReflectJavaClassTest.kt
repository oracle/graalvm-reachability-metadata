package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.full.declaredMemberFunctions

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class JavaAnnotationEnvelope(val tags: ReflectJavaFixtures.JavaTags)

class DescriptorBackedCollection : ArrayList<String>() {
    @JavaAnnotationEnvelope(
        ReflectJavaFixtures.JavaTags(
            value = [ReflectJavaFixtures.JavaTag("described"), ReflectJavaFixtures.JavaTag("collection")],
        ),
    )
    fun describe(): String = joinToString("|")
}

class ReflectJavaClassTest {
    @Test
    fun exercisesPublicReflectionBehavior() {
        val type = ReflectJavaFixtures.JavaBean::class
        val members = type.members.associateBy { it.name }
        val constructors = type.constructors
        assertThat(constructors).hasSizeGreaterThanOrEqualTo(2)
        val noArg = constructors.single { it.parameters.isEmpty() }.call()
        val named = constructors.single { it.parameters.size == 1 }.call("constructed")
        assertThat(noArg.name).isEqualTo("default")
        assertThat(named.name).isEqualTo("constructed")
        assertThat(type.nestedClasses.map { it.simpleName }).contains("Nested")
        assertThat(members.keys).contains("publicField", "greet")
    }

    @Test
    fun readsJavaAnnotationArgumentsFromDescriptorBackedMembers() {
        val values = DescriptorBackedCollection().apply {
            add("first")
            add("second")
        }
        val describe = DescriptorBackedCollection::class.declaredMemberFunctions
            .single { it.name == "describe" }
        val envelope = describe.annotations.filterIsInstance<JavaAnnotationEnvelope>().single()

        assertThat(envelope.tags.value.map { it.value }).containsExactly("described", "collection")
        assertThat(describe.call(values)).isEqualTo("first|second")
    }
}
