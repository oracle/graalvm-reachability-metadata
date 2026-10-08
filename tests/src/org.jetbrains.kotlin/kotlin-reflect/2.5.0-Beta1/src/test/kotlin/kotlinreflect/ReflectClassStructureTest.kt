package kotlinreflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

class ReflectClassStructureTest {
    @Test
    fun loadsMembersForACharSequenceSubclass() {
        val type = ReflectClassStructureFixture::class
        val constructor = type.constructors.single()
        val fixture = constructor.call("core")

        assertThat(constructor.annotations.filterIsInstance<ReflectTag>().map { it.value })
            .containsExactly("constructor")
        assertThat(constructor.parameters.single().annotations.filterIsInstance<ReflectTag>().map { it.value })
            .containsExactly("parameter")

        val decorate = type.declaredMemberFunctions.single { it.name == "decorate" }
        assertThat(decorate.annotations.filterIsInstance<ReflectTag>().map { it.value })
            .containsExactly("method")
        assertThat(decorate.call(fixture, "[", "]")).isEqualTo("[core]")

        val label = type.declaredMemberProperties.single { it.name == "label" }
        assertThat(label.annotations.filterIsInstance<ReflectTag>().map { it.value })
            .containsExactly("property")
        assertThat(label.getter.call(fixture)).isEqualTo("value:core")
        assertThat(fixture.toString()).isEqualTo("core")
    }

    @Test
    fun exercisesPublicReflectionBehavior() {
        val type = RichReflectionFixture::class
        val members = type.members.associateBy { it.name }
        val constructor = type.constructors.single()
        assertThat(constructor.annotations.filterIsInstance<ReflectTag>().map { it.value })
            .contains("constructor")
        assertThat(constructor.parameters.single().annotations.filterIsInstance<ReflectTag>().map { it.value })
            .contains("parameter")
        assertThat(members.keys).contains("greet", "fieldValue")
        val greet = members.getValue("greet")
        assertThat(greet.annotations.filterIsInstance<ReflectTag>().map { it.value }).contains("method")
        val fieldValue = members.getValue("fieldValue")
        assertThat(fieldValue.annotations.filterIsInstance<ReflectTag>().map { it.value })
            .contains("property")
    }
}

class ReflectClassStructureFixture @ReflectTag("constructor") constructor(
    @ReflectTag("parameter") private val value: String,
) : CharSequence {
    @ReflectTag("property")
    @field:ReflectTag("field")
    val label: String = "value:$value"

    override val length: Int
        get() = value.length

    override fun get(index: Int): Char = value[index]

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
        value.subSequence(startIndex, endIndex)

    @ReflectTag("method")
    fun decorate(prefix: String, suffix: String): String = "$prefix$value$suffix"

    override fun toString(): String = value
}
