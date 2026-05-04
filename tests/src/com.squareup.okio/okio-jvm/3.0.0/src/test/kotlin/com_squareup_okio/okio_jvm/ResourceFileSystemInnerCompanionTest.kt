/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okio.okio_jvm

import okio.Path.Companion.toPath
import okio.asResourceFileSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URL
import java.util.Collections
import java.util.Enumeration

public class ResourceFileSystemInnerCompanionTest {
    @Test
    fun asResourceFileSystemQueriesClasspathRootResources(): Unit {
        val classLoader: RecordingResourceClassLoader = RecordingResourceClassLoader()
        val resourceFileSystem = classLoader.asResourceFileSystem()

        assertThat(resourceFileSystem.metadataOrNull("/anything.txt".toPath())).isNull()
        assertThat(classLoader.requestedResourceNames)
            .containsExactly("", "META-INF/MANIFEST.MF")
    }

    private class RecordingResourceClassLoader : ClassLoader(null) {
        val requestedResourceNames: MutableList<String> = mutableListOf()

        override fun findResources(name: String): Enumeration<URL> {
            requestedResourceNames += name
            return Collections.emptyEnumeration()
        }
    }
}
