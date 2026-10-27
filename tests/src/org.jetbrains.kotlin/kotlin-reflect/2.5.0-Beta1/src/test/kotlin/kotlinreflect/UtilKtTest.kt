package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

@Target(AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
annotation class ReflectTypeComposition(val parts: Array<ReflectPart>)

class TypeAnnotationFixture(private val value: String = "typed") : CharSequence {
    override val length: Int
        get() = value.length

    override fun get(index: Int): Char = value[index]

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
        value.subSequence(startIndex, endIndex)

    fun annotatedValue(): @ReflectTypeComposition(
        [ReflectPart("type-left"), ReflectPart("type-right")],
    ) String = value
}

class UtilKtTest {
    @Test
    fun materializesAnnotationArraysFromKotlinTypeMetadata() {
        val fixture = TypeAnnotationFixture()
        val function = TypeAnnotationFixture::class.declaredMemberFunctions
            .single { it.name == "annotatedValue" }
        val composition = function.returnType.findAnnotation<ReflectTypeComposition>()!!

        assertThat(function.call(fixture)).isEqualTo("typed")
        assertThat(composition.parts.map { it.value }).containsExactly("type-left", "type-right")
    }

    @Test
    fun exercisesPublicReflectionBehavior() {
        val type = RichReflectionFixture::class
        val annotation = type.annotations.filterIsInstance<ReflectDetails>().single()
        val composition = type.members.single { it.name == "composedAnnotation" }
            .annotations.filterIsInstance<ReflectComposition>().single()
        assertThat(annotation.values).containsExactly("one", "two")
        assertThat(composition.parts.map { it.value }).containsExactly("left", "right")

        val arrayAnnotation = ArrayAnnotationFixture::class.findAnnotation<ReflectComposition>()!!
        assertThat(arrayAnnotation.parts.map { it.value }).containsExactly("class-left", "class-right")

        val javaTags = ReflectJavaFixtures.JavaBean::class.annotations
            .filterIsInstance<ReflectJavaFixtures.JavaTags>()
            .single()
            .value
        assertThat(javaTags.map { it.value }).containsExactly("alpha", "beta")

        val inheritedTags = ReflectJavaFixtures.TaggedChild::class.annotations
            .filterIsInstance<ReflectJavaFixtures.InheritedJavaTags>()
            .single()
            .value
        assertThat(inheritedTags.map { it.value }).containsExactly("parent-one", "parent-two")

        val companionProperty = CompanionFixture.Companion::companionValue
        val mappedProperty = companionProperty.javaField!!.kotlinProperty!!
        assertThat(mappedProperty.call(CompanionFixture.Companion)).isEqualTo("companion")
        assertThat(Array<String>::class.starProjectedType.toString()).contains("Array")
    }
}
