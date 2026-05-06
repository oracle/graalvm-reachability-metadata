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
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode

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
    fun loadPluginMethodsInstantiateJvmAgentFactory(): Unit {
        try {
            val internalPlatformClass = PatchedInternalPlatformLoader().loadPatchedInternalPlatform()
            val internalPlatform = internalPlatformClass.getField("INSTANCE").get(null)

            val explicitPlugin = internalPlatformClass
                .getMethod("loadPlugin", String::class.java, String::class.java)
                .invoke(
                    internalPlatform,
                    JvmMockKAgentFactory::class.java.name,
                    "explicit plugin loading test",
                )

            val defaultPlugin = internalPlatformClass
                .getMethod(
                    "loadPlugin\$default",
                    internalPlatformClass,
                    String::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType!!,
                    Any::class.java,
                ).invoke(
                    null,
                    internalPlatform,
                    JvmMockKAgentFactory::class.java.name,
                    "ignored because the default-argument mask selects the default message",
                    2,
                    null,
                )

            assertThat(explicitPlugin).isInstanceOf(JvmMockKAgentFactory::class.java)
            assertThat(defaultPlugin).isInstanceOf(JvmMockKAgentFactory::class.java)
        } catch (error: Error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error
            }
        }
    }
}

// The JVM stubs for inline reified functions throw before their bodies execute, so this loader removes
// only that guard to exercise the plugin-loading bytecode that is present in the published class.
private class PatchedInternalPlatformLoader : ClassLoader(InternalPlatform::class.java.classLoader) {
    fun loadPatchedInternalPlatform(): Class<*> {
        val patchedBytes = removeReifiedMarkersFromLoadPluginMethods()
        return defineClass(InternalPlatform::class.java.name, patchedBytes, 0, patchedBytes.size)
    }

    private fun removeReifiedMarkersFromLoadPluginMethods(): ByteArray {
        val resourceName = "/${InternalPlatform::class.java.name.replace('.', '/')}.class"
        val originalBytes = InternalPlatform::class.java.getResourceAsStream(resourceName)?.use { input ->
            input.readBytes()
        } ?: error("Cannot read ${InternalPlatform::class.java.name} bytecode")

        val classNode = ClassNode()
        ClassReader(originalBytes).accept(classNode, 0)

        classNode.methods
            .filter { method -> method.name == "loadPlugin" || method.name == "loadPlugin\$default" }
            .forEach { method ->
                var instruction = method.instructions.first
                while (instruction != null) {
                    val nextInstruction = instruction.next
                    if (isReifiedOperationMarker(instruction)) {
                        val typeNameInstruction = instruction.previous
                        val markerKindInstruction = checkNotNull(typeNameInstruction?.previous) {
                            "Missing reified marker kind instruction in ${method.name}"
                        }
                        check(typeNameInstruction is LdcInsnNode && typeNameInstruction.cst == "T") {
                            "Unexpected reified marker type-name instruction in ${method.name}"
                        }
                        check(markerKindInstruction.opcode == Opcodes.ICONST_4) {
                            "Unexpected reified marker kind instruction in ${method.name}"
                        }
                        method.instructions.remove(markerKindInstruction)
                        method.instructions.remove(typeNameInstruction)
                        method.instructions.remove(instruction)
                    }
                    instruction = nextInstruction
                }
            }

        val classWriter = ClassWriter(0)
        classNode.accept(classWriter)
        return classWriter.toByteArray()
    }

    private fun isReifiedOperationMarker(instruction: AbstractInsnNode): Boolean =
        instruction is MethodInsnNode &&
            instruction.opcode == Opcodes.INVOKESTATIC &&
            instruction.owner == "kotlin/jvm/internal/Intrinsics" &&
            instruction.name == "reifiedOperationMarker" &&
            instruction.desc == "(ILjava/lang/String;)V"
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
