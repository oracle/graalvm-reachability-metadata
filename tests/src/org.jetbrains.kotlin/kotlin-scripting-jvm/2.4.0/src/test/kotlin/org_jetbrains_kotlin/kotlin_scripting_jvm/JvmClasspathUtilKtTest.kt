/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_scripting_jvm

import java.io.File
import java.net.URL
import java.util.Collections
import kotlin.script.experimental.jvm.util.classpathFromClassloader
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

public class JvmClasspathUtilKtTest {
    @Test
    public fun discoversClasspathsThroughGetAllParentsAndGetUrls(@TempDir directory: File): Unit {
        val classLoader: NewStyleClassLoader = NewStyleClassLoader(directory)

        val classpath: List<File> = classpathFromClassloader(classLoader, true) ?: emptyList()

        assertTrue(classpath.contains(directory))
        assertTrue(classLoader.requestedResources.isNotEmpty())
    }

    @Test
    public fun discoversClasspathsThroughLegacyMyParentsField(@TempDir directory: File): Unit {
        val classLoader: OldStyleClassLoader = OldStyleClassLoader(directory)

        val classpath: List<File> = classpathFromClassloader(classLoader, false) ?: emptyList()

        assertTrue(classpath.contains(directory))
    }

    @Test
    public fun discoversClasspathsThroughClassLoaderResources(@TempDir directory: File): Unit {
        val resourceDirectory: File = File(directory, "META-INF")
        assertTrue(resourceDirectory.mkdirs())
        val manifest: File = File(resourceDirectory, "MANIFEST.MF")
        manifest.writeText("Manifest-Version: 1.0\n")
        assertTrue(manifest.isFile)
        val classLoader: ResourceClassLoader = ResourceClassLoader(manifest.toURI().toURL())

        val classpath: List<File> = classpathFromClassloader(classLoader, false) ?: emptyList()

        assertTrue(classpath.contains(directory))
    }

    private class NewStyleClassLoader(private val classpathRoot: File) : ClassLoader(null) {
        val requestedResources: MutableList<String> = mutableListOf()

        @Suppress("unused")
        private fun getAllParents(): Array<ClassLoader> = emptyArray()

        @Suppress("unused")
        public fun getUrls(): List<URL> = listOf(classpathRoot.toURI().toURL())

        override fun getResource(name: String): URL {
            requestedResources += name
            return classpathRoot.toURI().toURL()
        }

        override fun getResources(name: String) = Collections.enumeration(listOf(getResource(name)))
    }

    private class OldStyleClassLoader(private val classpathRoot: File) : ClassLoader(null) {
        @Suppress("unused")
        private val myParents: Array<ClassLoader> = arrayOf(ClassLoader.getSystemClassLoader())

        @Suppress("unused")
        public fun getUrls(): List<URL> = listOf(classpathRoot.toURI().toURL())
    }

    private class ResourceClassLoader(private val resource: URL) : ClassLoader(null) {
        override fun getResources(name: String) = Collections.enumeration(listOf(resource))
    }
}
