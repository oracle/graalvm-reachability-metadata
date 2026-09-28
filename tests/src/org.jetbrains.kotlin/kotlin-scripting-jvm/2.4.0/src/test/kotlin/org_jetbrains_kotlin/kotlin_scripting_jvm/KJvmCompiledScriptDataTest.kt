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
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.jvm.impl.KJvmCompiledScriptData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

public class KJvmCompiledScriptDataTest {
    @Test
    public fun serializesCompiledScriptDataThroughCompiledScriptRoundTrip(): Unit {
        val original: KJvmCompiledScriptData = KJvmCompiledScriptData(
            sourceLocationId = "memory://compiled-script.kts",
            compilationConfiguration = ScriptCompilationConfiguration {},
            scriptClassFQName = "example.CompiledScript",
            resultField = "result" to KotlinType("kotlin.String"),
            otherScripts = emptyList(),
        )
        val serialized: ByteArray = ByteArrayOutputStream().use { output: ByteArrayOutputStream ->
            ObjectOutputStream(output).use { objectOutput: ObjectOutputStream ->
                objectOutput.writeObject(original)
            }
            output.toByteArray()
        }

        val restored: KJvmCompiledScriptData = ByteArrayInputStream(serialized).use { input: ByteArrayInputStream ->
            ObjectInputStream(input).use { objectInput: ObjectInputStream ->
                objectInput.readObject() as KJvmCompiledScriptData
            }
        }

        assertEquals(original.sourceLocationId, restored.sourceLocationId)
        assertEquals(original.compilationConfiguration, restored.compilationConfiguration)
        assertEquals(original.scriptClassFQName, restored.scriptClassFQName)
        assertEquals(original.resultField, restored.resultField)
        assertEquals(original.otherScripts, restored.otherScripts)
    }
}
