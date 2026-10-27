/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package org_jetbrains_kotlin.kotlin_scripting_jvm

import java.io.InputStream
import kotlin.script.experimental.jvm.impl.KJvmCompiledModuleFromClassLoader
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

public class DualClassLoaderTest {
    @Test
    public fun loadsClassesAndResourcesFromTheModuleClassLoader(): Unit {
        val moduleLoader: ClassLoader = KJvmCompiledModuleFromClassLoader::class.java.classLoader
        val baseLoader: ClassLoader = object : ClassLoader(null) {}
        val combinedLoader: ClassLoader = KJvmCompiledModuleFromClassLoader(moduleLoader)
            .createClassLoader(baseLoader)

        val loadedClass: Class<*> = combinedLoader.loadClass(
            "kotlin.script.experimental.jvm.impl.KJvmCompiledModuleFromClassLoader",
        )
        assertEquals(
            "kotlin.script.experimental.jvm.impl.KJvmCompiledModuleFromClassLoader",
            loadedClass.name,
        )

        val classBytes: ByteArray = combinedLoader.getResourceAsStream(
            "kotlin/script/experimental/jvm/impl/KJvmCompiledModuleFromClassLoader.class",
        ).use { stream: InputStream? ->
            requireNotNull(stream).readBytes()
        }
        assertArrayEquals(
            byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()),
            classBytes.copyOf(4),
        )
    }
}
