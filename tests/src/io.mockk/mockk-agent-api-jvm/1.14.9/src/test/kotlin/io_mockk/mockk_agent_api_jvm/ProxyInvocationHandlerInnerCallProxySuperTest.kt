/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_mockk.mockk_agent_api_jvm

import io.mockk.proxy.MockKInvocationHandler
import io.mockk.proxy.common.ProxyInvocationHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.Method
import java.util.concurrent.Callable

class ProxyInvocationHandlerInnerCallProxySuperTest {
    @Test
    fun callsGeneratedProxySuperMethod(): Unit {
        val proxy: ProxyInvocationHandlerCallProxySuperTarget = ProxyInvocationHandlerCallProxySuperTarget()
        val handler: ProxyInvocationHandler = ProxyInvocationHandler(CallsOriginalHandler)
        val method: Method = ProxyInvocationHandlerCallProxySuperTarget::class.java.getMethod(
            "describe",
            String::class.java,
        )

        val result: Any? = handler.invoke(proxy, method, arrayOf("Ada"))

        assertThat(result).isEqualTo("super Ada")
    }

    private object CallsOriginalHandler : MockKInvocationHandler {
        override fun invocation(
            self: Any,
            method: Method?,
            originalCall: Callable<*>?,
            args: Array<Any?>,
        ): Any? {
            assertThat(self).isInstanceOf(ProxyInvocationHandlerCallProxySuperTarget::class.java)
            assertThat(method?.name).isEqualTo("describe")
            assertThat(args).containsExactly("Ada")
            return originalCall!!.call()
        }
    }
}

class ProxyInvocationHandlerCallProxySuperTarget {
    fun describe(name: String): String = "direct $name"

    fun `super$describe$java_lang_String`(name: String): String = "super $name"
}
