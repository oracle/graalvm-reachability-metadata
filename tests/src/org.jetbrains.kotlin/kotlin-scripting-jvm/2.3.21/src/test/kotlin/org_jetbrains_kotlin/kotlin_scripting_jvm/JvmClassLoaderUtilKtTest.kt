/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_scripting_jvm

import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.Enumeration
import kotlin.script.experimental.jvm.util.forAllMatchingFiles
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

public class JvmClassLoaderUtilKtTest {
    @Test
    public fun scansDirectoryRootDiscoveredFromClassLoaderResources(@TempDir tempDir: Path): Unit {
        val rootDirectory: Path = Files.createDirectory(tempDir.resolve("classpath-root"))
        val scriptDirectory: Path = Files.createDirectory(rootDirectory.resolve("scripts"))
        val marker: Path = scriptDirectory.resolve("marker.resource")
        val matchingScript: Path = scriptDirectory.resolve("main.kts")
        Files.writeString(marker, "marker")
        Files.writeString(matchingScript, "println(\"covered\")")
        Files.writeString(scriptDirectory.resolve("notes.txt"), "ignored")
        val classLoader: FixedResourceClassLoader = FixedResourceClassLoader(
            resourcesByName = mapOf("scripts/marker.resource" to listOf(marker.toUri().toURL())),
        )
        val matches: MutableList<Pair<String, String>> = mutableListOf()

        classLoader.forAllMatchingFiles("scripts/*.kts", "scripts/marker.resource") { path: String, stream: InputStream ->
            matches.add(path to stream.bufferedReader().use { reader -> reader.readText() })
        }

        assertThat(matches).containsExactly("scripts/main.kts" to "println(\"covered\")")
    }

    private class FixedResourceClassLoader(
        private val resourcesByName: Map<String, List<URL>>,
    ) : ClassLoader(null) {
        override fun getResources(name: String): Enumeration<URL> =
            Collections.enumeration(resourcesByName[name].orEmpty())
    }
}
