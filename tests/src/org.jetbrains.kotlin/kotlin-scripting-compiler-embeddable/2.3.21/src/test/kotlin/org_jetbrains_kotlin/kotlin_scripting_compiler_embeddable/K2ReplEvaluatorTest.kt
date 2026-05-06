/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_scripting_compiler_embeddable

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.jvm.KJvmEvaluatedSnippet
import kotlin.script.experimental.jvm.impl.KJvmCompiledModuleFromClassLoader
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.util.LinkedSnippet
import kotlin.script.experimental.util.LinkedSnippetImpl
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.K2ReplEvaluator
import org.junit.jupiter.api.Test

public class K2ReplEvaluatorTest {
    @Test
    public fun evaluatesCompiledReplSnippetAndReadsResultField(): Unit {
        K2ReplValueSnippet.computedValue = "not evaluated"
        val script: KJvmCompiledScript = KJvmCompiledScript(
            sourceLocationId = "memory://k2-repl-value-snippet.kts",
            compilationConfiguration = ScriptCompilationConfiguration {},
            scriptClassFQName = K2ReplValueSnippet::class.java.name,
            resultField = "computedValue" to KotlinType(String::class),
            otherScripts = emptyList(),
            compiledModule = KJvmCompiledModuleFromClassLoader(K2ReplValueSnippet::class.java.classLoader),
        )

        val result: ResultWithDiagnostics<LinkedSnippet<KJvmEvaluatedSnippet>> = evaluate(script)

        assertThat(result).isInstanceOf(ResultWithDiagnostics.Success::class.java)
        val success: ResultWithDiagnostics.Success<LinkedSnippet<KJvmEvaluatedSnippet>> =
            result as ResultWithDiagnostics.Success<LinkedSnippet<KJvmEvaluatedSnippet>>
        val resultValue: ResultValue = success.value.get().result
        assertThat(resultValue).isInstanceOf(ResultValue.Value::class.java)
        val value: ResultValue.Value = resultValue as ResultValue.Value
        assertThat(value.name).isEqualTo("computedValue")
        assertThat(value.value).isEqualTo("evaluated by K2ReplEvaluator")
        assertThat(value.type).isEqualTo("kotlin.String")
    }

    private fun evaluate(script: KJvmCompiledScript): ResultWithDiagnostics<LinkedSnippet<KJvmEvaluatedSnippet>> {
        val evaluator: K2ReplEvaluator = K2ReplEvaluator()
        val snippet: LinkedSnippetImpl<KJvmCompiledScript> = LinkedSnippetImpl(script, null)
        var evaluationResult: Result<ResultWithDiagnostics<LinkedSnippet<KJvmEvaluatedSnippet>>>? = null

        suspend { evaluator.eval(snippet, ScriptEvaluationConfiguration {}) }.startCoroutine(
            object : Continuation<ResultWithDiagnostics<LinkedSnippet<KJvmEvaluatedSnippet>>> {
                override val context: EmptyCoroutineContext = EmptyCoroutineContext

                override fun resumeWith(
                    result: Result<ResultWithDiagnostics<LinkedSnippet<KJvmEvaluatedSnippet>>>,
                ): Unit {
                    evaluationResult = result
                }
            },
        )

        return requireNotNull(evaluationResult).getOrThrow()
    }
}

public object K2ReplValueSnippet {
    @JvmField
    public var computedValue: String = "not evaluated"

    @Suppress("FunctionName")
    public fun `$$eval`(): Unit {
        computedValue = "evaluated by K2ReplEvaluator"
    }
}
