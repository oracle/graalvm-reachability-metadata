/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package org_jetbrains_kotlin.kotlin_scripting_jvm

import java.io.File
import kotlin.script.experimental.jvm.impl.getResourceRoot
import kotlin.script.experimental.jvm.util.KotlinJars
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

public class PathUtilKtTest {
    @Test
    public fun resolvesMarkerClassPathThroughKotlinJars(): Unit {
        val classLoader: ClassLoader = object : ClassLoader(PathUtilKtTest::class.java.classLoader) {}

        val resourceRoot: File? = KotlinJars.getLib(
            propertyName = "property-that-is-not-set",
            jarName = "jar-that-is-not-explicit",
            markerClass = PathUtilKtTest::class,
            classLoader = classLoader,
        )

        assertNotNull(resourceRoot)
        assertTrue(requireNotNull(resourceRoot).exists())
    }

    @Test
    public fun resolvesResourceThroughContextClassResource(): Unit {
        val resourceRoot: String? = getResourceRoot(
            PathUtilKtTest::class.java,
            "/${PathUtilKtTest::class.java.name.replace('.', '/')}.class",
        )

        assertNotNull(resourceRoot)
        assertTrue(requireNotNull(resourceRoot).isNotEmpty())
    }

    @Test
    public fun fallsBackToSystemResourceForBootstrapContext(): Unit {
        val resourceRoot: String? = getResourceRoot(
            String::class.java,
            "/${PathUtilKtTest::class.java.name.replace('.', '/')}.class",
        )

        assertNotNull(resourceRoot)
        assertTrue(requireNotNull(resourceRoot).isNotEmpty())
    }
}
