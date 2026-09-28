/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_scripting_jvm

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.jvm.impl.createScriptFromClassLoader
import kotlin.script.experimental.jvm.impl.scriptMetadataPath
import kotlin.script.experimental.jvm.impl.toBytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class KJvmCompiledScriptKtTest {
    @Test
    public fun createsCompiledScriptFromSerializedMetadataResource(): Unit {
        val scriptClassFQName: String = "example.MetadataBackedScript"
        val resultField: Pair<String, KotlinType> = "result" to KotlinType("kotlin.String")
        val originalScript: KJvmCompiledScript = KJvmCompiledScript(
            sourceLocationId = "memory://metadata-backed.kts",
            compilationConfiguration = ScriptCompilationConfiguration {},
            scriptClassFQName = scriptClassFQName,
            resultField = resultField,
            otherScripts = emptyList(),
            compiledModule = null,
        )
        val classLoader: InMemoryScriptMetadataClassLoader = InMemoryScriptMetadataClassLoader(
            resourceName = scriptMetadataPath(scriptClassFQName),
            resourceBytes = originalScript.toBytes(),
        )

        val restoredScript: KJvmCompiledScript = createScriptFromClassLoader(scriptClassFQName, classLoader)

        assertThat(restoredScript.sourceLocationId).isEqualTo("memory://metadata-backed.kts")
        assertThat(restoredScript.compilationConfiguration.isEmpty()).isTrue()
        assertThat(restoredScript.scriptClassFQName).isEqualTo(scriptClassFQName)
        assertThat(restoredScript.resultField).isEqualTo(resultField)
        assertThat(restoredScript.otherScripts).isEmpty()
        assertThat(requireNotNull(restoredScript.getCompiledModule()).createClassLoader(null)).isSameAs(classLoader)
    }

    private class InMemoryScriptMetadataClassLoader(
        private val resourceName: String,
        private val resourceBytes: ByteArray,
    ) : ClassLoader(KJvmCompiledScriptKtTest::class.java.classLoader) {
        override fun getResourceAsStream(name: String): InputStream? {
            return if (name == resourceName) ByteArrayInputStream(resourceBytes) else super.getResourceAsStream(name)
        }
    }
}
