/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_mockk.mockk_jvm

import io.mockk.every
import io.mockk.impl.JvmMockKGateway
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Test

public class JvmMockKGatewayTest {
    @Test
    fun newGatewayInitializesJvmAgentPlugin(): Unit {
        try {
            val gateway: JvmMockKGateway = JvmMockKGateway()

            assertThat(gateway.mockFactory).isNotNull()
            assertThat(gateway.staticMockFactory).isNotNull()
            assertThat(gateway.objectMockFactory).isNotNull()
            assertThat(gateway.constructorMockFactory).isNotNull()
        } catch (error: Error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error
            }
        }
    }

    @Test
    fun mockkDslInitializesGatewayAndCreatesJvmProxy(): Unit {
        try {
            val service: GatewayService = mockk()

            every { service.message("MockK") } returns "hello MockK"

            assertThat(service.message("MockK")).isEqualTo("hello MockK")
            verify { service.message("MockK") }
        } catch (error: Error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error
            }
        }
    }

    @Test
    fun reloadedGatewayInitializesJvmAgentPlugin(): Unit {
        try {
            val reloadedGatewayClass: Class<*> = ReloadedGatewayLoader().loadClass(JvmMockKGateway::class.java.name)
            val gateway: Any = reloadedGatewayClass.getDeclaredConstructor().newInstance()

            assertThat(gateway).isInstanceOf(io.mockk.MockKGateway::class.java)
        } catch (error: Error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error
            }
        }
    }
}

private interface GatewayService {
    fun message(name: String): String
}

private class ReloadedGatewayLoader : ClassLoader(JvmMockKGateway::class.java.classLoader) {
    private val gatewayClassName: String = JvmMockKGateway::class.java.name
    private val gatewayNestedClassPrefix: String = gatewayClassName + "\$"

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (name == gatewayClassName || name.startsWith(gatewayNestedClassPrefix)) {
            synchronized(getClassLoadingLock(name)) {
                val loadedClass: Class<*>? = findLoadedClass(name)
                val gatewayClass: Class<*> = loadedClass ?: findClass(name)
                if (resolve) {
                    resolveClass(gatewayClass)
                }
                return gatewayClass
            }
        }
        return super.loadClass(name, resolve)
    }

    override fun findClass(name: String): Class<*> {
        val bytes: ByteArray = readClassBytes(name)
        return defineClass(name, bytes, 0, bytes.size)
    }

    private fun readClassBytes(name: String): ByteArray {
        val resourceName = "/${name.replace('.', '/')}.class"
        return JvmMockKGateway::class.java.getResourceAsStream(resourceName)?.use { input ->
            input.readBytes()
        } ?: error("Cannot read $name bytecode")
    }
}
