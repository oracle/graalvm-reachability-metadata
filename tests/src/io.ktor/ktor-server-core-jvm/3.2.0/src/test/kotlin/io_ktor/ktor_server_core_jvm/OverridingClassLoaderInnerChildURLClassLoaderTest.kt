/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_core_jvm

import io.ktor.events.Events
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationEnvironment
import io.ktor.server.application.serverConfig
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.EngineConnectorConfig
import io.ktor.server.engine.applicationEnvironment
import org.assertj.core.api.Assertions.assertThat
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Test
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path

private const val MODULE_FUNCTION_NAME: String =
    "io_ktor.ktor_server_core_jvm.OverridingClassLoaderInnerChildURLClassLoaderTestKt.childUrlClassLoaderProbeModule"
private const val PROBE_RESOURCE_NAME: String =
    "io_ktor/ktor_server_core_jvm/child-url-classloader-resource.txt"
private const val PROBE_RESOURCE_TEXT: String = "resource loaded through the real parent class loader"
private const val PROBE_LOADER_PROPERTY: String = "ktor.childUrlClassLoader.loader"
private const val PROBE_RESOURCE_COUNT_PROPERTY: String = "ktor.childUrlClassLoader.resourceCount"
private const val PROBE_RESOURCE_URL_PROPERTY: String = "ktor.childUrlClassLoader.resourceUrl"
private const val PROBE_RESOURCE_TEXT_PROPERTY: String = "ktor.childUrlClassLoader.resourceText"

public class OverridingClassLoaderInnerChildURLClassLoaderTest {
    @Test
    fun `development reload delegates class and resource lookups through child URL class loader`() {
        clearProbeProperties()
        val baseClassLoader: URLClassLoader = URLClassLoader(
            arrayOf(Path.of("build/classes/kotlin/test").toUri().toURL()),
            Thread.currentThread().contextClassLoader
        )
        val server: EmbeddedServer<RecordingApplicationEngine, ApplicationEngine.Configuration> =
            embeddedServerWithDynamicModule(baseClassLoader)

        try {
            try {
                server.reload()
            } catch (error: Error) {
                if (NativeImageSupport.isUnsupportedFeatureError(error)) {
                    return
                }
                throw error
            }

            val probeLoaderName: String = System.getProperty(PROBE_LOADER_PROPERTY).orEmpty()
            if (isNativeImageRuntime()) {
                assertThat(probeLoaderName).contains("AppClassLoader")
            } else {
                assertThat(probeLoaderName).contains("ChildURLClassLoader")
            }
            assertThat(System.getProperty(PROBE_RESOURCE_COUNT_PROPERTY).toInt()).isGreaterThanOrEqualTo(1)
            assertThat(System.getProperty(PROBE_RESOURCE_URL_PROPERTY)).isNotBlank()
            assertThat(System.getProperty(PROBE_RESOURCE_TEXT_PROPERTY)).isEqualTo(PROBE_RESOURCE_TEXT)
        } finally {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 250)
            baseClassLoader.close()
            clearProbeProperties()
        }
    }

    private fun embeddedServerWithDynamicModule(
        baseClassLoader: ClassLoader
    ): EmbeddedServer<RecordingApplicationEngine, ApplicationEngine.Configuration> {
        ensureJavaHomeSet()
        val config: MapApplicationConfig = MapApplicationConfig().apply {
            put("ktor.application.modules", listOf(MODULE_FUNCTION_NAME))
        }
        val environment: ApplicationEnvironment = applicationEnvironment {
            this.config = config
            classLoader = baseClassLoader
        }
        val rootConfig = serverConfig(environment) {
            developmentMode = true
            watchPaths = listOf("classes/kotlin/test")
        }

        return EmbeddedServer(rootConfig, RecordingEngineFactory) {
            shutdownGracePeriod = 0
            shutdownTimeout = 250
        }
    }

    private fun clearProbeProperties() {
        listOf(
            PROBE_LOADER_PROPERTY,
            PROBE_RESOURCE_COUNT_PROPERTY,
            PROBE_RESOURCE_URL_PROPERTY,
            PROBE_RESOURCE_TEXT_PROPERTY
        ).forEach(System::clearProperty)
    }

    private object RecordingEngineFactory :
        ApplicationEngineFactory<RecordingApplicationEngine, ApplicationEngine.Configuration> {
        override fun configuration(
            configure: ApplicationEngine.Configuration.() -> Unit
        ): ApplicationEngine.Configuration = ApplicationEngine.Configuration().apply(configure)

        override fun create(
            environment: ApplicationEnvironment,
            monitor: Events,
            developmentMode: Boolean,
            configuration: ApplicationEngine.Configuration,
            applicationProvider: () -> Application
        ): RecordingApplicationEngine = RecordingApplicationEngine(environment)
    }

    private class RecordingApplicationEngine(
        override val environment: ApplicationEnvironment
    ) : ApplicationEngine {
        override suspend fun resolvedConnectors(): List<EngineConnectorConfig> = emptyList()

        override fun start(wait: Boolean): ApplicationEngine = this

        override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {
        }
    }
}

public fun childUrlClassLoaderProbeModule(@Suppress("UNUSED_PARAMETER") application: Application) {
    val probeClassLoader: ClassLoader = object {}.javaClass.classLoader
    val resources: List<URL> = probeClassLoader.getResources(PROBE_RESOURCE_NAME).toList()
    val resourceUrl: String = probeClassLoader.getResource(PROBE_RESOURCE_NAME)?.toExternalForm().orEmpty()
    val resourceText: String = probeClassLoader.getResourceAsStream(PROBE_RESOURCE_NAME)?.use { stream ->
        stream.bufferedReader().readText().trim()
    }.orEmpty()

    System.setProperty(PROBE_LOADER_PROPERTY, probeClassLoader.javaClass.name)
    System.setProperty(PROBE_RESOURCE_COUNT_PROPERTY, resources.size.toString())
    System.setProperty(PROBE_RESOURCE_URL_PROPERTY, resourceUrl)
    System.setProperty(PROBE_RESOURCE_TEXT_PROPERTY, resourceText)
}
