/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import static aj.org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static aj.org.objectweb.asm.Opcodes.ACC_STATIC;
import static aj.org.objectweb.asm.Opcodes.ACC_SUPER;
import static aj.org.objectweb.asm.Opcodes.ALOAD;
import static aj.org.objectweb.asm.Opcodes.ARETURN;
import static aj.org.objectweb.asm.Opcodes.DUP;
import static aj.org.objectweb.asm.Opcodes.GOTO;
import static aj.org.objectweb.asm.Opcodes.IFEQ;
import static aj.org.objectweb.asm.Opcodes.ILOAD;
import static aj.org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static aj.org.objectweb.asm.Opcodes.NEW;
import static aj.org.objectweb.asm.Opcodes.RETURN;
import static aj.org.objectweb.asm.Opcodes.V1_8;
import static org.assertj.core.api.Assertions.assertThat;

import aj.org.objectweb.asm.ClassReader;
import aj.org.objectweb.asm.ClassWriter;
import aj.org.objectweb.asm.Label;
import aj.org.objectweb.asm.MethodVisitor;
import org.junit.jupiter.api.Test;

public class ClassWriterTest {
    private static final String GENERATED_CLASS_NAME = "org_aspectj/aspectjweaver/generated/ClassWriterMergedCollections";

    @Test
    void computesStackFrameCommonSuperclassForMergedCollectionImplementations() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        writer.visit(V1_8, ACC_PUBLIC | ACC_SUPER, GENERATED_CLASS_NAME, null, "java/lang/Object", null);
        writeDefaultConstructor(writer);
        writeCollectionMergeMethod(writer);
        writer.visitEnd();

        byte[] classBytes = writer.toByteArray();

        assertThat(classBytes).isNotEmpty();
        assertThat(new ClassReader(classBytes).getClassName()).isEqualTo(GENERATED_CLASS_NAME);
    }

    private static void writeDefaultConstructor(ClassWriter writer) {
        MethodVisitor constructor = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
    }

    private static void writeCollectionMergeMethod(ClassWriter writer) {
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "chooseList",
                "(Z)Ljava/util/AbstractList;", null, null);
        Label useLinkedList = new Label();
        Label returnList = new Label();

        method.visitCode();
        method.visitVarInsn(ILOAD, 0);
        method.visitJumpInsn(IFEQ, useLinkedList);
        method.visitTypeInsn(NEW, "java/util/ArrayList");
        method.visitInsn(DUP);
        method.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
        method.visitJumpInsn(GOTO, returnList);
        method.visitLabel(useLinkedList);
        method.visitTypeInsn(NEW, "java/util/LinkedList");
        method.visitInsn(DUP);
        method.visitMethodInsn(INVOKESPECIAL, "java/util/LinkedList", "<init>", "()V", false);
        method.visitLabel(returnList);
        method.visitInsn(ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }
}
