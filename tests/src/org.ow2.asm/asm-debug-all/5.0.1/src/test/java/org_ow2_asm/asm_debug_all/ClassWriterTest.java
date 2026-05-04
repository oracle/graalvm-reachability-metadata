/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ow2_asm.asm_debug_all;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassWriterTest {
    @Test
    void computesCommonSuperClassWhenMergingControlFlowFrames() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        classWriter.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                "org_ow2_asm/asm_debug_all/GeneratedLists", null, "java/lang/Object", null);

        MethodVisitor methodVisitor = classWriter.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "chooseList", "(Z)Ljava/util/AbstractList;", null, null);
        methodVisitor.visitCode();
        Label elseBranch = new Label();
        Label done = new Label();
        methodVisitor.visitVarInsn(Opcodes.ILOAD, 0);
        methodVisitor.visitJumpInsn(Opcodes.IFEQ, elseBranch);
        methodVisitor.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList");
        methodVisitor.visitInsn(Opcodes.DUP);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
        methodVisitor.visitJumpInsn(Opcodes.GOTO, done);
        methodVisitor.visitLabel(elseBranch);
        methodVisitor.visitTypeInsn(Opcodes.NEW, "java/util/LinkedList");
        methodVisitor.visitInsn(Opcodes.DUP);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/LinkedList", "<init>", "()V", false);
        methodVisitor.visitLabel(done);
        methodVisitor.visitInsn(Opcodes.ARETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
        classWriter.visitEnd();

        byte[] bytecode = classWriter.toByteArray();

        assertThat(bytecode).isNotEmpty();
        assertThat(new ClassReader(bytecode).getClassName())
                .isEqualTo("org_ow2_asm/asm_debug_all/GeneratedLists");
    }
}
