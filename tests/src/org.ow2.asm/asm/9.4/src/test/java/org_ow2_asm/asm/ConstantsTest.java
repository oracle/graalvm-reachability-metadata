/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ow2_asm.asm;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConstantsTest {
    private static final String GENERATED_CLASS_NAME = "asm.coverage.GeneratedExperimentalClassVisitor";
    private static final String GENERATED_INTERNAL_NAME = GENERATED_CLASS_NAME.replace('.', '/');

    @Test
    void experimentalVisitorsLoadPreviewClassBytesFromTheirClassLoaderResources() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(isNativeImageRuntime());
        byte[] classBytes = createGeneratedVisitorClassBytes();
        PreviewClassLoader classLoader = new PreviewClassLoader(
                ConstantsTest.class.getClassLoader(),
                GENERATED_CLASS_NAME,
                classBytes,
                withPreviewMinorVersion(classBytes)
        );

        Class<? extends ClassVisitor> generatedType = classLoader.loadClass(GENERATED_CLASS_NAME)
                .asSubclass(ClassVisitor.class);
        ClassVisitor classVisitor = generatedType.getDeclaredConstructor().newInstance();

        assertThat(classVisitor).isNotNull();
        assertThat(classLoader.requestedResourceNames()).contains(GENERATED_INTERNAL_NAME + ".class");
    }

    @SuppressWarnings("deprecation")
    private static byte[] createGeneratedVisitorClassBytes() {
        ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(
                Opcodes.V1_5,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                GENERATED_INTERNAL_NAME,
                null,
                "org/objectweb/asm/ClassVisitor",
                null
        );

        MethodVisitor constructor = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitLdcInsn(Opcodes.ASM10_EXPERIMENTAL);
        constructor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "org/objectweb/asm/ClassVisitor",
                "<init>",
                "(I)V",
                false
        );
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(2, 1);
        constructor.visitEnd();

        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    private static byte[] withPreviewMinorVersion(byte[] classBytes) {
        byte[] previewClassBytes = classBytes.clone();
        previewClassBytes[4] = (byte) 0xFF;
        previewClassBytes[5] = (byte) 0xFF;
        return previewClassBytes;
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    private static final class PreviewClassLoader extends ClassLoader {
        private final String generatedClassName;
        private final byte[] generatedClassBytes;
        private final byte[] previewClassBytes;
        private final List<String> requestedResourceNames = new ArrayList<>();

        private PreviewClassLoader(
                ClassLoader parent,
                String generatedClassName,
                byte[] generatedClassBytes,
                byte[] previewClassBytes
        ) {
            super(parent);
            this.generatedClassName = generatedClassName;
            this.generatedClassBytes = generatedClassBytes;
            this.previewClassBytes = previewClassBytes;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!generatedClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, generatedClassBytes, 0, generatedClassBytes.length);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            requestedResourceNames.add(name);
            if ((GENERATED_INTERNAL_NAME + ".class").equals(name)) {
                return new ByteArrayInputStream(previewClassBytes);
            }
            return super.getResourceAsStream(name);
        }

        private List<String> requestedResourceNames() {
            return requestedResourceNames;
        }
    }
}
