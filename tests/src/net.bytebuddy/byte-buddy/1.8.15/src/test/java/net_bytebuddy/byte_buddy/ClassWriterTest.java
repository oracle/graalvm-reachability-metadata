/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassWriterTest {
    @Test
    void computesFramesForBranchesWithDifferentCollectionImplementations() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        classWriter.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                "net_bytebuddy/byte_buddy/generated/AsmFrameMergeSample",
                null,
                "java/lang/Object",
                null);
        writeDefaultConstructor(classWriter);
        writeChooserMethod(classWriter);
        classWriter.visitEnd();

        byte[] generatedClass = classWriter.toByteArray();

        assertThat(generatedClass).isNotEmpty();
        assertThat(new ClassReader(generatedClass).getClassName())
                .isEqualTo("net_bytebuddy/byte_buddy/generated/AsmFrameMergeSample");
    }

    private static void writeDefaultConstructor(ClassWriter classWriter) {
        MethodVisitor constructor = classWriter.visitMethod(
                Opcodes.ACC_PUBLIC,
                "<init>",
                "()V",
                null,
                null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
    }

    private static void writeChooserMethod(ClassWriter classWriter) {
        MethodVisitor methodVisitor = classWriter.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "chooseCollection",
                "(Z)Ljava/lang/Object;",
                null,
                null);
        Label linkedListBranch = new Label();
        Label merge = new Label();

        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(Opcodes.ILOAD, 0);
        methodVisitor.visitJumpInsn(Opcodes.IFEQ, linkedListBranch);
        methodVisitor.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList");
        methodVisitor.visitInsn(Opcodes.DUP);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
        methodVisitor.visitJumpInsn(Opcodes.GOTO, merge);
        methodVisitor.visitLabel(linkedListBranch);
        methodVisitor.visitTypeInsn(Opcodes.NEW, "java/util/LinkedList");
        methodVisitor.visitInsn(Opcodes.DUP);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/LinkedList", "<init>", "()V", false);
        methodVisitor.visitLabel(merge);
        methodVisitor.visitInsn(Opcodes.ARETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
    }
}
