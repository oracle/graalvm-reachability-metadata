/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_mockk.mockk_agent_jvm

import io.mockk.proxy.MockKAgentLogFactory
import io.mockk.proxy.jvm.JvmMockKAgentFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

public class BootJarLoaderTest {
    @Test
    @Timeout(60)
    fun initializesAgentFactoryWithDispatcherClassesOnBootstrapPath(): Unit {
        val factory: JvmMockKAgentFactory = JvmMockKAgentFactory()

        try {
            factory.init(MockKAgentLogFactory.simpleConsoleLogFactory)
        } catch (throwable: Throwable) {
            if (!MockkNativeImageSupport.isExpectedNativeImageFailure(throwable)) {
                throw throwable
            }
            return
        }

        assertThat(factory.proxyMaker).isNotNull()
        assertThat(factory.staticProxyMaker).isNotNull()
        assertThat(factory.constructorProxyMaker).isNotNull()
        assertThat(factory.instantiator).isNotNull()
    }
}
