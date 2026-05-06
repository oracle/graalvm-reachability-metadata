/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_mockk.mockk_jvm

import io.mockk.impl.InternalPlatform
import io.mockk.proxy.jvm.JvmMockKAgentFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class InternalPlatformTest {
    @Test
    fun copyFieldsCopiesPrivateFieldsFromConcreteClassAndSuperclass(): Unit {
        val source = CopyFieldsChild("source-parent", 42, "source-child")
        val target = CopyFieldsChild("target-parent", 7, "target-child")

        InternalPlatform.copyFields(target, source)

        assertThat(target.parentSnapshot()).isEqualTo("source-parent")
        assertThat(target.childSnapshot()).isEqualTo("source-child:42")
    }

    @Test
    fun loadPluginInstantiatesJvmAgentFactoryWithExplicitMessage(): Unit {
        val plugin = InternalPlatform.loadPlugin<JvmMockKAgentFactory>(
            JvmMockKAgentFactory::class.java.name,
            "explicit plugin loading test",
        )

        assertThat(plugin).isInstanceOf(JvmMockKAgentFactory::class.java)
    }

    @Test
    fun loadPluginInstantiatesJvmAgentFactoryWithDefaultMessage(): Unit {
        val plugin = InternalPlatform.loadPlugin<JvmMockKAgentFactory>(
            JvmMockKAgentFactory::class.java.name,
        )

        assertThat(plugin).isInstanceOf(JvmMockKAgentFactory::class.java)
    }
}

private open class CopyFieldsParent(initialParentValue: String) {
    private var parentValue: String = initialParentValue

    fun parentSnapshot(): String = parentValue
}

private class CopyFieldsChild(
    initialParentValue: String,
    initialNumber: Int,
    initialChildValue: String,
) : CopyFieldsParent(initialParentValue) {
    private var number: Int = initialNumber
    private var childValue: String = initialChildValue

    fun childSnapshot(): String = "$childValue:$number"
}
