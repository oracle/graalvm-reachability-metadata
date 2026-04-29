/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import static com.esotericsoftware.reflectasm.shaded.org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static com.esotericsoftware.reflectasm.shaded.org.objectweb.asm.Opcodes.ACC_STATIC;
import static com.esotericsoftware.reflectasm.shaded.org.objectweb.asm.Opcodes.ALOAD;
import static com.esotericsoftware.reflectasm.shaded.org.objectweb.asm.Opcodes.ARETURN;
import static com.esotericsoftware.reflectasm.shaded.org.objectweb.asm.Opcodes.DUP;
import static com.esotericsoftware.reflectasm.shaded.org.objectweb.asm.Opcodes.GOTO;
import static com.esotericsoftware.reflectasm.shaded.org.objectweb.asm.Opcodes.IFEQ;
import static com.esotericsoftware.reflectasm.shaded.org.objectweb.asm.Opcodes.ILOAD;
import static com.esotericsoftware.reflectasm.shaded.org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static com.esotericsoftware.reflectasm.shaded.org.objectweb.asm.Opcodes.NEW;
import static com.esotericsoftware.reflectasm.shaded.org.objectweb.asm.Opcodes.RETURN;
import static com.esotericsoftware.reflectasm.shaded.org.objectweb.asm.Opcodes.V1_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.esotericsoftware.reflectasm.shaded.org.objectweb.asm.ClassReader;
import com.esotericsoftware.reflectasm.shaded.org.objectweb.asm.ClassWriter;
import com.esotericsoftware.reflectasm.shaded.org.objectweb.asm.Label;
import com.esotericsoftware.reflectasm.shaded.org.objectweb.asm.MethodVisitor;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ClassWriterTest {
    private static final String GENERATED_CLASS_NAME =
            "com_esotericsoftware/kryo_shaded/generated/CommonSuperClassSubject";
    private static final String OBJECT = Object.class.getName().replace('.', '/');
    private static final String ARRAY_LIST = ArrayList.class.getName().replace('.', '/');
    private static final String LINKED_LIST = LinkedList.class.getName().replace('.', '/');
    private static final String ABSTRACT_LIST = AbstractList.class.getName().replace('.', '/');

    @Test
    void computesStackFramesByResolvingCommonSuperClassThroughClassLoader() {
        RecordingClassWriter writer = new RecordingClassWriter(ClassWriter.COMPUTE_FRAMES);
        writer.visit(V1_8, ACC_PUBLIC, GENERATED_CLASS_NAME, null, OBJECT, null);
        writeDefaultConstructor(writer);
        writeListFactoryMethod(writer);
        writer.visitEnd();

        byte[] bytecode = writer.toByteArray();
        ClassReader reader = new ClassReader(bytecode);

        assertThat(reader.getClassName()).isEqualTo(GENERATED_CLASS_NAME);
        assertThat(writer.commonSuperClasses()).contains(ABSTRACT_LIST);
    }

    private static void writeDefaultConstructor(ClassWriter writer) {
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        method.visitCode();
        method.visitVarInsn(ALOAD, 0);
        method.visitMethodInsn(INVOKESPECIAL, OBJECT, "<init>", "()V", false);
        method.visitInsn(RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static void writeListFactoryMethod(ClassWriter writer) {
        MethodVisitor method = writer.visitMethod(
                ACC_PUBLIC | ACC_STATIC, "newList", "(Z)Ljava/util/AbstractList;", null, null);
        Label linkedListBranch = new Label();
        Label returnValue = new Label();

        method.visitCode();
        method.visitVarInsn(ILOAD, 0);
        method.visitJumpInsn(IFEQ, linkedListBranch);
        method.visitTypeInsn(NEW, ARRAY_LIST);
        method.visitInsn(DUP);
        method.visitMethodInsn(INVOKESPECIAL, ARRAY_LIST, "<init>", "()V", false);
        method.visitJumpInsn(GOTO, returnValue);
        method.visitLabel(linkedListBranch);
        method.visitTypeInsn(NEW, LINKED_LIST);
        method.visitInsn(DUP);
        method.visitMethodInsn(INVOKESPECIAL, LINKED_LIST, "<init>", "()V", false);
        method.visitLabel(returnValue);
        method.visitInsn(ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static final class RecordingClassWriter extends ClassWriter {
        private final List<String> commonSuperClasses = new ArrayList<>();

        private RecordingClassWriter(int flags) {
            super(flags);
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            String commonSuperClass = super.getCommonSuperClass(type1, type2);
            commonSuperClasses.add(commonSuperClass);
            return commonSuperClass;
        }

        private List<String> commonSuperClasses() {
            return commonSuperClasses;
        }
    }
}
