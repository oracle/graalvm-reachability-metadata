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
import kotlin.script.experimental.jvm.impl.getResourceRoot
import kotlin.script.experimental.jvm.impl.tryGetResourcePathForClass
import kotlin.script.experimental.jvm.impl.tryGetResourcePathForClassByName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class PathUtilKtTest {
    @Test
    public fun resolvesResourceRootFromClassResource(): Unit {
        val testClass: Class<PathUtilKtTest> = PathUtilKtTest::class.java
        val classResourcePath: String = testClass.absoluteClassResourcePath()
        val classResource: URL? = testClass.getResource(classResourcePath)

        val resourcePath: File? = tryGetResourcePathForClass(testClass)

        assertExtractedRootMatchesResourceProtocol(classResource, resourcePath)
    }

    @Test
    public fun loadsClassByNameBeforeResolvingItsResourceRoot(): Unit {
        val testClass: Class<PathUtilKtTest> = PathUtilKtTest::class.java
        val classResource: URL? = testClass.getResource(testClass.absoluteClassResourcePath())

        val resourcePath: File? = tryGetResourcePathForClassByName(testClass.name, testClass.classLoader)

        assertExtractedRootMatchesResourceProtocol(classResource, resourcePath)
    }

    @Test
    public fun fallsBackToSystemResourceWhenContextClassLoaderCannotSeeResource(): Unit {
        val resourcePath: String = "/org/junit/jupiter/api/Test.class"
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
