/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_minidev.json_smart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_8;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

public class ClassWriterTest {
    private static final String GENERATED_CLASS_NAME = "net_minidev/json_smart/GeneratedFrameMergeSample";

    @Test
    void resolvesCommonSuperclassThroughAsmClassLoaderLookup() {
        CommonSuperClassWriter writer = new CommonSuperClassWriter();

        String commonSuperClass = writer.commonSuperClass("java/lang/String", "java/lang/StringBuilder");

        assertThat(commonSuperClass).isEqualTo("java/lang/Object");
    }

    @Test
    void computesStackMapFramesForMergedReferenceTypes() throws IOException {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        writer.visit(V1_8, ACC_PUBLIC | ACC_SUPER, GENERATED_CLASS_NAME, null, "java/lang/Object", null);
        writeConstructor(writer);
        writeMergedReferenceMethod(writer);
        writer.visitEnd();

        byte[] classBytes = writer.toByteArray();
        ClassReader reader = new ClassReader(classBytes);

        assertThat(classBytes).isNotEmpty();
        assertThat(reader.getClassName()).isEqualTo(GENERATED_CLASS_NAME);
        assertThat(reader.getSuperName()).isEqualTo("java/lang/Object");
    }

    private static final class CommonSuperClassWriter extends ClassWriter {
        CommonSuperClassWriter() {
            super(0);
        }

        String commonSuperClass(String firstType, String secondType) {
            return getCommonSuperClass(firstType, secondType);
        }
    }

    private static void writeConstructor(ClassWriter writer) {
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        method.visitCode();
        method.visitVarInsn(ALOAD, 0);
        method.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
        method.visitInsn(RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static void writeMergedReferenceMethod(ClassWriter writer) {
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "choose", "(Z)Ljava/lang/Object;", null, null);
        Label useBuilder = new Label();
        Label returnValue = new Label();

        method.visitCode();
        method.visitVarInsn(ILOAD, 1);
        method.visitJumpInsn(IFEQ, useBuilder);
        method.visitTypeInsn(NEW, "java/lang/String");
        method.visitInsn(DUP);
        method.visitLdcInsn("json-smart");
        method.visitMethodInsn(INVOKESPECIAL, "java/lang/String", "<init>", "(Ljava/lang/String;)V");
        method.visitJumpInsn(GOTO, returnValue);
        method.visitLabel(useBuilder);
        method.visitTypeInsn(NEW, "java/lang/StringBuilder");
        method.visitInsn(DUP);
        method.visitLdcInsn("json-smart");
        method.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V");
        method.visitLabel(returnValue);
        method.visitInsn(ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }
}
