/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import static org.assertj.core.api.Assertions.assertThat;

import net.sf.cglib.asm.ClassReader;
import net.sf.cglib.asm.ClassWriter;
import net.sf.cglib.asm.Label;
import net.sf.cglib.asm.MethodVisitor;
import net.sf.cglib.asm.Opcodes;
import org.junit.jupiter.api.Test;

public class ClassWriterTest {
    private static final String GENERATED_CLASS_NAME = "cglib/cglib_nodep/generated/MergedCollectionFactory";

    @Test
    void computesStackMapFramesForBranchesWithDifferentCollectionImplementations() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        classWriter.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, GENERATED_CLASS_NAME, null, "java/lang/Object", null);
        writeDefaultConstructor(classWriter);
        writeFactoryMethodWithMergedStackType(classWriter);
        classWriter.visitEnd();

        byte[] classBytes = classWriter.toByteArray();
        ClassReader classReader = new ClassReader(classBytes);

        assertThat(classBytes).isNotEmpty();
        assertThat(classReader.getClassName()).isEqualTo(GENERATED_CLASS_NAME);
        assertThat(classReader.getSuperName()).isEqualTo("java/lang/Object");
    }

    private static void writeDefaultConstructor(ClassWriter classWriter) {
        MethodVisitor constructor = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
    }

    private static void writeFactoryMethodWithMergedStackType(ClassWriter classWriter) {
        MethodVisitor method = classWriter.visitMethod(
                Opcodes.ACC_PUBLIC,
                "chooseCollection",
                "(Z)Ljava/lang/Object;",
                null,
                null);
        Label useLinkedList = new Label();
        Label returnValue = new Label();

        method.visitCode();
        method.visitVarInsn(Opcodes.ILOAD, 1);
        method.visitJumpInsn(Opcodes.IFEQ, useLinkedList);
        method.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList");
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V");
        method.visitJumpInsn(Opcodes.GOTO, returnValue);
        method.visitLabel(useLinkedList);
        method.visitTypeInsn(Opcodes.NEW, "java/util/LinkedList");
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/LinkedList", "<init>", "()V");
        method.visitLabel(returnValue);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }
}
