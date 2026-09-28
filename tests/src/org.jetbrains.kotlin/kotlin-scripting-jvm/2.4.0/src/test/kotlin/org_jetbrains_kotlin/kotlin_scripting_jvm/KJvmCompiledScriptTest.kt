/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package org_jetbrains_kotlin.kotlin_scripting_jvm

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.reflect.KClass
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.jvm.impl.KJvmCompiledModuleFromClassLoader
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

public class KJvmCompiledScriptTest {
    @Test
    public fun restoresCompiledScriptAndResolvesItsClass(): Unit {
        val original: KJvmCompiledScript = KJvmCompiledScript(
            sourceLocationId = "memory://serializable-script.kts",
            compilationConfiguration = ScriptCompilationConfiguration {},
            scriptClassFQName = RoundTripScript::class.java.name,
            resultField = null,
            otherScripts = emptyList(),
            compiledModule = null,
        )

        val serialized: ByteArray = ByteArrayOutputStream().use { output: ByteArrayOutputStream ->
            ObjectOutputStream(output).use { objectOutput: ObjectOutputStream ->
                objectOutput.writeObject(original)
            }
            output.toByteArray()
        }
        val restored: KJvmCompiledScript = ByteArrayInputStream(serialized).use { input: ByteArrayInputStream ->
            ObjectInputStream(input).use { objectInput: ObjectInputStream ->
                objectInput.readObject() as KJvmCompiledScript
            }
        }

        assertEquals(original.sourceLocationId, restored.sourceLocationId)
        val resolvable: KJvmCompiledScript = KJvmCompiledScript(
            sourceLocationId = restored.sourceLocationId,
            compilationConfiguration = restored.compilationConfiguration,
            scriptClassFQName = RoundTripScript::class.java.name,
            resultField = null,
            otherScripts = emptyList(),
            compiledModule = KJvmCompiledModuleFromClassLoader(RoundTripScript::class.java.classLoader),
        )
        val resolved: ResultWithDiagnostics<KClass<*>> = resolveClass(resolvable)
        assertTrue(resolved is ResultWithDiagnostics.Success<*>)
        val resolvedClass: KClass<*> = (resolved as ResultWithDiagnostics.Success<KClass<*>>).value
        assertEquals(RoundTripScript::class, resolvedClass)
    }

    private fun resolveClass(script: KJvmCompiledScript): ResultWithDiagnostics<KClass<*>> {
        var result: Result<ResultWithDiagnostics<KClass<*>>>? = null

        suspend { script.getClass(ScriptEvaluationConfiguration {}) }.startCoroutine(
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

public class RoundTripScript
