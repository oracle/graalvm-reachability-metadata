/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_scripting_jsr223

import java.io.StringReader
import javax.script.Bindings
import javax.script.Compilable
import javax.script.CompiledScript
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager
import javax.script.ScriptException
import javax.script.SimpleBindings
import kotlin.script.experimental.jsr223.KOTLIN_JSR223_RESOLVE_FROM_CLASSLOADER_PROPERTY
import kotlin.script.experimental.jsr223.KotlinJsr223DefaultScriptCompilationConfiguration
import kotlin.script.experimental.jsr223.KotlinJsr223DefaultScriptEngineFactory
import kotlin.script.experimental.jsr223.KotlinJsr223DefaultScriptEvaluationConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Test

public class KotlinScriptingJsr223Test {
    @Test
    public fun exposesFactoryMetadataAndSourceHelpers(): Unit {
        val factory: KotlinJsr223DefaultScriptEngineFactory = KotlinJsr223DefaultScriptEngineFactory()

        assertThat(factory.engineName).containsIgnoringCase("kotlin")
        assertThat(factory.languageName).containsIgnoringCase("kotlin")
        assertThat(factory.engineVersion).isNotBlank()
        assertThat(factory.languageVersion).isNotBlank()
        assertThat(factory.extensions).contains("kts")
        assertThat(factory.mimeTypes).contains("text/x-kotlin")
        assertThat(factory.names).contains("kotlin")
        assertThat(factory.getParameter(ScriptEngine.NAME)).isEqualTo("kotlin")
        assertThat(factory.getParameter("THREADING")).isNull()

        val methodCall: String = factory.getMethodCallSyntax("receiver", "combine", "first", "second")
        assertThat(methodCall).startsWith("receiver.combine(")
        assertThat(methodCall).contains("first", "second")

        val outputStatement: String = factory.getOutputStatement("hello \"kotlin\"")
        assertThat(outputStatement).contains("print")
        assertThat(outputStatement).contains("hello", "kotlin")

        val program: String = factory.getProgram("val value = 41", "value + 1")
        assertThat(program).contains("val value = 41")
        assertThat(program).contains("value + 1")
    }

    @Test
    public fun discoversDefaultEngineThroughJsr223ServiceProvider(): Unit {
        val manager: ScriptEngineManager = ScriptEngineManager(Thread.currentThread().contextClassLoader)

        val byName: ScriptEngine? = manager.getEngineByName("kotlin")
        val byExtension: ScriptEngine? = manager.getEngineByExtension("kts")
        val byMimeType: ScriptEngine? = manager.getEngineByMimeType("text/x-kotlin")

        assertThat(byName).isNotNull()
        assertThat(byExtension).isNotNull()
        assertThat(byMimeType).isNotNull()
        assertThat(byName!!.factory.engineName).containsIgnoringCase("kotlin")
        assertThat(byExtension!!.factory.names).contains("kotlin")
        assertThat(byMimeType!!.factory.extensions).contains("kts")
    }

    @Test
    public fun initializesDefaultScriptConfigurationsAndClassLoaderProperty(): Unit {
        assertThat(KotlinJsr223DefaultScriptCompilationConfiguration).isNotNull()
        assertThat(KotlinJsr223DefaultScriptEvaluationConfiguration).isNotNull()
        assertThat(KOTLIN_JSR223_RESOLVE_FROM_CLASSLOADER_PROPERTY)
            .contains("kotlin")
            .containsIgnoringCase("classloader")
    }

    @Test
    public fun evaluatesExpressionWithPerCallBindings(): Unit = runDynamicScriptTest {
        val engine: ScriptEngine = newEngine()
        val bindings: Bindings = SimpleBindings(
            mutableMapOf<String, Any>(
                "left" to 34,
                "right" to 8,
            ),
        )

        val result: Any? = engine.eval("left + right", bindings)

        assertThat(result).isEqualTo(42)
    }

    @Test
    public fun keepsDeclarationsAcrossSequentialEvaluations(): Unit = runDynamicScriptTest {
        val engine: ScriptEngine = newEngine()
        engine.put("suffix", "script")

        engine.eval(
            """
            fun decorate(value: String): String = "${'$'}value-${'$'}{bindings["suffix"]}"
            val base = 21
            """.trimIndent(),
        )

        val result: Any? = engine.eval("decorate(\"kotlin\") + ':' + (base * 2)")

        assertThat(result).isEqualTo("kotlin-script:42")
    }

    @Test
    public fun compilesReusableScriptAndEvaluatesItWithDifferentBindings(): Unit = runDynamicScriptTest {
        val engine: ScriptEngine = newEngine()
        assertThat(engine).isInstanceOf(Compilable::class.java)
        val compilable: Compilable = engine as Compilable

        val compiledScript: CompiledScript = compilable.compile(
            """
            val value = bindings["value"] as Int
            value * value
            """.trimIndent(),
        )

        assertThat(compiledScript.eval(SimpleBindings(mutableMapOf<String, Any>("value" to 6)))).isEqualTo(36)
        assertThat(compiledScript.eval(SimpleBindings(mutableMapOf<String, Any>("value" to 7)))).isEqualTo(49)
    }

    @Test
    public fun evaluatesScriptSourceFromReader(): Unit = runDynamicScriptTest {
        val engine: ScriptEngine = newEngine()
        val source: StringReader = StringReader(
            """
            val values = listOf(8, 13, 21)
            values.sum()
            """.trimIndent(),
        )

        val result: Any? = engine.eval(source)

        assertThat(result).isEqualTo(42)
    }

    @Test
    public fun reportsCompilationDiagnosticsAsScriptException(): Unit = runDynamicScriptTest {
        val engine: ScriptEngine = newEngine()

        try {
            engine.eval("val broken = ")
            throw AssertionError("Expected invalid Kotlin source to fail with ScriptException")
        } catch (exception: ScriptException) {
            assertThat(exception).isNotNull()
        }
    }

    @Test
    public fun scriptTemplateCreatesBindingsForNestedEvaluation(): Unit = runDynamicScriptTest {
        val engine: ScriptEngine = newEngine()

        val result: Any? = engine.eval(
            """
            val nestedBindings = createBindings()
            nestedBindings["amount"] = 21
            nestedBindings["label"] = "answer"
            val nestedSource = "(bindings[\"label\"] as String) + \":\" + " +
                "((bindings[\"amount\"] as Int) * 2)"
            eval(nestedSource, nestedBindings)
            """.trimIndent(),
        )

        assertThat(result).isEqualTo("answer:42")
    }

    private fun newEngine(): ScriptEngine = KotlinJsr223DefaultScriptEngineFactory().scriptEngine

    private fun runDynamicScriptTest(test: () -> Unit): Unit {
        try {
            test()
        } catch (error: Error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error
            }
        }
    }
}
