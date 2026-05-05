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

public class ProxyMakerInnerCompanionTest {
    @Test
    fun scansDeclaredMethodsWhenInlineInstrumentationIsUnavailable(): Unit {
        val logger: RecordingLogger = RecordingLogger()
        val handlers: MutableMap<Any, MockKInvocationHandler> = linkedMapOf()
        val target: ProxyMakerCompanionOpenTarget = ProxyMakerCompanionOpenTarget()
        val proxyMaker: ProxyMaker = ProxyMaker(
            logger,
            null,
            NoopSubclassInstrumentation,
            UnusedInstantiator,
            handlers,
        )

        val result: Cancelable<ProxyMakerCompanionOpenTarget> = proxyMaker.proxy(
            ProxyMakerCompanionOpenTarget::class.java,
            emptyArray(),
            NoopInvocationHandler,
            useDefaultConstructor = false,
            instance = target,
        )
        val proxy: ProxyMakerCompanionOpenTarget = result.get()

        assertThat(proxy).isSameAs(target)
        assertThat(handlers).containsEntry(target, NoopInvocationHandler)
        assertThat(logger.debugMessages).anySatisfy { message: String ->
            assertThat(message)
                .contains("ProxyMakerCompanionOpenTarget.finalMessage")
                .contains("because it is final")
        }

        result.cancel()

        assertThat(handlers).doesNotContainKey(target)
    }

    private class RecordingLogger : MockKAgentLogger {
        val debugMessages: MutableList<String> = mutableListOf()

        override fun debug(msg: String): Unit {
            debugMessages += msg
        }

        override fun trace(msg: String): Unit = Unit

        override fun trace(ex: Throwable, msg: String): Unit = Unit

        override fun warn(msg: String): Unit = Unit

        override fun warn(ex: Throwable, msg: String): Unit = Unit
    }

    private object NoopSubclassInstrumentation : SubclassInstrumentation {
        override fun <T> subclass(clazz: Class<T>, interfaces: Array<Class<*>>): Class<T> = clazz

        override fun setProxyHandler(proxy: Any, handler: MockKInvocationHandler): Unit = Unit
    }

    private object UnusedInstantiator : MockKInstantiatior {
        override fun <T> instance(cls: Class<T>): T {
            error("Object mock path should not use the instantiator")
        }
    }

    private object NoopInvocationHandler : MockKInvocationHandler {
        override fun invocation(
            self: Any,
            method: Method?,
            originalCall: Callable<*>?,
            args: Array<Any?>,
        ): Any? = null
    }
}

open class ProxyMakerCompanionOpenTarget {
    fun finalMessage(): String = "final"

    open fun openMessage(): String = "open"
}
