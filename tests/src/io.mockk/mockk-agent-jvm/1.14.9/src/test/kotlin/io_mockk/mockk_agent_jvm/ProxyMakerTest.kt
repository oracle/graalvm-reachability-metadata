/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_mockk.mockk_agent_jvm

import io.mockk.proxy.Cancelable
import io.mockk.proxy.MockKAgentLogFactory
import io.mockk.proxy.MockKInvocationHandler
import io.mockk.proxy.MockKProxyMaker
import io.mockk.proxy.jvm.JvmMockKAgentFactory
import org.assertj.core.api.Assertions.assertThat
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.lang.reflect.Method
import java.util.concurrent.Callable

public class ProxyMakerTest {
    @Test
    @Timeout(60)
    fun createsProxyUsingTargetDefaultConstructor(): Unit {
        val factory: JvmMockKAgentFactory = JvmMockKAgentFactory()
        try {
            factory.init(MockKAgentLogFactory.simpleConsoleLogFactory)
            val proxyMaker: MockKProxyMaker = factory.proxyMaker
            val proxy: Cancelable<DefaultConstructorTarget> = proxyMaker.proxy(
                DefaultConstructorTarget::class.java,
                emptyArray(),
                object : MockKInvocationHandler {
                    override fun invocation(
                        self: Any,
                        method: Method?,
                        originalCall: Callable<*>?,
                        args: Array<Any?>,
                    ): Any? = originalCall?.call()
                },
                true,
                null,
            )

            try {
                assertThat(proxy.get().createdByDefaultConstructor).isTrue()
            } finally {
                proxy.cancel()
            }
        } catch (error: Error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error
            }
        }
    }

    private class DefaultConstructorTarget {
        val createdByDefaultConstructor: Boolean = true
    }
}
