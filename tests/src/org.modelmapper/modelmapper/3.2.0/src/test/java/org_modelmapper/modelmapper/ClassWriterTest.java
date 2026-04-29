/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.asm.ClassWriter;
import org.modelmapper.internal.asm.Label;
import org.modelmapper.internal.asm.MethodVisitor;
import org.modelmapper.internal.asm.Opcodes;

public class ClassWriterTest {
    @Test
    void computesCommonSuperClassForMergedReferenceTypes() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        classWriter.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC,
            "org_modelmapper/modelmapper/generated/GeneratedListSelector",
            null,
            "java/lang/Object",
            null);

        MethodVisitor methodVisitor = classWriter.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "selectList",
            "(Z)Ljava/util/List;",
            null,
            null);
        methodVisitor.visitCode();

        Label useLinkedList = new Label();
        Label returnList = new Label();
        methodVisitor.visitVarInsn(Opcodes.ILOAD, 0);
        methodVisitor.visitJumpInsn(Opcodes.IFEQ, useLinkedList);
        methodVisitor.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList");
        methodVisitor.visitInsn(Opcodes.DUP);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
        methodVisitor.visitJumpInsn(Opcodes.GOTO, returnList);

        methodVisitor.visitLabel(useLinkedList);
        methodVisitor.visitTypeInsn(Opcodes.NEW, "java/util/LinkedList");
        methodVisitor.visitInsn(Opcodes.DUP);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/LinkedList", "<init>", "()V", false);

        methodVisitor.visitLabel(returnList);
        methodVisitor.visitInsn(Opcodes.ARETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
        classWriter.visitEnd();

        byte[] generatedClass = classWriter.toByteArray();

        assertThat(generatedClass).isNotEmpty();
    }
}
