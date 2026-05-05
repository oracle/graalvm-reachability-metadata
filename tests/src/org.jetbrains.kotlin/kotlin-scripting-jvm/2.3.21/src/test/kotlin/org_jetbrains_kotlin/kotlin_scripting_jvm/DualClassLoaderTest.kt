/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_scripting_jvm

import example.FallbackOnly
import java.io.InputStream
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import kotlin.script.experimental.jvm.impl.KJvmCompiledModuleFromClassLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class DualClassLoaderTest {
    @Test
    fun loadsClassFromFallbackLoaderWhenBaseLoaderCannotFindIt(): Unit {
        val fallbackOnlyClassName: String = FallbackOnly::class.java.name
        val moduleClassLoader: ClassLoader = FallbackClassLoader(fallbackOnlyClassName, FallbackOnly::class.java)
        val baseClassLoader: ClassLoader = EmptyClassLoader()
        val scriptClassLoader: ClassLoader = KJvmCompiledModuleFromClassLoader(moduleClassLoader)
            .createClassLoader(baseClassLoader)

        val loadedClass: Class<*> = scriptClassLoader.loadClass(fallbackOnlyClassName)

        assertThat(loadedClass).isSameAs(FallbackOnly::class.java)
    }

    @Test
    fun opensResourceStreamFromFallbackLoaderWhenBaseLoaderCannotFindIt(): Unit {
        val resourceName: String = "fallback/resource.txt"
        val resourceContent: String = "loaded from fallback"
        val moduleClassLoader: ClassLoader = FallbackResourceClassLoader(resourceName, resourceContent)
        val baseClassLoader: ClassLoader = EmptyClassLoader()
        val scriptClassLoader: ClassLoader = KJvmCompiledModuleFromClassLoader(moduleClassLoader)
            .createClassLoader(baseClassLoader)

        val actualContent: String? = scriptClassLoader.getResourceAsStream(resourceName)?.use { inputStream ->
            inputStream.bufferedReader().readText()
        }

        assertThat(actualContent).isEqualTo(resourceContent)
    }
    private class EmptyClassLoader : ClassLoader(null)

    private class FallbackClassLoader(
        private val fallbackOnlyClassName: String,
        private val fallbackClass: Class<*>,
    ) : ClassLoader(null) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            return if (name == fallbackOnlyClassName) {
                fallbackClass
            } else {
                super.loadClass(name, resolve)
            }
        }
    }

    private class FallbackResourceClassLoader(
        private val resourceName: String,
        private val resourceContent: String,
    ) : ClassLoader(null) {
        override fun getResource(name: String): URL? {
            return if (name == resourceName) {
                inMemoryUrl(resourceContent)
            } else {
                super.getResource(name)
            }
        }

        private fun inMemoryUrl(content: String): URL {
            return URL(null, "memory:fallback-resource", object : URLStreamHandler() {
                override fun openConnection(url: URL): URLConnection {
                    return object : URLConnection(url) {
                        override fun connect(): Unit = Unit

                        override fun getInputStream(): InputStream = content.byteInputStream()
                    }
                }
            })
        }
    }
}
