package kotlinreflect

import kotlinx.coroutines.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.time.Instant
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.reflect.KParameter
import kotlin.reflect.full.createType
import kotlin.reflect.full.memberProperties

class KotlinReflectTests {
    @Test
    fun test() {
        val klass = Foo::class
        val ctor = klass.constructors.first { it.parameters.isEmpty() }
        val foo = ctor.call()

        val method = klass.members.first { it.name == "method" }
        assertThat(method.parameters).hasSize(1)
        assertThat(method.parameters[0].kind).isEqualTo(KParameter.Kind.INSTANCE)

        val methodWithArgs = klass.members.first { it.name == "methodWithArgs" }
        assertThat(methodWithArgs.parameters).hasSize(2)
        assertThat(methodWithArgs.parameters[1].type).isEqualTo(String::class.createType())

        val methodWithResult = klass.members.first { it.name == "methodWithResult" }
        assertThat(methodWithResult.returnType).isEqualTo(Int::class.createType())
        val result = methodWithResult.call(foo)
        assertThat(result).isEqualTo(1)

        val annotatedMethod = klass.members.first { it.name == "annotatedMethod" }
        assertThat(annotatedMethod.annotations).hasSize(1)
        assertThat(annotatedMethod.annotations[0]).isInstanceOf(TestAnnotation::class.java)
        assertThat((annotatedMethod.annotations[0] as TestAnnotation).value).isEqualTo("annotation-on-method")

        val stringProperty = klass.memberProperties.first { it.name == "stringProperty" }
        assertThat(stringProperty.returnType).isEqualTo(String::class.createType())
        assertThat(stringProperty.get(foo)).isEqualTo("string-1")

        val annotatedProperty = klass.memberProperties.first { it.name == "annotatedProperty" }
        assertThat(annotatedProperty.annotations).hasSize(1)
        assertThat(annotatedProperty.annotations[0]).isInstanceOf(TestAnnotation::class.java)
        assertThat((annotatedProperty.annotations[0] as TestAnnotation).value).isEqualTo("annotation-on-field")
    }
    @Test
    fun testCoroutine() {
        val updated = AtomicBoolean(false)
        CoroutineScope(Dispatchers.IO).launch {
            val writer = FileWriter(File.createTempFile("tmp-couroutine", ".txt"))
            writer.use {
                val start = "start:" + Instant.now()
                writer.append(start)
                println(start)
                delay(500)
                updated.set(true)
                val end = "end:" + Instant.now()
                writer.append(end)
                println(end)
            }
        }
        println("sleep:" + Instant.now())
        Thread.sleep(1000)
        println("awake:" + Instant.now())
        assertThat(updated.get()).isTrue
    }
}

class Foo {
    val stringProperty = "string-1"

    @TestAnnotation("annotation-on-field")
    val annotatedProperty = 1

    fun method() {
    }

    fun methodWithArgs(first: String) {
    }

    fun methodWithResult(): Int = 1

    @TestAnnotation("annotation-on-method")
    fun annotatedMethod() {
    }
}

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class TestAnnotation(val value: String)

