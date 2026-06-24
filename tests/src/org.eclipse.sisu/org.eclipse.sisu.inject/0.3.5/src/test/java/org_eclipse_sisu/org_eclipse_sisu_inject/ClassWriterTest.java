/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.sisu.space.asm.Opcodes.ACC_PUBLIC;
import static org.eclipse.sisu.space.asm.Opcodes.ACC_STATIC;
import static org.eclipse.sisu.space.asm.Opcodes.ACC_SUPER;
import static org.eclipse.sisu.space.asm.Opcodes.ALOAD;
import static org.eclipse.sisu.space.asm.Opcodes.ARETURN;
import static org.eclipse.sisu.space.asm.Opcodes.ASTORE;
import static org.eclipse.sisu.space.asm.Opcodes.DUP;
import static org.eclipse.sisu.space.asm.Opcodes.GOTO;
import static org.eclipse.sisu.space.asm.Opcodes.IFEQ;
import static org.eclipse.sisu.space.asm.Opcodes.ILOAD;
import static org.eclipse.sisu.space.asm.Opcodes.INVOKESPECIAL;
import static org.eclipse.sisu.space.asm.Opcodes.NEW;
import static org.eclipse.sisu.space.asm.Opcodes.V1_6;

import org.eclipse.sisu.space.asm.ClassReader;
import org.eclipse.sisu.space.asm.ClassWriter;
import org.eclipse.sisu.space.asm.Label;
import org.eclipse.sisu.space.asm.MethodVisitor;
import org.junit.jupiter.api.Test;

public class ClassWriterTest {
    @Test
    void computeFramesResolvesCommonSuperclassForMergedBranchTypes() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        writer.visit(V1_6, ACC_PUBLIC | ACC_SUPER, "example/GeneratedLists", null, "java/lang/Object", null);
        addChooseListMethod(writer);
        writer.visitEnd();

        byte[] bytecode = writer.toByteArray();

        assertThat(bytecode).isNotEmpty();
        assertThat(new ClassReader(bytecode).getClassName()).isEqualTo("example/GeneratedLists");
    }

    private static void addChooseListMethod(ClassWriter writer) {
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "choose",
            "(Z)Ljava/util/AbstractList;", null, null);
        Label useLinkedList = new Label();
        Label returnList = new Label();

        method.visitCode();
        method.visitVarInsn(ILOAD, 0);
        method.visitJumpInsn(IFEQ, useLinkedList);
        method.visitTypeInsn(NEW, "java/util/ArrayList");
        method.visitInsn(DUP);
        method.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
        method.visitVarInsn(ASTORE, 1);
        method.visitJumpInsn(GOTO, returnList);

        method.visitLabel(useLinkedList);
        method.visitTypeInsn(NEW, "java/util/LinkedList");
        method.visitInsn(DUP);
        method.visitMethodInsn(INVOKESPECIAL, "java/util/LinkedList", "<init>", "()V", false);
        method.visitVarInsn(ASTORE, 1);

        method.visitLabel(returnList);
        method.visitVarInsn(ALOAD, 1);
        method.visitInsn(ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }
}
