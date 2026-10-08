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
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.jvm.impl.KJvmCompiledModuleFromClassLoader
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

public class KJvmCompiledScriptTest {
    @Test
    public fun resolvesScriptClassThroughCompiledModuleClassLoader(): Unit {
        val script: KJvmCompiledScript = KJvmCompiledScript(
            sourceLocationId = "memory://compiled-script.kts",
            compilationConfiguration = ScriptCompilationConfiguration {},
            scriptClassFQName = RoundTripScript::class.java.name,
            resultField = "result" to KotlinType(String::class),
            otherScripts = emptyList(),
            compiledModule = KJvmCompiledModuleFromClassLoader(RoundTripScript::class.java.classLoader),
        )

        val result: ResultWithDiagnostics<KClass<*>> = runSuspendingGetClass(script)

        assertTrue(result is ResultWithDiagnostics.Success<*>)
        val resolvedClass: KClass<*> = (result as ResultWithDiagnostics.Success<KClass<*>>).value
        assertEquals(RoundTripScript::class, resolvedClass)
    }

    private fun runSuspendingGetClass(script: KJvmCompiledScript): ResultWithDiagnostics<KClass<*>> {
        var result: Result<ResultWithDiagnostics<KClass<*>>>? = null

        suspend { script.getClass(null) }.startCoroutine(
            object : Continuation<ResultWithDiagnostics<KClass<*>>> {
                override val context: EmptyCoroutineContext = EmptyCoroutineContext

                override fun resumeWith(resumeResult: Result<ResultWithDiagnostics<KClass<*>>>): Unit {
                    result = resumeResult
                }
            },
        )

        return requireNotNull(result).getOrThrow()
    }
}
