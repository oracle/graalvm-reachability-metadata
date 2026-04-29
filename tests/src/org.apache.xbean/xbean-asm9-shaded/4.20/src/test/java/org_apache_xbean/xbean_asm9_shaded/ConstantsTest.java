/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_xbean.xbean_asm9_shaded;

import org.apache.xbean.asm9.ClassVisitor;
import org.apache.xbean.asm9.ClassWriter;
import org.apache.xbean.asm9.MethodVisitor;
import org.apache.xbean.asm9.Opcodes;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConstantsTest {
    private static final String GENERATED_VISITOR_NAME =
            "org_apache_xbean.xbean_asm9_shaded.GeneratedExperimentalVisitor";
    private static final String GENERATED_VISITOR_INTERNAL_NAME =
            GENERATED_VISITOR_NAME.replace('.', '/');
    private static final String GENERATED_VISITOR_RESOURCE =
            GENERATED_VISITOR_INTERNAL_NAME + ".class";

    @Test
    void experimentalApiChecksCallerBytecodeThroughClassLoaderResource() throws ClassNotFoundException {
        try {
            new GeneratedVisitorLoader().loadAndInitializeVisitor();
        } catch (UnsupportedOperationException | LinkageError classDefinitionUnavailable) {
            assertThatClassDefinitionIsUnavailable(classDefinitionUnavailable);
            assertThatThrownBy(ExperimentalClassVisitor::new).isInstanceOf(IllegalStateException.class);
        }
    }

    private static void assertThatClassDefinitionIsUnavailable(Throwable throwable) {
        String throwableClassName = throwable.getClass().getName();
        String message = String.valueOf(throwable.getMessage());
        if (!throwableClassName.contains("UnsupportedFeature") && !message.contains("Defining new classes")) {
            throw new AssertionError("Unexpected class definition failure", throwable);
        }
    }

    private static final class GeneratedVisitorLoader extends ClassLoader {
        private final byte[] classBytes;
        private final byte[] previewResourceBytes;

        private GeneratedVisitorLoader() {
            super(ConstantsTest.class.getClassLoader());
            this.classBytes = generateVisitorClass();
            this.previewResourceBytes = asPreviewClassResource(classBytes);
        }

        private void loadAndInitializeVisitor() throws ClassNotFoundException {
            Class.forName(GENERATED_VISITOR_NAME, true, this);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (GENERATED_VISITOR_RESOURCE.equals(name)) {
                return new ByteArrayInputStream(previewResourceBytes);
            }
            return super.getResourceAsStream(name);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!GENERATED_VISITOR_NAME.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, classBytes, 0, classBytes.length);
        }
    }

    private static byte[] generateVisitorClass() {
        ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                GENERATED_VISITOR_INTERNAL_NAME,
                null,
                "org/apache/xbean/asm9/ClassVisitor",
                null);
        writeConstructor(classWriter);
        writeStaticInitializer(classWriter);
        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    private static void writeConstructor(ClassWriter classWriter) {
        MethodVisitor constructor = classWriter.visitMethod(
                Opcodes.ACC_PUBLIC,
                "<init>",
                "()V",
                null,
                null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitLdcInsn(Opcodes.ASM10_EXPERIMENTAL);
        constructor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "org/apache/xbean/asm9/ClassVisitor",
                "<init>",
                "(I)V",
                false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(2, 1);
        constructor.visitEnd();
    }

    private static void writeStaticInitializer(ClassWriter classWriter) {
        MethodVisitor staticInitializer = classWriter.visitMethod(
                Opcodes.ACC_STATIC,
                "<clinit>",
                "()V",
                null,
                null);
        staticInitializer.visitCode();
        staticInitializer.visitTypeInsn(Opcodes.NEW, GENERATED_VISITOR_INTERNAL_NAME);
        staticInitializer.visitInsn(Opcodes.DUP);
        staticInitializer.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                GENERATED_VISITOR_INTERNAL_NAME,
                "<init>",
                "()V",
                false);
        staticInitializer.visitInsn(Opcodes.POP);
        staticInitializer.visitInsn(Opcodes.RETURN);
        staticInitializer.visitMaxs(2, 0);
        staticInitializer.visitEnd();
    }

    private static byte[] asPreviewClassResource(byte[] originalClassBytes) {
        byte[] previewClassBytes = Arrays.copyOf(originalClassBytes, originalClassBytes.length);
        previewClassBytes[4] = (byte) 0xFF;
        previewClassBytes[5] = (byte) 0xFF;
        return previewClassBytes;
    }

    private static final class ExperimentalClassVisitor extends ClassVisitor {
        private ExperimentalClassVisitor() {
            super(Opcodes.ASM10_EXPERIMENTAL);
        }
    }
}
