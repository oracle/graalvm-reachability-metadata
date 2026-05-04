/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_core_jvm

import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.internal.MainDispatcherFactory
import org.graalvm.internal.tck.NativeImageSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class FastServiceLoaderTest {
    @Test
    public fun dispatchersMainUsesProviderDiscoveredByFastServiceLoader() {
        val dispatcher: MainCoroutineDispatcher = Dispatchers.Main
        val executed: AtomicBoolean = AtomicBoolean(false)

        assertThat(dispatcher).isSameAs(FastServiceLoaderTestMainDispatcher)
        dispatcher.dispatch(EmptyCoroutineContext, Runnable { executed.set(true) })

        assertThat(executed.get()).isTrue()
    }

    @Test
    public fun androidDispatcherLookupInstantiatesKnownFactoryClass() {
        try {
            val androidApiDirectory: Path = Files.createTempDirectory("fast-service-loader-android-api")
            try {
                writeAndroidBuildClass(androidApiDirectory)
                val urls: Array<URL> = (coroutinesCoreJar()?.let { jar -> listOf(jar.toUri().toURL()) }.orEmpty() +
                    androidApiDirectory.toUri().toURL()).toTypedArray()
                CoroutineChildFirstClassLoader(urls, FastServiceLoaderTest::class.java.classLoader).use { loader ->
                    val dispatchersClass: Class<*> = loader.loadClass("kotlinx.coroutines.Dispatchers")
                    val mainDispatcher: Any? = dispatchersClass.getMethod("getMain").invoke(null)

                    assertThat(mainDispatcher).isNotNull
                }
            } finally {
                androidApiDirectory.toFile().deleteRecursively()
            }
        } catch (error: Error) {
            if (!isUnsupportedNativeImageError(error)) {
                throw error
            }
        } catch (exception: InvocationTargetException) {
            val cause: Throwable? = exception.cause
            if (cause !is Error || !isUnsupportedNativeImageError(cause)) {
                throw exception
            }
        }
    }

    private fun isUnsupportedNativeImageError(error: Error): Boolean =
        NativeImageSupport.isUnsupportedFeatureError(error) ||
            ((error.cause as? Error)?.let { cause -> isUnsupportedNativeImageError(cause) } ?: false)

    private fun writeAndroidBuildClass(root: Path) {
        val buildClass: Path = root.resolve("android/os/Build.class")
        Files.createDirectories(buildClass.parent)
        Files.write(buildClass, Base64.getDecoder().decode(ANDROID_BUILD_CLASS_BASE64))
    }

    private fun coroutinesCoreJar(): File? {
        val codeSource: URL? = Dispatchers::class.java.protectionDomain.codeSource?.location
        if (codeSource != null && codeSource.path.endsWith(".jar")) {
            return File(codeSource.toURI())
        }
        val classPathEntries: List<File> = System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .map { entry -> File(entry) }
        return classPathEntries.firstOrNull { entry ->
            entry.isFile &&
                entry.name.startsWith("kotlinx-coroutines-core-jvm-") &&
                entry.name.endsWith(".jar")
        }
    }

    private class CoroutineChildFirstClassLoader(urls: Array<URL>, parent: ClassLoader) : URLClassLoader(urls, parent) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (name == "android.os.Build" || name.startsWith("kotlinx.coroutines.")) {
                findLoadedClass(name)?.let { loadedClass -> return loadedClass }
                try {
                    val loadedClass: Class<*> = findClass(name)
                    if (resolve) {
                        resolveClass(loadedClass)
                    }
                    return loadedClass
                } catch (exception: ClassNotFoundException) {
                    // Delegate optional coroutine integration classes to the test class path.
                }
            }
            return super.loadClass(name, resolve)
        }
    }

    private companion object {
        private const val ANDROID_BUILD_CLASS_BASE64: String =
            "yv66vgAAADQADAoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClWBwAIAQAQ" +
                "YW5kcm9pZC9vcy9CdWlsZAEABENvZGUBAApTb3VyY2VGaWxlAQAKQnVpbGQuamF2YQAhAAcAAgAAAAAA" +
                "AQABAAUABgABAAkAAAARAAEAAQAAAAUqtwABsQAAAAAAAQAKAAAAAgAM"
    }
}

@OptIn(InternalCoroutinesApi::class)
public class FastServiceLoaderTestMainDispatcherFactory : MainDispatcherFactory {
    override val loadPriority: Int = Int.MAX_VALUE

    override fun createDispatcher(allFactories: List<MainDispatcherFactory>): MainCoroutineDispatcher {
        assertThat(allFactories).anyMatch { factory -> factory === this }
        return FastServiceLoaderTestMainDispatcher
    }

    override fun hintOnError(): String = "FastServiceLoader test dispatcher failed"
}

public object FastServiceLoaderTestMainDispatcher : MainCoroutineDispatcher() {
    override val immediate: MainCoroutineDispatcher = this

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = false

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        block.run()
    }

    override fun toString(): String = "FastServiceLoaderTestMainDispatcher"
}
