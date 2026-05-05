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
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.EngineConnectorConfig
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.embeddedServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

public class CallableUtilsKtTest {
    @Test
    fun `configuration module references instantiate callable module classes`(): Unit {
        CallableModuleReference.invocations.set(0)
        val moduleClassName: String = CallableModuleReference::class.java.name
        val config: MapApplicationConfig = MapApplicationConfig().apply {
            put("ktor.application.modules", listOf("$moduleClassName#module"))
        }
        val environment: ApplicationEnvironment = applicationEnvironment {
            this.config = config
            classLoader = CallableModuleReference::class.java.classLoader
        }
        val rootConfig: ServerConfig = serverConfig(environment) {
            developmentMode = false
        }
        val server: EmbeddedServer<CallableModuleStubEngine, ApplicationEngine.Configuration> = embeddedServer(
            CallableModuleStubEngineFactory,
            rootConfig
        ) {
            shutdownGracePeriod = 0
            shutdownTimeout = 1_000
        }

        try {
            server.start(wait = false)

            assertThat(CallableModuleReference.invocations.get()).isEqualTo(1)
        } finally {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
        }
    }
}

public class CallableModuleReference : (Application) -> Unit {
    override fun invoke(application: Application): Unit {
        invocations.incrementAndGet()
    }

    public companion object {
        public val invocations: AtomicInteger = AtomicInteger()
    }
}

private object CallableModuleStubEngineFactory :
    ApplicationEngineFactory<CallableModuleStubEngine, ApplicationEngine.Configuration> {
    override fun configuration(configure: ApplicationEngine.Configuration.() -> Unit): ApplicationEngine.Configuration {
        return ApplicationEngine.Configuration().apply(configure)
    }

    override fun create(
        environment: ApplicationEnvironment,
        monitor: Events,
        developmentMode: Boolean,
        configuration: ApplicationEngine.Configuration,
        applicationProvider: () -> Application
    ): CallableModuleStubEngine {
        return CallableModuleStubEngine(environment, configuration)
    }
}

public class CallableModuleStubEngine(
    override val environment: ApplicationEnvironment,
    private val configuration: ApplicationEngine.Configuration
) : ApplicationEngine {
    override suspend fun resolvedConnectors(): List<EngineConnectorConfig> = configuration.connectors

    override fun start(wait: Boolean): ApplicationEngine = this

    override fun stop(gracePeriodMillis: Long, timeoutMillis: Long): Unit = Unit
}
