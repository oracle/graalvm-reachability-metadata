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
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.opentest4j.TestAbortedException
import java.io.InputStream
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

public class OverridingClassLoaderInnerChildURLClassLoaderTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `autoreload child class loader delegates missing classes and resources to parent`(): Unit {
        try {
            exerciseAutoreloadChildClassLoader()
        } catch (exception: ClassNotFoundException) {
            if (!isNativeImageRuntime()) {
                throw exception
            }
            throw TestAbortedException(
                "Native image runtime does not support reloading application classes via autoreload child URLClassLoader",
                exception
            )
        } catch (error: Error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error
            }
        }
    }

    private fun exerciseAutoreloadChildClassLoader(): Unit {
        val childRoot: Path = temporaryDirectory.resolve("child-classes")
        val probeClassName: String = ChildURLClassLoaderResourceProbe::class.java.name
        copyProbeClassTo(childRoot, probeClassName)

        val parentClassLoader: ClassLoader = Thread.currentThread().contextClassLoader
        val baseClassLoader: URLClassLoader = URLClassLoader(arrayOf(childRoot.toUri().toURL()), parentClassLoader)
        var applicationClassLoader: ClassLoader? = null
        var loadedProbeClass: Class<*>? = null
        val server: EmbeddedServer<ChildLoaderStubEngine, ApplicationEngine.Configuration> = serverFor(
            baseClassLoader,
            childRoot.fileName.toString()
        ) {
            val loader: ClassLoader = Thread.currentThread().contextClassLoader
            applicationClassLoader = loader
            loadedProbeClass = Class.forName(probeClassName, true, loader)
            loader.loadClass("org.junit.jupiter.api.Test")
        }

        try {
            server.start(wait = false)

            assertThat(applicationClassLoader).isNotNull()
            assertThat(applicationClassLoader!!.javaClass.name).isEqualTo("io.ktor.server.engine.OverridingClassLoader")
            assertThat(loadedProbeClass).isNotNull()
            assertThat(loadedProbeClass!!.classLoader).isNotSameAs(parentClassLoader)
            assertThat(System.getProperty("ktor.child.loader.resource.probe")).isEqualTo("executed")
        } finally {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
            baseClassLoader.close()
            System.clearProperty("ktor.child.loader.resource.probe")
        }
    }

    private fun serverFor(
        classLoader: ClassLoader,
        watchPath: String,
        applicationModule: suspend Application.() -> Unit
    ): EmbeddedServer<ChildLoaderStubEngine, ApplicationEngine.Configuration> {
        ensureJavaHomeProperty()
        val environment: ApplicationEnvironment = applicationEnvironment {
            this.classLoader = classLoader
        }
        val rootConfig: ServerConfig = serverConfig(environment) {
            developmentMode = true
            watchPaths = listOf(watchPath)
            module(applicationModule)
        }
        return embeddedServer(
            ChildLoaderStubEngineFactory,
            rootConfig
        ) {
            shutdownGracePeriod = 0
            shutdownTimeout = 1_000
        }
    }

    private fun copyProbeClassTo(targetRoot: Path, className: String): Unit {
        val resourceName: String = className.replace('.', '/') + ".class"
        val target: Path = targetRoot.resolve(resourceName)
        Files.createDirectories(target.parent)

        val sourcePath: Path? = probeClassFileCandidates(resourceName).firstOrNull(Files::exists)
        if (sourcePath != null) {
            Files.copy(sourcePath, target, StandardCopyOption.REPLACE_EXISTING)
            return
        }

        val resourceStream: InputStream = ChildURLClassLoaderResourceProbe::class.java.classLoader
            .getResourceAsStream(resourceName)
            ?: error("Unable to locate probe class file $resourceName")
        resourceStream.use { stream ->
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun probeClassFileCandidates(resourceName: String): List<Path> {
        val codeSourcePath: Path? = runCatching {
            Paths.get(ChildURLClassLoaderResourceProbe::class.java.protectionDomain.codeSource.location.toURI())
        }.getOrNull()

        return listOfNotNull(
            codeSourcePath?.resolve(resourceName),
            Paths.get("build/classes/kotlin/test").resolve(resourceName),
            Paths.get("build/classes/java/test").resolve(resourceName)
        )
    }
}

private object ChildLoaderStubEngineFactory :
    ApplicationEngineFactory<ChildLoaderStubEngine, ApplicationEngine.Configuration> {
    override fun configuration(configure: ApplicationEngine.Configuration.() -> Unit): ApplicationEngine.Configuration {
        return ApplicationEngine.Configuration().apply(configure)
    }

    override fun create(
        environment: ApplicationEnvironment,
        monitor: Events,
        developmentMode: Boolean,
        configuration: ApplicationEngine.Configuration,
        applicationProvider: () -> Application
    ): ChildLoaderStubEngine {
        return ChildLoaderStubEngine(environment, configuration)
    }
}

private class ChildLoaderStubEngine(
    override val environment: ApplicationEnvironment,
    private val configuration: ApplicationEngine.Configuration
) : ApplicationEngine {
    override suspend fun resolvedConnectors(): List<EngineConnectorConfig> = configuration.connectors

    override fun start(wait: Boolean): ApplicationEngine = this

    override fun stop(gracePeriodMillis: Long, timeoutMillis: Long): Unit = Unit
}

private object ChildURLClassLoaderResourceProbe {
    init {
        val loader: ClassLoader = ChildURLClassLoaderResourceProbe::class.java.classLoader
        loader.getResources("ktor-child-loader-probe.txt")
        loader.getResource("ktor-child-loader-probe.txt")
        loader.getResourceAsStream("ktor-child-loader-probe.txt")?.close()
        System.setProperty("ktor.child.loader.resource.probe", "executed")
    }
}

private fun isNativeImageRuntime(): Boolean =
    System.getProperty("org.graalvm.nativeimage.imagecode") == "runtime"
