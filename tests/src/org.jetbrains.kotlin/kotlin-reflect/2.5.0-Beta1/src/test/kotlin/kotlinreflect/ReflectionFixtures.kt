package kotlinreflect

import kotlin.properties.Delegates

@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FIELD,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER,
)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
annotation class ReflectTag(val value: String)

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ReflectDetails(val name: String = "default", val values: Array<String> = [])

annotation class ReflectPart(val value: String)

@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER,
)
@Retention(AnnotationRetention.RUNTIME)
annotation class ReflectComposition(val parts: Array<ReflectPart>)

annotation class ArrayParameterFixture(
    @ReflectComposition([ReflectPart("parameter-left"), ReflectPart("parameter-right")])
    val value: String,
)

annotation class MarkedParameterFixture(
    @ReflectTag("metadata-parameter") val value: String,
)

@ReflectComposition([ReflectPart("class-left"), ReflectPart("class-right")])
class ArrayAnnotationFixture

@ReflectTag("first")
@ReflectTag("second")
@ReflectDetails("fixture", ["one", "two"])
class RichReflectionFixture @ReflectTag("constructor") constructor(
    @ReflectTag("parameter") val prefix: String = "hello",
) {
    class Nested(val value: Int)

    @ReflectTag("property")
    @field:ReflectTag("field")
    @ReflectComposition([ReflectPart("property-left"), ReflectPart("property-right")])
    @JvmField
    var fieldValue: String = "field"

    val delegateSource: String = "$prefix-source"

    val delegatedReference: String by ::delegateSource

    val delegatedValue: String by lazy { "$prefix-delegate" }

    var observedValue: String by Delegates.observable("initial") { _, _, _ -> }

    @ReflectTag("method")
    fun greet(name: String = "world"): String = "$prefix, $name"

    fun arraySize(values: Array<String>): Int = values.size

    fun join(vararg values: String): String = values.joinToString("|")

    @ReflectComposition([ReflectPart("left"), ReflectPart("right")])
    fun composedAnnotation(): String = "composed"
}

@ReflectDetails("created")
class DefaultDetailsFixture

class CompanionFixture {
    companion object {
        @JvmField
        var companionValue: String = "companion"
    }
}

object ReflectionSingleton {
    val message: String = "singleton"
}

@JvmInline
value class ReflectionValue(val text: String) {
    fun decorate(suffix: String): String = "$text$suffix"
}

@JvmInline
value class InternalReflectionValue(internal val content: String) {
    fun render(): String = "internal:$content"
}

class ValueClassService {
    fun describe(value: ReflectionValue): String = "value:${value.text}"

    fun create(text: String): ReflectionValue = ReflectionValue(text)
}

class ValueClassHolder(val value: ReflectionValue)

class OuterFixture(val prefix: String) {
    inner class InnerValue(val value: ReflectionValue) {
        fun render(): String = "$prefix:${value.text}"
    }

    inner class PlainInner(val value: String) {
        fun render(): String = "$prefix:$value"
    }
}

object ReflectionStatics {
    @JvmField
    var globalValue: String = "global"
}

interface AnnotatedReflectionInterface {
    @ReflectTag("interface-property")
    val interfaceValue: String
        get() = "interface"
}

val packageDelegateSource: String = "package-source"

val packageDelegatedReference: String by ::packageDelegateSource

val String.packageDelegatedExtension: String by ::packageDelegateSource

class ExtensionDelegateFixture {
    val String.memberDelegatedExtension: String by ::packageDelegateSource
}

sealed class ReflectionSealed {
    data object First : ReflectionSealed()
    data object Second : ReflectionSealed()
}

enum class ReflectionEnum {
    FIRST,
    SECOND
}

fun packageGreeting(name: String): String = "package:$name"

val packageNumber: Int = 42
