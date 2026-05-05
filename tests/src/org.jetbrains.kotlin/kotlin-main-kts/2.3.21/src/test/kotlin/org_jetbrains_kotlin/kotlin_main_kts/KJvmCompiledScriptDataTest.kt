/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_main_kts

import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.displayName
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

public class KJvmCompiledScriptDataTest {
    @Test
    fun serializesCompiledScriptDataThroughCompiledScriptRoundTrip(): Unit {
        val configurationDisplayName: String = "serializable main kts script"
        val script: KJvmCompiledScript = KJvmCompiledScript(
            "memory://serializable.main.kts",
            ScriptCompilationConfiguration {
                displayName(configurationDisplayName)
            },
            "example.SerializableMainKtsScript",
            "result" to KotlinType("kotlin.String"),
            emptyList(),
            null,
        )

        val copy: KJvmCompiledScript = roundTrip(script)

        assertThat(copy.sourceLocationId).isEqualTo("memory://serializable.main.kts")
        assertThat(copy.compilationConfiguration[ScriptCompilationConfiguration.displayName]).isEqualTo(configurationDisplayName)
        assertThat(copy.otherScripts).isEmpty()
        assertThat(copy.scriptClassFQName).isEqualTo("example.SerializableMainKtsScript")
        assertThat(copy.resultField?.first).isEqualTo("result")
        assertThat(copy.resultField?.second?.typeName).isEqualTo("kotlin.String")
        assertThat(copy.getCompiledModule()).isNull()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> roundTrip(value: T): T {
        val bytes: ByteArray = ByteArrayOutputStream().use { byteOutput: ByteArrayOutputStream ->
            ObjectOutputStream(byteOutput).use { objectOutput: ObjectOutputStream ->
                objectOutput.writeObject(value)
            }
            byteOutput.toByteArray()
        }

        return ObjectInputStream(ByteArrayInputStream(bytes)).use { objectInput: ObjectInputStream ->
            objectInput.readObject() as T
        }
    }
}
