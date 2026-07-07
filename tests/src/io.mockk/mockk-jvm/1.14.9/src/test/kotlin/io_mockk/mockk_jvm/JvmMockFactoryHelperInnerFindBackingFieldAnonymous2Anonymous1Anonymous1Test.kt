/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_mockk.mockk_jvm

import io.mockk.Answer
import io.mockk.BackingFieldValue
import io.mockk.BackingFieldValueProvider
import io.mockk.Invocation
import io.mockk.InvocationMatcher
import io.mockk.MethodDescription
import io.mockk.MockKGateway
import io.mockk.impl.instantiation.JvmMockFactoryHelper
import io.mockk.impl.stub.Stub
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.Method
import java.util.concurrent.Callable
import kotlin.reflect.KClass

public class JvmMockFactoryHelperInnerFindBackingFieldAnonymous2Anonymous1Anonymous1Test {
    @Test
    fun invocationProviderReadsKotlinPropertyBackingField(): Unit {
        val target: TargetWithBackingField = TargetWithBackingField("stored value")
        val getter: Method = TargetWithBackingField::class.java.getDeclaredMethod("getMessage")
        val stub: CapturingStub = CapturingStub { fieldValueProvider ->
            val fieldValue: BackingFieldValue = fieldValueProvider()
                ?: error("Kotlin property getter should expose a backing field")
            fieldValue.getter()
        }

        val result: Any? = JvmMockFactoryHelper.mockHandler(stub).invocation(
            target,
            getter,
            Callable { target.message },
            emptyArray<Any?>(),
        )

        assertThat(result).isEqualTo("stored value")
    }

    private class TargetWithBackingField(
        val message: String,
    )

    private class CapturingStub(
        private val invocationHandler: (BackingFieldValueProvider) -> Any?,
    ) : Stub {
        override val name: String = "capturing-stub"
        override val type: KClass<*> = TargetWithBackingField::class
        override val threadId: Long = Thread.currentThread().id

        override fun addAnswer(
            matcher: InvocationMatcher,
            answer: Answer<*>,
        ): Unit = unused()

        override fun answer(invocation: Invocation): Any? = unused()

        override fun childMockK(
            matcher: InvocationMatcher,
            childType: KClass<*>,
        ): Any = unused()

        override fun recordCall(invocation: Invocation): Unit = unused()

        override fun allRecordedCalls(): List<Invocation> = emptyList()

        override fun allRecordedCalls(method: MethodDescription): List<Invocation> = emptyList()

        override fun excludeRecordedCalls(
            params: MockKGateway.ExclusionParameters,
            matcher: InvocationMatcher,
        ): Unit = unused()

        override fun markCallVerified(invocation: Invocation): Unit = unused()

        override fun verifiedCalls(): List<Invocation> = emptyList()

        override fun matcherUsages(): Map<InvocationMatcher, Int> = emptyMap()

        override fun clear(options: MockKGateway.ClearOptions): Unit = Unit

        override fun handleInvocation(
            self: Any,
            method: MethodDescription,
            originalCall: () -> Any?,
            args: Array<out Any?>,
            fieldValueProvider: BackingFieldValueProvider,
        ): Any? = invocationHandler(fieldValueProvider)

        override fun toStr(): String = name

        override fun stdObjectAnswer(invocation: Invocation): Any? = unused()

        override fun dispose(): Unit = Unit

        private fun unused(): Nothing = error("This stub method is not used by the backing-field scenario")
    }
}
