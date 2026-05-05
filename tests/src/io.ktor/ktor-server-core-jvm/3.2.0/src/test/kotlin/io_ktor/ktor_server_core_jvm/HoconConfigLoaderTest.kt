/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_core_jvm

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.HoconConfigLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.Enumeration

public class HoconConfigLoaderTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `loader reads configuration found through context class loader resource`(): Unit {
        val resourceName: String = "dynamic-access-application.conf"
        val configFile: Path = temporaryDirectory.resolve(resourceName)
        Files.writeString(
            configFile,
            """
            coverage {
                value = "loaded from resource lookup"
            }
            """.trimIndent()
        )
        val originalClassLoader: ClassLoader? = Thread.currentThread().contextClassLoader
        val classLoader: SingleResourceClassLoader = SingleResourceClassLoader(
            parent = originalClassLoader ?: ClassLoader.getSystemClassLoader(),
            resourceName = resourceName,
            resourceUrl = configFile.toUri().toURL()
        )
        Thread.currentThread().contextClassLoader = classLoader

        try {
            val config: ApplicationConfig? = HoconConfigLoader().load(resourceName)

            assertThat(config).isNotNull()
            assertThat(config!!.property("coverage.value").getString())
                .isEqualTo("loaded from resource lookup")
            assertThat(classLoader.requestedResources).contains(resourceName)
        } finally {
            Thread.currentThread().contextClassLoader = originalClassLoader
        }
    }

    private class SingleResourceClassLoader(
        parent: ClassLoader?,
        private val resourceName: String,
        private val resourceUrl: URL
    ) : ClassLoader(parent) {
        val requestedResources: MutableList<String> = mutableListOf()

        override fun getResource(name: String): URL? {
            if (name == resourceName) {
                requestedResources.add(name)
                return resourceUrl
            }
            return super.getResource(name)
        }

        override fun getResources(name: String): Enumeration<URL> {
            if (name == resourceName) {
                return Collections.enumeration(listOf(resourceUrl))
            }
            return super.getResources(name)
        }
    }
}
