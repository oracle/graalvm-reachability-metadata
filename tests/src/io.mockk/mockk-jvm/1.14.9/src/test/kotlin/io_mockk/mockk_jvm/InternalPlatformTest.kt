/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_mockk.mockk_jvm

import io.mockk.impl.InternalPlatform
import io.mockk.impl.JvmMockKGateway
import io.mockk.impl.platform.JvmWeakConcurrentMap
import io.mockk.proxy.MockKAgentFactory
import io.mockk.proxy.jvm.JvmMockKAgentFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

public class InternalPlatformTest {
    @Test
    @Timeout(60)
    fun initializesJvmGatewayThroughPluginLoader(): Unit {
        val gateway: JvmMockKGateway = JvmMockKGateway()

        assertThat(gateway.mockFactory).isNotNull()
        assertThat(gateway.clearer).isNotNull()
    }

    @Test
    fun loadsJvmAgentPluginByClassName(): Unit {
        val explicitPlugin: MockKAgentFactory = InternalPlatform.loadPlugin(
            JVM_AGENT_FACTORY_CLASS_NAME,
            "while loading the JVM MockK agent factory",
        )
        val defaultMessagePlugin: MockKAgentFactory = InternalPlatform.loadPlugin(JVM_AGENT_FACTORY_CLASS_NAME)

        assertThat(explicitPlugin).isInstanceOf(JvmMockKAgentFactory::class.java)
        assertThat(defaultMessagePlugin).isInstanceOf(JvmMockKAgentFactory::class.java)
    }

    @Test
    fun copiesFieldsBetweenWeakConcurrentMaps(): Unit {
        val key: Any = Any()
        val source: JvmWeakConcurrentMap<Any, String> = JvmWeakConcurrentMap()
        source[key] = "copied value"
        val target: JvmWeakConcurrentMap<Any, String> = JvmWeakConcurrentMap()

        InternalPlatform.copyFields(target, source)

        assertThat(target[key]).isEqualTo("copied value")
        assertThat(target.size).isEqualTo(1)
    }

    private companion object {
        private const val JVM_AGENT_FACTORY_CLASS_NAME: String = "io.mockk.proxy.jvm.JvmMockKAgentFactory"
    }
}
