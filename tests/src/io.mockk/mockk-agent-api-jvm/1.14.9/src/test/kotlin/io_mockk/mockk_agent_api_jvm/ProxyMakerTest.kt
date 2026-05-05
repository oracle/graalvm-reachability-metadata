/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_mockk.mockk_agent_api_jvm

import io.mockk.proxy.Cancelable
import io.mockk.proxy.MockKAgentLogger
import io.mockk.proxy.MockKInstantiatior
import io.mockk.proxy.MockKInvocationHandler
import io.mockk.proxy.common.ProxyMaker
import io.mockk.proxy.common.transformation.SubclassInstrumentation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.Method
import java.util.concurrent.Callable

class ProxyMakerTest {
    @Test
    fun createsJdkProxyForInterfaceMocks(): Unit {
        val proxyMaker: ProxyMaker = newProxyMaker()
        val invocationHandler: RecordingInvocationHandler = RecordingInvocationHandler("hello Ada")

        val proxy: ProxyMakerGreetingService = proxyMaker.proxy(
            ProxyMakerGreetingService::class.java,
            emptyArray(),
            invocationHandler,
            useDefaultConstructor = false,
            instance = null,
        ).get()

        assertThat(proxy.greet("Ada")).isEqualTo("hello Ada")
        assertThat(invocationHandler.invokedMethodName).isEqualTo("greet")
        assertThat(invocationHandler.arguments).containsExactly("Ada")
    }

    @Test
    fun createsClassProxyViaDefaultConstructor(): Unit {
        val handlers: MutableMap<Any, MockKInvocationHandler> = linkedMapOf()
        val subclasser: RecordingSubclassInstrumentation = RecordingSubclassInstrumentation()
        val proxyMaker: ProxyMaker = newProxyMaker(subclasser = subclasser, handlers = handlers)
        val invocationHandler: RecordingInvocationHandler = RecordingInvocationHandler(null)

        val result: Cancelable<ProxyMakerConstructibleTarget> = proxyMaker.proxy(
            ProxyMakerConstructibleTarget::class.java,
            emptyArray(),
            invocationHandler,
            useDefaultConstructor = true,
            instance = null,
        )
        val proxy: ProxyMakerConstructibleTarget = result.get()

        assertThat(proxy.message()).isEqualTo("constructed")
        assertThat(subclasser.proxyHandlerAssignments).containsEntry(proxy, invocationHandler)
        assertThat(handlers).containsEntry(proxy, invocationHandler)

        result.cancel()

        assertThat(handlers).doesNotContainKey(proxy)
    }

    private fun newProxyMaker(
        subclasser: RecordingSubclassInstrumentation = RecordingSubclassInstrumentation(),
        handlers: MutableMap<Any, MockKInvocationHandler> = linkedMapOf(),
    ): ProxyMaker = ProxyMaker(
        NoopLogger,
        null,
        subclasser,
        UnusedInstantiator,
        handlers,
    )

    private object NoopLogger : MockKAgentLogger {
        override fun debug(msg: String): Unit = Unit

        override fun trace(msg: String): Unit = Unit

        override fun trace(ex: Throwable, msg: String): Unit = Unit

        override fun warn(msg: String): Unit = Unit

        override fun warn(ex: Throwable, msg: String): Unit = Unit
    }

    private object UnusedInstantiator : MockKInstantiatior {
        override fun <T> instance(cls: Class<T>): T {
            error("The default-constructor path should not use the instantiator")
        }
    }

    private class RecordingSubclassInstrumentation : SubclassInstrumentation {
        val proxyHandlerAssignments: MutableMap<Any, MockKInvocationHandler> = linkedMapOf()

        override fun <T> subclass(clazz: Class<T>, interfaces: Array<Class<*>>): Class<T> = clazz

        override fun setProxyHandler(proxy: Any, handler: MockKInvocationHandler): Unit {
            proxyHandlerAssignments[proxy] = handler
        }
    }

    private class RecordingInvocationHandler(
        private val returnValue: Any?,
    ) : MockKInvocationHandler {
        var invokedMethodName: String? = null
            private set
        var arguments: List<Any?> = emptyList()
            private set

        override fun invocation(
            self: Any,
            method: Method?,
            originalCall: Callable<*>?,
            args: Array<Any?>,
        ): Any? {
            invokedMethodName = method?.name
            arguments = args.toList()
            return returnValue
        }
    }
}

interface ProxyMakerGreetingService {
    fun greet(name: String): String
}

class ProxyMakerConstructibleTarget {
    fun message(): String = "constructed"
}
