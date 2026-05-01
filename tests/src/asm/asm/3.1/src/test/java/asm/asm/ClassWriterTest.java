/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package asm.asm;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassWriterTest {
    @Test
    public void computesFramesByResolvingCommonSuperclassForMergedStackValues() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        classWriter.visit(
                Opcodes.V1_6,
                Opcodes.ACC_PUBLIC,
                "asm/asm/GeneratedExceptionSelector",
                null,
                internalName(Object.class),
                null);
        MethodVisitor methodVisitor = classWriter.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "selectException",
                "(Z)Ljava/lang/RuntimeException;",
                null,
                null);
        Label useIllegalStateException = new Label();
        Label returnMergedException = new Label();

        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(Opcodes.ILOAD, 0);
        methodVisitor.visitJumpInsn(Opcodes.IFEQ, useIllegalStateException);
        methodVisitor.visitTypeInsn(Opcodes.NEW, internalName(IllegalArgumentException.class));
        methodVisitor.visitInsn(Opcodes.DUP);
        methodVisitor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                internalName(IllegalArgumentException.class),
                "<init>",
                "()V");
        methodVisitor.visitJumpInsn(Opcodes.GOTO, returnMergedException);
        methodVisitor.visitLabel(useIllegalStateException);
        methodVisitor.visitTypeInsn(Opcodes.NEW, internalName(IllegalStateException.class));
        methodVisitor.visitInsn(Opcodes.DUP);
        methodVisitor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                internalName(IllegalStateException.class),
                "<init>",
                "()V");
        methodVisitor.visitLabel(returnMergedException);
        methodVisitor.visitInsn(Opcodes.ARETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
        classWriter.visitEnd();

        byte[] bytecode = classWriter.toByteArray();

        assertThat(bytecode).startsWith((byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE);
    }

    @Test
    public void returnsSharedSuperclassForLoadedExceptionTypes() {
        TestableClassWriter classWriter = new TestableClassWriter();

        String commonSuperClass = classWriter.commonSuperClass(
                internalName(IllegalArgumentException.class),
                internalName(IllegalStateException.class));

        assertThat(commonSuperClass).isEqualTo(internalName(RuntimeException.class));
    }

    @Test
    public void returnsFirstTypeWhenItIsAssignableFromSecondType() {
        TestableClassWriter classWriter = new TestableClassWriter();

        String commonSuperClass = classWriter.commonSuperClass(
                internalName(RuntimeException.class),
                internalName(IllegalArgumentException.class));

        assertThat(commonSuperClass).isEqualTo(internalName(RuntimeException.class));
    }

    @Test
    public void returnsSecondTypeWhenItIsAssignableFromFirstType() {
        TestableClassWriter classWriter = new TestableClassWriter();

        String commonSuperClass = classWriter.commonSuperClass(
                internalName(IllegalStateException.class),
                internalName(RuntimeException.class));

        assertThat(commonSuperClass).isEqualTo(internalName(RuntimeException.class));
    }

    @Test
    public void returnsObjectWhenEitherLoadedTypeIsAnInterface() {
        TestableClassWriter classWriter = new TestableClassWriter();

        String commonSuperClass = classWriter.commonSuperClass(
                internalName(List.class),
                internalName(Set.class));

        assertThat(commonSuperClass).isEqualTo(internalName(Object.class));
    }

    private static String internalName(Class<?> type) {
        return type.getName().replace('.', '/');
    }

    private static final class TestableClassWriter extends ClassWriter {
        private TestableClassWriter() {
            super(0);
        }

        private String commonSuperClass(String firstType, String secondType) {
            return super.getCommonSuperClass(firstType, secondType);
        }
    }
}
