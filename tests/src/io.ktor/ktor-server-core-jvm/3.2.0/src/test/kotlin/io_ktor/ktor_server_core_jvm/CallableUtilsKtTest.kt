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
import org.junit.jupiter.api.Test

private const val FUNCTION_MODULE_REFERENCE: String =
    "io_ktor.ktor_server_core_jvm.ZeroArgFunctionModule#invoke"
private const val FUNCTION_MODULE_PROPERTY: String = "ktor.callableUtils.functionModuleInvoked"

public class CallableUtilsKtTest {
    @Test
    fun `module function loader instantiates zero argument Function1 module`() {
        System.clearProperty(FUNCTION_MODULE_PROPERTY)
        val server: EmbeddedServer<RecordingApplicationEngine, ApplicationEngine.Configuration> =
            embeddedServerWithFunctionModule()

        try {
            server.start(wait = false)

            assertThat(System.getProperty(FUNCTION_MODULE_PROPERTY)).isEqualTo(Application::class.java.name)
        } finally {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 250)
            System.clearProperty(FUNCTION_MODULE_PROPERTY)
        }
    }

    private fun embeddedServerWithFunctionModule(): EmbeddedServer<
        RecordingApplicationEngine,
        ApplicationEngine.Configuration,
    > {
        val config: MapApplicationConfig = MapApplicationConfig().apply {
            put("ktor.application.modules", listOf(FUNCTION_MODULE_REFERENCE))
        }
        val environment: ApplicationEnvironment = applicationEnvironment {
            this.config = config
            classLoader = ZeroArgFunctionModule::class.java.classLoader
        }
        val rootConfig = serverConfig(environment) {
            developmentMode = false
        }

        return EmbeddedServer(rootConfig, RecordingEngineFactory) {
            shutdownGracePeriod = 0
            shutdownTimeout = 250
        }
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

public class ZeroArgFunctionModule : Function1<Application, Unit> {
    override fun invoke(application: Application) {
        System.setProperty(FUNCTION_MODULE_PROPERTY, application::class.java.name)
    }
}
