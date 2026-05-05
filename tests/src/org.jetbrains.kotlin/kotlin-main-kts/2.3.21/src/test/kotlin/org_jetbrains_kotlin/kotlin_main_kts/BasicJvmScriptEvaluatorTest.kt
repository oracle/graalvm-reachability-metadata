/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_main_kts

import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.reflect.KClass
import kotlin.script.experimental.api.CompiledScript
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.scriptExecutionWrapper
import kotlin.script.experimental.api.valueOrThrow
import kotlin.script.experimental.jvm.BasicJvmScriptEvaluator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class BasicJvmScriptEvaluatorTest {
    @Test
    fun evaluatesScriptClassAndReadsConfiguredResultField(): Unit {
        val compiledScript: CompiledScript = FixedClassCompiledScript(
            scriptClass = ResultFieldScript::class,
            resultField = "scriptResult" to KotlinType("kotlin.String"),
        )

        val evaluation: EvaluationResult = evaluate(compiledScript).valueOrThrow()
        val returnValue: ResultValue = evaluation.returnValue

        assertThat(returnValue).isInstanceOf(ResultValue.Value::class.java)
        val value: ResultValue.Value = returnValue as ResultValue.Value
        assertThat(value.name).isEqualTo("scriptResult")
        assertThat(value.value).isEqualTo("field value")
        assertThat(value.type).isEqualTo("kotlin.String")
        assertThat(value.scriptClass).isEqualTo(ResultFieldScript::class)
        assertThat(value.scriptInstance).isInstanceOf(ResultFieldScript::class.java)
    }

    @Test
    fun evaluatesScriptClassThroughExecutionWrapper(): Unit {
        var wrapperInvoked: Boolean = false
        val configuration: ScriptEvaluationConfiguration = ScriptEvaluationConfiguration {
            scriptExecutionWrapper<Any> { block: () -> Any ->
                wrapperInvoked = true
                block()
            }
        }
        val compiledScript: CompiledScript = FixedClassCompiledScript(UnitScript::class)

        val evaluation: EvaluationResult = evaluate(compiledScript, configuration).valueOrThrow()
        val returnValue: ResultValue = evaluation.returnValue

        assertThat(wrapperInvoked).isTrue()
        assertThat(returnValue).isInstanceOf(ResultValue.Unit::class.java)
        assertThat(returnValue.scriptClass).isEqualTo(UnitScript::class)
        assertThat(returnValue.scriptInstance).isInstanceOf(UnitScript::class.java)
    }

    private fun evaluate(
        compiledScript: CompiledScript,
        configuration: ScriptEvaluationConfiguration = ScriptEvaluationConfiguration.Default,
    ): ResultWithDiagnostics<EvaluationResult> {
        val evaluator: BasicJvmScriptEvaluator = BasicJvmScriptEvaluator()
        val evaluation: suspend () -> ResultWithDiagnostics<EvaluationResult> = {
            evaluator(compiledScript, configuration)
        }
        var outcome: Result<ResultWithDiagnostics<EvaluationResult>>? = null
        evaluation.startCoroutine(
            object : Continuation<ResultWithDiagnostics<EvaluationResult>> {
                override val context: CoroutineContext = EmptyCoroutineContext

                override fun resumeWith(result: Result<ResultWithDiagnostics<EvaluationResult>>): Unit {
                    outcome = result
                }
            },
        )
        return requireNotNull(outcome) { "Script evaluation did not complete synchronously" }.getOrThrow()
    }

    private class FixedClassCompiledScript(
        private val scriptClass: KClass<*>,
        override val resultField: Pair<String, KotlinType>? = null,
        override val compilationConfiguration: ScriptCompilationConfiguration = ScriptCompilationConfiguration.Default,
    ) : CompiledScript {
        override suspend fun getClass(
            scriptEvaluationConfiguration: ScriptEvaluationConfiguration?,
        ): ResultWithDiagnostics<KClass<*>> = scriptClass.asSuccess()
    }
}

public class ResultFieldScript {
    @Suppress("unused")
    private val scriptResult: String = "field value"
}

public class UnitScript
