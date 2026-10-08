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
import kotlin.script.experimental.jvm.util.forAllMatchingFiles
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

public class JvmClassLoaderUtilKtTest {
    @Test
    public fun readsMatchingFilesFromClassLoaderResources(@TempDir directory: File): Unit {
        val manifest: File = File(directory, "META-INF/MANIFEST.MF")
        assertTrue(manifest.parentFile.mkdirs())
        manifest.writeText("Manifest-Version: 1.0\n")
        val script: File = File(directory, "example.kts")
        script.writeText("42\n")
        val classLoader: ClassLoader = ResourceClassLoader(manifest.toURI().toURL())
        val matches: MutableList<String> = mutableListOf()

        classLoader.forAllMatchingFiles("*.kts", "META-INF/MANIFEST.MF") { name, input ->
            input.use { stream ->
                assertEquals("42\n", stream.readBytes().toString(Charsets.UTF_8))
            }
            matches += name
            kotlin.Unit
        }

        assertEquals(listOf("example.kts"), matches)
    }

    private class ResourceClassLoader(private val resource: URL) : ClassLoader(null) {
        override fun getResources(name: String) = Collections.enumeration(listOf(resource))
    }
}
