/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_scripting_jvm

import kotlin.coroutines.Continuation
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
import kotlin.script.experimental.api.scriptExecutionWrapper
import kotlin.script.experimental.jvm.BasicJvmScriptEvaluator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class BasicJvmScriptEvaluatorTest {
    @Test
    public fun evaluatesScriptAndReadsResultField(): Unit {
        val evaluationResult: ResultWithDiagnostics<EvaluationResult> = evaluateScript(
            StaticCompiledScript(
                scriptClass = ResultReturningScript::class,
                resultField = "result" to KotlinType(String::class),
            ),
            ScriptEvaluationConfiguration {},
        )

        val resultValue: ResultValue.Value = successfulValue(evaluationResult)

        assertThat(resultValue.name).isEqualTo("result")
        assertThat(resultValue.value).isEqualTo("script-result")
        assertThat(resultValue.type).isEqualTo("kotlin.String")
        assertThat(resultValue.scriptInstance).isInstanceOf(ResultReturningScript::class.java)
    }

    @Test
    public fun evaluatesScriptInsideExecutionWrapper(): Unit {
        var wrapperInvoked: Boolean = false
        val configuration: ScriptEvaluationConfiguration = ScriptEvaluationConfiguration {
            scriptExecutionWrapper { body: () -> Any ->
                wrapperInvoked = true
                body()
            }
        }

        val evaluationResult: ResultWithDiagnostics<EvaluationResult> = evaluateScript(
            StaticCompiledScript(ResultReturningScript::class),
            configuration,
        )

        val resultValue: ResultValue = successfulResult(evaluationResult).returnValue

        assertThat(wrapperInvoked).isTrue()
        assertThat(resultValue).isInstanceOf(ResultValue.Unit::class.java)
        assertThat(resultValue.scriptInstance).isInstanceOf(ResultReturningScript::class.java)
    }

    private fun evaluateScript(
        script: CompiledScript,
        configuration: ScriptEvaluationConfiguration,
    ): ResultWithDiagnostics<EvaluationResult> {
        val evaluator: BasicJvmScriptEvaluator = BasicJvmScriptEvaluator()
        var result: Result<ResultWithDiagnostics<EvaluationResult>>? = null

        suspend { evaluator(script, configuration) }.startCoroutine(
            object : Continuation<ResultWithDiagnostics<EvaluationResult>> {
                override val context: EmptyCoroutineContext = EmptyCoroutineContext

                override fun resumeWith(resumeResult: Result<ResultWithDiagnostics<EvaluationResult>>): Unit {
                    result = resumeResult
                }
            },
        )

        return requireNotNull(result).getOrThrow()
    }

    private fun successfulValue(result: ResultWithDiagnostics<EvaluationResult>): ResultValue.Value {
        val evaluationResult: EvaluationResult = successfulResult(result)
        assertThat(evaluationResult.returnValue).isInstanceOf(ResultValue.Value::class.java)
        return evaluationResult.returnValue as ResultValue.Value
    }

    @Suppress("UNCHECKED_CAST")
    private fun successfulResult(result: ResultWithDiagnostics<EvaluationResult>): EvaluationResult {
        assertThat(result).isInstanceOf(ResultWithDiagnostics.Success::class.java)
        return (result as ResultWithDiagnostics.Success<EvaluationResult>).value
    }

    public class ResultReturningScript {
        @Suppress("unused")
        public val result: String = "script-result"
    }

    private class StaticCompiledScript(
        private val scriptClass: KClass<*>,
        override val resultField: Pair<String, KotlinType>? = null,
    ) : CompiledScript {
        override val sourceLocationId: String = "memory://basic-evaluator.kts"
        override val compilationConfiguration: ScriptCompilationConfiguration = ScriptCompilationConfiguration {}
        override val otherScripts: List<CompiledScript> = emptyList()

        override suspend fun getClass(
            scriptEvaluationConfiguration: ScriptEvaluationConfiguration?,
        ): ResultWithDiagnostics<KClass<*>> = ResultWithDiagnostics.Success(scriptClass)
    }
}
