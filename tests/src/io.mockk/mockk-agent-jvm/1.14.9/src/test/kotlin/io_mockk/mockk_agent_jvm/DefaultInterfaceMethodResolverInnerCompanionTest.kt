/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "UNUSED_PARAMETER")

package io_mockk.mockk_agent_jvm

import io.mockk.proxy.jvm.util.DefaultInterfaceMethodResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.lang.reflect.Method
import java.util.concurrent.Callable

public class DefaultInterfaceMethodResolverInnerCompanionTest {
    @Test
    @Timeout(60)
    fun resolvesKotlinInterfaceDefaultImplementationFromDefaultImplsClass(): Unit {
        val mock: KotlinDefaultMethodContract = object : KotlinDefaultMethodContract {
            override fun defaultMessage(value: String, repetitions: Int): String = error("Should use DefaultImpls")
        }
        val interfaceMethod: Method = KotlinDefaultMethodContract::class.java.getMethod(
            "defaultMessage",
            String::class.java,
            Int::class.javaPrimitiveType!!,
        )

        val defaultImplementation: Callable<Any?>? = DefaultInterfaceMethodResolver
            .getDefaultImplementationOrNull(mock, interfaceMethod, arrayOf("mockk", 2))

        assertThat(defaultImplementation).isNotNull
        assertThat(defaultImplementation?.call()).isEqualTo("mockk-mockk")
    }

    public interface KotlinDefaultMethodContract {
        public fun defaultMessage(value: String, repetitions: Int): String

        public class DefaultImpls {
            public companion object {
                @JvmStatic
                public fun defaultMessage(
                    self: KotlinDefaultMethodContract,
                    value: String,
                    repetitions: Int,
                ): String = List(repetitions) { value }.joinToString("-")
            }
        }
    }
}
