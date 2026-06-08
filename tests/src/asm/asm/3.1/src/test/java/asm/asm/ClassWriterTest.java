/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package asm.asm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class ClassWriterTest extends ClassWriter {
    public ClassWriterTest() {
        super(0);
    }

    @Test
    void findsCommonSuperClassForApplicationTypes() {
        String commonSuperClass = getCommonSuperClass(
                internalName(ClassWriterFirstChild.class),
                internalName(ClassWriterSecondChild.class));

        assertThat(commonSuperClass).isEqualTo(internalName(ClassWriterSharedParent.class));
    }

    @Test
    void computesFramesForMergedApplicationTypes() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        writer.visit(
                Opcodes.V1_6,
                Opcodes.ACC_PUBLIC,
                "asm/asm/GeneratedMergedCollections",
                null,
                "java/lang/Object",
                null);
        writeDefaultConstructor(writer);
        writeChooseMethod(writer);
        writer.visitEnd();

        byte[] bytecode = writer.toByteArray();
        ClassReader reader = new ClassReader(bytecode);

        assertThat(bytecode).isNotEmpty();
        assertThat(reader.getClassName()).isEqualTo("asm/asm/GeneratedMergedCollections");
        assertThat(reader.getSuperName()).isEqualTo("java/lang/Object");
    }

    private static String internalName(Class<?> type) {
        return type.getName().replace('.', '/');
    }

    private static void writeDefaultConstructor(ClassWriter writer) {
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
    }

    private static void writeChooseMethod(ClassWriter writer) {
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "choose",
                "(Z)L" + internalName(ClassWriterSharedParent.class) + ";",
                null,
                null);
        Label useLinkedList = new Label();
        Label done = new Label();

        method.visitCode();
        method.visitVarInsn(Opcodes.ILOAD, 0);
        method.visitJumpInsn(Opcodes.IFEQ, useLinkedList);
        method.visitTypeInsn(Opcodes.NEW, internalName(ClassWriterFirstChild.class));
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, internalName(ClassWriterFirstChild.class), "<init>", "()V");
        method.visitJumpInsn(Opcodes.GOTO, done);
        method.visitLabel(useLinkedList);
        method.visitTypeInsn(Opcodes.NEW, internalName(ClassWriterSecondChild.class));
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, internalName(ClassWriterSecondChild.class), "<init>", "()V");
        method.visitLabel(done);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }
}

class ClassWriterSharedParent {
}

class ClassWriterFirstChild extends ClassWriterSharedParent {
}

class ClassWriterSecondChild extends ClassWriterSharedParent {
}
