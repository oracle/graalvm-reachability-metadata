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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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

        assertEquals("result", resultValue.name)
        assertEquals("script-result", resultValue.value)
        assertEquals("kotlin.String", resultValue.type)
        assertTrue(resultValue.scriptInstance is ResultReturningScript)
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

        assertTrue(wrapperInvoked)
        assertTrue(resultValue is ResultValue.Unit)
        assertTrue(resultValue.scriptInstance is ResultReturningScript)
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
        assertTrue(evaluationResult.returnValue is ResultValue.Value)
        return evaluationResult.returnValue as ResultValue.Value
    }

    @Suppress("UNCHECKED_CAST")
    private fun successfulResult(result: ResultWithDiagnostics<EvaluationResult>): EvaluationResult {
        assertTrue(result is ResultWithDiagnostics.Success<*>)
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
