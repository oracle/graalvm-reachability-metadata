/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_scripting_jvm

import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.Enumeration
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.script.experimental.jvm.util.classpathFromClassloader
import kotlin.script.experimental.jvm.util.classpathFromFQN
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

public class JvmClasspathUtilKtTest {
    @Test
    public fun discoversClasspathsThroughGetAllParentsAndGetUrls(@TempDir tempDir: Path): Unit {
        val parentDirectory: Path = Files.createDirectory(tempDir.resolve("method-parent"))
        val ownDirectory: Path = Files.createDirectory(tempDir.resolve("method-own"))

        URLClassLoader(arrayOf(parentDirectory.toUri().toURL()), null).use { parentLoader: URLClassLoader ->
            val classLoader: MethodParentClassLoader = MethodParentClassLoader(
                urls = listOf(ownDirectory.toUri().toURL()),
                parents = arrayOf(parentLoader),
            )

            val classpath: List<java.io.File> = classpathFromClassloader(classLoader).orEmpty()

            assertThat(classpath).contains(parentDirectory.toFile(), ownDirectory.toFile())
        }
    }

    @Test
    public fun discoversClasspathsThroughLegacyMyParentsField(@TempDir tempDir: Path): Unit {
        val parentDirectory: Path = Files.createDirectory(tempDir.resolve("field-parent"))
        val ownDirectory: Path = Files.createDirectory(tempDir.resolve("field-own"))

        URLClassLoader(arrayOf(parentDirectory.toUri().toURL()), null).use { parentLoader: URLClassLoader ->
            val classLoader: LegacyFieldParentClassLoader = LegacyFieldParentClassLoader(
                urls = listOf(ownDirectory.toUri().toURL()),
                parents = arrayOf(parentLoader),
            )

            val classpath: List<java.io.File> = classpathFromClassloader(classLoader).orEmpty()

            assertThat(classpath).contains(parentDirectory.toFile(), ownDirectory.toFile())
        }
    }

    @Test
    public fun unpacksJarCollectionsFromClassLoaderResources(@TempDir tempDir: Path): Unit {
        val jarCollection: Path = createJarCollection(tempDir.resolve("application.jar"))
        val resourceUrl: URL = URL("jar:${jarCollection.toUri().toURL()}!/BOOT-INF/classes")
        val classLoader: FixedResourceClassLoader = FixedResourceClassLoader(
            resourcesByName = mapOf(
                "BOOT-INF/classes" to listOf(resourceUrl),
                "BOOT-INF/lib" to listOf(URL("jar:${jarCollection.toUri().toURL()}!/BOOT-INF/lib")),
            ),
        )

        val classpath: List<java.io.File> = classpathFromClassloader(classLoader, unpackJarCollections = true).orEmpty()

        assertThat(classpath.map { file: java.io.File -> file.invariantSeparatorsPath })
            .anySatisfy { path: String -> assertThat(path).endsWith("BOOT-INF/classes") }
            .anySatisfy { path: String -> assertThat(path).endsWith("BOOT-INF/lib/nested.jar") }
    }

    @Test
    public fun derivesClasspathRootFromKeyResourcePath(@TempDir tempDir: Path): Unit {
        val rootDirectory: Path = Files.createDirectory(tempDir.resolve("resource-root"))
        val markerClass: Path = rootDirectory.resolve("example/Marker.class")
        Files.createDirectories(markerClass.parent)
        Files.write(markerClass, byteArrayOf(0xca.toByte(), 0xfe.toByte(), 0xba.toByte(), 0xbe.toByte()))
        val classLoader: FixedResourceClassLoader = FixedResourceClassLoader(
            resourcesByName = mapOf("example/Marker.class" to listOf(markerClass.toUri().toURL())),
        )

        val classpath: List<java.io.File> = classpathFromFQN(classLoader, "example.Marker").orEmpty()

        assertThat(classpath).containsExactly(rootDirectory.toFile())
    }

    private fun createJarCollection(path: Path): Path {
        JarOutputStream(Files.newOutputStream(path)).use { jarOutputStream: JarOutputStream ->
            jarOutputStream.putNextEntry(JarEntry("BOOT-INF/classes/"))
            jarOutputStream.closeEntry()
            jarOutputStream.putNextEntry(JarEntry("BOOT-INF/classes/application.properties"))
            jarOutputStream.write("name=test".toByteArray())
            jarOutputStream.closeEntry()
            jarOutputStream.putNextEntry(JarEntry("BOOT-INF/lib/nested.jar"))
            jarOutputStream.write(byteArrayOf(0))
            jarOutputStream.closeEntry()
        }
        return path
    }

    private class MethodParentClassLoader(
        private val urls: List<URL>,
        private val parents: Array<ClassLoader>,
    ) : ClassLoader(null) {
        @Suppress("unused")
        private fun getAllParents(): Array<ClassLoader> = parents

        @Suppress("unused")
        public fun getUrls(): List<URL> = urls
    }

    private class LegacyFieldParentClassLoader(
        private val urls: List<URL>,
        parents: Array<ClassLoader>,
    ) : ClassLoader(null) {
        @Suppress("unused")
        private val myParents: Array<ClassLoader> = parents

        @Suppress("unused")
        public fun getUrls(): List<URL> = urls
    }

    private class FixedResourceClassLoader(
        private val resourcesByName: Map<String, List<URL>>,
    ) : ClassLoader(null) {
        override fun getResource(name: String): URL? = resourcesByName[name]?.firstOrNull()

        override fun getResources(name: String): Enumeration<URL> =
            Collections.enumeration(resourcesByName[name].orEmpty())
    }
}
