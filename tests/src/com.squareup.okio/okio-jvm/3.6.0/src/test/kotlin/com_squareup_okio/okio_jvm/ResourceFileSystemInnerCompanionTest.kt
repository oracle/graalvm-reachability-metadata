/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okio.okio_jvm

import okio.FileSystem
import okio.Path.Companion.toPath
import okio.asResourceFileSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path as NioPath
import java.util.Collections
import java.util.Enumeration

public class ResourceFileSystemInnerCompanionTest {
    @Test
    fun classLoaderResourceFileSystemIndexesClasspathRoots(@TempDir temporaryDirectory: NioPath): Unit {
        Files.writeString(temporaryDirectory.resolve("okio-resource-file-system.txt"), "indexed resource")
        Files.createDirectories(temporaryDirectory.resolve("META-INF"))
        Files.writeString(temporaryDirectory.resolve("META-INF").resolve("MANIFEST.MF"), "Manifest-Version: 1.0\n")

        val classLoader: ClassLoader = DirectoryResourceClassLoader(temporaryDirectory)
        val resources: FileSystem = classLoader.asResourceFileSystem()

        val content: String = resources.read("/okio-resource-file-system.txt".toPath()) {
            readUtf8()
        }

        assertThat(content).isEqualTo("indexed resource")
        assertThat(resources.list("/".toPath()))
            .contains("/okio-resource-file-system.txt".toPath(), "/META-INF".toPath())
    }

    private class DirectoryResourceClassLoader(
        private val root: NioPath,
    ) : ClassLoader(null) {
        override fun findResources(name: String): Enumeration<URL> {
            val urls: List<URL> = when (name) {
                "" -> listOf(root.toUri().toURL())
                "META-INF/MANIFEST.MF" -> listOf(root.resolve(name).toUri().toURL())
                else -> {
                    val candidate: NioPath = root.resolve(name)
                    if (Files.exists(candidate)) listOf(candidate.toUri().toURL()) else emptyList()
                }
            }
            return Collections.enumeration(urls)
        }
    }
}
