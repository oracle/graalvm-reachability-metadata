/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_guice;

import static com.google.inject.internal.asm.Opcodes.ACC_PUBLIC;
import static com.google.inject.internal.asm.Opcodes.ACC_STATIC;
import static com.google.inject.internal.asm.Opcodes.ACC_SUPER;
import static com.google.inject.internal.asm.Opcodes.ALOAD;
import static com.google.inject.internal.asm.Opcodes.ARETURN;
import static com.google.inject.internal.asm.Opcodes.DUP;
import static com.google.inject.internal.asm.Opcodes.GOTO;
import static com.google.inject.internal.asm.Opcodes.IFEQ;
import static com.google.inject.internal.asm.Opcodes.ILOAD;
import static com.google.inject.internal.asm.Opcodes.INVOKESPECIAL;
import static com.google.inject.internal.asm.Opcodes.NEW;
import static com.google.inject.internal.asm.Opcodes.RETURN;
import static com.google.inject.internal.asm.Opcodes.V1_6;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.internal.asm.ClassReader;
import com.google.inject.internal.asm.ClassWriter;
import com.google.inject.internal.asm.Label;
import com.google.inject.internal.asm.MethodVisitor;
import org.junit.jupiter.api.Test;

public class ClassWriterTest {
    private static final String PACKAGE_INTERNAL_NAME = "org_sonatype_sisu/sisu_guice/";
    private static final String VALUE_TYPE = PACKAGE_INTERNAL_NAME + "ClassWriterValue";
    private static final String WORD_VALUE_TYPE = PACKAGE_INTERNAL_NAME + "ClassWriterWordValue";
    private static final String NUMBER_VALUE_TYPE =
            PACKAGE_INTERNAL_NAME + "ClassWriterNumberValue";

    @Test
    void computesStackFramesForBranchesWithDifferentLoadableTypes() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        String generatedType = PACKAGE_INTERNAL_NAME + "GeneratedClassWriterMerge";

        writeMergeClass(writer, generatedType);
        ClassReader reader = new ClassReader(writer.toByteArray());

        assertThat(reader.getClassName()).isEqualTo(generatedType);
        assertThat(reader.getSuperName()).isEqualTo("java/lang/Object");
    }

    @Test
    void resolvesCommonSuperClassWhileComputingFrames() {
        CountingClassWriter writer = new CountingClassWriter();
        String generatedType = PACKAGE_INTERNAL_NAME + "GeneratedCountingClassWriterMerge";

        writeMergeClass(writer, generatedType);
        ClassReader reader = new ClassReader(writer.toByteArray());

        assertThat(reader.getClassName()).isEqualTo(generatedType);
        assertThat(writer.commonSuperClassCalls).isPositive();
    }

    private static void writeMergeClass(ClassWriter writer, String generatedType) {
        writer.visit(V1_6, ACC_PUBLIC | ACC_SUPER, generatedType, null, "java/lang/Object", null);
        writeDefaultConstructor(writer);
        writeMergedReturnMethod(writer);
        writer.visitEnd();
    }

    private static void writeDefaultConstructor(ClassWriter writer) {
        MethodVisitor constructor = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
    }

    private static void writeMergedReturnMethod(ClassWriter writer) {
        MethodVisitor method = writer.visitMethod(
                ACC_PUBLIC | ACC_STATIC,
                "choose",
                "(Z)L" + VALUE_TYPE + ";",
                null,
                null);
        Label elseBranch = new Label();
        Label returnValue = new Label();

        method.visitCode();
        method.visitVarInsn(ILOAD, 0);
        method.visitJumpInsn(IFEQ, elseBranch);
        method.visitTypeInsn(NEW, WORD_VALUE_TYPE);
        method.visitInsn(DUP);
        method.visitMethodInsn(INVOKESPECIAL, WORD_VALUE_TYPE, "<init>", "()V");
        method.visitJumpInsn(GOTO, returnValue);
        method.visitLabel(elseBranch);
        method.visitTypeInsn(NEW, NUMBER_VALUE_TYPE);
        method.visitInsn(DUP);
        method.visitMethodInsn(INVOKESPECIAL, NUMBER_VALUE_TYPE, "<init>", "()V");
        method.visitLabel(returnValue);
        method.visitInsn(ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static final class CountingClassWriter extends ClassWriter {
        private int commonSuperClassCalls;

        private CountingClassWriter() {
            super(ClassWriter.COMPUTE_FRAMES);
        }

        @Override
        protected String getCommonSuperClass(String left, String right) {
            commonSuperClassCalls++;
            return super.getCommonSuperClass(left, right);
        }
    }
}

class ClassWriterValue {
}

final class ClassWriterWordValue extends ClassWriterValue {
}

final class ClassWriterNumberValue extends ClassWriterValue {
}
