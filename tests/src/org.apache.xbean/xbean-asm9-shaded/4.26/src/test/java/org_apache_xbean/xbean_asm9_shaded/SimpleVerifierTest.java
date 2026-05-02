/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_xbean.xbean_asm9_shaded;

import java.util.ArrayList;
import java.util.List;

import org.apache.xbean.asm9.ClassReader;
import org.apache.xbean.asm9.ClassWriter;
import org.apache.xbean.asm9.Opcodes;
import org.apache.xbean.asm9.tree.AbstractInsnNode;
import org.apache.xbean.asm9.tree.ClassNode;
import org.apache.xbean.asm9.tree.InsnList;
import org.apache.xbean.asm9.tree.InsnNode;
import org.apache.xbean.asm9.tree.LdcInsnNode;
import org.apache.xbean.asm9.tree.MethodInsnNode;
import org.apache.xbean.asm9.tree.MethodNode;
import org.apache.xbean.asm9.tree.VarInsnNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleVerifierTest {
    @Test
    void writesAndReadsClassNodeWithGeneratedMethods() {
        ClassNode generatedClass = createGeneratedClassNode();

        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        generatedClass.accept(classWriter);

        ClassNode parsedClass = new ClassNode();
        new ClassReader(classWriter.toByteArray()).accept(parsedClass, 0);

        assertThat(parsedClass.name).isEqualTo("org/apache/xbean/generated/Greeter");
        assertThat(parsedClass.superName).isEqualTo("java/lang/Object");
        assertThat(parsedClass.methods)
                .extracting(methodNode -> methodNode.name + methodNode.desc)
                .containsExactly("<init>()V", "message()Ljava/lang/String;");
        assertThat(instructionOpcodes(method(parsedClass, "message")))
                .containsExactly(Opcodes.LDC, Opcodes.ARETURN);
    }

    private static ClassNode createGeneratedClassNode() {
        ClassNode classNode = new ClassNode();
        classNode.version = Opcodes.V17;
        classNode.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL;
        classNode.name = "org/apache/xbean/generated/Greeter";
        classNode.superName = "java/lang/Object";
        classNode.methods.add(createConstructor());
        classNode.methods.add(createMessageMethod());
        return classNode;
    }

    private static MethodNode createConstructor() {
        MethodNode methodNode = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        InsnList instructions = methodNode.instructions;
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        instructions.add(new InsnNode(Opcodes.RETURN));
        return methodNode;
    }

    private static MethodNode createMessageMethod() {
        MethodNode methodNode = new MethodNode(Opcodes.ACC_PUBLIC, "message", "()Ljava/lang/String;", null, null);
        InsnList instructions = methodNode.instructions;
        instructions.add(new LdcInsnNode("xbean-asm9-shaded"));
        instructions.add(new InsnNode(Opcodes.ARETURN));
        return methodNode;
    }

    private static MethodNode method(ClassNode classNode, String name) {
        return classNode.methods.stream()
                .filter(methodNode -> methodNode.name.equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static List<Integer> instructionOpcodes(MethodNode methodNode) {
        List<Integer> opcodes = new ArrayList<>();
        for (int index = 0; index < methodNode.instructions.size(); index++) {
            AbstractInsnNode instruction = methodNode.instructions.get(index);
            int opcode = instruction.getOpcode();
            if (opcode >= 0) {
                opcodes.add(opcode);
            }
        }
        return opcodes;
    }
}
