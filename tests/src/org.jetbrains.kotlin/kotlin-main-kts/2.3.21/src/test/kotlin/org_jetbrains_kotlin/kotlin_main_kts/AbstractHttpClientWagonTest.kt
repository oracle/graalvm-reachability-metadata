/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_main_kts

import org.assertj.core.api.Assertions.assertThat
import org.graalvm.internal.tck.NativeImageSupport
import org.jetbrains.kotlin.mainKts.MainKtsScriptDefinition
import org.junit.jupiter.api.Test
import java.io.Closeable
import java.lang.reflect.InvocationTargetException
import java.net.URL
import java.net.URLClassLoader

public class AbstractHttpClientWagonTest {
    @Test
    fun httpClientCreationUsesConfiguredRetryComponents(): Unit {
        try {
            createHttpClientWith(
                mapOf(
                    RETRY_HANDLER_CLASS_PROPERTY to CustomWagonRetryHandler::class.java.name,
                    SERVICE_UNAVAILABLE_RETRY_STRATEGY_CLASS_PROPERTY to
                        CustomServiceUnavailableRetryStrategy::class.java.name,
                ),
            )
            createHttpClientWith(
                mapOf(
                    RETRY_HANDLER_CLASS_PROPERTY to "default",
                    RETRY_HANDLER_EXCEPTIONS_PROPERTY to CustomNonRetryableIOException::class.java.name,
                    SERVICE_UNAVAILABLE_RETRY_STRATEGY_CLASS_PROPERTY to "none",
                ),
            )
        } catch (error: Error) {
            if (!isUnsupportedFeatureError(error)) {
                throw error
            }
        } catch (exception: InvocationTargetException) {
            val cause: Throwable? = exception.cause
            if (cause !is Error || !isUnsupportedFeatureError(cause)) {
                throw exception
            }
        }
    }

    private fun createHttpClientWith(properties: Map<String, String>): Unit {
        withSystemProperties(properties) {
            IsolatedWagonClassLoader(isolatedClasspathUrls()).use { classLoader ->
                val wagonClass: Class<*> = classLoader.loadClass(ABSTRACT_HTTP_CLIENT_WAGON_CLASS)
                val client: Any? = wagonClass.getMethod("getHttpClient").invoke(null)

                assertThat(client).isNotNull()
                if (client is Closeable) {
                    client.close()
                }
            }
        }
    }

    private fun isolatedClasspathUrls(): Array<URL> {
        return listOf(
            codeSourceUrl(MainKtsScriptDefinition::class.java),
            codeSourceUrl(CustomWagonRetryHandler::class.java),
            codeSourceUrl(AbstractHttpClientWagonTest::class.java),
        ).distinct().toTypedArray()
    }

    private fun codeSourceUrl(type: Class<*>): URL = type.protectionDomain.codeSource.location

    private fun withSystemProperties(properties: Map<String, String>, block: () -> Unit): Unit {
        val previousValues: Map<String, String?> = WAGON_SYSTEM_PROPERTIES.associateWith { propertyName ->
            System.getProperty(propertyName)
        }
        try {
            WAGON_SYSTEM_PROPERTIES.forEach { propertyName -> System.clearProperty(propertyName) }
            properties.forEach { (propertyName, propertyValue) -> System.setProperty(propertyName, propertyValue) }
            block()
        } finally {
            WAGON_SYSTEM_PROPERTIES.forEach { propertyName ->
                val previousValue: String? = previousValues[propertyName]
                if (previousValue == null) {
                    System.clearProperty(propertyName)
                } else {
                    System.setProperty(propertyName, previousValue)
                }
            }
        }
    }

    private fun isUnsupportedFeatureError(error: Error): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is Error && NativeImageSupport.isUnsupportedFeatureError(current)) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private class IsolatedWagonClassLoader(urls: Array<URL>) : URLClassLoader(
        urls,
        AbstractHttpClientWagonTest::class.java.classLoader,
    ) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            synchronized(getClassLoadingLock(name)) {
                val loadedClass: Class<*>? = findLoadedClass(name)
                if (loadedClass != null) {
                    return loadedClass
                }

                if (isIsolatedClass(name)) {
                    try {
                        val foundClass: Class<*> = findClass(name)
                        if (resolve) {
                            resolveClass(foundClass)
                        }
                        return foundClass
                    } catch (_: ClassNotFoundException) {
                        // Fall through to regular parent delegation for classes outside the isolated URLs.
                    }
                }

                return super.loadClass(name, resolve)
            }
        }

        private fun isIsolatedClass(name: String): Boolean {
            return name.startsWith("org.jetbrains.kotlin.org.") ||
                name == CustomWagonRetryHandler::class.java.name ||
                name == CustomServiceUnavailableRetryStrategy::class.java.name ||
                name == CustomNonRetryableIOException::class.java.name
        }
    }

    private companion object {
        private const val ABSTRACT_HTTP_CLIENT_WAGON_CLASS: String =
            "org.jetbrains.kotlin.org.apache.maven.wagon.shared.http.AbstractHttpClientWagon"
        private const val RETRY_HANDLER_CLASS_PROPERTY: String = "maven.wagon.http.retryHandler.class"
        private const val RETRY_HANDLER_EXCEPTIONS_PROPERTY: String =
            "maven.wagon.http.retryHandler.nonRetryableClasses"
        private const val SERVICE_UNAVAILABLE_RETRY_STRATEGY_CLASS_PROPERTY: String =
            "maven.wagon.http.serviceUnavailableRetryStrategy.class"
        private val WAGON_SYSTEM_PROPERTIES: List<String> = listOf(
            RETRY_HANDLER_CLASS_PROPERTY,
            RETRY_HANDLER_EXCEPTIONS_PROPERTY,
            SERVICE_UNAVAILABLE_RETRY_STRATEGY_CLASS_PROPERTY,
        )
    }
}
