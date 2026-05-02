/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.asm.ClassWriter;
import org.springframework.asm.Label;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;

public class ClassWriterTest {

    @Test
    void computesFramesForMergedReferenceTypes() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        writer.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                "org_springframework/spring_core/GeneratedClassWriterFrameMerge",
                null,
                "java/lang/Object",
                null
        );
        writeDefaultConstructor(writer);
        writeMergedReferenceMethod(writer);
        writer.visitEnd();

        byte[] classBytes = writer.toByteArray();

        assertThat(classBytes).startsWith(new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
    }

    @Test
    void resolvesCommonSuperClassThroughProtectedApi() {
        ExposedClassWriter writer = new ExposedClassWriter();

        assertThat(writer.commonSuperClass("java/lang/String", "java/lang/Integer"))
                .isEqualTo("java/lang/Object");
        assertThat(writer.commonSuperClass("java/util/ArrayList", "java/util/AbstractList"))
                .isEqualTo("java/util/AbstractList");
        assertThat(writer.commonSuperClass("java/util/List", "java/io/Serializable"))
                .isEqualTo("java/lang/Object");
    }

    private static void writeDefaultConstructor(ClassWriter writer) {
        MethodVisitor methodVisitor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        methodVisitor.visitInsn(Opcodes.RETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
    }

    private static void writeMergedReferenceMethod(ClassWriter writer) {
        MethodVisitor methodVisitor = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "selectValue",
                "(Z)Ljava/lang/Object;",
                null,
                null
        );
        Label stringLabel = new Label();
        Label endLabel = new Label();

        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(Opcodes.ILOAD, 0);
        methodVisitor.visitJumpInsn(Opcodes.IFEQ, stringLabel);
        methodVisitor.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
        methodVisitor.visitInsn(Opcodes.DUP);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
        methodVisitor.visitJumpInsn(Opcodes.GOTO, endLabel);
        methodVisitor.visitLabel(stringLabel);
        methodVisitor.visitLdcInsn("value");
        methodVisitor.visitLabel(endLabel);
        methodVisitor.visitInsn(Opcodes.ARETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
    }

    private static final class ExposedClassWriter extends ClassWriter {

        ExposedClassWriter() {
            super(0);
        }

        String commonSuperClass(String type1, String type2) {
            return getCommonSuperClass(type1, type2);
        }
    }
}
