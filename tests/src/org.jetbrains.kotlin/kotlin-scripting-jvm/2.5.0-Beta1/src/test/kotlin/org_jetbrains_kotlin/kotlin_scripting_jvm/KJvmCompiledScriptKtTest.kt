/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package org_jetbrains_kotlin.kotlin_scripting_jvm

import java.io.ByteArrayInputStream
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.jvm.impl.createScriptFromClassLoader
import kotlin.script.experimental.jvm.impl.scriptMetadataPath
import kotlin.script.experimental.jvm.impl.toBytes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

public class KJvmCompiledScriptKtTest {
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
        val metadataPath: String = scriptMetadataPath(RoundTripScript::class.java.name)
        val classLoader: ClassLoader = SerializedScriptClassLoader(
            parent = RoundTripScript::class.java.classLoader,
            resourcePath = metadataPath,
            serializedScript = original.toBytes(),
        )

        val restored: KJvmCompiledScript = createScriptFromClassLoader(
            RoundTripScript::class.java.name,
            classLoader,
        )

        assertEquals(original.sourceLocationId, restored.sourceLocationId)
        assertEquals(original.scriptClassFQName, restored.scriptClassFQName)
        assertNotNull(restored.getCompiledModule())
    }

    private class SerializedScriptClassLoader(
        parent: ClassLoader,
        private val resourcePath: String,
        private val serializedScript: ByteArray,
    ) : ClassLoader(parent) {
        override fun getResourceAsStream(name: String): ByteArrayInputStream? =
            if (name == resourcePath) ByteArrayInputStream(serializedScript) else null
    }
}

public class RoundTripScript
