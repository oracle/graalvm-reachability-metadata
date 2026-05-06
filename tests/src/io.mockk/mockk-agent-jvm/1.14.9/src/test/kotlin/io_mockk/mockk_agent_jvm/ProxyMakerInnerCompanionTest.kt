/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io_mockk.mockk_agent_jvm

import io.mockk.proxy.Cancelable
import io.mockk.proxy.MockKAgentLogger
import io.mockk.proxy.MockKInvocationHandler
import io.mockk.proxy.MockKProxyMaker
import io.mockk.proxy.jvm.ObjenesisInstantiator
import io.mockk.proxy.jvm.ProxyMaker
import io.mockk.proxy.jvm.advice.jvm.MockHandlerMap
import io.mockk.proxy.jvm.transformation.SubclassInstrumentation
import net.bytebuddy.ByteBuddy
import net.bytebuddy.dynamic.scaffold.TypeValidation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.lang.reflect.Method
import java.util.concurrent.Callable

public class ProxyMakerInnerCompanionTest {
    @Test
    @Timeout(60)
    fun proxyCreationWithoutInlineInstrumentationScansInheritedFinalMethods(): Unit {
        val logger: CapturingLogger = CapturingLogger()

        try {
            val proxyMaker: MockKProxyMaker = newProxyMakerWithoutInlineInstrumentation(logger)
            val proxy: Cancelable<ProxyMakerCompanionChildTarget> = proxyMaker.proxy(
                ProxyMakerCompanionChildTarget::class.java,
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
                assertThat(proxy.get().baseFinalMessage()).isEqualTo("base")
            } finally {
                proxy.cancel()
            }
        } catch (throwable: Throwable) {
            if (!MockkNativeImageSupport.isExpectedNativeImageFailure(throwable)) {
                throw throwable
            }
            return
        }

        assertThat(logger.debugMessages)
            .anySatisfy { message: String -> assertThat(message).contains("baseFinalMessage") }
        assertThat(logger.debugMessages)
            .anySatisfy { message: String -> assertThat(message).contains("childFinalMessage") }
    }

    private fun newProxyMakerWithoutInlineInstrumentation(logger: MockKAgentLogger): MockKProxyMaker {
        val byteBuddy: ByteBuddy = ByteBuddy().with(TypeValidation.DISABLED)
        val handlers: MockHandlerMap = MockHandlerMap.create(true)
        return ProxyMaker(
            logger,
            null,
            SubclassInstrumentation(logger, handlers, byteBuddy),
            ObjenesisInstantiator(logger, byteBuddy),
            handlers,
        )
    }

    private class CapturingLogger : MockKAgentLogger {
        val debugMessages: MutableList<String> = mutableListOf()

        override fun debug(msg: String): Unit {
            debugMessages.add(msg)
        }

        override fun trace(msg: String): Unit = Unit

        override fun trace(ex: Throwable, msg: String): Unit = Unit

        override fun warn(msg: String): Unit = Unit

        override fun warn(ex: Throwable, msg: String): Unit = Unit
    }

    public open class ProxyMakerCompanionBaseTarget {
        public fun baseFinalMessage(): String = "base"
    }

    public open class ProxyMakerCompanionChildTarget : ProxyMakerCompanionBaseTarget() {
        public fun childFinalMessage(): String = "child"
    }
}
