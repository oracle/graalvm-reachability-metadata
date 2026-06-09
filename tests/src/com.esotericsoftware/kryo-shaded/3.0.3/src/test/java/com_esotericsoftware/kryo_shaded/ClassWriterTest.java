/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import static org.assertj.core.api.Assertions.assertThat;

import com.esotericsoftware.reflectasm.shaded.org.objectweb.asm.ClassReader;
import com.esotericsoftware.reflectasm.shaded.org.objectweb.asm.ClassWriter;
import com.esotericsoftware.reflectasm.shaded.org.objectweb.asm.Label;
import com.esotericsoftware.reflectasm.shaded.org.objectweb.asm.MethodVisitor;
import com.esotericsoftware.reflectasm.shaded.org.objectweb.asm.Opcodes;
import org.junit.jupiter.api.Test;

public class ClassWriterTest {
    @Test
    void commonSuperclassResolverUsesClassLoader() {
        ExposedClassWriter writer = new ExposedClassWriter();

        assertThat(writer.getCommonSuperclass(internalName(FirstBranch.class), internalName(SecondBranch.class)))
                .isEqualTo(internalName(BranchBase.class));
        assertThat(writer.getCommonSuperclass(internalName(BranchBase.class), internalName(FirstBranch.class)))
                .isEqualTo(internalName(BranchBase.class));
        assertThat(writer.getCommonSuperclass(internalName(BranchMarker.class), internalName(FirstBranch.class)))
                .isEqualTo(internalName(BranchMarker.class));
    }

    @Test
    void computedFramesResolveCommonSuperclassForMergedBranchTypes() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        writer.visit(
                Opcodes.V1_6,
                Opcodes.ACC_PUBLIC,
                "generated/CommonSuperclassMergeExample",
                null,
                "java/lang/Object",
                null);
        writeDefaultConstructor(writer);
        writeMergedBranchMethod(writer);
        writer.visitEnd();

        byte[] classBytes = writer.toByteArray();
        ClassReader reader = new ClassReader(classBytes);

        assertThat(classBytes).isNotEmpty();
        assertThat(reader.getClassName()).isEqualTo("generated/CommonSuperclassMergeExample");
        assertThat(reader.getSuperName()).isEqualTo("java/lang/Object");
    }

    private static String internalName(Class<?> type) {
        return type.getName().replace('.', '/');
    }

    public interface BranchMarker {
    }

    public static class BranchBase implements BranchMarker {
    }

    public static class FirstBranch extends BranchBase {
    }

    public static class SecondBranch extends BranchBase {
    }

    private static final class ExposedClassWriter extends ClassWriter {
        private ExposedClassWriter() {
            super(ClassWriter.COMPUTE_FRAMES);
        }

        private String getCommonSuperclass(String firstType, String secondType) {
            return getCommonSuperClass(firstType, secondType);
        }
    }

    private static void writeDefaultConstructor(ClassWriter writer) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static void writeMergedBranchMethod(ClassWriter writer) {
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "chooseBranch",
                "(Z)L" + internalName(BranchBase.class) + ";",
                null,
                null);
        Label useLinkedList = new Label();
        Label returnList = new Label();

        method.visitCode();
        method.visitVarInsn(Opcodes.ILOAD, 0);
        method.visitJumpInsn(Opcodes.IFEQ, useLinkedList);
        method.visitTypeInsn(Opcodes.NEW, internalName(FirstBranch.class));
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, internalName(FirstBranch.class), "<init>", "()V");
        method.visitJumpInsn(Opcodes.GOTO, returnList);
        method.visitLabel(useLinkedList);
        method.visitTypeInsn(Opcodes.NEW, internalName(SecondBranch.class));
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, internalName(SecondBranch.class), "<init>", "()V");
        method.visitLabel(returnList);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }
}
