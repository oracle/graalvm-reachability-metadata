/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jersey_core.jersey_server;

import jersey.repackaged.org.objectweb.asm.ClassWriter;
import jersey.repackaged.org.objectweb.asm.Label;
import jersey.repackaged.org.objectweb.asm.MethodVisitor;
import jersey.repackaged.org.objectweb.asm.Opcodes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassWriterTest {
    @Test
    void computesFramesForBranchesWithDifferentReferenceTypes() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        classWriter.visit(
                Opcodes.V17,
                Opcodes.ACC_PUBLIC,
                "org_glassfish_jersey_core/jersey_server/GeneratedSelection",
                null,
                "java/lang/Object",
                null);

        MethodVisitor method = classWriter.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "select",
                "(Z)Ljava/lang/Object;",
                null,
                null);
        Label useStringBuffer = new Label();
        Label returnResult = new Label();

        method.visitCode();
        method.visitVarInsn(Opcodes.ILOAD, 0);
        method.visitJumpInsn(Opcodes.IFEQ, useStringBuffer);
        method.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
        method.visitJumpInsn(Opcodes.GOTO, returnResult);
        method.visitLabel(useStringBuffer);
        method.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuffer");
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuffer", "<init>", "()V", false);
        method.visitLabel(returnResult);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        classWriter.visitEnd();

        byte[] classFile = classWriter.toByteArray();

        assertThat(classFile).isNotEmpty();
    }
}
