/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_scripting_compiler_embeddable

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.CompiledScriptClassLoader
import org.junit.jupiter.api.Test

public class CompiledScriptClassLoaderTest {
    @Test
    public fun delegatesMissingResourceStreamLookupToParentClassLoader(): Unit {
        val resourceName: String = "parent-only-resource.txt"
        val resourceText: String = "resource provided by the parent loader"
        val parentLoader: ClassLoader = ByteArrayResourceClassLoader(
            resourceName = resourceName,
            resourceBytes = resourceText.toByteArray(Charsets.UTF_8),
        )
        val scriptLoader: CompiledScriptClassLoader = CompiledScriptClassLoader(
            parent = parentLoader,
            entries = mapOf("compiled-script-resource.txt" to "compiled resource".toByteArray(Charsets.UTF_8)),
        )

        scriptLoader.getResourceAsStream(resourceName).use { stream: InputStream? ->
            assertThat(stream).isNotNull
            val content: String = requireNotNull(stream).readBytes().toString(Charsets.UTF_8)
            assertThat(content).isEqualTo(resourceText)
        }
    }
}

private class ByteArrayResourceClassLoader(
    private val resourceName: String,
    private val resourceBytes: ByteArray,
) : ClassLoader(null) {
    override fun findResource(name: String): URL? =
        if (name == resourceName) {
            URL(null, "memory:///$name", ByteArrayUrlStreamHandler(resourceBytes))
        } else {
            null
        }
}

private class ByteArrayUrlStreamHandler(
    private val resourceBytes: ByteArray,
) : URLStreamHandler() {
    override fun openConnection(url: URL): URLConnection =
        object : URLConnection(url) {
            override fun connect(): Unit = Unit

            override fun getInputStream(): InputStream = ByteArrayInputStream(resourceBytes)
        }
}
