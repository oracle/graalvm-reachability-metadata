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
import io.ktor.server.application.ServerConfig
import io.ktor.server.application.serverConfig
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.EngineConnectorConfig
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.embeddedServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URL
import java.nio.file.Path
import java.util.Collections
import java.util.Enumeration

public class ClassLoadersKtTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `embedded server development startup inspects non URL class loader class path`(): Unit {
        val discoveredRoot: URL = temporaryDirectory.toUri().toURL()
        val classLoader: FallbackResourceClassLoader = FallbackResourceClassLoader(
            discoveredRoot,
            failOnUrlAccess = true
        )
        val server: EmbeddedServer<StubEngine, ApplicationEngine.Configuration> = serverFor(classLoader)

        try {
            server.start(wait = false)

            assertThat(classLoader.requestedResources).contains("")
            assertThat(classLoader.requestedResources).isNotEmpty()
        } finally {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
        }
    }

    @Test
    fun `embedded server development startup invokes URL class path accessor`(): Unit {
        val discoveredRoot: URL = temporaryDirectory.toUri().toURL()
        val classLoader: FallbackResourceClassLoader = FallbackResourceClassLoader(
            discoveredRoot,
            failOnUrlAccess = false
        )
        val server: EmbeddedServer<StubEngine, ApplicationEngine.Configuration> = serverFor(classLoader)

        try {
            server.start(wait = false)

            assertThat(classLoader.urlAccessCount).isPositive()
        } finally {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
        }
    }

    private fun serverFor(classLoader: ClassLoader): EmbeddedServer<StubEngine, ApplicationEngine.Configuration> {
        ensureJavaHomeProperty()
        val environment: ApplicationEnvironment = applicationEnvironment {
            this.classLoader = classLoader
        }
        val rootConfig: ServerConfig = serverConfig(environment) {
            developmentMode = true
            watchPaths = listOf("dynamic-access-watch-pattern")
        }
        return embeddedServer(
            StubEngineFactory,
            rootConfig
        ) {
            shutdownGracePeriod = 0
            shutdownTimeout = 1_000
        }
    }

    public class URLClassPath(
        private val discoveredRoot: URL,
        private val failOnAccess: Boolean
    ) {
        public var accessCount: Int = 0
            private set

        public fun getURLs(): Array<URL> {
            accessCount++
            if (failOnAccess) {
                throw IllegalStateException("Force Ktor to use the package-resource class path fallback")
            }
            return arrayOf(discoveredRoot)
        }
    }
}

private class FallbackResourceClassLoader(
    private val discoveredRoot: URL,
    failOnUrlAccess: Boolean
) : ClassLoader(null) {
    @Suppress("unused")
    private val ucp: ClassLoadersKtTest.URLClassPath = ClassLoadersKtTest.URLClassPath(
        discoveredRoot,
        failOnUrlAccess
    )
    val requestedResources: MutableList<String> = mutableListOf()
    val urlAccessCount: Int get() = ucp.accessCount

    override fun getResources(name: String): Enumeration<URL> {
        requestedResources.add(name)
        return Collections.enumeration(listOf(discoveredRoot))
    }
}

private object StubEngineFactory : ApplicationEngineFactory<StubEngine, ApplicationEngine.Configuration> {
    override fun configuration(configure: ApplicationEngine.Configuration.() -> Unit): ApplicationEngine.Configuration {
        return ApplicationEngine.Configuration().apply(configure)
    }

    override fun create(
        environment: ApplicationEnvironment,
        monitor: Events,
        developmentMode: Boolean,
        configuration: ApplicationEngine.Configuration,
        applicationProvider: () -> Application
    ): StubEngine {
        return StubEngine(environment, configuration)
    }
}

private class StubEngine(
    override val environment: ApplicationEnvironment,
    private val configuration: ApplicationEngine.Configuration
) : ApplicationEngine {
    override suspend fun resolvedConnectors(): List<EngineConnectorConfig> = configuration.connectors

    override fun start(wait: Boolean): ApplicationEngine = this

    override fun stop(gracePeriodMillis: Long, timeoutMillis: Long): Unit = Unit
}
