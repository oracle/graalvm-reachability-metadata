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
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.EngineConnectorConfig
import io.ktor.server.engine.applicationEnvironment
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URL
import java.nio.file.Path
import java.util.Collections
import java.util.Enumeration

public class ClassLoadersKtTest {
    @Test
    fun `development startup reads URLs from a URLClassPath field`(@TempDir tempDir: Path) {
        val urlClassPath = AccessibleUrlClassPath.URLClassPath(arrayOf(tempDir.toUri().toURL()))
        val classLoader = UrlClassPathFieldClassLoader(urlClassPath)
        val server = embeddedServerFor(classLoader)

        try {
            server.start(wait = false)

            assertThat(server.engine.started).isTrue()
            assertThat(urlClassPath.getURLsCallCount).isEqualTo(1)
        } finally {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 250)
        }
    }

    @Test
    fun `development startup falls back to package resource lookup`(@TempDir tempDir: Path) {
        val resourceUrl = tempDir.toUri().toURL()
        val classLoader = FallbackResourceClassLoader(listOf(resourceUrl))
        val server = embeddedServerFor(classLoader)

        try {
            server.start(wait = false)

            assertThat(server.engine.started).isTrue()
            assertThat(classLoader.requestedResourceNames).contains("")
        } finally {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 250)
        }
    }

    private fun embeddedServerFor(
        classLoader: ClassLoader
    ): EmbeddedServer<RecordingApplicationEngine, ApplicationEngine.Configuration> {
        val environment = applicationEnvironment {
            this.classLoader = classLoader
        }
        val rootConfig = serverConfig(environment) {
            developmentMode = true
            watchPaths = listOf("pattern-that-will-not-match-any-test-url")
        }

        return EmbeddedServer(rootConfig, RecordingEngineFactory) {
            shutdownGracePeriod = 0
            shutdownTimeout = 250
        }
    }

    private class UrlClassPathFieldClassLoader(
        @Suppress("unused")
        private val ucp: AccessibleUrlClassPath.URLClassPath
    ) : ClassLoader(null)

    private object AccessibleUrlClassPath {
        class URLClassPath(private val urls: Array<URL>) {
            var getURLsCallCount: Int = 0
                private set

            fun getURLs(): Array<URL> {
                getURLsCallCount++
                return urls.copyOf()
            }
        }
    }

    private class FallbackResourceClassLoader(
        private val resources: List<URL>
    ) : ClassLoader(null) {
        @Suppress("unused")
        private val ucp: MissingGetURLs.URLClassPath = MissingGetURLs.URLClassPath()
        val requestedResourceNames: MutableList<String> = mutableListOf()

        override fun getResources(name: String): Enumeration<URL> {
            requestedResourceNames += name
            return Collections.enumeration(resources)
        }
    }

    private object MissingGetURLs {
        class URLClassPath
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
        var started: Boolean = false
            private set

        override suspend fun resolvedConnectors(): List<EngineConnectorConfig> = emptyList()

        override fun start(wait: Boolean): ApplicationEngine {
            started = true
            return this
        }

        override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {
        }
    }
}
