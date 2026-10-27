/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_scripting_jvm

import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.jvm.impl.toBytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class KJvmCompiledScriptDataTest {
    @Test
    fun serializesCompiledScriptDataThroughCompiledScriptRoundTrip(): Unit {
        val sourceLocationId = "memory://round-trip.kts"
        val resultType = KotlinType("kotlin.String")
        val script = KJvmCompiledScript(
            sourceLocationId = sourceLocationId,
            compilationConfiguration = ScriptCompilationConfiguration {},
            scriptClassFQName = "example.RoundTripScript",
            resultField = "result" to resultType,
            otherScripts = emptyList(),
            compiledModule = null,
        )

        val restored = deserializeCompiledScript(script.toBytes())

        assertThat(restored.sourceLocationId).isEqualTo(sourceLocationId)
        assertThat(restored.compilationConfiguration.isEmpty()).isTrue()
        assertThat(restored.scriptClassFQName).isEqualTo("example.RoundTripScript")
        assertThat(restored.resultField).isEqualTo("result" to resultType)
        assertThat(restored.otherScripts).isEmpty()
        assertThat(restored.getCompiledModule()).isNull()
    }

    private fun deserializeCompiledScript(serialized: ByteArray): KJvmCompiledScript {
        return ObjectInputStream(ByteArrayInputStream(serialized)).use { objectStream ->
            objectStream.readObject() as KJvmCompiledScript
        }
    }
}
