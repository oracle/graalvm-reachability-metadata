/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_main_kts

import kotlin.script.experimental.jvm.util.classPathFromTypicalResourceUrls
import kotlin.script.experimental.jvm.util.classpathFromClassloader
import kotlin.script.experimental.jvm.util.classpathFromFQN
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.Enumeration
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

public class JvmClasspathUtilKtTest {
    @Test
    fun readsClasspathFromClassLoaderGetUrlsMethod(@TempDir tempDir: Path): Unit {
        val classesDirectory: Path = Files.createDirectory(tempDir.resolve("classes"))
        val ignoredTextFile: Path = Files.createFile(tempDir.resolve("ignored.txt"))
        val classLoader = GetUrlsClassLoader(
            listOf(classesDirectory.toUri().toURL(), ignoredTextFile.toUri().toURL()),
        )

        val classpath: List<File> = requireNotNull(classpathFromClassloader(classLoader))

        assertThat(classpath).contains(classesDirectory.toFile())
        assertThat(classpath).doesNotContain(ignoredTextFile.toFile())
    }

    @Test
    fun walksClassLoadersFromNewParentAccessor(@TempDir tempDir: Path): Unit {
        val parentClassesDirectory: Path = Files.createDirectory(tempDir.resolve("new-parent-classes"))
        val parentClassLoader = GetUrlsClassLoader(listOf(parentClassesDirectory.toUri().toURL()))
        val classLoader = NewParentAccessorClassLoader(arrayOf(parentClassLoader))

        val classpath: List<File> = requireNotNull(classpathFromClassloader(classLoader))

        assertThat(classpath).contains(parentClassesDirectory.toFile())
    }

    @Test
    fun walksClassLoadersFromOldParentField(@TempDir tempDir: Path): Unit {
        val parentClassesDirectory: Path = Files.createDirectory(tempDir.resolve("old-parent-classes"))
        val parentClassLoader = GetUrlsClassLoader(listOf(parentClassesDirectory.toUri().toURL()))
        val classLoader = OldParentFieldClassLoader(arrayOf(parentClassLoader))

        val classpath: List<File> = requireNotNull(classpathFromClassloader(classLoader))

        assertThat(classpath).contains(parentClassesDirectory.toFile())
    }

    @Test
    fun locatesClasspathRootFromClassResource(@TempDir tempDir: Path): Unit {
        val classpathRoot: Path = Files.createDirectory(tempDir.resolve("resource-root"))
        val packageDirectory: Path = Files.createDirectories(classpathRoot.resolve("example"))
        val classResource: Path = Files.createFile(packageDirectory.resolve("Marker.class"))
        val classLoader = ResourceBackedClassLoader(
            resourcesByName = mapOf("example/Marker.class" to listOf(classResource.toUri().toURL())),
        )

        val classpath: List<File> = requireNotNull(classpathFromFQN(classLoader, "example.Marker"))

        assertThat(classpath).containsExactly(classpathRoot.toFile())
    }

    @Test
    fun discoversTypicalClasspathRootsFromResources(@TempDir tempDir: Path): Unit {
        val classpathRoot: Path = Files.createDirectory(tempDir.resolve("typical-resource-root"))
        val manifestDirectory: Path = Files.createDirectories(classpathRoot.resolve("META-INF"))
        val manifestResource: Path = Files.createFile(manifestDirectory.resolve("MANIFEST.MF"))
        val classLoader = ResourceBackedClassLoader(
            resourcesByName = mapOf("META-INF/MANIFEST.MF" to listOf(manifestResource.toUri().toURL())),
        )

        val classpath: List<File> = classLoader.classPathFromTypicalResourceUrls().toList()

        assertThat(classpath).containsExactly(classpathRoot.toFile())
    }

    @Test
    fun unpacksJarCollectionsDiscoveredFromClassLoaderResources(@TempDir tempDir: Path): Unit {
        val jarCollection: Path = createJarCollection(tempDir.resolve("application.jar"))
        val classesResource = URL("jar:${jarCollection.toUri().toURL()}!/BOOT-INF/classes")
        val libraryResource = URL("jar:${jarCollection.toUri().toURL()}!/BOOT-INF/lib")
        val classLoader = ResourceBackedClassLoader(
            resourcesByName = mapOf(
                "BOOT-INF/classes" to listOf(classesResource),
                "BOOT-INF/lib" to listOf(libraryResource),
            ),
        )

        val classpath: List<File> = requireNotNull(classpathFromClassloader(classLoader, unpackJarCollections = true))

        assertThat(classpath.map { it.invariantSeparatorsPath() })
            .anySatisfy { path: String -> assertThat(path).endsWith("/BOOT-INF/classes") }
            .anySatisfy { path: String -> assertThat(path).endsWith("/BOOT-INF/lib/nested.jar") }
    }

    private fun createJarCollection(jarPath: Path): Path {
        JarOutputStream(Files.newOutputStream(jarPath)).use { jarOutput: JarOutputStream ->
            jarOutput.putNextEntry(JarEntry("BOOT-INF/classes/example.txt"))
            jarOutput.write("compiled classes marker".toByteArray())
            jarOutput.closeEntry()

            jarOutput.putNextEntry(JarEntry("BOOT-INF/lib/nested.jar"))
            jarOutput.write("nested jar marker".toByteArray())
            jarOutput.closeEntry()
        }
        return jarPath
    }

    private fun File.invariantSeparatorsPath(): String = absolutePath.replace(File.separatorChar, '/')

    public open class ResourceBackedClassLoader(
        parent: ClassLoader? = null,
        private val resourcesByName: Map<String, List<URL>> = emptyMap(),
    ) : ClassLoader(parent) {
        override fun getResource(name: String): URL? = resourcesByName[name]?.firstOrNull()

        override fun getResources(name: String): Enumeration<URL> = Collections.enumeration(resourcesByName[name].orEmpty())
    }

    public class GetUrlsClassLoader(private val urlEntries: List<URL>) : ResourceBackedClassLoader() {
        @Suppress("unused")
        public fun getUrls(): List<URL> = urlEntries
    }

    public class NewParentAccessorClassLoader(private val parents: Array<ClassLoader>) : ResourceBackedClassLoader() {
        @Suppress("unused")
        private fun getAllParents(): Array<ClassLoader> = parents
    }

    public class OldParentFieldClassLoader(
        @Suppress("unused")
        private val myParents: Array<ClassLoader>,
    ) : ResourceBackedClassLoader()
}
