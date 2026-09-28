/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package org_jetbrains_kotlin.kotlin_scripting_jvm

import java.io.ByteArrayInputStream
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.reflect.KClass
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.jvm.impl.createScriptFromClassLoader
import kotlin.script.experimental.jvm.impl.scriptMetadataPath
import kotlin.script.experimental.jvm.impl.toBytes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

public class KJvmCompiledScriptTest {
    @Test
    public fun restoresCompiledScriptFromItsMetadataResource(): Unit {
        val original: KJvmCompiledScript = KJvmCompiledScript(
            sourceLocationId = "memory://serializable-script.kts",
            compilationConfiguration = ScriptCompilationConfiguration {},
            scriptClassFQName = RoundTripScript::class.java.name,
            resultField = null,
            otherScripts = emptyList(),
            compiledModule = null,
        )
        val serialized: ByteArray = original.toBytes()
        val metadataPath: String = scriptMetadataPath(RoundTripScript::class.java.name)
        val classLoader: ClassLoader = SerializedScriptClassLoader(
            parent = RoundTripScript::class.java.classLoader,
            resourcePath = metadataPath,
            serializedScript = serialized,
        )

        val restored: KJvmCompiledScript = createScriptFromClassLoader(
            RoundTripScript::class.java.name,
            classLoader,
        )

        assertEquals(original.sourceLocationId, restored.sourceLocationId)
        val resolved: ResultWithDiagnostics<KClass<*>> = resolveClass(restored)
        assertTrue(resolved is ResultWithDiagnostics.Success<*>)
        val resolvedClass: KClass<*> = (resolved as ResultWithDiagnostics.Success<KClass<*>>).value
        assertEquals(RoundTripScript::class, resolvedClass)
    }

    private class SerializedScriptClassLoader(
        parent: ClassLoader,
        private val resourcePath: String,
        private val serializedScript: ByteArray,
    ) : ClassLoader(parent) {
        override fun getResourceAsStream(name: String): ByteArrayInputStream? =
            if (name == resourcePath) ByteArrayInputStream(serializedScript) else null
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
