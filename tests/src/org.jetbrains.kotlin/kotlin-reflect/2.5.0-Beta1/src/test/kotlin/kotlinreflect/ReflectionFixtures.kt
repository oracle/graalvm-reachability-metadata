package kotlinreflect

import kotlin.properties.Delegates

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
annotation class ReflectTag(val value: String)

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ReflectDetails(val name: String = "default", val values: Array<String> = [])

@ReflectTag("first")
@ReflectTag("second")
@ReflectDetails("fixture", ["one", "two"])
class RichReflectionFixture(val prefix: String = "hello") {
    class Nested(val value: Int)

    @JvmField
    var fieldValue: String = "field"

    val delegatedValue: String by lazy { "$prefix-delegate" }

    var observedValue: String by Delegates.observable("initial") { _, _, _ -> }

    fun greet(name: String = "world"): String = "$prefix, $name"

    fun arraySize(values: Array<String>): Int = values.size
}

object ReflectionSingleton {
    val message: String = "singleton"
}

@JvmInline
value class ReflectionValue(val text: String)

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
