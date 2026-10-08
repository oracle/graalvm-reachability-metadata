/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package org_jetbrains_kotlin.kotlin_scripting_jvm

import java.io.File
import java.net.URL
import java.sql.DriverManager
import kotlin.script.experimental.jvm.impl.KJvmCompiledModuleFromClassLoader
import kotlin.script.experimental.jvm.impl.getResourceRoot
import kotlin.script.experimental.jvm.impl.tryGetResourcePathForClass
import kotlin.script.experimental.jvm.impl.tryGetResourcePathForClassByName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class PathUtilKtTest {
    @Test
    public fun resolvesResourceRootFromClassResource(): Unit {
        val libraryClass: Class<KJvmCompiledModuleFromClassLoader> = KJvmCompiledModuleFromClassLoader::class.java
        val classResourcePath: String = libraryClass.absoluteClassResourcePath()
        val classResource: URL? = libraryClass.getResource(classResourcePath)

        val resourcePath: File? = tryGetResourcePathForClass(libraryClass)

        assertExtractedRootMatchesResourceProtocol(classResource, resourcePath)
    }

    @Test
    public fun loadsClassByNameBeforeResolvingItsResourceRoot(): Unit {
        val libraryClass: Class<KJvmCompiledModuleFromClassLoader> = KJvmCompiledModuleFromClassLoader::class.java
        val classResource: URL? = libraryClass.getResource(libraryClass.absoluteClassResourcePath())

        val resourcePath: File? = tryGetResourcePathForClassByName(libraryClass.name, libraryClass.classLoader)

        assertExtractedRootMatchesResourceProtocol(classResource, resourcePath)
    }

    @Test
    public fun fallsBackToSystemResourceWhenContextClassLoaderCannotSeeResource(): Unit {
        val resourcePath: String = KJvmCompiledModuleFromClassLoader::class.java.absoluteClassResourcePath()
        val systemResource: URL? = ClassLoader.getSystemResource(resourcePath.removePrefix("/"))

        val resourceRoot: String? = getResourceRoot(DriverManager::class.java, resourcePath)

        if (systemResource.supportsPathUtilRootExtraction()) {
            assertThat(resourceRoot).isNotBlank()
            assertThat(File(resourceRoot!!)).exists()
        } else {
            assertThat(resourceRoot).isNull()
        }
    }

    private fun Class<*>.absoluteClassResourcePath(): String =
        "/" + name.replace('.', '/') + ".class"

    private fun assertExtractedRootMatchesResourceProtocol(resource: URL?, root: File?): Unit {
        if (resource.supportsPathUtilRootExtraction()) {
            assertThat(root).isNotNull()
            assertThat(root!!).exists()
        } else {
            assertThat(root).isNull()
        }
    }

    private fun URL?.supportsPathUtilRootExtraction(): Boolean =
        this != null && protocol in setOf("file", "jar")
}
